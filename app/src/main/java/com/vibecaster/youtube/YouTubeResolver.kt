package com.vibecaster.youtube

import com.vibecaster.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Resolves a YouTube video URL into a direct audio stream using NewPipe Extractor. */
object YouTubeResolver {

    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    @Volatile
    private var initialized = false

    private fun ensureInit() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(OkHttpDownloader())
            initialized = true
        }
    }

    /**
     * @param compact When true, picks a storage-optimized audio stream
     * (~128-160 kbps AAC — transparent quality at roughly half the size of
     * the top bitrate). When false, picks the highest available bitrate.
     */
    suspend fun resolve(url: String, compact: Boolean = false): Track = withContext(Dispatchers.IO) {
        ensureInit()
        // Music-search results come as music.youtube.com links; the regular
        // watch URL extracts far more reliably.
        val normalized = url.trim()
            .replace("music.youtube.com", "www.youtube.com")
            .substringBefore("&list=") // playlist context can break extraction

        val info = StreamInfo.getInfo(ServiceList.YouTube, normalized)

        val candidates = info.audioStreams.filter { it.isUrl && !it.content.isNullOrBlank() }

        val audio = if (compact) {
            // Storage-optimized pick: best stream within the 96-170 kbps
            // sweet spot, else whatever is closest to 140 kbps.
            candidates.filter { it.averageBitrate in 96..170 }.maxByOrNull { it.averageBitrate }
                ?: candidates.minByOrNull { abs(it.averageBitrate - 140) }
        } else {
            candidates.maxByOrNull { it.averageBitrate }
        }

        // Fallback: muxed (video+audio) progressive stream — ExoPlayer will
        // just play its audio track. Lowest resolution saves bandwidth.
        val muxed = if (audio == null) {
            info.videoStreams
                .filter { it.isUrl && !it.content.isNullOrBlank() }
                .minByOrNull { it.resolution?.filter(Char::isDigit)?.toIntOrNull() ?: Int.MAX_VALUE }
        } else null

        val streamUrl = audio?.content ?: muxed?.content
            ?: throw IllegalStateException(
                "No playable stream for this video " +
                    "(audio: ${info.audioStreams.size}, video: ${info.videoStreams.size})"
            )

        Track(
            id = normalized.hashCode().toLong(),
            title = info.name ?: "YouTube audio",
            artist = info.uploaderName ?: "YouTube",
            uri = streamUrl,
            artworkUri = info.thumbnails.maxByOrNull { it.width }?.url,
            durationMs = info.duration * 1000L,
            fromYouTube = true,
            sourceUrl = normalized
        )
    }

    /**
     * Search YouTube for songs. Returned tracks have an empty [Track.uri];
     * resolve [Track.sourceUrl] before playing.
     */
    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        ensureInit()
        val service = ServiceList.YouTube
        val info = try {
            // Prefer the music catalog for song results.
            SearchInfo.getInfo(
                service,
                service.searchQHFactory.fromQuery(
                    query,
                    listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
                    ""
                )
            )
        } catch (e: Throwable) {
            // Fall back to a regular video search if music search breaks.
            SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(query))
        }
        info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { item ->
                Track(
                    id = ("yt:" + item.url).hashCode().toLong(),
                    title = item.name ?: "Unknown",
                    artist = item.uploaderName ?: "YouTube",
                    uri = "",
                    artworkUri = item.thumbnails.maxByOrNull { it.width }?.url,
                    durationMs = item.duration * 1000L,
                    fromYouTube = true,
                    sourceUrl = item.url
                )
            }
    }

    private class OkHttpDownloader : Downloader() {

        private val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        override fun execute(request: Request): Response {
            val dataToSend = request.dataToSend()
            val body = dataToSend?.toRequestBody(null, 0, dataToSend.size)

            val builder = okhttp3.Request.Builder()
                .method(request.httpMethod(), body)
                .url(request.url())
                .addHeader("User-Agent", USER_AGENT)

            request.headers().forEach { (name, values) ->
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            }

            val response = client.newCall(builder.build()).execute()
            if (response.code == 429) {
                response.close()
                throw ReCaptchaException("reCaptcha challenge requested", request.url())
            }
            val responseBody = response.body?.string()
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBody,
                response.request.url.toString()
            )
        }
    }
}

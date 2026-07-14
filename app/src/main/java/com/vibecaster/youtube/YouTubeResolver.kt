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
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit

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

    suspend fun resolve(url: String): Track = withContext(Dispatchers.IO) {
        ensureInit()
        val info = StreamInfo.getInfo(ServiceList.YouTube, url.trim())
        val audio = info.audioStreams
            .filter { !it.content.isNullOrBlank() }
            .maxByOrNull { it.averageBitrate }
            ?: throw IllegalStateException("No audio stream found for this video")

        Track(
            id = url.hashCode().toLong(),
            title = info.name ?: "YouTube audio",
            artist = info.uploaderName ?: "YouTube",
            uri = audio.content,
            artworkUri = info.thumbnails.maxByOrNull { it.width }?.url,
            durationMs = info.duration * 1000L,
            fromYouTube = true
        )
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

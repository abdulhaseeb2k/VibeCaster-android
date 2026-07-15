package com.vibecaster.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.vibecaster.youtube.YouTubeResolver
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Offline downloads. Audio-only streams are saved (small size, full audio
 * quality) into app-specific storage, so no extra permissions are needed
 * and other apps cannot access the files.
 */
object DownloadRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun dir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads").apply { mkdirs() }

    private fun metaFile(context: Context) = File(dir(context), "downloads.json")

    @Synchronized
    fun load(context: Context): List<Track> {
        val f = metaFile(context)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length())
                .map { PlaylistRepository.trackFromJson(arr.getJSONObject(it)) }
                .filter { t ->
                    val path = Uri.parse(t.uri).path
                    path != null && File(path).exists()
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun saveMeta(context: Context, tracks: List<Track>) {
        val arr = JSONArray()
        tracks.forEach { arr.put(PlaylistRepository.trackToJson(it)) }
        metaFile(context).writeText(arr.toString())
    }

    /**
     * Downloads [track]'s stream (track.uri must be a resolved http URL)
     * and returns the local, offline-playable copy.
     */
    fun download(context: Context, track: Track, onProgress: (Float) -> Unit): Track {
        val d = dir(context)
        val safe = track.title
            .replace(Regex("[^A-Za-z0-9 _.-]"), "")
            .trim()
            .take(40)
            .ifBlank { "track" }
        val base = "${safe}_${abs(track.id)}"

        val request = Request.Builder()
            .url(track.uri)
            .header("User-Agent", YouTubeResolver.USER_AGENT)
            .build()

        val audioFile: File
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Download failed: HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("Empty response body")
            val contentType = resp.header("Content-Type").orEmpty()
            val ext = when {
                contentType.contains("mpeg") -> "mp3"
                contentType.contains("webm") -> "webm"
                else -> "m4a"
            }
            audioFile = File(d, "$base.$ext")
            val total = body.contentLength()
            body.byteStream().use { input ->
                FileOutputStream(audioFile).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }

        // Cache the artwork too, so it shows offline.
        var artUri = track.artworkUri
        if (artUri != null && artUri.startsWith("http")) {
            runCatching {
                client.newCall(Request.Builder().url(artUri!!).build()).execute().use { ar ->
                    if (ar.isSuccessful) {
                        val artFile = File(d, "$base.jpg")
                        ar.body?.byteStream()?.use { input ->
                            FileOutputStream(artFile).use { input.copyTo(it) }
                        }
                        artUri = Uri.fromFile(artFile).toString()
                    }
                }
            }
        }

        val local = track.copy(
            uri = Uri.fromFile(audioFile).toString(),
            artworkUri = artUri,
            fromYouTube = false
        )
        saveMeta(context, load(context).filterNot { it.sourceUrl == local.sourceUrl } + local)
        return local
    }

    /**
     * Copies a downloaded file into the public Music/VibeCaster folder via
     * MediaStore, so other apps (and the user) can access it.
     */
    fun exportToMusic(context: Context, track: Track): Boolean {
        val path = Uri.parse(track.uri).path ?: return false
        val src = File(path)
        if (!src.exists()) return false
        val ext = src.extension.ifBlank { "m4a" }
        val mime = when (ext) {
            "mp3" -> "audio/mpeg"
            "webm" -> "audio/webm"
            else -> "audio/mp4"
        }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "${track.title}.$ext")
            put(MediaStore.Audio.Media.TITLE, track.title)
            put(MediaStore.Audio.Media.ARTIST, track.artist)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/VibeCaster")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out ->
            src.inputStream().use { it.copyTo(out) }
        } ?: return false
        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }

    @Synchronized
    fun delete(context: Context, track: Track) {
        Uri.parse(track.uri).path?.let { path ->
            val f = File(path)
            f.delete()
            File(f.parentFile, f.nameWithoutExtension + ".jpg").delete()
        }
        saveMeta(context, load(context).filterNot { it.uri == track.uri })
    }
}

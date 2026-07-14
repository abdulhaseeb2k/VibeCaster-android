package com.vibecaster

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.PlaybackException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.vibecaster.data.AudiusRepository
import com.vibecaster.data.LocalAudioRepository
import com.vibecaster.data.Track
import com.vibecaster.player.PlaybackService
import com.vibecaster.player.PlayerHolder
import com.vibecaster.youtube.YouTubeResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val player: ExoPlayer = PlayerHolder.get(app)

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks = _tracks.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())

    private val _current = MutableStateFlow<Track?>(null)
    val current = _current.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _effectOn = MutableStateFlow(true)
    val effectOn = _effectOn.asStateFlow()

    private val _rotationSpeed = MutableStateFlow(0.12f)
    val rotationSpeed = _rotationSpeed.asStateFlow()

    private val _intensity = MutableStateFlow(0.9f)
    val intensity = _intensity.asStateFlow()

    private val _ytLoading = MutableStateFlow(false)
    val ytLoading = _ytLoading.asStateFlow()

    private val _ytError = MutableStateFlow<String?>(null)
    val ytError = _ytError.asStateFlow()

    private val _audiusResults = MutableStateFlow<List<Track>>(emptyList())
    val audiusResults = _audiusResults.asStateFlow()

    private val _audiusLoading = MutableStateFlow(false)
    val audiusLoading = _audiusLoading.asStateFlow()

    private val _audiusError = MutableStateFlow<String?>(null)
    val audiusError = _audiusError.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _current.value = _queue.value.getOrNull(player.currentMediaItemIndex) ?: _current.value
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback error", error)
                if (_current.value?.fromYouTube == true) {
                    _ytError.value =
                        "Playback failed (${error.errorCodeName}). The stream URL may have expired — resolve the link again."
                }
            }
        })
        // Position ticker
        viewModelScope.launch {
            while (true) {
                _positionMs.value = player.currentPosition.coerceAtLeast(0L)
                _durationMs.value = player.duration.coerceAtLeast(0L)
                delay(500)
            }
        }
        applyEffect()
    }

    fun loadLibrary() {
        viewModelScope.launch {
            _tracks.value = withContext(Dispatchers.IO) {
                LocalAudioRepository.load(getApplication())
            }
        }
    }

    fun play(track: Track, list: List<Track> = _tracks.value) {
        val queue = if (list.any { it.uri == track.uri }) list else listOf(track)
        _queue.value = queue
        val items = queue.map { t ->
            MediaItem.Builder()
                .setUri(t.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(t.artist)
                        .setArtworkUri(t.artworkUri?.toUri())
                        .build()
                )
                .build()
        }
        player.setMediaItems(items, queue.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0), 0L)
        player.prepare()
        player.play()
        _current.value = track
        // Keep playing in background via foreground media service.
        val ctx = getApplication<Application>()
        runCatching { ctx.startService(Intent(ctx, PlaybackService::class.java)) }
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause()
        else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    fun seekTo(ms: Long) = player.seekTo(ms)
    fun next() = player.seekToNextMediaItem()
    fun previous() = player.seekToPreviousMediaItem()

    fun setEffectOn(on: Boolean) {
        _effectOn.value = on
        applyEffect()
    }

    fun setRotationSpeed(v: Float) {
        _rotationSpeed.value = v
        applyEffect()
    }

    fun setIntensity(v: Float) {
        _intensity.value = v
        applyEffect()
    }

    private fun applyEffect() {
        PlayerHolder.processor.effectEnabled = _effectOn.value
        PlayerHolder.processor.rotationSpeed = _rotationSpeed.value
        PlayerHolder.processor.intensity = _intensity.value
    }

    fun playFromYouTube(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _ytLoading.value = true
            _ytError.value = null
            try {
                val track = YouTubeResolver.resolve(url)
                play(track, listOf(track))
            } catch (e: Exception) {
                Log.e(TAG, "YouTube resolve failed", e)
                val cause = e.cause?.message?.let { " — $it" } ?: ""
                _ytError.value = "${e.javaClass.simpleName}: ${e.message ?: "unknown error"}$cause"
            } finally {
                _ytLoading.value = false
            }
        }
    }

    fun searchAudius(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _audiusLoading.value = true
            _audiusError.value = null
            try {
                _audiusResults.value = AudiusRepository.search(query)
                if (_audiusResults.value.isEmpty()) _audiusError.value = "No results found."
            } catch (e: Exception) {
                Log.e(TAG, "Audius search failed", e)
                _audiusError.value = "${e.javaClass.simpleName}: ${e.message ?: "unknown error"}"
            } finally {
                _audiusLoading.value = false
            }
        }
    }

    companion object {
        private const val TAG = "VibeCaster"
    }
}

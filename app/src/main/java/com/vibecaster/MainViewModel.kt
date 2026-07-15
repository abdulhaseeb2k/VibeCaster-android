package com.vibecaster

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.vibecaster.data.AudiusRepository
import com.vibecaster.data.DownloadRepository
import com.vibecaster.data.LocalAudioRepository
import com.vibecaster.data.LyricsRepository
import com.vibecaster.data.RecentsRepository
import com.vibecaster.data.Playlist
import com.vibecaster.data.PlaylistRepository
import com.vibecaster.data.Track
import com.vibecaster.data.UpdateRepository
import com.vibecaster.player.PlaybackService
import com.vibecaster.player.PlayerHolder
import com.vibecaster.ui.AppTab
import com.vibecaster.ui.theme.ThemeMode
import com.vibecaster.youtube.YouTubeResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val player: ExoPlayer = PlayerHolder.get(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks = _tracks.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())

    private val _current = MutableStateFlow<Track?>(null)
    val current = _current.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    /** True while the player is loading/buffering from the network. */
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering = _isBuffering.asStateFlow()

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

    private val _ytResults = MutableStateFlow<List<Track>>(emptyList())
    val ytResults = _ytResults.asStateFlow()

    private val _audiusResults = MutableStateFlow<List<Track>>(emptyList())
    val audiusResults = _audiusResults.asStateFlow()

    private val _audiusLoading = MutableStateFlow(false)
    val audiusLoading = _audiusLoading.asStateFlow()

    private val _audiusError = MutableStateFlow<String?>(null)
    val audiusError = _audiusError.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists = _playlists.asStateFlow()

    private val _downloads = MutableStateFlow<List<Track>>(emptyList())
    val downloads = _downloads.asStateFlow()

    /** Per-track download progress (0..1), keyed by track id. */
    private val _downloadProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val downloadProgress = _downloadProgress.asStateFlow()

    /** When set, the UI must launch this system dialog to confirm file deletion. */
    private val _deleteRequest = MutableStateFlow<IntentSender?>(null)
    val deleteRequest = _deleteRequest.asStateFlow()

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString("theme", ThemeMode.VIBE.name)!!) }
            .getOrDefault(ThemeMode.VIBE)
    )
    val themeMode = _themeMode.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(prefs.getBoolean("is_logged_in", false))
    val isLoggedIn = _isLoggedIn.asStateFlow()

    fun login() {
        _isLoggedIn.value = true
        prefs.edit().putBoolean("is_logged_in", true).apply()
    }

    fun logout() {
        _isLoggedIn.value = false
        prefs.edit().putBoolean("is_logged_in", false).apply()
    }

    private val _tabOrder = MutableStateFlow(loadTabOrder())

    val tabOrder = _tabOrder.asStateFlow()

    /** Current playback queue (visible in the queue sheet). */
    val queue = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(0)
    val queueIndex = _queueIndex.asStateFlow()

    private val _shuffleOn = MutableStateFlow(false)
    val shuffleOn = _shuffleOn.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed = _playbackSpeed.asStateFlow()

    /** Remaining sleep-timer time in ms, or null when off. */
    private val _sleepRemainingMs = MutableStateFlow<Long?>(null)
    val sleepRemainingMs = _sleepRemainingMs.asStateFlow()
    private var sleepJob: Job? = null

    private val _recents = MutableStateFlow<List<Track>>(emptyList())
    val recents = _recents.asStateFlow()

    private val _lyrics = MutableStateFlow<String?>(null)
    val lyrics = _lyrics.asStateFlow()

    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading = _lyricsLoading.asStateFlow()

    private val _bassDb = MutableStateFlow(0f)
    val bassDb = _bassDb.asStateFlow()

    private val _trebleDb = MutableStateFlow(0f)
    val trebleDb = _trebleDb.asStateFlow()

    private val _reverse8d = MutableStateFlow(false)
    val reverse8d = _reverse8d.asStateFlow()

    /** Set when a newer GitHub release is available. */
    private val _updateInfo = MutableStateFlow<UpdateRepository.UpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog = _showUpdateDialog.asStateFlow()

    val appVersion: String by lazy {
        runCatching {
            getApplication<Application>().packageManager
                .getPackageInfo(getApplication<Application>().packageName, 0)
                .versionName
        }.getOrNull() ?: "1.0"
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                _isBuffering.value = state == Player.STATE_BUFFERING
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _queueIndex.value = player.currentMediaItemIndex
                _current.value = _queue.value.getOrNull(player.currentMediaItemIndex) ?: _current.value
                _lyrics.value = null
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
        loadPlaylists()
        loadDownloads()
        viewModelScope.launch(Dispatchers.IO) {
            _recents.value = RecentsRepository.load(getApplication())
        }
        // Silent update check on launch.
        checkForUpdates(manual = false)
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    private fun loadTabOrder(): List<AppTab> {
        val saved = prefs.getString("tab_order", null)
            ?.split(",")
            ?.mapNotNull { name -> runCatching { AppTab.valueOf(name) }.getOrNull() }
        return if (saved != null &&
            saved.size == AppTab.entries.size &&
            saved.toSet().size == saved.size
        ) saved else AppTab.entries.toList()
    }

    fun setTabOrder(order: List<AppTab>) {
        if (order.toSet() != AppTab.entries.toSet()) return
        _tabOrder.value = order
        prefs.edit().putString("tab_order", order.joinToString(",") { it.name }).apply()
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
        val startIndex = queue.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0)
        player.setMediaItems(items, startIndex, 0L)
        player.prepare()
        player.play()
        _queueIndex.value = startIndex
        _current.value = track
        _lyrics.value = null
        addRecent(track)
        // Keep playing in background via foreground media service.
        val ctx = getApplication<Application>()
        runCatching { ctx.startService(Intent(ctx, PlaybackService::class.java)) }
    }

    fun togglePlayPause() {
        when {
            player.isPlaying -> player.pause()
            // After a song (or queue) finishes, restart from the beginning.
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0L)
                player.play()
            }
            else -> {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
        }
    }

    fun seekTo(ms: Long) = player.seekTo(ms)
    fun next() = player.seekToNextMediaItem()
    fun previous() = player.seekToPreviousMediaItem()

    fun playQueueItem(index: Int) {
        if (index in 0 until player.mediaItemCount) {
            player.seekTo(index, 0L)
            player.play()
        }
    }

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        _shuffleOn.value = player.shuffleModeEnabled
    }

    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = player.repeatMode
    }

    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    /** Pass null to cancel the timer. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes == null) {
            _sleepRemainingMs.value = null
            return
        }
        sleepJob = viewModelScope.launch {
            var remaining = minutes * 60_000L
            while (remaining > 0) {
                _sleepRemainingMs.value = remaining
                delay(1000)
                remaining -= 1000
            }
            _sleepRemainingMs.value = null
            player.pause()
        }
    }

    fun setBass(db: Float) {
        _bassDb.value = db
        PlayerHolder.toneProcessor.bassDb = db
    }

    fun setTreble(db: Float) {
        _trebleDb.value = db
        PlayerHolder.toneProcessor.trebleDb = db
    }

    fun setReverse8d(on: Boolean) {
        _reverse8d.value = on
        PlayerHolder.processor.reverse = on
    }

    fun fetchLyrics() {
        val track = _current.value ?: return
        if (_lyrics.value != null || _lyricsLoading.value) return
        viewModelScope.launch {
            _lyricsLoading.value = true
            _lyrics.value = try {
                LyricsRepository.fetch(track.title, track.artist) ?: "Lyrics not found for this song."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Lyrics fetch failed", e)
                "Could not load lyrics. Check your connection."
            } finally {
                _lyricsLoading.value = false
            }
        }
    }

    private fun addRecent(track: Track) {
        val key = track.sourceUrl ?: track.uri
        // YouTube stream URLs expire, so store them without the URL —
        // replaying re-resolves from the source link.
        val toStore = if (track.fromYouTube) track.copy(uri = "") else track
        val updated = (listOf(toStore) + _recents.value.filterNot { (it.sourceUrl ?: it.uri) == key }).take(50)
        _recents.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            RecentsRepository.save(getApplication(), updated)
        }
    }

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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "YouTube resolve failed", e)
                val cause = e.cause?.message?.let { " — $it" } ?: ""
                _ytError.value = "${e.javaClass.simpleName}: ${e.message ?: "unknown error"}$cause"
            } finally {
                _ytLoading.value = false
            }
        }
    }

    /** Search YouTube's music catalog; results are resolved on tap via [playTrack]. */
    fun searchYouTube(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _ytLoading.value = true
            _ytError.value = null
            try {
                _ytResults.value = YouTubeResolver.search(query)
                if (_ytResults.value.isEmpty()) _ytError.value = "No results found."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "YouTube search failed", e)
                _ytError.value = "${e.javaClass.simpleName}: ${e.message ?: "unknown error"}"
            } finally {
                _ytLoading.value = false
            }
        }
    }

    /** Plays any track: YouTube tracks are resolved first, others play directly. */
    fun playTrack(track: Track, list: List<Track> = listOf(track)) {
        if (track.fromYouTube && track.uri.isBlank()) {
            playFromYouTube(track.sourceUrl ?: return)
        } else {
            play(track, list.filter { it.uri.isNotBlank() })
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Audius search failed", e)
                _audiusError.value = "${e.javaClass.simpleName}: ${e.message ?: "unknown error"}"
            } finally {
                _audiusLoading.value = false
            }
        }
    }

    // ---- Playlists ----

    fun loadPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            _playlists.value = PlaylistRepository.load(getApplication())
        }
    }

    private fun savePlaylists(updated: List<Playlist>) {
        _playlists.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            PlaylistRepository.save(getApplication(), updated)
        }
    }

    fun createPlaylist(name: String) {
        val n = name.trim()
        if (n.isBlank() || _playlists.value.any { it.name == n }) return
        savePlaylists(_playlists.value + Playlist(n, emptyList()))
    }

    fun deletePlaylist(name: String) {
        savePlaylists(_playlists.value.filterNot { it.name == name })
    }

    fun addToPlaylist(name: String, track: Track) = addAllToPlaylist(name, listOf(track))

    /** Adds several tracks at once, skipping ones already in the playlist. */
    fun addAllToPlaylist(name: String, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        savePlaylists(_playlists.value.map { p ->
            if (p.name != name) p
            else {
                val existing = p.tracks.map { it.sourceUrl ?: it.uri }.toSet()
                p.copy(tracks = p.tracks + tracks.filter { (it.sourceUrl ?: it.uri) !in existing })
            }
        })
    }

    fun removeFromPlaylist(name: String, track: Track) {
        val key = track.sourceUrl ?: track.uri
        savePlaylists(_playlists.value.map { p ->
            if (p.name == name) p.copy(tracks = p.tracks.filterNot { (it.sourceUrl ?: it.uri) == key })
            else p
        })
    }

    // ---- Offline downloads ----

    fun loadDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            _downloads.value = DownloadRepository.load(getApplication())
        }
    }

    fun isDownloaded(track: Track): Boolean {
        val key = track.sourceUrl ?: track.uri
        return _downloads.value.any { it.sourceUrl == key }
    }

    fun download(track: Track) {
        val key = track.sourceUrl ?: track.uri
        if (isDownloaded(track) || _downloadProgress.value.containsKey(track.id)) return
        viewModelScope.launch {
            _downloadProgress.value = _downloadProgress.value + (track.id to 0f)
            try {
                // YouTube tracks need a fresh, storage-optimized stream URL first.
                val prepared = if (track.fromYouTube) {
                    val resolved = YouTubeResolver.resolve(
                        track.sourceUrl ?: error("Missing video link"),
                        compact = true
                    )
                    resolved.copy(id = track.id, sourceUrl = key)
                } else {
                    track.copy(sourceUrl = key)
                }
                val local = withContext(Dispatchers.IO) {
                    DownloadRepository.download(getApplication(), prepared) { p ->
                        _downloadProgress.value = _downloadProgress.value + (track.id to p)
                    }
                }
                _downloads.value = _downloads.value.filterNot { it.sourceUrl == key } + local
                toast("Downloaded: ${track.title}")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Download failed", e)
                toast("Download failed: ${e.message ?: "unknown error"}")
            } finally {
                _downloadProgress.value = _downloadProgress.value - track.id
            }
        }
    }

    fun deleteDownload(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            DownloadRepository.delete(getApplication(), track)
            _downloads.value = _downloads.value.filterNot { it.uri == track.uri }
        }
    }

    /** Copies a downloaded song into the public Music folder (Music/VibeCaster). */
    fun exportDownload(track: Track) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { DownloadRepository.exportToMusic(getApplication(), track) }
                    .onFailure { Log.e(TAG, "Export failed", it) }
                    .getOrDefault(false)
            }
            toast(
                if (ok) "Exported to Music/VibeCaster: ${track.title}"
                else "Export failed"
            )
        }
    }

    // ---- Local file deletion (system confirmation required on Android 11+) ----

    fun requestDeleteTrack(track: Track) = requestDeleteTracks(listOf(track))

    /** One system confirmation dialog covers all given files. */
    fun requestDeleteTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        try {
            val resolver = getApplication<Application>().contentResolver
            val uris = tracks.map { it.uri.toUri() }
            val pending = MediaStore.createDeleteRequest(resolver, uris)
            _deleteRequest.value = pending.intentSender
        } catch (e: Exception) {
            Log.e(TAG, "Delete request failed", e)
        }
    }

    fun clearDeleteRequest() {
        _deleteRequest.value = null
    }

    fun checkForUpdates(manual: Boolean) {
        viewModelScope.launch {
            if (manual) toast("Checking for updates...")
            try {
                val info = UpdateRepository.check(appVersion)
                _updateInfo.value = info
                if (info != null) {
                    _showUpdateDialog.value = true
                } else if (manual) {
                    toast("You're on the latest version ($appVersion)")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Update check failed", e)
                if (manual) toast("Update check failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    private fun toast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "VibeCaster"
    }
}

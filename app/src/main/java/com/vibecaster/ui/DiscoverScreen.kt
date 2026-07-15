package com.vibecaster.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.vibecaster.MainViewModel
import com.vibecaster.data.Track
import com.vibecaster.ui.theme.Cyan
import com.vibecaster.ui.theme.Pink
import com.vibecaster.ui.theme.Violet

/** Search and stream free music from Audius. */
@UnstableApi
@Composable
fun DiscoverScreen(vm: MainViewModel, padding: PaddingValues, onOpenPlayer: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results by vm.audiusResults.collectAsStateWithLifecycle()
    val loading by vm.audiusLoading.collectAsStateWithLifecycle()
    val error by vm.audiusError.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()

    var addTarget by remember { mutableStateOf<Track?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Discover", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Free music from Audius — streams in 8D",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search songs or artists...") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Cyan) },
            trailingIcon = {
                if (loading) {
                    CircularProgressIndicator(
                        color = Violet,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    IconButton(onClick = { vm.searchAudius(query) }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search", tint = Violet)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.searchAudius(query) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Violet,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = Violet
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error ?: "", color = Pink, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(results, key = { it.id }) { track ->
                DiscoverRow(
                    track = track,
                    isCurrent = current?.uri == track.uri,
                    downloaded = downloads.any { it.sourceUrl == track.uri },
                    progress = downloadProgress[track.id],
                    onClick = {
                        vm.play(track, results)
                        onOpenPlayer()
                    },
                    onAddToPlaylist = { addTarget = track },
                    onDownload = { vm.download(track) }
                )
            }
        }
    }

    addTarget?.let { track ->
        AddToPlaylistDialog(
            playlists = playlists.map { it.name },
            onPick = { name ->
                vm.addToPlaylist(name, track)
                addTarget = null
            },
            onCreateAndAdd = { name ->
                vm.createPlaylist(name)
                vm.addToPlaylist(name, track)
                addTarget = null
            },
            onDismiss = { addTarget = null }
        )
    }
}

@Composable
private fun DiscoverRow(
    track: Track,
    isCurrent: Boolean,
    downloaded: Boolean,
    progress: Float?,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit
) {
    Surface(
        color = if (isCurrent) Violet.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            Artwork(model = track.artworkUri, size = 52.dp, corner = 12.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) Violet else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(4.dp))
            if (isCurrent) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = "Playing", tint = Pink)
            }
            when {
                progress != null -> DownloadProgressBadge(progress)
                downloaded -> Icon(
                    Icons.Rounded.DownloadDone,
                    contentDescription = "Downloaded",
                    tint = Cyan
                )
                else -> IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = "Download for offline",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onAddToPlaylist) {
                Icon(
                    Icons.Rounded.PlaylistAdd,
                    contentDescription = "Add to playlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

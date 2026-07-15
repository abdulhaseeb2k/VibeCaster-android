package com.vibecaster.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.vibecaster.MainViewModel
import com.vibecaster.data.Track
import com.vibecaster.ui.AppTab
import com.vibecaster.ui.theme.Pink
import com.vibecaster.ui.theme.ThemeMode
import com.vibecaster.ui.theme.Violet
import com.vibecaster.ui.theme.VioletDeep

@UnstableApi
@Composable
fun LibraryScreen(vm: MainViewModel, padding: PaddingValues, onOpenPlayer: () -> Unit) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (it) vm.loadLibrary()
    }

    LaunchedEffect(Unit) {
        if (granted) vm.loadLibrary() else launcher.launch(permission)
    }

    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val deleteRequest by vm.deleteRequest.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()

    // Multi-select: long-press any song to start selecting.
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()
    fun toggle(track: Track) {
        selectedIds = if (track.id in selectedIds) selectedIds - track.id else selectedIds + track.id
    }

    // Back gesture clears the selection first.
    BackHandler(enabled = selectionMode) { selectedIds = emptySet() }

    // Library view: 0 = all songs, 1 = downloads, 2 = recents.
    var libView by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    var addTracks by remember { mutableStateOf<List<Track>?>(null) }
    var deleteTracks by remember { mutableStateOf<List<Track>?>(null) }
    var deleteDownloadTarget by remember { mutableStateOf<Track?>(null) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showTabOrder by remember { mutableStateOf(false) }
    val tabOrder by vm.tabOrder.collectAsStateWithLifecycle()

    // System confirmation dialog for deleting media files (one dialog for all).
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.loadLibrary()
            selectedIds = emptySet()
        }
        vm.clearDeleteRequest()
    }
    LaunchedEffect(deleteRequest) {
        deleteRequest?.let { deleteLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        // Header — switches to a selection toolbar in multi-select mode.
        if (selectionMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                IconButton(onClick = { selectedIds = emptySet() }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear selection", tint = Violet)
                }
                Text(
                    "${selectedIds.size} selected",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    addTracks = tracks.filter { it.id in selectedIds }
                }) {
                    Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Add selected to playlist", tint = Violet)
                }
                IconButton(onClick = {
                    deleteTracks = tracks.filter { it.id in selectedIds }
                }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete selected", tint = Pink)
                }
            }
        } else {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Vibe Caster",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "Put on your headphones and let the sound spin 🎧",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Violet)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    ModeChip("All songs", libView == 0) { libView = 0 }
                    Spacer(Modifier.width(8.dp))
                    ModeChip("Downloads (${downloads.size})", libView == 1) { libView = 1 }
                    Spacer(Modifier.width(8.dp))
                    ModeChip("Recents", libView == 2) { libView = 2 }
                }
            }
        }

        when {
            libView == 2 && !selectionMode -> {
                val recents by vm.recents.collectAsStateWithLifecycle()
                if (recents.isEmpty()) {
                    EmptyState(
                        icon = { Icon(Icons.Rounded.History, null, tint = Violet, modifier = Modifier.size(48.dp)) },
                        title = "Nothing played yet",
                        subtitle = "Songs you play will show up here."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(recents, key = { it.sourceUrl ?: it.uri }) { track ->
                            RecentRow(
                                track = track,
                                isCurrent = current?.let { c ->
                                    (c.sourceUrl ?: c.uri) == (track.sourceUrl ?: track.uri)
                                } == true,
                                onClick = {
                                    vm.playTrack(track)
                                    onOpenPlayer()
                                },
                                onAddToPlaylist = { addTracks = listOf(track) }
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }

            libView == 1 && !selectionMode -> {
                if (downloads.isEmpty()) {
                    EmptyState(
                        icon = { Icon(Icons.Rounded.DownloadDone, null, tint = Violet, modifier = Modifier.size(48.dp)) },
                        title = "No downloads yet",
                        subtitle = "Use the download button on YouTube or Discover songs to save them for offline."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(downloads, key = { it.uri }) { track ->
                            DownloadRow(
                                track = track,
                                isCurrent = current?.uri == track.uri,
                                onClick = {
                                    vm.play(track, downloads)
                                    onOpenPlayer()
                                },
                                onAddToPlaylist = { addTracks = listOf(track) },
                                onExport = { vm.exportDownload(track) },
                                onDelete = { deleteDownloadTarget = track }
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }

            !granted -> EmptyState(
                icon = { Icon(Icons.Rounded.MusicOff, null, tint = Violet, modifier = Modifier.size(48.dp)) },
                title = "Storage permission needed",
                subtitle = "Allow access to show the songs on your phone."
            ) {
                Button(
                    onClick = { launcher.launch(permission) },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletDeep)
                ) { Text("Allow", color = Color.White) }
            }

            tracks.isEmpty() -> EmptyState(
                icon = { Icon(Icons.Rounded.MusicOff, null, tint = Violet, modifier = Modifier.size(48.dp)) },
                title = "No songs found",
                subtitle = "Add music files to your phone, or try the YouTube tab."
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isCurrent = current?.uri == track.uri,
                        selectionMode = selectionMode,
                        selected = track.id in selectedIds,
                        onClick = {
                            if (selectionMode) toggle(track)
                            else {
                                vm.play(track)
                                onOpenPlayer()
                            }
                        },
                        onLongClick = { toggle(track) },
                        onAddToPlaylist = { addTracks = listOf(track) },
                        onDelete = { deleteTracks = listOf(track) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    addTracks?.let { list ->
        AddToPlaylistDialog(
            playlists = playlists.map { it.name },
            onPick = { name ->
                vm.addAllToPlaylist(name, list)
                addTracks = null
                selectedIds = emptySet()
            },
            onCreateAndAdd = { name ->
                vm.createPlaylist(name)
                vm.addAllToPlaylist(name, list)
                addTracks = null
                selectedIds = emptySet()
            },
            onDismiss = { addTracks = null }
        )
    }

    deleteTracks?.let { list ->
        AlertDialog(
            onDismissRequest = { deleteTracks = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text(if (list.size == 1) "Delete song?" else "Delete ${list.size} songs?") },
            text = {
                Text(
                    if (list.size == 1) {
                        "\"${list.first().title}\" will be permanently deleted from your phone."
                    } else {
                        "${list.size} songs will be permanently deleted from your phone."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.requestDeleteTracks(list)
                    deleteTracks = null
                }) { Text("Delete", color = Pink) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTracks = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    deleteDownloadTarget?.let { track ->
        AlertDialog(
            onDismissRequest = { deleteDownloadTarget = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Remove download?") },
            text = { Text("\"${track.title}\" will be removed from offline storage.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteDownload(track)
                    deleteDownloadTarget = null
                }) { Text("Remove", color = Pink) }
            },
            dismissButton = {
                TextButton(onClick = { deleteDownloadTarget = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showSettings) {
        SettingsDialog(
            currentTheme = themeMode,
            appVersion = vm.appVersion,
            onOpenTheme = {
                showSettings = false
                showThemePicker = true
            },
            onOpenTabOrder = {
                showSettings = false
                showTabOrder = true
            },
            onCheckUpdates = {
                showSettings = false
                vm.checkForUpdates(manual = true)
            },
            onDismiss = { showSettings = false }
        )
    }

    if (showThemePicker) {
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Theme") },
            text = {
                Column {
                    ThemeOption("Vibe (purple)", themeMode == ThemeMode.VIBE) {
                        vm.setThemeMode(ThemeMode.VIBE); showThemePicker = false
                    }
                    ThemeOption("Dark (black)", themeMode == ThemeMode.DARK) {
                        vm.setThemeMode(ThemeMode.DARK); showThemePicker = false
                    }
                    ThemeOption("Light (white)", themeMode == ThemeMode.LIGHT) {
                        vm.setThemeMode(ThemeMode.LIGHT); showThemePicker = false
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemePicker = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showTabOrder) {
        TabOrderDialog(
            order = tabOrder,
            onReorder = { vm.setTabOrder(it) },
            onDismiss = { showTabOrder = false }
        )
    }
}

@Composable
private fun SettingsDialog(
    currentTheme: ThemeMode,
    appVersion: String,
    onOpenTheme: () -> Unit,
    onOpenTabOrder: () -> Unit,
    onCheckUpdates: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Settings") },
        text = {
            Column {
                SettingsRow(
                    title = "Theme",
                    subtitle = when (currentTheme) {
                        ThemeMode.VIBE -> "Vibe (purple)"
                        ThemeMode.DARK -> "Dark (black)"
                        ThemeMode.LIGHT -> "Light (white)"
                    },
                    onClick = onOpenTheme
                )
                SettingsRow(
                    title = "Tab order",
                    subtitle = "Arrange the bottom menu",
                    onClick = onOpenTabOrder
                )
                SettingsRow(
                    title = "Check for updates",
                    subtitle = "Current version: v$appVersion",
                    onClick = onCheckUpdates
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecentRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    Surface(
        color = if (isCurrent) VioletDeep.copy(alpha = 0.22f)
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
            if (isCurrent) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = "Playing", tint = Pink)
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

@Composable
private fun TabOrderDialog(
    order: List<AppTab>,
    onReorder: (List<AppTab>) -> Unit,
    onDismiss: () -> Unit
) {
    // Local working copy so tiles move smoothly while dragging.
    var items by remember(order) { mutableStateOf(order) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var pointerX by remember { mutableFloatStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Tab order") },
        text = {
            Column {
                Text(
                    "Drag an icon left or right to rearrange. The first tab opens on launch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val slot = maxWidth / items.size
                    val slotPx = with(LocalDensity.current) { slot.toPx() }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        dragIndex = (offset.x / slotPx).toInt().coerceIn(0, items.lastIndex)
                                        pointerX = offset.x
                                    },
                                    onDragEnd = { dragIndex = -1 },
                                    onDragCancel = { dragIndex = -1 }
                                ) { change, amount ->
                                    change.consume()
                                    pointerX = (pointerX + amount.x)
                                        .coerceIn(0f, slotPx * items.size - 1f)
                                    val target = (pointerX / slotPx).toInt().coerceIn(0, items.lastIndex)
                                    if (dragIndex != -1 && target != dragIndex) {
                                        items = items.toMutableList().apply {
                                            add(target, removeAt(dragIndex))
                                        }
                                        dragIndex = target
                                    }
                                }
                            }
                    ) {
                        items.forEachIndexed { i, t ->
                            val isDragging = i == dragIndex
                            val offsetX = if (isDragging) pointerX - (i * slotPx + slotPx / 2) else 0f
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(slot)
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer {
                                        translationX = offsetX
                                        scaleX = if (isDragging) 1.15f else 1f
                                        scaleY = if (isDragging) 1.15f else 1f
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(
                                            if (isDragging) VioletDeep else Violet.copy(alpha = 0.15f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        tabIcon(t),
                                        contentDescription = t.label,
                                        tint = if (isDragging) Color.White else Violet
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    t.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onReorder(items)
                onDismiss()
            }) { Text("Done", color = Pink) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

private fun tabIcon(t: AppTab) = when (t) {
    AppTab.YOUTUBE -> Icons.Rounded.SmartDisplay
    AppTab.DISCOVER -> Icons.Rounded.Explore
    AppTab.PLAYLISTS -> Icons.AutoMirrored.Rounded.QueueMusic
    AppTab.LIBRARY -> Icons.Rounded.LibraryMusic
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Icon(
            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) Pink else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) VioletDeep.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun EmptyState(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .background(
                    Brush.linearGradient(listOf(VioletDeep.copy(0.3f), Pink.copy(0.2f))),
                    CircleShape
                )
        ) { icon() }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = when {
            selected -> Violet.copy(alpha = 0.30f)
            isCurrent -> VioletDeep.copy(alpha = 0.22f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
            if (selectionMode) {
                Icon(
                    if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (selected) "Selected" else "Not selected",
                    tint = if (selected) Pink else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
            } else {
                if (isCurrent) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = "Playing", tint = Pink)
                } else {
                    Text(
                        formatTime(track.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onAddToPlaylist) {
                    Icon(
                        Icons.Rounded.PlaylistAdd,
                        contentDescription = "Add to playlist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete song",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = if (isCurrent) VioletDeep.copy(alpha = 0.22f)
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
            if (isCurrent) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = "Playing", tint = Pink)
            }
            IconButton(onClick = onAddToPlaylist) {
                Icon(
                    Icons.Rounded.PlaylistAdd,
                    contentDescription = "Add to playlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onExport) {
                Icon(
                    Icons.Rounded.SaveAlt,
                    contentDescription = "Export to phone Music folder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Remove download",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

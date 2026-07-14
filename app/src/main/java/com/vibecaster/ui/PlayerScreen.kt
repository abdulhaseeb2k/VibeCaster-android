package com.vibecaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.automirrored.rounded._360
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.vibecaster.MainViewModel
import com.vibecaster.ui.theme.Cyan
import com.vibecaster.ui.theme.DeepSpace
import com.vibecaster.ui.theme.Pink
import com.vibecaster.ui.theme.SurfaceCard
import com.vibecaster.ui.theme.Violet
import com.vibecaster.ui.theme.VioletDeep

@UnstableApi
@Composable
fun PlayerScreen(vm: MainViewModel, onClose: () -> Unit) {
    val current by vm.current.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val position by vm.positionMs.collectAsStateWithLifecycle()
    val duration by vm.durationMs.collectAsStateWithLifecycle()
    val effectOn by vm.effectOn.collectAsStateWithLifecycle()
    val speed by vm.rotationSpeed.collectAsStateWithLifecycle()
    val intensity by vm.intensity.collectAsStateWithLifecycle()

    // Disc rotation — spins only while playing, speed matches 8D effect if on.
    var angle by remember { mutableFloatStateOf(0f) }
    val currentRotationSpeed by rememberUpdatedState(if (effectOn) speed else 0.08f)
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var last = 0L
            withFrameNanos { last = it }
            while (true) {
                withFrameNanos { now ->
                    angle = (angle + (now - last) / 1_000_000_000f * currentRotationSpeed * 360f) % 360f
                    last = now
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2A1650), Color(0xFF160C2B), DeepSpace)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close", tint = Color.White)
            }
            Text(
                "NOW PLAYING",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(20.dp))

        // Rotating disc with glow ring
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(292.dp)
                    .background(
                        Brush.sweepGradient(
                            listOf(Violet.copy(0.45f), Pink.copy(0.35f), Cyan.copy(0.35f), Violet.copy(0.45f))
                        ),
                        CircleShape
                    )
            )
            Box(
                Modifier
                    .size(276.dp)
                    .background(DeepSpace, CircleShape)
            )
            Box(modifier = Modifier.rotate(angle)) {
                Artwork(
                    model = current?.artworkUri,
                    size = 256.dp,
                    corner = 128.dp
                )
            }
            // Center hole like a vinyl
            Box(
                Modifier
                    .size(26.dp)
                    .background(DeepSpace, CircleShape)
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            current?.title ?: "Nothing playing yet",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            current?.artist ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(16.dp))

        // Seek bar
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(0f) }
        val progress = if (duration > 0) position.toFloat() / duration else 0f
        Slider(
            value = if (dragging) dragValue else progress.coerceIn(0f, 1f),
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                vm.seekTo((dragValue * duration).toLong())
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = Pink,
                activeTrackColor = Violet,
                inactiveTrackColor = SurfaceCard
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatTime(if (dragging) (dragValue * duration).toLong() else position),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatTime(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))

        // Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            IconButton(onClick = { vm.previous() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(76.dp)
                    .background(Brush.linearGradient(listOf(VioletDeep, Pink)), CircleShape)
            ) {
                IconButton(onClick = { vm.togglePlayPause() }, modifier = Modifier.size(76.dp)) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
            IconButton(onClick = { vm.next() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        // 8D control panel
        Surface(
            color = SurfaceCard.copy(alpha = 0.75f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded._360, null, tint = Cyan)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "8D Effect",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = effectOn,
                        onCheckedChange = { vm.setEffectOn(it) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = VioletDeep,
                            checkedThumbColor = Color.White
                        )
                    )
                }
                if (effectOn) {
                    Spacer(Modifier.height(6.dp))
                    LabeledSlider(
                        label = "Rotation speed",
                        value = speed,
                        range = 0.05f..0.5f,
                        onChange = { vm.setRotationSpeed(it) }
                    )
                    Spacer(Modifier.height(12.dp))
                    LabeledSlider(
                        label = "Depth",
                        value = intensity,
                        range = 0.2f..1f,
                        onChange = { vm.setIntensity(it) }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Cyan,
                activeTrackColor = Cyan,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

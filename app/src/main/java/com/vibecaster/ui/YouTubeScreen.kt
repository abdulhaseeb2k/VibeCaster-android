package com.vibecaster.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.vibecaster.MainViewModel
import com.vibecaster.ui.theme.Cyan
import com.vibecaster.ui.theme.Pink
import com.vibecaster.ui.theme.Violet
import com.vibecaster.ui.theme.VioletDeep

@UnstableApi
@Composable
fun YouTubeScreen(vm: MainViewModel, padding: PaddingValues) {
    var url by remember { mutableStateOf("") }
    val loading by vm.ytLoading.collectAsStateWithLifecycle()
    val error by vm.ytError.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Listen from YouTube",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            "Paste a video link — its audio will play in 8D",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            placeholder = { Text("https://youtube.com/watch?v=...") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Rounded.SmartDisplay, null, tint = Pink) },
            trailingIcon = {
                IconButton(onClick = {
                    clipboard.getText()?.text?.let { url = it }
                }) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste", tint = Violet)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Violet,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = Violet
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { vm.playFromYouTube(url) },
            enabled = !loading && url.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VioletDeep),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text("Extracting audio...", color = Color.White)
            } else {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Play in 8D", color = Color.White)
            }
        }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                error ?: "",
                color = Pink,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(32.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(Brush.linearGradient(listOf(Violet, Cyan)), CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Tip: Use headphones for the best 8D experience.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Note: YouTube extraction is for personal use only.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

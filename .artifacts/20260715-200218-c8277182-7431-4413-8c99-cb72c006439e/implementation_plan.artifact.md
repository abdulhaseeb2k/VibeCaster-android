# Fix YouTube Background Playback and Keyboard Dismissal

This plan addresses two issues:
1. The software keyboard remains open after searching or selecting a song on the YouTube tab.
2. Media controls (notification/lock screen) are not appearing or working correctly when playing from the YouTube tab and backgrounding/locking the app.

## Proposed Changes

### UI Components

#### [YouTubeScreen.kt](file:///E:/2026-Project/AndroidProjects/8DMusic/VibeCaster/app/src/main/java/com/vibecaster/ui/YouTubeScreen.kt)

- Dismiss the keyboard on search submission and track selection.
- Use `LocalSoftwareKeyboardController` for dismissal.

```kotlin
@UnstableApi
@Composable
fun YouTubeScreen(vm: MainViewModel, padding: PaddingValues, onOpenPlayer: () -> Unit) {
    // ...
    val keyboardController = LocalSoftwareKeyboardController.current

    fun submit() {
        val q = query.trim()
        if (q.isBlank()) return
        keyboardController?.hide() // DISMISS KEYBOARD
        // ...
    }

    // ...
    YtResultRow(
        // ...
        onClick = {
            keyboardController?.hide() // DISMISS KEYBOARD
            vm.playTrack(track)
            onOpenPlayer()
        },
        // ...
    )
}
```

### Media Playback Components

#### [PlaybackService.kt](file:///E:/2026-Project/AndroidProjects/8DMusic/VibeCaster/app/src/main/java/com/vibecaster/player/PlaybackService.kt)

- Ensure the `MediaSession` is correctly configured with a `SessionActivity` (already implemented, but verifying).
- Add a `MediaSession.Callback` if necessary to handle custom actions, though default controls should work with `ExoPlayer`.
- Ensure the service is robustly handling the `mediaPlayback` foreground type.

#### [MainViewModel.kt](file:///E:/2026-Project/AndroidProjects/8DMusic/VibeCaster/app/src/main/java/com/vibecaster/MainViewModel.kt)

- Ensure `play()` starts the `PlaybackService` using `startForegroundService` (on newer Android versions) if not already handled by `MediaSessionService`.
- Verify that `MediaMetadata` for YouTube tracks includes enough info for the system notification.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure compilation.

### Manual Verification
1. **Keyboard**: Go to YouTube tab, search for a song, verify keyboard closes. Select a song, verify keyboard closes.
2. **Notification**: Play a song from YouTube, background the app. Verify notification with Play/Pause, Next, Previous appears.
3. **Lock Screen**: Lock the phone while YouTube music is playing. Verify controls are visible and functional on the lock screen.

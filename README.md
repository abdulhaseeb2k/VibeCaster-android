# VibeCaster 🎧

A modern Android music player that plays any song in **real-time 8D audio** — from local files or YouTube links.

## Features

- **Audio Player** — all songs on your phone (MediaStore), album art, background playback with a media notification
- **Real-time 8D Effect** — a custom Media3 `AudioProcessor` rotates the sound around your head. Rotation speed and depth adjust live (no conversion, no waiting)
- **YouTube Playback** — paste a video link; NewPipe Extractor resolves the audio stream and plays it in 8D
- **Modern UI** — Jetpack Compose, dark gradient theme, rotating vinyl disc, mini player

## Build

Open the `VibeCaster` folder in Android Studio → Gradle sync → Run ▶

- minSdk 31 (Android 12+), targetSdk 37
- Package: `com.vibecaster`

## Architecture

```
audio/EightDAudioProcessor.kt   <- 8D DSP (equal-power rotating pan + crossfeed + distance LFO)
player/PlayerHolder.kt          <- Single ExoPlayer with the processor wired into its audio sink
player/PlaybackService.kt       <- MediaSessionService (background playback + notification)
youtube/YouTubeResolver.kt      <- NewPipe Extractor + OkHttp downloader
data/LocalAudioRepository.kt    <- Local songs via MediaStore
ui/                             <- Compose screens (Library, YouTube, Player)
```

## Notes

- The 8D effect is only noticeable with **headphones**
- YouTube extraction may be against YouTube's ToS — personal use only
- YouTube stream URLs expire after a few hours (resolve the link again if playback fails later)

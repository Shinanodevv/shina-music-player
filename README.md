# 🦊 Shina Music Player

A native Android music player built with Kotlin. The APK is built entirely by GitHub Actions — no local Android SDK needed.

## Features

- Scan and play music from the device library (MediaStore)
- Search by title / artist, sort by title / artist / duration
- Play / pause, next / previous, seek bar with elapsed / total time
- Shuffle and repeat (off / all / one)
- Favorites (❤️) with a favorites-only view
- Background playback with notification controls (prev / play-pause / next)
- Album art, playback speed (0.75x / 1.0x / 1.25x / 1.5x)
- Built-in equalizer (device audio session)
- Sleep timer (5 / 10 / 15 / 30 / 60 minutes)

## Download the APK

1. Open the **Actions** tab in this repo
2. Open the latest successful **Build APK** run
3. Download the `ShinaMusicPlayer-apk` artifact, unzip it, and install `app-debug.apk`

This is a debug APK, so Android may ask you to allow installing from your browser / files app.

## Build

Every push to `main` runs `.github/workflows/build-apk.yml`, which sets up JDK 17 + Gradle on the GitHub runner, generates the Gradle wrapper, and runs `./gradlew assembleDebug`.

## Permissions

- `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (older) — read the music library
- `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — playback notification & background play

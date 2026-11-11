# ReelVault — Your Reels. Your Phone. Always Offline.

Offline vertical-reels player for videos already on your phone. No internet permission, no login, no ads.
Originals are only ever read — never modified or deleted.

## Build (Android Studio)
1. Install Android Studio (Ladybug 2024.2+ or newer) with JDK 17 and Android SDK 35.
2. File ▸ Open ▸ select this `ReelVault` folder, wait for Gradle sync (needs internet once to download libraries).
3. Run ▸ Run 'app' on your phone (USB debugging on), or Build ▸ Build APK(s).
4. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
   Command line: `./gradlew assembleDebug` (run `gradle wrapper --gradle-version 8.9` once if no wrapper script).

## Features
- Home: thumbnails, duration, filename, search, sort (newest/oldest/name), grid/list, continue watching
- Reels player (Media3 ExoPlayer): swipe up/down, tap pause, double-tap like, like/save/share/more,
  progress seek, speed 0.5–2x, volume, full-screen, caption editing, saved position, loop or auto-next
- Library: MediaStore auto-scan (Download, Movies, Instagram, WhatsApp Video…), folder list, SAF folder picker, refresh
- Favorites: save/remove, "Play all" continuous playback
- Profile: your name, @username, bio, photo (shown on your reels), library stats
- Settings: system/dark/light theme, auto-scan, remember last video, loop, default speed, folders, clear cache
- Liquid-glass UI with Haze blur (Android 12+; older versions fall back to frosted tint), spring animations

## Low-RAM design (6 GB phones)
- ExoPlayer streams from disk, small buffers, one player instance, next item only
- Coil: 10 % memory cache, RGB565, 150 MB disk thumbnail cache
- Blur is used only on the header, nav bar and Settings/Profile cards; video grid cards use cheap tint + border
- Reels player has no live blur (video surface) — glass there is a translucent frosted tint

## Notes
- Permission: READ_MEDIA_VIDEO (Android 13+) / READ_EXTERNAL_STORAGE (≤12). Extra folders use the system picker.
- Haze is pinned to 1.0.2. If a future version changes `hazeChild`/`HazeStyle`, adjust `Glass.kt` only.
- Package: `com.reelvault.app`, minSdk 26, target/compile SDK 35.

## Logo
Original ReelVault mark: a vault-dial ring (gap + pink indicator dot) around a "V" that is also a play
button turned downward, with a small play cut-out inside. Files: `res/drawable/ic_launcher_foreground.xml`,
`res/drawable/ic_logo_mark.xml` (used in the app header). The adaptive icon also has a monochrome layer.

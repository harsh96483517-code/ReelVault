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

## Build on GitHub (no PC needed)
1. Create a new GitHub repo and upload the *contents* of this folder (app/, build.gradle.kts, .github/ …).
2. Open the **Actions** tab ▸ "Build debug APK" ▸ **Run workflow** (it also runs on every push).
3. When it turns green, open the run ▸ **Artifacts** ▸ download `ReelVault-debug-apk`, unzip, install `app-debug.apk`.

## If the app closes on open
The next launch shows the crash text on screen (Copy error / Retry in safe mode). Send that text to get a fix.


## Releases & in-app updates
- CI signs every APK with the fixed key in `app/reelvault.keystore`, so updates install over the old version.
  (Builds signed with an older random debug key must be uninstalled once.)
- To ship an update: GitHub ▸ Releases ▸ Draft a new release ▸ create tag like `v1.1` ▸ Publish.
  `release.yml` builds the APK and attaches it. Installed apps (with internet) show an "Update available" popup.
- The repo is read from CI automatically; you can also type it in Settings ▸ App updates. The repo must be public.
- INTERNET is only used for that update check/download. Android will ask once to "allow install unknown apps".
- Security note: the keystore is committed for convenience. For a public repo, move it to GitHub Secrets later.

## Dock & Reels
Bottom dock is icons only. The center ▶ button starts Reels directly; Settings ▸ Direct Reels button
chooses Random or In order (continues from last played when "Remember last played" is on).


## Hidden videos, app lock, black & white theme
- Long-press any video (Home or Favorites) to select several. Top bar: select all, favorite, **hide**.
  Hidden videos vanish from Home, Library counts, Favorites, search, stats and random Reels.
  They live only in Profile ▸ Hidden vault (opens after pattern or fingerprint). Select there to Unhide.
- Hiding is inside ReelVault only: the original files are not moved or changed, so phone Gallery / file managers can still show them.
- Settings ▸ App lock: set a pattern (draw twice), optional fingerprint, auto-lock delay (instantly / 1 min / 5 min),
  change or remove. 5 wrong patterns = 30 second wait. With a lock set, the "recent apps" preview is hidden and the vault blocks screenshots.
- The whole UI is black & white (dark = black with white accents, light = clean white cards with black accents).
- Database upgraded to v2 (adds `hidden`); existing favorites and positions are kept.


## Logo & liquid-water feel
- App icon (outer) = your full "Reel" logo on white; in-app header (inner) = the "Reel" chrome wordmark only (no text).
  The wordmark has a dark-theme variant (inverted) so it stays visible on black.
  Files: `res/drawable-nodpi/wordmark_light.png`, `wordmark_dark.png`, `logo_app.png`, `ic_launcher_foreground.png`.
- Every pressable control squashes elastically and sends a water ripple from the touch point.
- Tabs switch with a springy scale-in; the dock lens stretches like a water drop as it moves;
  videos glide into their new places with a bounce when you hide/unhide/sort.


## Profile & Instagram
- Header bar is always black with the white chrome "Reel" wordmark (both themes).
- Profile: banner + overlapping avatar, name, @handle, bio, stats, vault and settings rows.
- Edit profile has an "Instagram username" field. The profile shows an `@username` button that opens your Instagram profile
  (Instagram app if installed, otherwise the browser). No Instagram login or API is used by ReelVault.
- Videos you save from Instagram (e.g. your own reels via the reel's ⋯ > Download, or Settings > Your activity > Download your information)
  land in your phone storage and ReelVault finds them on the next scan.


## Delete, bin & black glass
- Long-press to select videos > trash icon: **Move to bin** (Android trash, ~30 days) or **Delete permanently**.
  In the player: More (⋮) > Move to bin / Delete permanently. Library > Bin: restore or erase for good / empty the bin.
- Android 11+ shows its own "Allow ReelVault to delete?" confirmation (required by Android). Bin needs Android 11+.
  Videos in folders you added with the folder picker can only be deleted permanently (and the folder must be re-added once
  so ReelVault has write access).
- Dark theme and everything over video now uses black liquid glass: smoked body, top highlight, bottom caustic,
  inner edge ring, specular rim and glint. Light theme stays clean white cards.

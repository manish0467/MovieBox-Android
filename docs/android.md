# Android App

MovieBox ships a native Android application that reuses the Rust core engine
and replaces the terminal UI with Jetpack Compose and Media3 (ExoPlayer).

```text
┌─────────────────────────────┐        ┌──────────────────────────────┐
│  Android app (Kotlin)       │  JNI   │  libmoviebox_android.so      │
│  ─────────────────────────  │◀──────▶│  ────────────────────────────│
│  • Compose UI               │        │  • providers / scrapers      │
│  • Media3 ExoPlayer         │  JSON  │  • aggregation service       │
│  • Coil (posters)           │◀──────▶│  • cache / history / favs    │
│  • kotlinx.serialization    │        │  • header + DASH resolution  │
└─────────────────────────────┘        └──────────────────────────────┘
```

The Rust side is the single source of truth for provider behaviour: the
CloudFront token signing, the HTML parsers, the HubCloud mirror resolution and
the BDIX heuristics are **not** reimplemented in Kotlin.

## Repository layout

| Path | Purpose |
| :--- | :--- |
| `src/` (root crate) | Core engine + terminal frontend. The terminal frontend is behind the `tui` Cargo feature. |
| `src/util.rs` | Shared, platform-agnostic helpers (duration parsing, language labels, header rules). |
| `crates/moviebox-android/` | The JNI bridge (`cdylib`) that the Android app links against. |
| `android/` | Gradle project, Compose UI, Media3 player. |

The core crate is split by feature so the Android `.so` never links `ratatui`,
`crossterm` or the self-updater:

```bash
# Terminal app (default)
cargo build --release

# Headless core only (what Android links)
cargo build --release --no-default-features
```

## Prerequisites

- JDK 17 or 21 (JDK 27 is not supported by AGP 8.7 / Gradle 8.11)
- Android SDK with platform `android-35` and build-tools `35.0.0`
- Android NDK r26 or newer (verified with r27c) — `sdkmanager "ndk;27.2.12479018"`
- Rust toolchain with the Android target: `rustup target add aarch64-linux-android`
- [`cargo-ndk`](https://github.com/rust-android/cargo-ndk): `cargo install cargo-ndk`

## Building

1. Build the Rust library into `jniLibs`:

   ```bash
   cd crates/moviebox-android
   cargo ndk -t arm64-v8a -o ../../android/app/src/main/jniLibs build --release -p moviebox-android
   ```

   The result is `android/app/src/main/jniLibs/arm64-v8a/libmoviebox_android.so`
   (≈ 5.8 MB stripped).

   Without `cargo-ndk` the NDK toolchain can be used directly:

   ```bash
   NDK=$ANDROID_HOME/ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64
   export CC_aarch64_linux_android=$NDK/bin/aarch64-linux-android24-clang
   export AR_aarch64_linux_android=$NDK/bin/llvm-ar
   export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=$NDK/bin/aarch64-linux-android24-clang
   cargo build -p moviebox-android --target aarch64-linux-android --release
   cp target/aarch64-linux-android/release/libmoviebox_android.so \
      android/app/src/main/jniLibs/arm64-v8a/
   ```

2. Build the APK:

   ```bash
   cd android
   ./gradlew assembleDebug
   ```

   The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`
   (≈ 27 MB with the unstripped debug DEX).

   The Gradle build can also drive the Rust build directly:

   ```bash
   ./gradlew assembleDebug -PmovieboxBuildRust=true
   ```

3. Install on a device:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## The JNI surface

`com.moviebox.app.core.NativeBridge` mirrors `crates/moviebox-android/src/lib.rs`.
Every function returns a JSON envelope:

```json
{ "ok": true,  "data": { "items": [] } }
{ "ok": false, "error": "All hosts exhausted" }
```

| Function | Purpose |
| :--- | :--- |
| `nativeInit(configDir, dataDir, cacheDir)` | Points the core at the app-private directories (Android has no XDG dirs). |
| `nativeProviders()` | Provider list with capability flags. |
| `nativeSearch(provider, query, page)` | Catalog search for one provider. |
| `nativeSuggest(query)` | Search-as-you-type suggestions. |
| `nativeHomepage(tabId, page)` | MovieBox home deck. |
| `nativeDetails(provider, subjectId)` | Full metadata, seasons and episodes. |
| `nativeEpisodeStreams(provider, subjectId, season, episode)` | Releases (mirrors, quality, size). |
| `nativeResolvePlayback(provider, releaseJson)` | Turns a release into `{url, headers, subtitle, maxHeight}`. |
| `nativeSubtitles(...)` | Subtitle tracks for the selected resource. |
| `nativeFavorites()` / `nativeToggleFavorite(itemJson)` | Favorites library. |
| `nativeHistory()` | Watch history. |
| `nativeRecordWatchProgress(itemJson)` | Records watch progress into `history.json`. |
| `nativeDownloadStart(requestJson)` | Starts a download, returns the job id. |
| `nativeDownloadList()` | The state of every download job. |
| `nativeDownloadCancel(jobId)` | Pauses a running download (the partial file is kept). |
| `nativeDownloadRemove(jobId)` | Forgets a job; the file on disk stays. |
| `nativeDownloadDirectory()` | The directory the app should save to. |
| `nativePosterUrlHeaders(url)` | Headers the image loader must send for posters. |

### Downloads

The core download engine is exposed as jobs the app polls.

`nativeDownloadStart(requestJson)` takes:

```json
{
  "url": "https://bcdn.hakunaymatata.com/resource/h265/x.mp4?sign=…&t=…",
  "headers": [["User-Agent", "com.community.oneroom/…"]],
  "title": "Dune [Hindi]-1080P",
  "provider": "moviebox",
  "subjectId": "1511967966181605720",
  "season": 0,
  "episode": 0,
  "destination": "/storage/emulated/0/Movies"
}
```

`destination` is optional; without it the file goes to the core download
directory (`nativeDownloadDirectory()`). Series land in
`<base>/Series/<title>/Season N/<title> - SxxExx.mp4`, movies in
`<base>/Movies/<title>/<title>.mp4`.

`nativeDownloadList()` returns the jobs:

```json
{ "jobs": [ { "id": "dl-1", "title": "…", "state": "running",
              "progress": { "downloaded": 1234, "total": 5678,
                            "bytesPerSecond": 4096.0, "attempt": 1, "workers": 4 },
              "destination": "/…/Dune.mp4", "error": null } ] }
```

`state` is one of `queued`, `running`, `completed`, `paused` or `failed`. The
engine resumes a partial download on its own, so a paused job can be started
again with the same destination.

The `headers` field of a playback source is **not** optional for most
providers. Which headers a source needs depends on the host: the MovieBox
detector links want the app user agent and answer `429` when a `Referer` is sent,
while the signed manifest hosts want the `Referer` and the CloudFront cookie. The
Kotlin player applies whatever the bridge returns through
`DefaultHttpDataSource.Factory#setDefaultRequestProperties`.

## Features and Parity Details

### 1. Favorites Library
- Backed by the bridge functions `nativeFavorites` and `nativeToggleFavorite`.
- Accessible from the **Favorites** bottom navigation destination.
- Heart toggle icon on the `DetailsScreen` top bar updates favorite status in real time.
- Tap a favorite to open its metadata and releases, or tap the heart badge to remove.

### 2. Subtitles Selection & Attachment
- Fetched via `nativeSubtitles` using the subject, resource ID and sibling IDs.
- Subtitle tracks are downloaded to local cache (`cacheDir/subtitles`) using the provider's request headers.
- Attached to Media3 as `MediaItem.SubtitleConfiguration` with the proper MIME type (`TEXT_VTT` or `APPLICATION_SUBRIP`).
- In-player subtitle picker allows switching subtitle tracks or turning subtitles off on the fly.
- Mandatory stream headers are strictly preserved on all media requests; the bridge decides which ones a host needs.

### 3. Resolution Picker
- Multi-resolution releases in `DetailsScreen` can be filtered with resolution chips (`All`, `1080p`, `720p`, `480p`, etc.).
- In `PlayerActivity`, a quality/resolution picker overlay allows capping max video height via `TrackSelectionParameters`.

### 4. Continue Watching & Watch History
- Position reporting runs periodically during playback and flushes on pause, stop, and destroy.
- Watch progress is saved to `<data_dir>/playback/*.json` conforming to `PendingPlaybackState`, which the bridge's `HistoryManager::new()` reconciles into `history.json` on the next `nativeHistory()` call.
- The **History** tab displays the full watch history with progress bars and quick resume.
- An in-progress **Continue Watching** deck is rendered on the home screen for easy one-tap resumption.

### 5. Provider Switching UX & Home Deck
- Horizontally scrollable provider switcher with active indicators and capability badges.
- When on a provider supporting homepage (e.g., MovieBox), a home deck with tabs (Featured, Movies, Series) is loaded.
- For search-only providers (4KHDHub, BDIX, Dramachi), quick category suggestion chips are shown.

### 6. Background Downloads & Job Management
- Backed by the download engine exposed through `nativeDownloadStart`, `nativeDownloadList`, `nativeDownloadCancel`, `nativeDownloadRemove`, and `nativeDownloadDirectory`.
- Download buttons next to "Play" on every source row in `DetailsScreen` resolve the source URL and headers via `nativeResolvePlayback` before dispatching.
- The **Downloads** bottom navigation tab displays live jobs with progress bars, transfer speeds (MB/s or KB/s), downloaded/total sizes, and state chips (Running, Paused, Completed, Failed, Queued).
- Active jobs are polled every second while the Downloads screen is visible and automatically stopped when leaving the screen.
- Controls include Pause, Resume (which re-resolves fresh signed URLs and headers on expiry), Remove, and Open.
- Finished downloads are indexed into the system media store via `MediaScannerConnection` and can be played directly with the native Media3 player.

## Feature parity

| Feature | Terminal | Android |
| :--- | :--- | :--- |
| Search across providers | ✅ | ✅ |
| Details, seasons, episodes | ✅ | ✅ |
| Source list with quality | ✅ | ✅ |
| Playback (headers, subtitles) | ✅ | ✅ (Media3) |
| Poster art | ✅ (Kitty/Sixel) | ✅ (Coil) |
| Favorites | ✅ | ✅ |
| Watch history & continue watching | ✅ | ✅ |
| Multi-resolution picker | ✅ | ✅ |
| Provider switching & home deck | ✅ | ✅ |
| Downloads | ✅ | ✅ |
| Live TV / M3U | ✅ | 📋 [roadmap](../ROADMAP.md#next--live-tv) |
| Stremio addons | ✅ | 📋 [roadmap](../ROADMAP.md#later) |
| Self-update | ✅ | 📋 Play / sideload |

## Distribution

The catalog aggregates third-party providers, so a Play Store release is
unlikely to be accepted under the current content policies. Sideloading
(GitHub Releases), F-Droid or a dedicated store is the realistic channel.
That also removes the need for the self-updater: the Android build ships the
Rust library inside the APK and updates through the store.

### The current build

Two APKs are published as a GitHub release and can be installed right now:

```bash
gh release download android-preview-1 --repo manish0467/MovieBox-Android --pattern "*.apk"
adb install -r app-release.apk   # signed, minified
adb install -r app-debug.apk     # debug signed, for the emulator
```

or copy the APK to the phone and open it (allow "unknown sources"). Both cover
`arm64-v8a` (real devices) and `x86_64` (the emulator), need Android 7 (API 24)
or newer, and need the INTERNET permission only.

### Where the downloads land

The engine writes the file into the app's external Movies folder
(`Android/data/com.moviebox.app/files/Movies/MovieBox`), because it writes a
plain file. When the download is finished, the app moves it into the public
`Movies/MovieBox` collection through the MediaStore, so the file shows up in a
gallery and in a file manager and survives the removal of the app.

- The free space is checked before the copy, and the user is told when it
  does not fit.
- The original is only deleted once the copy has succeeded.
- On Android 9 and older the file stays in the app's external folder and is
  registered with the media scanner, because the public folder would need a
  storage permission there.
- "Open" plays the published file through its `content://` uri.

### The in-app update check

The app is distributed by sideloading, so the settings dialog checks the
release server for a newer build and offers to install it:

1. The app asks `api.github.com` for the latest release of this repository.
2. The number in the tag (`android-preview-<versionCode>`) is compared with
   the version code of the installed app.
3. The newer APK is downloaded into the cache and **only kept when it carries
   the same signing certificate as the installed app** — a tampered download
   cannot replace the app.
4. The installer is opened with the file. Android asks the user to allow the
   app to install packages the first time.

The check needs the `REQUEST_INSTALL_PACKAGES` permission and a `FileProvider`
for the downloaded file. The bridge is not involved: the check is plain Kotlin.

### Signing a release

The signing key never enters the repository. `android/app/build.gradle.kts`
reads the key from `android/keystore.properties` (gitignored) or from the
environment:

```properties
storeFile=/home/you/keystore/moviebox-release.jks
storePassword=…
keyAlias=moviebox
keyPassword=…
```

| Variable | Purpose |
| :--- | :--- |
| `MOVIEBOX_STORE_FILE` | the path of the `.jks` file |
| `MOVIEBOX_STORE_PASSWORD` | the store password |
| `MOVIEBOX_KEY_ALIAS` | the key alias |
| `MOVIEBOX_KEY_PASSWORD` | the key password |

Without them the release build is produced unsigned, so a checkout still
builds. With them:

```bash
cd android && ./gradlew assembleRelease
```

The CI does the same, so a release does not depend on one machine. The
`.github/workflows/release.yml` workflow restores the key from a secret and
publishes the APK under the tag `android-preview-<versionCode>` — the name
the in-app update check looks for. It needs four repository secrets:

| Secret | Content |
| :--- | :--- |
| `MOVIEBOX_KEYSTORE_BASE64` | `base64 -w 0 moviebox-release.jks` |
| `MOVIEBOX_STORE_PASSWORD` | the store password |
| `MOVIEBOX_KEY_ALIAS` | the key alias (`moviebox`) |
| `MOVIEBOX_KEY_PASSWORD` | the key password |

Run it with `gh workflow run release.yml --repo manish0467/MovieBox-Android`.
The workflow also runs the format, lint and test gates, so a broken build
never reaches a release.

The signing certificate of the current preview key:

```text
SHA-256: 8bf4b05ba6e56604949df829f526b361855f85268b9e81ada1821fa94c2db05c
SHA-1:   d572d2e33a514ed59199edfef50457104c20d553
DN:      CN=MovieBox, OU=MovieBox, O=MovieBox
```

Keep the `.jks` file and its passwords — a store listing is bound to the
certificate, and a lost key means a new application id.

## Verified build

The following was reproduced on this repository:

| Step | Result |
| :--- | :--- |
| `cargo build --no-default-features` | ✅ headless core, no terminal deps |
| `cargo build -p moviebox-android --target aarch64-linux-android --release` | ✅ `libmoviebox_android.so` (6.1 MB, 26 JNI symbols) |
| `./gradlew assembleDebug` | ✅ `app-debug.apk` (38.8 MB) |
| `./gradlew assembleRelease` | ✅ `app-release.apk` (15.1 MB, R8 + resource shrinking, signed) |

## Roadmap

The planned work — the player polish, the design system, Live TV and the
release engineering — is in [ROADMAP.md](../ROADMAP.md).

## Verified on a device

The app was installed on an Android 15 (API 35) x86_64 emulator and driven
end to end:

| Step | Result |
| :--- | :--- |
| Launch | ✅ no crash, the Rust library loads and the core logs a session start |
| Home deck | ✅ posters and tabs from `nativeHomepage` |
| Search | ✅ live results with poster art |
| Details | ✅ metadata, subtitle picker, source list with the real resolutions |
| Sources | ✅ `Dune [Hindi]-1080P · adilessa · 1080p · h264` from the detector links |
| Playback | ✅ the H.264 decoder renders the stream from the CDN |
| Watch progress | ✅ `history.json` is written through `nativeRecordWatchProgress` |

Two bugs were only reproducible on a device and are fixed: the bridge
rejected the release payload the app sends back (the app omits fields that
are unset), and the CDN answers `429` when a `Referer` header is sent.

## Privacy

Same as the terminal client: zero telemetry. The core writes only to the
app-private `files/` and `cache/` directories, which the app passes to
`nativeInit`.

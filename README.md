# MovieBox Android

The native Android app for [MovieBox](https://github.com/manish0467/MovieBox-Tui) —
Jetpack Compose for the UI, Media3 for playback, and the shared Rust core engine
behind a JNI bridge.

## What it does

- Browse the MovieBox home deck, search the catalog and open any title
- Pick a source per resolution and play it with the headers the CDN needs
- Subtitles: pick a language, download the track and switch it inside the player
- Favourites, watch history and continue watching
- Downloads: start a download from any source, watch it progress, pause, resume
  (the signed link is re-resolved when it expires), remove it, or open the finished
  file — the completed file is indexed so it shows up in a gallery

## Layout

```text
android/                     the Gradle project (Compose + Media3)
crates/moviebox-android/    the JNI bridge (cdylib) onto the Rust core
docs/android.md              the bridge contract, the parity table and the build steps
```

## The core is a git dependency

The app does not vendor the engine. It depends on the published crate from the
MovieBox repository:

```toml
moviebox-tui = { git = "https://github.com/manish0467/MovieBox-Tui", tag = "v0.1.28", default-features = false }
```

Bump the tag when a new core release lands.

## Build

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export ANDROID_HOME=$HOME/Android/Sdk

# 1. the Rust library
cargo ndk -t arm64-v8a -o android/app/src/main/jniLibs build --release -p moviebox-android
cargo ndk -t x86_64  -o android/app/src/main/jniLibs build --release -p moviebox-android

# 2. the APK
cd android && ./gradlew assembleDebug
```

`adb install -r android/app/build/outputs/apk/debug/app-debug.apk`

The Rust core is fetched by cargo from GitHub, so the first build needs network
access to the MovieBox repository.

## Licence

GPL-3.0-or-later. The app reuses interface patterns and some components from
[Flow](https://github.com/Flow-Tube/Flow) (also GPL-3.0); see the headers of the
files taken from it. The Rust core in
[MovieBox-Tui](https://github.com/manish0467/MovieBox-Tui) is MIT OR Apache-2.0 and
is consumed as a dependency.

# AGENTS.md

Working notes for coding agents on the **MovieBox Android app**.

## What this repository is

A native Android app (Jetpack Compose + Media3) over the Rust core engine that
lives in [MovieBox-Tui](https://github.com/manish0467/MovieBox-Tui). The engine is
consumed as a git dependency:

```toml
moviebox-tui = { git = "https://github.com/manish0467/MovieBox-Tui", tag = "v0.1.28", default-features = false }
```

Bump the tag when a new core release lands.

The app is **GPL-3.0** (it reuses interface work from the Flow app, also GPL-3.0).
The core is MIT OR Apache-2.0.

## Layout

```text
android/app/src/main/kotlin/com/moviebox/app/
  core/       DTOs, the repository, the JNI declarations   (do not change the bridge contract)
  player/     the Media3 player and its screen
  ui/         screens, view models, the theme
  ui/player/  the player controls (new work goes here)
android/app/src/main/res/    strings, layouts, drawables
crates/moviebox-android/     the JNI bridge (Rust, cdylib)
docs/android.md              the bridge contract and the parity table
ROADMAP.md                   what is shipped and what comes next
```

## The bridge contract is fixed

`crates/moviebox-android/src/lib.rs` exports 19 entry points as
`Java_com_moviebox_app_core_NativeBridge_<fn>`; the Kotlin side declares them
as `@JvmStatic external fun` in `core/NativeBridge.kt`. **Change both sides
together** or the app dies with `UnsatisfiedLinkError`.

Every call returns a JSON envelope: `{"ok":true,"data":…}` or
`{"ok":false,"error":"…"}`. The Kotlin side decodes it in
`core/MovieBoxRepository.kt`.

The contract is documented in `docs/android.md`. If you need a new entry point,
add it in **both** repositories in the same pull request: the Rust side here,
the Kotlin side here, and the core (if it needs a new engine capability) in
MovieBox-Tui.

## Rules

- **Never drop the stream headers.** The CDN answers `429` without the MovieBox
  user agent and `403` without the rest. Take the headers from the source you
  resolved and pass them on.
- **The signed links expire.** Re-resolve a source when it is used; do not
  cache a URL across a session.
- **No blocking on the main thread.** The repository calls suspend on
  `Dispatchers.IO`.
- **No panics.** Kotlin: no `!!` on platform types, no swallowed exceptions in
  the playback path.
- Follow the existing Compose style: Material 3, a `MovieBoxTheme` from
  `ui/theme`, state hoisted into the view models, screens as pure composables
  that take a state object and callbacks.
- Keep the app offline-friendly: no analytics, no telemetry, no crash
  reporting. Everything stays on the device.

## Build and verify

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export ANDROID_HOME=$HOME/Android/Sdk

# the Rust library (both ABIs)
NDK=$ANDROID_HOME/ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64
export CC_aarch64_linux_android=$NDK/bin/aarch64-linux-android24-clang
export AR_aarch64_linux_android=$NDK/bin/llvm-ar
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=$NDK/bin/aarch64-linux-android24-clang
cargo ndk -t arm64-v8a -o android/app/src/main/jniLibs build --release -p moviebox-android
export CC_x86_64_linux_android=$NDK/bin/x86_64-linux-android24-clang
export AR_x86_64_linux_android=$NDK/bin/llvm-ar
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER=$NDK/bin/x86_64-linux-android24-clang
cargo ndk -t x86_64 -o android/app/src/main/jniLibs build --release -p moviebox-android

# the APK
cd android && ~/tools/gradle-8.11.1/bin/gradle assembleDebug --no-daemon
```

Rust checks: `cargo fmt --all --check`, `cargo clippy --workspace --all-targets
--locked -- -D warnings`, `cargo test --workspace --locked`.

### On the emulator

```bash
~/Android/Sdk/emulator/emulator -avd moviebox -no-window -no-audio \
  -no-boot-anim -no-snapshot -gpu swiftshader_indirect &
~/Android/Sdk/platform-tools/adb install -r \
  android/app/build/outputs/apk/debug/app-debug.apk
```

The emulator is slow (320×640, software rendering) and its SystemUI sometimes
shows an "isn't responding" dialog — that is the emulator, not the app. Search
"dune", open a title and press Play: playback must decode from the CDN.

## Lanes and file ownership

Three agents work in parallel, each in its own worktree. **Do not edit files
outside your lane** — a conflict costs more than the feature.

| Lane | Worktree | Owns |
| :--- | :--- | :--- |
| Player | `../box-android-player` | `player/**`, `ui/player/**` |
| Design | `../box-android-design` | `ui/theme/**`, `res/**` |
| Core | `../box-android-core` | `core/**`, `crates/moviebox-android/**`, `MainActivity.kt`, `.github/**`, `docs/**` |

If you need something from another lane, say so in your report instead of
editing it.

## Before you say "done"

Report what you changed, the exact commands you ran and their output, and
anything you need from another lane. If something could not be run (no NDK, no
device, no network), say so explicitly instead of implying it passed.

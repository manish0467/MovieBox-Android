# Changelog

## [Unreleased]

### Added
- **Native Android App**:
  - Added a native Android application (Jetpack Compose + Media3) built on the shared Rust core engine, with the core split behind a `tui` Cargo feature so the library never links the terminal frontend.
  - Added a favorites library, a watch history and continue-watching deck, subtitle selection with in-player track switching, a resolution picker, provider switching and a home deck to the Android app.
  - Added `nativeRecordWatchProgress` to the JNI bridge so the app records watch progress through the core instead of writing the history file itself.
  - Added downloads: a download action per source, a downloads tab with live progress, speed, pause, resume, remove and open. The core download engine (ranges, resume, segmented) is exposed as jobs over the bridge, and the finished file is indexed into the system media store.

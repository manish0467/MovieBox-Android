# Changelog

## [0.12.0] - 2026-10-25

### Added
- **Player: Lock mode** — a top-bar lock hides every control and the system
  bars and raises a touch shield; the first back press (or the shield's unlock
  button) unlocks instead of leaving the player, and entering
  picture-in-picture releases the lock.
- **Player: Double-tap seek zones** — double-tapping the left or right third
  of the video seeks ten seconds and keeps stacking (+10s, +20s...) while the
  taps continue, with a pill on the tapped side; the first tap reveals the
  controls instantly while hiding waits out the double-tap window, so a
  double-tap never flashes the controls.
- **Player: Center play/pause pulse** — double-tapping the center toggles
  playback, and every play-state change pulses a center icon whichever
  control caused it.
- **Player: Long-press speed boost** — holding the video doubles the speed
  and shows a boost chip until the finger lifts.
- **Player: Subtitle timing** — subtitles render in the app with live timing
  controls (±0.25 s / ±0.5 s, clamped to ten seconds), applied against the
  playback clock so seeks and speed changes are picked up instantly.

## [Unreleased]

### Added
- **Native Android App**:
  - Added a native Android application (Jetpack Compose + Media3) built on the shared Rust core engine, with the core split behind a `tui` Cargo feature so the library never links the terminal frontend.
  - Added a favorites library, a watch history and continue-watching deck, subtitle selection with in-player track switching, a resolution picker, provider switching and a home deck to the Android app.
  - Added `nativeRecordWatchProgress` to the JNI bridge so the app records watch progress through the core instead of writing the history file itself.
  - Added downloads: a download action per source, a downloads tab with live progress, speed, pause, resume, remove and open. The core download engine (ranges, resume, segmented) is exposed as jobs over the bridge, and the finished file is indexed into the system media store.

# Changelog

## [0.12.3] - 2026-10-26

### Added
- **ToonPlay provider** — an anime and cartoon catalog: search, full details
  with seasons and episodes, and movie playback resolved to signed
  multi-audio HLS streams. Series playback is reported as not yet supported.
  The engine is bumped to the v0.1.31 tag (the ToonPlay provider release).

## [0.12.2] - 2026-10-26

### Added
- **Quality memory** — the resolution you pick is remembered for the session;
  a fresh details page starts on it when the title offers that resolution,
  and falls back to All when it does not.

### Fixed
- **Player: safe-drawing insets** — the player now draws edge-to-edge and
  applies the status-bar and navigation-bar padding itself, so the top
  buttons can no longer sit under the status bar or a display cutout on any
  device.

## [0.12.1] - 2026-10-26

### Fixed
- **History: resume** — playing from the details screen now starts at your
  saved position (season/episode aware; completed titles start fresh)
  instead of always from the beginning.
- **History: resume goes straight to playback** — the history tab's Resume
  button and the home continue-watching cards now resolve the source and
  open the player at the saved point, instead of only opening the details
  screen.
- **History: removable entries** — every history row has a delete button;
  removal goes through the engine's history manager (new
  `nativeRemoveHistoryItem` bridge entry point) and updates the list
  instantly.

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

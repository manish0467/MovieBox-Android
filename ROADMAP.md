# Roadmap — MovieBox Android

The app is a Compose + Media3 front end over the Rust core in
[MovieBox-Tui](https://github.com/manish0467/MovieBox-Tui), consumed as a git
dependency on the `v0.1.28` tag.

## Shipped

- Home deck, search, title details with seasons and episodes
- Source list per resolution, playback with the CDN headers replayed
- Subtitles: pick a language, download the track, switch it inside the player
- Favourites, watch history, continue watching, provider switching
- Downloads: start, live progress, pause, resume (the signed URL is re-resolved
  when it expires), remove, open, and the finished file is indexed into the
  system media store

## Now — the player and the interface

The player is a bare `PlayerView` today. These are the Media3 and Compose
features that make it feel like a real player, in order of value:

| Feature | Mechanism | Owner |
| :--- | :--- | :--- |
| Gesture controls (brightness, volume, seek) | `Modifier.pointerInput` on the surface | player lane |
| Chapters and seek-to-chapter | Media3 `chapterMetadata` on `MediaMetadata` | player lane |
| Playback speed 0.25–2× | `PlaybackParameters(speed)` | player lane |
| Subtitle styling (size, colour, background) | `CaptionStyleCompat` on the `SubtitleView` | player lane |
| Picture in picture, properly driven | `enterPictureInPictureMode()` on user intent | player lane |
| Background playback | `MediaSession` + `MediaSessionService` + a media notification | player lane |
| Seek preview thumbnails | a thumbnail loader over the scrub position | player lane |
| Casting | the Cast SDK and a registered application id | core lane |
| Theme set and picker (light, dark, OLED, accents) + dynamic colour | `MovieBoxTheme` variants, stored in the core's `config.json` | design lane |
| Localisation | `res/values/strings.xml` and `values-xx/` | design lane |
| Tablet and adaptive layouts | `res/layout*` and window size classes | design lane |
| Accessibility (TalkBack, font scaling) | semantics and a Compose pass | design lane |

## Next — Live TV

M3U playlist support. The terminal client already has TV mode, so the engine
mostly exists; the work is bridge plumbing and the app side.

**Design (settled)**

*The core* — `providers::tv` provides `M3UParser::fetch_playlist` (remote
**and** local file, 24 h disk cache, 15 MB cap), `parse_m3u` and the
`Channel { id, name, logo, group, stream_url }` model, which is already
`Serialize`.

*One refactor first.* The playlist list currently lives in the terminal UI
(`save_tv_playlists` / `load_tv_playlists_from_config` in `src/tui/app/mod.rs`)
as a JSON array of strings in `<config_dir>/tv_config.json`. Lift it into the
core as `config::load_tv_playlists()` / `config::save_tv_playlists(&[String])`
and let the terminal client call those. The app and the terminal client then
share one playlist file.

*The bridge* — five entry points:

| Function | Purpose |
| :--- | :--- |
| `nativeTvPlaylists()` | The configured playlist URLs and local paths. |
| `nativeTvAddPlaylist(url)` / `nativeTvRemovePlaylist(url)` | Maintain `tv_config.json`. |
| `nativeTvChannels(query, limit)` | Fetch every playlist in parallel, merge, drop empty and duplicate stream URLs, then filter by name and group **in Rust**. |
| `nativeTvResolve(streamUrl)` | The playable URL plus the headers the playlist fetch used. |

Filtering in Rust is deliberate: a 15 MB M3U is tens of thousands of
channels, and shipping all of them across JNI on every keystroke would jank
the UI. The app renders a list, exactly like its other screens.

*The app* — a fifth bottom-navigation destination with the channel list grouped
by `group`, a search field that calls `nativeTvChannels(query)` as you type, a
playlist manager mirroring the terminal client's `/config` popup, and
tap-to-play through the existing player.

*Notes*

- Plain `http://` playlists already work; the Android network security
  configuration permits cleartext.
- Scoped storage means a user cannot point at `/sdcard/…`. Offer *import file*
  through the Storage Access Framework, copy it into the app's directory and use
  that path — the core already handles local files.
- The core README mentions "EPG support" for Live TV. There is no EPG code
  today: the parser reads `tvg-id`, `tvg-logo` and `group-title` only.
  Either implement XMLTV parsing or correct the claim; it is deliberately not
  part of this work.

## Later

- **Stremio addons.** The terminal client supports community addons; the app
  would need the manifest, catalog and stream providers bridged, plus a source
  picker.
- **Custom playlists** on top of favourites.
- **Per-source quality memory**, so the app remembers the resolution you
  picked last time.
- **Release signing.** The published APK is debug-signed, so it cannot replace
  a release build. A release keystore, a signed build in CI and store metadata
  are needed for anything beyond sideloading.
- **An in-app update check** against the GitHub releases, mirroring the
  terminal client's self-updater.
- **A signing fingerprint in the README**, next to the `SHA256SUMS` file.

## Blocked or not planned

- **MovieBox series episodes.** Upstream exposes no direct link for series
  episodes — the detector points at index sites that are dead or unreachable.
  The app reports this and points at 4KHDHub or Dramachi. A source-page
  resolver would be unverifiable, so it is not planned.
- **Music, subscriptions and local recommendations.** Those belong to the
  Flow app and have no counterpart in a film catalogue.

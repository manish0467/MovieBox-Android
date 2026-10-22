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
- A colour scheme picker (system, light, dark, OLED black and three accents)
  with Material 3 dynamic colour, stored by the engine and applied at startup
- Localisation in Bengali, Hindi and Spanish
- **Live TV**: add an M3U playlist by URL or file path, browse and search its
  channels, and play them; the playlist list is shared with the terminal
  client
- **The downloads land in the device storage**: a finished download is moved
  into the public `Movies/MovieBox` collection, so it shows up in a gallery and
  in a file manager
- The player hides the system bars and keeps the screen awake while it plays

## Now — the player and the interface

The player is a bare `PlayerView` today. These are the Media3 and Compose
features that make it feel like a real player, in order of value:

| Feature | Mechanism | Owner |
| :--- | :--- | :--- |
| ~~Gesture controls (brightness, volume, seek)~~ | shipped | player lane |
| ~~Chapters and seek-to-chapter~~ | shipped | player lane |
| ~~Playback speed 0.25–2×~~ | shipped | player lane |
| ~~Subtitle styling (size, colour, background)~~ | shipped | player lane |
| ~~Picture in picture, properly driven~~ | shipped | player lane |
| ~~Background playback~~ (the media notification, the session) | shipped | player lane |
| ~~Seek preview thumbnails~~ | shipped | player lane |
| ~~Theme set and picker + dynamic colour~~ | shipped | design lane |
| ~~Localisation~~ | shipped | design lane |
| Tablet and adaptive layouts | `res/layout*` and window size classes | design lane |
| Accessibility (TalkBack, font scaling) | semantics and a Compose pass | design lane |

## Later

- **A new provider: toonplay.in.** The catalogue API of the site is clean and
  reachable (`animesalt.streamindia.co.in/api`), but the playback chain is not:
  the site's own extractor serves a certificate for a different domain, and
  the fallback needs a JavaScript unpacker plus a CryptoJS compatible AES
  decryption to turn the packed player page into a stream link. That is a
  brittle thing to build against a site that can change at any time, so it
  waits until the extractor works again.
- **cinejoy.pk and flixer.gd** follow the same shape of work: find the API
  behind the page, write a client, add a provider kind.
- **A custom endpoint per provider** — the engine's Dramachi client already
  has `with_base_url`, so a per-provider base-URL override is feasible for the
  HTML providers. This is the escape hatch for a provider that changes its
  domain.
- **Casting.** The Cast SDK plus a button on the player. This needs a Cast
  **application id**, which is registered in the Google Cast console against a
  package name and a signing certificate — so it waits until the app has a
  release keystore, otherwise the registered id would not match the shipped
  build. Everything else about casting (the SDK, the session manager, the
  route picker) can be built before that.
- **Stremio addons.** The terminal client supports community addons; the app
  would need the manifest, catalog and stream providers bridged, plus a source
  picker.
- **Custom playlists** on top of favourites.
- **Per-source quality memory**, so the app remembers the resolution you
  picked last time.
- ~~Release signing.~~ Shipped: the key is read from `keystore.properties` or
  the environment, a signed `app-release.apk` is published and its
  certificate fingerprint is in `docs/android.md`. Still open: a CI job that
  signs from a secret, and the store metadata.
- ~~An in-app update check~~ Shipped: the app checks the releases, downloads a
  newer build, verifies its signing certificate and hands it to the installer.
- ~~A signing fingerprint in the README~~ Shipped: it is in `docs/android.md`.

## Blocked or not planned

- **MovieBox series episodes.** Upstream exposes no direct link for series
  episodes — the detector points at index sites that are dead or unreachable.
  The app reports this and points at 4KHDHub or Dramachi. A source-page
  resolver would be unverifiable, so it is not planned.
- **Music, subscriptions and local recommendations.** Those belong to the
  Flow app and have no counterpart in a film catalogue.

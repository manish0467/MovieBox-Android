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
- **Themes**: a palette picker with the app's own six schemes and the 22 Omarchy
  palettes, each shown as a swatch drawn in its own colours; system keeps
  Material 3 dynamic colour, and the choice is stored by the engine and applied
  at startup
- **Edge-to-edge and press feedback**: the system bars follow the theme, the
  launch surface follows the system mode, and the cards and rows spring under a
  press with a haptic
- Localisation in Bengali, Hindi and Spanish
- **Live TV**: add an M3U playlist by URL or file path, browse and search its
  channels, and play them; the playlist list is shared with the terminal
  client
- **The downloads land in the device storage**: a finished download is moved
  into the public `Movies/MovieBox` collection, so it shows up in a gallery and
  in a file manager
- The player hides the system bars and keeps the screen awake while it plays
- **Adaptive layouts**: the details screen shows the media information and the
  source list side by side on a tablet, and the home and Live TV grids grow with
  the window; the navigation labels and the deck tabs survive a large font
  scale, and the icon buttons that have no visible label announce what they do
- **The whole interface is localised**, the player and its dialogs included, in
  Bengali, Hindi and Spanish: every user-visible label comes from the resources
- **Audio track picker** for files with multiple in-file audio tracks
- **Sleep timer** for 5–60 minutes or the end of the current episode
- Fixed a crash when opening 4KHDHub titles
- **The Flow-style player controls** (0.12.0): lock mode behind a touch
  shield, double-tap seek zones with stacking ±10s, a center play/pause
  pulse, the long-press speed boost, and subtitle timing with in-app cue
  rendering
- **History that resumes and lets go** (0.12.1): playback from the details
  screen starts at the saved position, the history tab's resume button and
  the continue-watching cards go straight into playback, and every history
  row can be removed

## Next release candidates

- Fix for player top buttons that can sit under the status bar on some
  devices

## Now — getting it onto real devices

- **A test on a real phone.** Every build so far ran on an x86_64 emulator, so
  the arm64 library inside the release APK has never executed. A physical device
  should install the published APK and play, download and update before a build
  is called final.
- **Store metadata.** The key, the CI secrets and the certificate fingerprint
  are in place; the listing itself — description, screenshots, data-safety
  answers — is the remaining piece.
- **Native review of Bengali/Hindi strings pending.** The shipped translations
  have had no native-speaker pass yet; corrections are expected after release.

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
  **application id**, registered in the Google Cast console against the package
  name and the release certificate. The keystore exists now, so the id can be
  registered; the SDK and the route picker are what remain.
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

# Local player fixtures

Requirements: `ffmpeg` with `libx264` and AAC encoders, Android SDK platform
tools, and an emulator or device with USB debugging enabled.

Run from the repository root. The commands create fixtures in
`scripts/player-fixtures/out/`.

```sh
mkdir -p scripts/player-fixtures/out

# H.264 video with English stereo and Hindi 5.1 AAC tracks.
ffmpeg -y \
  -f lavfi -i 'testsrc2=size=640x360:rate=24:duration=90' \
  -f lavfi -i 'sine=frequency=440:sample_rate=48000:duration=90' \
  -f lavfi -i 'aevalsrc=0.12*sin(2*PI*220*t)|0.12*sin(2*PI*330*t)|0.12*sin(2*PI*440*t)|0.12*sin(2*PI*550*t)|0.12*sin(2*PI*660*t)|0.12*sin(2*PI*770*t):s=48000:d=90' \
  -map 0:v:0 -map 1:a:0 -map 2:a:0 -t 90 \
  -c:v libx264 -preset veryfast -pix_fmt yuv420p -c:a aac -b:a 128k \
  -metadata:s:a:0 language=eng -metadata:s:a:0 title='English stereo' -ac:a:0 2 \
  -metadata:s:a:1 language=hin -metadata:s:a:1 title='Hindi 5.1' -ac:a:1 6 \
  -disposition:a:0 default -disposition:a:1 0 \
  scripts/player-fixtures/out/multi-audio.mkv

# Single-audio MP4, for checking that the audio picker is hidden.
ffmpeg -y \
  -f lavfi -i 'testsrc2=size=640x360:rate=24:duration=90' \
  -f lavfi -i 'sine=frequency=440:sample_rate=48000:duration=90' \
  -map 0:v:0 -map 1:a:0 -t 90 -c:v libx264 -preset veryfast -pix_fmt yuv420p \
  -c:a aac -b:a 128k -movflags +faststart \
  scripts/player-fixtures/out/single-audio.mp4

# 90-second seek/sleep-timer MP4.
ffmpeg -y \
  -f lavfi -i 'testsrc2=size=640x360:rate=24:duration=90' \
  -f lavfi -i 'sine=frequency=660:sample_rate=48000:duration=90' \
  -map 0:v:0 -map 1:a:0 -t 90 -c:v libx264 -preset veryfast -pix_fmt yuv420p \
  -c:a aac -b:a 128k -movflags +faststart \
  scripts/player-fixtures/out/tone-90s.mp4
```

Serve the fixtures to an emulator. `adb reverse` maps host port 8000 to the
emulator's loopback address:

```sh
~/Android/Sdk/platform-tools/adb reverse tcp:8000 tcp:8000
cd scripts/player-fixtures/out
python3 -m http.server 8000 --bind 0.0.0.0
```

In another terminal, start the debug-only URL hook:

```sh
~/Android/Sdk/platform-tools/adb shell am start \
  -n com.moviebox.app.debug/com.moviebox.app.debug.DebugOpenUrlActivity \
  --es url http://127.0.0.1:8000/multi-audio.mkv \
  --es title 'Multi-audio fixture'
```

Replace the URL filename with `single-audio.mp4` or `tone-90s.mp4` to test
those fixtures. Optional URL extras are `--es subtitle URL` and
`--es title NAME`; comma-separated header strings use `--esa headers
'Name=Value,Other=Value'`.

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

cat > scripts/player-fixtures/out/offset.srt <<'EOF'
1
00:00:02,000 --> 00:00:08,000
Subtitle delivered through the debug hook

2
00:00:12,000 --> 00:00:18,000
Second subtitle fixture cue
EOF
```

Serve the fixtures with the request-header logger. It prints the method, path,
and every request header, including custom headers passed through the hook.
`adb reverse` maps host port 8000 to the emulator's loopback address:

```sh
~/Android/Sdk/platform-tools/adb reverse tcp:8000 tcp:8000
python3 scripts/player-fixtures/serve.py --bind 0.0.0.0 --port 8000
```

In another terminal, start the debug-only URL hook:

```sh
~/Android/Sdk/platform-tools/adb shell am start \
  -n com.moviebox.app.debug/com.moviebox.app.debug.DebugOpenUrlActivity \
  --es url http://127.0.0.1:8000/tone-90s.mp4 \
  --es title 'Tone fixture' \
  --esa headers 'X-Debug-Fixture=hook-header-check'
```

Replace the URL filename with `single-audio.mp4` or `tone-90s.mp4` to test
those fixtures. For the subtitle fixture, use `multi-audio.mkv` and pass
`--es subtitle http://127.0.0.1:8000/offset.srt`. Optional metadata is passed
with `--es title NAME`; comma-separated header strings use `--esa headers
'Name=Value,Other=Value'`.

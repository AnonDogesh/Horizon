# FFmpeg Android AAR Build (Docker)

This workspace builds FFmpeg shared libraries for:
- `arm64-v8a`
- `armeabi-v7a`

Enabled features:
- Protocols: `http`, `https`, `file`
- Demuxer: `hls`
- Shared `.so` output

## Build inside Docker

```bash
cd tools/ffmpeg-android
docker build -t horizon-ffmpeg-android .
docker run --rm -v "$PWD:/workspace" horizon-ffmpeg-android
```

After completion:
- FFmpeg install output: `tools/ffmpeg-android/output/<abi>/...`
- AAR: `tools/ffmpeg-android/ffmpeg-lib.aar`

## Notes

- FFmpeg usually emits `libav*.so` libraries. The packaging script also creates a compatibility alias `libffmpeg.so` from `libavcodec.so` if absent, because some consumers expect that name.
- Tune `build_ffmpeg_android.sh` if you need more codecs/demuxers/filters.

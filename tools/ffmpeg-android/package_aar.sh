#!/usr/bin/env bash
set -euo pipefail

OUTPUT_ROOT="/workspace/output"
AAR_ROOT="/workspace/ffmpeg-lib"
AAR_NAME="/workspace/ffmpeg-lib.aar"

rm -rf "$AAR_ROOT" "$AAR_NAME"
mkdir -p "$AAR_ROOT/jni/arm64-v8a" "$AAR_ROOT/jni/armeabi-v7a"

copy_libs() {
  local abi="$1"
  local src="$OUTPUT_ROOT/$abi/lib"
  if [[ ! -d "$src" ]]; then
    echo "Missing build output for $abi at $src" >&2
    exit 1
  fi

  shopt -s nullglob
  local libs=("$src"/*.so)
  if [[ ${#libs[@]} -eq 0 ]]; then
    echo "No shared libraries produced for $abi" >&2
    exit 1
  fi

  cp "${libs[@]}" "$AAR_ROOT/jni/$abi/"

  # Optional compatibility alias if consumer expects libffmpeg.so
  if [[ ! -f "$AAR_ROOT/jni/$abi/libffmpeg.so" ]]; then
    cp "$AAR_ROOT/jni/$abi/libavcodec.so" "$AAR_ROOT/jni/$abi/libffmpeg.so"
  fi
}

copy_libs "arm64-v8a"
copy_libs "armeabi-v7a"

cat > "$AAR_ROOT/AndroidManifest.xml" <<'MANIFEST'
<manifest package="com.dean.ffmpeglib" xmlns:android="http://schemas.android.com/apk/res/android" />
MANIFEST

# Empty classes.jar
TMP_DIR=$(mktemp -d)
pushd "$TMP_DIR" >/dev/null
jar cf "$AAR_ROOT/classes.jar" .
popd >/dev/null
rm -rf "$TMP_DIR"

pushd "$AAR_ROOT" >/dev/null
zip -qr "$AAR_NAME" .
popd >/dev/null

echo "AAR packaged at $AAR_NAME"

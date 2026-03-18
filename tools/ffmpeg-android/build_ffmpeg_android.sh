#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_NDK:=/android-ndk-r26b}"
FFMPEG_DIR="/FFmpeg"
BUILD_ROOT="/workspace/build"
OUTPUT_ROOT="/workspace/output"
API=21

mkdir -p "$BUILD_ROOT" "$OUTPUT_ROOT"

build_arch() {
  local abi="$1"
  local arch="$2"
  local cpu="$3"
  local host="$4"
  local target="$5"

  local prefix="$OUTPUT_ROOT/$abi"
  local build_dir="$BUILD_ROOT/$abi"

  rm -rf "$build_dir"
  mkdir -p "$build_dir" "$prefix"

  pushd "$FFMPEG_DIR" >/dev/null
  make distclean >/dev/null 2>&1 || true

  ./configure \
    --prefix="$prefix" \
    --target-os=android \
    --arch="$arch" \
    --cpu="$cpu" \
    --enable-cross-compile \
    --cc="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/${target}${API}-clang" \
    --cxx="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/${target}${API}-clang++" \
    --cross-prefix="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/${host}-" \
    --sysroot="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot" \
    --enable-shared \
    --disable-static \
    --disable-programs \
    --disable-doc \
    --disable-avdevice \
    --disable-postproc \
    --disable-swresample \
    --disable-everything \
    --enable-avformat \
    --enable-avcodec \
    --enable-avutil \
    --enable-swscale \
    --enable-protocol=file \
    --enable-protocol=http \
    --enable-protocol=https \
    --enable-demuxer=hls \
    --enable-demuxer=mov \
    --enable-demuxer=matroska \
    --enable-muxer=mp4 \
    --enable-parser=aac \
    --enable-parser=h264 \
    --enable-parser=hevc \
    --enable-decoder=aac \
    --enable-decoder=h264 \
    --enable-decoder=hevc

  make -j"$(nproc)"
  make install
  popd >/dev/null
}

build_arch "arm64-v8a" "aarch64" "armv8-a" "aarch64-linux-android" "aarch64-linux-android"
build_arch "armeabi-v7a" "arm" "armv7-a" "arm-linux-androideabi" "armv7a-linux-androideabi"

echo "FFmpeg Android build complete. Outputs: $OUTPUT_ROOT"

FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    git wget unzip build-essential pkg-config yasm nasm \
    openjdk-17-jdk ca-certificates zip \
 && rm -rf /var/lib/apt/lists/*

# Android NDK
RUN wget -q https://dl.google.com/android/repository/android-ndk-r26b-linux.zip \
 && unzip -q android-ndk-r26b-linux.zip \
 && rm android-ndk-r26b-linux.zip

ENV ANDROID_NDK=/android-ndk-r26b
ENV PATH=$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH

# Clone FFmpeg source
RUN git clone --depth 1 https://github.com/FFmpeg/FFmpeg.git /FFmpeg

WORKDIR /workspace
COPY build_ffmpeg_android.sh package_aar.sh ./
RUN chmod +x build_ffmpeg_android.sh package_aar.sh

CMD ["bash", "-lc", "./build_ffmpeg_android.sh && ./package_aar.sh"]

#!/usr/bin/env bash
#
# Builds the Media3 FFmpeg audio decoder extension and drops the AAR in libs/.
#
# IPTV channels carry audio the Android platform decoders do not handle: MPEG-1 Layer II on
# European DVB feeds, AC-3 and E-AC-3 on most film channels, DTS on a few. Without this the
# picture plays and nothing comes out of the speakers. Media3 does not publish the extension to
# Maven (androidx/media issue 2781), so we build it once, check the AAR in, and keep ordinary
# builds free of the NDK.
#
# Everything is pinned, so two runs of this script produce the same decoder. Rerun it when the
# Media3 version in gradle/libs.versions.toml moves.
#
# FFmpeg is configured LGPL: no --enable-gpl and no --enable-nonfree. See libs/README.md.
#
# Usage: scripts/build-ffmpeg-decoder.sh [work-dir]

set -euo pipefail

MEDIA3_TAG="1.11.1"
FFMPEG_TAG="n6.0.1"
NDK_VERSION="26.1.10909125"
# Must not exceed the app's minSdk. 21 is the oldest the NDK still targets.
ANDROID_API=21
# Google TV sticks are commonly 32 bit userspace, so both arm ABIs ship. No x86: the only x86
# Android TV devices are emulators, and the arm64 system image covers those we test on.
ABIS=(armeabi-v7a arm64-v8a)
# The names are FFmpeg's, chosen from the mime type mapping in Media3's FfmpegLibrary.
# getCodecName. MPEG-1 Layer I and II arrive as the "mp3" decoder, which handles all three layers.
#
# truehd is deliberately absent. MEASURED: MatroskaExtractor hands the renderer chunks of sixteen
# TrueHD access units, and only the first of them carries a major sync, so FFmpeg rejects every
# chunk after the first and the channel drops into a reconnect loop. Left out, a TrueHD track
# reports unsupported, the picture keeps playing and the player says the audio will not decode.
DECODERS=(mp1 mp2 mp3 ac3 eac3 dca aac opus vorbis flac alac pcm_mulaw pcm_alaw)

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${1:-${TMPDIR:-/tmp}/keeltv-ffmpeg-decoder}"
MEDIA3="${WORK}/media3"
JNI="${MEDIA3}/libraries/decoder_ffmpeg/src/main/jni"

: "${ANDROID_HOME:?set ANDROID_HOME to your Android SDK}"
NDK="${ANDROID_HOME}/ndk/${NDK_VERSION}"
HOST_PLATFORM="linux-x86_64"
[[ "$(uname -s)" == "Darwin" ]] && HOST_PLATFORM="darwin-x86_64"
TOOLCHAIN="${NDK}/toolchains/llvm/prebuilt/${HOST_PLATFORM}/bin"

if [[ ! -d "${TOOLCHAIN}" ]]; then
  echo "Missing NDK ${NDK_VERSION} and CMake. Install them with:"
  echo "  sdkmanager \"ndk;${NDK_VERSION}\" \"cmake;3.22.1\""
  exit 1
fi

mkdir -p "${WORK}"

if [[ ! -d "${MEDIA3}/.git" ]]; then
  echo "Cloning androidx/media ${MEDIA3_TAG}"
  git clone --depth 1 --branch "${MEDIA3_TAG}" https://github.com/androidx/media.git "${MEDIA3}"
fi

if [[ ! -d "${JNI}/ffmpeg/.git" ]]; then
  echo "Cloning FFmpeg ${FFMPEG_TAG}"
  git clone --depth 1 --branch "${FFMPEG_TAG}" https://github.com/FFmpeg/FFmpeg.git "${JNI}/ffmpeg"
fi

# The flags are Media3's own build_ffmpeg.sh, minus the ABIs we do not ship. Only avcodec,
# swresample and avutil are built: the extension decodes samples the Media3 extractors have
# already demuxed, so there is no need for avformat, swscale or the filters.
JOBS="$(sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || echo 4)"
COMMON=(
  --target-os=android
  --enable-static
  --disable-shared
  --disable-doc
  --disable-programs
  --disable-everything
  --disable-avdevice
  --disable-avformat
  --disable-swscale
  --disable-postproc
  --disable-avfilter
  --disable-symver
  --enable-swresample
  --extra-ldexeflags=-pie
  --disable-v4l2-m2m
  --disable-vulkan
)
for decoder in "${DECODERS[@]}"; do COMMON+=("--enable-decoder=${decoder}"); done

cd "${JNI}/ffmpeg"
for abi in "${ABIS[@]}"; do
  if [[ -f "android-libs/${abi}/libavcodec.a" ]]; then
    echo "FFmpeg ${abi} already built"
    continue
  fi
  echo "Building FFmpeg for ${abi}"
  case "${abi}" in
    armeabi-v7a)
      arch=(--arch=arm --cpu=armv7-a
        --cross-prefix="${TOOLCHAIN}/armv7a-linux-androideabi${ANDROID_API}-"
        --extra-cflags="-march=armv7-a -mfloat-abi=softfp"
        --extra-ldflags="-Wl,--fix-cortex-a8")
      ;;
    arm64-v8a)
      arch=(--arch=aarch64 --cpu=armv8-a
        --cross-prefix="${TOOLCHAIN}/aarch64-linux-android${ANDROID_API}-")
      ;;
    *)
      echo "Unsupported ABI ${abi}"
      exit 1
      ;;
  esac
  ./configure \
    --libdir="android-libs/${abi}" \
    --nm="${TOOLCHAIN}/llvm-nm" \
    --ar="${TOOLCHAIN}/llvm-ar" \
    --ranlib="${TOOLCHAIN}/llvm-ranlib" \
    --strip="${TOOLCHAIN}/llvm-strip" \
    "${arch[@]}" "${COMMON[@]}"
  # configure warns rather than fails on a decoder name FFmpeg has renamed, and the result is a
  # codec that silently plays silent, so check the generated config before spending the build.
  for decoder in "${DECODERS[@]}"; do
    grep -q "define CONFIG_$(echo "${decoder}" | tr '[:lower:]' '[:upper:]')_DECODER 1" config.h ||
      { echo "FFmpeg did not enable the ${decoder} decoder"; exit 1; }
  done
  make -j"${JOBS}"
  make install-libs
  make clean
done

# The module's own gradle build compiles the JNI wrapper for every ABI it knows about, and we
# only have FFmpeg for two of them.
MODULE="${MEDIA3}/libraries/decoder_ffmpeg"
if ! grep -q 'abiFilters' "${MODULE}/build.gradle.kts"; then
  {
    # The same NDK that built the static FFmpeg libraries, not whatever AGP defaults to.
    printf 'android.ndkVersion = "%s"\n' "${NDK_VERSION}"
    printf 'android.defaultConfig.ndk.abiFilters.addAll(listOf(%s))\n' \
      "$(printf '"%s", ' "${ABIS[@]}" | sed 's/, $//')"
  } >> "${MODULE}/build.gradle.kts"
fi

cd "${MEDIA3}"
./gradlew --no-daemon :lib-decoder-ffmpeg:assembleRelease

AAR="$(find "${MEDIA3}/libraries/decoder_ffmpeg" -name '*-release.aar' -print -quit)"
[[ -n "${AAR}" ]] || { echo "No AAR produced"; exit 1; }
mkdir -p "${ROOT}/libs"
cp "${AAR}" "${ROOT}/libs/media3-decoder-ffmpeg-${MEDIA3_TAG}.aar"
echo "Wrote ${ROOT}/libs/media3-decoder-ffmpeg-${MEDIA3_TAG}.aar"
unzip -l "${ROOT}/libs/media3-decoder-ffmpeg-${MEDIA3_TAG}.aar" | grep '\.so$' || true

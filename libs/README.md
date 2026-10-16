# Prebuilt libraries

## media3-decoder-ffmpeg-1.11.1.aar

Media3's FFmpeg audio decoder extension. Google does not publish it to Maven
([androidx/media#2781](https://github.com/google/ExoPlayer/issues/2781)), so it is built from
source and checked in. That keeps the NDK out of everyday builds and out of CI, which is why the
binary lives in the repository rather than being compiled on every machine.

Rebuild it with `../scripts/build-ffmpeg-decoder.sh`, which pins every input:

| Input | Pin |
| --- | --- |
| androidx/media | `1.11.1`, the Media3 version in `gradle/libs.versions.toml` |
| FFmpeg | `n6.0.1` |
| NDK | `26.1.10909125` |
| ABIs | `armeabi-v7a`, `arm64-v8a` |

Rerun it whenever the Media3 version moves. On an x86 device the library simply does not load and
playback falls back to the platform decoders, which is what happened before this existed.

### Decoders

MP1, MP2, MP3, AC-3, E-AC-3, DTS, AAC, Opus, Vorbis, FLAC, ALAC, PCM mu-law and PCM A-law. The
player puts this behind the platform decoders, so in practice it carries the formats Android has
no decoder for: MPEG-1 Layer II from European DVB feeds, AC-3 and E-AC-3, DTS and ALAC.

TrueHD is deliberately left out. Media3's Matroska extractor batches sixteen TrueHD access units
into one sample and only the first carries a major sync, so FFmpeg rejects the rest. Without the
decoder a TrueHD track reports unsupported, the picture keeps playing and the player says so.

### Licence

FFmpeg is built LGPL v2.1 or later: `configure` runs without `--enable-gpl` and without
`--enable-nonfree`, so no GPL-only or non-free component is compiled in. Its licence text is
`COPYING.LGPLv2.1` in the FFmpeg source at the pinned tag,
<https://github.com/FFmpeg/FFmpeg/tree/n6.0.1>.

FFmpeg is statically linked into `libffmpegJNI.so`. The LGPL asks that a recipient be able to
relink the work against a modified FFmpeg; `../scripts/build-ffmpeg-decoder.sh` plus the pins
above are that means, and FFmpeg is used unmodified. The extension's own Java and JNI code is
Apache 2.0, from androidx/media.

The app credits FFmpeg in Settings, under About.

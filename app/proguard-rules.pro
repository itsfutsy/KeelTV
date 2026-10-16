# Room writes its implementations at build time and reaches them by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

-keepclassmembers class com.keeltv.app.data.model.** { <fields>; <init>(...); }
-keepclassmembers class kotlin.Metadata { *; }
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# OkHttp and Okio name platform classes that are not on Android.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Media3 picks renderers and extractors up by reflection.
-keep class androidx.media3.exoplayer.** { *; }
-dontwarn androidx.media3.**

# The ffmpeg audio decoder in libs/. DefaultRenderersFactory finds the renderer by name,
# and libffmpegJNI.so calls back into the decoder, so neither survives being renamed.
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer { <init>(...); }
-keep class androidx.media3.decoder.ffmpeg.ExperimentalFfmpegVideoRenderer { <init>(...); }
-keep,includedescriptorclasses class androidx.media3.decoder.ffmpeg.** { native <methods>; }
-keep,includedescriptorclasses class androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder {
  private java.nio.ByteBuffer growOutputBuffer(androidx.media3.decoder.SimpleDecoderOutputBuffer, int);
}

-dontwarn androidx.tv.material3.**

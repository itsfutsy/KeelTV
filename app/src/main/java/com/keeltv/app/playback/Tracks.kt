package com.keeltv.app.playback

import java.util.Locale

enum class TrackKind { AUDIO, SUBTITLE }

data class PlayerTrack(
  val id: String,
  val kind: TrackKind,
  val language: String? = null,
  val label: String = "",
  val codec: String? = null,
  val channels: Int = 0,
  val isDefault: Boolean = false,
  val forced: Boolean = false,
  val supported: Boolean = true,
)

data class CaptionAppearance(
  val size: Int = 100,
  val color: String = WHITE,
  val background: Float = 0.6f,
) {
  companion object {
    const val WHITE = "white"
    const val YELLOW = "yellow"
  }
}

object CaptionMode {
  const val OFF = "off"
  const val AUTOMATIC = "automatic"
  const val ON = "on"
  val ALL = listOf(OFF, AUTOMATIC, ON)
}

data class PlaybackPreferences(
  val audioLanguage: String = "",
  val captionLanguage: String = "",
  val captionMode: String = CaptionMode.OFF,
  val appearance: CaptionAppearance = CaptionAppearance(),
)

data class TrackChoice(val audioId: String? = null, val subtitleId: String? = null)

private val LANGUAGE_ALIASES = mapOf(
  "eng" to "en", "fra" to "fr", "fre" to "fr", "deu" to "de", "ger" to "de", "spa" to "es",
  "ita" to "it", "por" to "pt", "zho" to "zh", "chi" to "zh", "jpn" to "ja", "kor" to "ko",
  "ara" to "ar", "hin" to "hi", "rus" to "ru", "nld" to "nl", "dut" to "nl", "pol" to "pl",
  "tur" to "tr", "ell" to "el", "gre" to "el", "swe" to "sv", "dan" to "da", "nor" to "no",
  "fin" to "fi", "ces" to "cs", "cze" to "cs", "heb" to "he", "tha" to "th", "vie" to "vi",
  "ind" to "id", "ukr" to "uk",
)

fun languageCode(value: String?): String {
  val parts = (value ?: "").trim().lowercase(Locale.ROOT).replace('_', '-').split('-').toMutableList()
  parts[0] = LANGUAGE_ALIASES[parts[0]] ?: parts[0]
  if (parts[0] == "und" || parts[0].isEmpty()) return ""
  return parts.joinToString("-")
}

fun languageMatch(track: PlayerTrack, language: String): Int {
  val wanted = languageCode(language)
  val actual = languageCode(track.language)
  if (wanted.isEmpty() || actual.isEmpty()) return 0
  if (actual == wanted) return 2
  return if (actual.substringBefore('-') == wanted.substringBefore('-')) 1 else 0
}

fun preferredTrack(tracks: List<PlayerTrack>, language: String): PlayerTrack? =
  tracks.filter { it.supported }
    .sortedWith(
      compareByDescending<PlayerTrack> { languageMatch(it, language) }
        .thenByDescending { it.isDefault },
    )
    .firstOrNull()

fun chooseTracks(tracks: List<PlayerTrack>, prefs: PlaybackPreferences): TrackChoice {
  val audio = preferredTrack(tracks.filter { it.kind == TrackKind.AUDIO }, prefs.audioLanguage)
  val subs = tracks.filter { it.kind == TrackKind.SUBTITLE && it.supported }
  if (prefs.captionMode == CaptionMode.OFF) return TrackChoice(audio?.id)
  if (prefs.captionMode == CaptionMode.AUTOMATIC) {
    val language = prefs.captionLanguage.ifEmpty { audio?.language.orEmpty() }
    val forced = subs.filter { it.forced && languageMatch(it, language) > 0 }
    return TrackChoice(audio?.id, preferredTrack(forced, language)?.id)
  }
  val matched = subs.filter { !it.forced && languageMatch(it, prefs.captionLanguage) > 0 }
  return TrackChoice(audio?.id, preferredTrack(matched.ifEmpty { subs }, prefs.captionLanguage)?.id)
}

fun trackLabel(track: PlayerTrack): String {
  val label = track.label.trim()
  val code = languageCode(track.language)
  if (label.isNotEmpty() && !label.equals(track.language?.trim(), ignoreCase = true)) return label
  if (code.isNotEmpty()) {
    val named = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH)
    if (named.isNotEmpty() && !named.equals(code, ignoreCase = true)) return named
  }
  return label.ifEmpty { if (track.kind == TrackKind.AUDIO) "Audio track" else "Captions" }
}

fun trackDetail(track: PlayerTrack): String = listOfNotNull(
  track.language?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT),
  track.codec?.takeIf { it.isNotBlank() },
  when (track.channels) {
    0 -> null
    1 -> "Mono"
    2 -> "Stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "${track.channels} ch"
  },
  if (track.forced) "Forced" else null,
  if (!track.supported) "Not supported" else null,
).joinToString(" · ")

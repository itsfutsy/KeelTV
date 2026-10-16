package com.keeltv.app.core

import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.MediaKind

private val LANGUAGE_TABLE = listOf(
    "English" to "en eng english",
    "French" to "fr fra fre french français",
    "German" to "de deu ger german deutsch",
    "Spanish" to "es spa spanish español",
    "Italian" to "it ita italian italiano",
    "Portuguese" to "pt por portuguese português",
    "Arabic" to "ar ara arab arabic",
    "Dutch" to "nl nld dut dutch",
    "Polish" to "pl pol polish",
    "Turkish" to "tr tur turkish",
    "Russian" to "ru rus russian",
    "Greek" to "el ell gre greek",
    "Hindi" to "hi hin hindi",
    "Tamil" to "ta tam tamil",
    "Telugu" to "te tel telugu",
    "Malayalam" to "ml mal malayalam",
    "Bengali" to "bn ben bengali",
    "Urdu" to "ur urd urdu",
    "Punjabi" to "pa pan punjabi",
    "Chinese" to "zh zho chi chinese",
    "Japanese" to "ja jpn japanese",
    "Korean" to "ko kor korean",
    "Thai" to "th tha thai",
    "Vietnamese" to "vi vie vietnamese",
    "Indonesian" to "id ind indonesian",
    "Swedish" to "sv swe swedish",
    "Norwegian" to "no nor norwegian",
    "Danish" to "da dan danish",
    "Finnish" to "fi fin finnish",
    "Czech" to "cs ces cze czech",
    "Slovak" to "sk slk slo slovak",
    "Romanian" to "ro ron rum romanian",
    "Hungarian" to "hu hun hungarian",
    "Bulgarian" to "bg bul bulgarian",
    "Croatian" to "hr hrv croatian",
    "Serbian" to "sr srp serbian",
    "Ukrainian" to "ukr ukrainian",
    "Hebrew" to "he heb hebrew",
    "Persian" to "fa fas per persian",
    "Albanian" to "sq alb sqi albanian shqip",
    "Bosnian" to "bs bos bosnian",
    "Macedonian" to "mk mkd mac macedonian",
    "Slovenian" to "sl slv slovenian",
    "Lithuanian" to "lt lit lithuanian",
    "Latvian" to "lv lav latvian",
    "Estonian" to "et est estonian",
    "Armenian" to "hy arm armenian",
    "Azerbaijani" to "az aze azerbaijani",
    "Icelandic" to "isl ice icelandic",
    "Malay" to "ms msa malay malaysian",
    // `si` is deliberately absent: SI is Slovenia's code and reads as a bouquet far more often.
    "Sinhala" to "sin sinhala sinhalese",
    "Kurdish" to "ku kur kurdish sorani badini kurmanji",
    "Maltese" to "mt mlt maltese",
    "Spanish (Latino)" to "latino",
)

private val LANGUAGES: Map<String, String> = buildMap {
  for ((label, aliases) in LANGUAGE_TABLE) {
    for (alias in aliases.split(' ')) put(fold(alias), label)
  }
}

val LANGUAGE_CODES: Map<String, String> = buildMap {
  for ((label, aliases) in LANGUAGE_TABLE) {
    put(label, aliases.substringBefore(' ').take(3).uppercase())
  }
}

fun languageCode(label: String): String =
  LANGUAGE_CODES[label] ?: label.take(3).uppercase()

val LANGUAGE_OPTIONS: List<String> = LANGUAGES.values.distinct().sorted()

const val UNKNOWN_LANGUAGE = "unknown"

const val CATALOG_RULES_VERSION = 9

val LANGUAGE_FILTER_CHOICES: List<Pair<String, String>> =
  listOf("" to "All languages") +
    LANGUAGE_OPTIONS.map { it to it } +
    listOf(
      "Multi-audio" to "Multi-audio",
      "Dual audio" to "Dual audio",
      UNKNOWN_LANGUAGE to "Unknown language",
    )

data class LanguageHint(
  val label: String,
  val description: String,
  val languages: List<String> = label.split(" / "),
)

private val REGION_TABLE = listOf(
  "AU GB IE NZ UK US ZA NG PH SG" to "English",
  "CA" to "English / French",
  "IN" to "Hindi",
  "PK" to "Urdu",
  "AR CL CO CR DO EC ES MX PE PR SV UY VE" to "Spanish",
  "BR PT" to "Portuguese",
  "FR QC" to "French",
  "AT DE" to "German",
  "CH" to "German / French / Italian",
  "BE" to "Dutch / French",
  "LU" to "French / German",
  "IT" to "Italian",
  "NL" to "Dutch",
  "AE EG IQ KW QA SA SY" to "Arabic",
  "DZ LB MA TN" to "Arabic / French",
  "GR" to "Greek",
  "IL" to "Hebrew",
  "AF IR" to "Persian",
  "CN TW" to "Chinese",
  "JP" to "Japanese",
  "KR" to "Korean",
  "TH" to "Thai",
  "VN" to "Vietnamese",
  "ID" to "Indonesian",
  "DK" to "Danish",
  "SE" to "Swedish",
  "NO" to "Norwegian",
  "FI" to "Finnish",
  "CZ" to "Czech",
  "SK" to "Slovak",
  "MD RO" to "Romanian",
  "HU" to "Hungarian",
  "BG" to "Bulgarian",
  "HR" to "Croatian",
  "ME RS" to "Serbian",
  "UA" to "Ukrainian",
  "BY RU" to "Russian",
  "PL" to "Polish",
  "TR" to "Turkish",
  "AL XK" to "Albanian",
  "BA" to "Bosnian",
  "MK" to "Macedonian",
  "SI" to "Slovenian",
  "LT" to "Lithuanian",
  "LV" to "Latvian",
  "EE" to "Estonian",
  "AM" to "Armenian",
  "AZ" to "Azerbaijani",
  "IS" to "Icelandic",
  "LK" to "Sinhala",
  "BD" to "Bengali",
  "CY" to "Greek",
  "MT" to "Maltese / English",
  "KU" to "Kurdish",
  "LA" to "Spanish",
)

private val REGION_LANGUAGES: Map<String, List<String>> = buildMap {
  for ((codes, languages) in REGION_TABLE) {
    for (code in codes.split(' ')) put(code, languages.split(" / "))
  }
}

private val COUNTRY_TABLE = listOf(
  "AU" to "australia",
  "UK" to "united kingdom, great britain, britain, england, scotland, wales",
  "US" to "united states, usa",
  "IE" to "ireland",
  "NZ" to "new zealand",
  "ZA" to "south africa",
  "CA" to "canada",
  "IN" to "india",
  "PK" to "pakistan",
  "PH" to "philippines",
  "NG" to "nigeria",
  "AR" to "argentina",
  "CL" to "chile",
  "CO" to "colombia",
  "CR" to "costa rica",
  "DO" to "dominican republic",
  "EC" to "ecuador",
  "ES" to "spain, españa",
  "MX" to "mexico, méxico",
  "PE" to "peru, perú",
  "PR" to "puerto rico",
  "SV" to "el salvador",
  "UY" to "uruguay",
  "VE" to "venezuela",
  "BR" to "brazil, brasil",
  "PT" to "portugal",
  "FR" to "france",
  "DE" to "germany, deutschland",
  "AT" to "austria",
  "CH" to "switzerland",
  "BE" to "belgium",
  "IT" to "italy, italia",
  "NL" to "netherlands, holland",
  "AE" to "united arab emirates, emirates, uae",
  "EG" to "egypt",
  "IQ" to "iraq",
  "KW" to "kuwait",
  "LB" to "lebanon",
  "MA" to "morocco",
  "QA" to "qatar",
  "SA" to "saudi arabia",
  "SY" to "syria",
  "TN" to "tunisia",
  "DZ" to "algeria",
  "GR" to "greece",
  "IL" to "israel",
  "IR" to "iran",
  "CN" to "china",
  "TW" to "taiwan",
  "JP" to "japan",
  "KR" to "korea, south korea",
  "TH" to "thailand",
  "VN" to "vietnam",
  "ID" to "indonesia",
  "DK" to "denmark",
  "SE" to "sweden",
  "NO" to "norway",
  "FI" to "finland",
  "CZ" to "czech republic, czechia",
  "SK" to "slovakia",
  "RO" to "romania",
  "HU" to "hungary",
  "BG" to "bulgaria",
  "HR" to "croatia",
  "RS" to "serbia",
  "UA" to "ukraine",
  "RU" to "russia",
  "PL" to "poland",
  "TR" to "turkey, türkiye",
  "AL" to "albania",
  "XK" to "kosovo",
  "BA" to "bosnia, bosnia and herzegovina",
  "ME" to "montenegro",
  "MK" to "macedonia, north macedonia",
  "SI" to "slovenia",
  "LT" to "lithuania",
  "LV" to "latvia",
  "EE" to "estonia",
  "AM" to "armenia",
  "AZ" to "azerbaijan",
  "IS" to "iceland",
  "LK" to "sri lanka",
  "BD" to "bangladesh",
  "CY" to "cyprus",
  "MT" to "malta",
  "SG" to "singapore",
)

private val COUNTRIES: Map<String, String> = buildMap {
  for ((code, names) in COUNTRY_TABLE) {
    for (name in names.split(", ")) put(fold(name), code)
  }
}

private val COUNTRY_HEAD = Regex("^(\\p{L}+)(?:\\s+(\\p{L}+))?")

private fun countryCode(text: String): String? {
  val head = COUNTRY_HEAD.find(text.trim()) ?: return null
  val first = fold(head.groupValues[1])
  val second = head.groupValues[2]
  if (second.isNotEmpty()) COUNTRIES["$first ${fold(second)}"]?.let { return it }
  return COUNTRIES[first]
}

private val MULTI = Regex("^(multi(?:[ -]*(?:audio|lang(?:uage)?s?))?)$")
private val DUAL = Regex("^dual[ -]*audio$")
private val LOCALE_TAG = Regex("^[a-z]{2,3}-[a-z]{2}$", RegexOption.IGNORE_CASE)

private fun languageLabel(value: String): String? {
  val key = fold(value.trim())
  if (MULTI.matches(key)) return "Multi-audio"
  if (DUAL.matches(key)) return "Dual audio"
  return LANGUAGES[key]
}

private val DECOR = Regex("^[\\s★☆❖✪◉▣●✺✦✧◆◇■□▪▫•►▶➤※»«|:—–\\-]+")
private val PREFIX = Regex(
  "^(?:\\[([^\\]]{2,24})\\]|\\(([^)]{2,24})\\)|([\\p{L}\\d]+(?:-[\\p{L}\\d]+)?)\\s*(?:[★☆❖✪◉▣●✺✦✧◆◇■□▪▫•►▶➤※»«|:]|[-—–]\\s))\\s*"
)
private val BARE_TOKEN = Regex("^([\\p{L}\\d-]+)(?:\\s+|$)")
private val TAIL_GROUPS = Regex("[\\[(]([^()\\[\\]]{2,24})[)\\]]")

private val NEVER_A_LANGUAGE = setOf(
  "4K", "8K", "UHD", "FHD", "HD", "SD", "EU", "ALL", "MULTISUB",
  "AFR", "ASIA", "EXYU", "EX-YU",
)

private val SUBTITLE_MARKS = setOf("SUB", "SUBS", "SUBBED", "MULTISUB", "MULTISUBS")

private data class TaggedLabel(val label: String, val region: Boolean)

private fun taggedLabel(raw: String, category: Boolean): TaggedLabel? {
  var rest = DECOR.replace(raw.trim(), "")
  var region: TaggedLabel? = null
  for (i in 0 until 5) {
    val match = PREFIX.find(rest) ?: (if (category) BARE_TOKEN.find(rest) else null) ?: break
    val groups = match.groupValues
    val token = (groups.drop(1).firstOrNull { it.isNotEmpty() } ?: "").trim()
    val code = token.uppercase()
    val parts = if ('-' in token && code !in PREFIX_CODES) {
      listOf(token) + token.split('-').filter { it.isNotEmpty() }
    } else {
      listOf(token)
    }
    val subtitles = parts.any { it.uppercase() in SUBTITLE_MARKS }
    if (!subtitles) {
      for (part in parts) {
        val partCode = part.uppercase()
        languageLabel(part)?.let { return TaggedLabel(it, false) }
        if (region == null && partCode in PREFIX_CODES && partCode !in NEVER_A_LANGUAGE) {
          region = TaggedLabel(partCode, true)
        }
      }
    }
    if (category && region == null) {
      (countryCode(token) ?: countryCode(rest))?.let { region = TaggedLabel(it, true) }
    }
    if (code !in PREFIX_CODES && code !in KIND_MARKERS) break
    rest = DECOR.replace(rest.substring(match.value.length).trim(), "")
  }
  region?.let { return it }
  val tail = TAIL_GROUPS.findAll(raw).map { it.groupValues[1].trim() }.toList().asReversed()
  for (group in tail) {
    val label = languageLabel(group)
    if (label != null) return TaggedLabel(label, false)
  }
  for (group in tail) {
    val code = group.uppercase()
    if (code in PREFIX_CODES && code !in NEVER_A_LANGUAGE) return TaggedLabel(code, true)
  }
  if (category) {
    scriptLanguage(raw)?.let { return TaggedLabel(it, false) }
    countryNamed(raw)?.let { return TaggedLabel(it, true) }
  }
  return null
}

private val SCRIPTS = listOf(
  Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\uFB50-\\uFDFF\\uFE70-\\uFEFF]") to "Arabic",
  Regex("[\\u0370-\\u03FF\\u1F00-\\u1FFF]") to "Greek",
  Regex("[\\u0590-\\u05FF]") to "Hebrew",
  Regex("[\\u0E00-\\u0E7F]") to "Thai",
  Regex("[\\u1100-\\u11FF\\uAC00-\\uD7AF]") to "Korean",
  Regex("[\\u0900-\\u097F]") to "Hindi",
)

private fun scriptLanguage(text: String): String? =
  SCRIPTS.firstOrNull { it.first.containsMatchIn(text) }?.second

private val WORD = Regex("\\p{L}+")

private fun countryNamed(text: String): String? {
  val words = WORD.findAll(text).map { fold(it.value) }.toList()
  for (i in words.indices) {
    if (i + 1 < words.size) COUNTRIES["${words[i]} ${words[i + 1]}"]?.let { return it }
    COUNTRIES[words[i]]?.let { return it }
  }
  return null
}

private val LANGUAGE_SPLIT = Regex("[,;/+]")

fun mediaLanguage(item: MediaItem): LanguageHint? {
  if (item.kind == MediaKind.LIVE) return null
  val provider = item.language
  if (!provider.isNullOrBlank()) {
    val labels = provider.split(LANGUAGE_SPLIT).mapNotNull { value ->
      val trimmed = value.trim()
      languageLabel(trimmed)
        ?: if (LOCALE_TAG.matches(trimmed)) languageLabel(trimmed.substringBefore('-')) else null
    }
    if (labels.isNotEmpty()) {
      return LanguageHint(labels.distinct().joinToString(" / "), "Provider language: $provider")
    }
  }
  return tagHint(item)
}

fun channelLanguage(item: MediaItem): LanguageHint? =
  if (item.kind == MediaKind.LIVE) withNamedLanguage(item, tagHint(item)) else mediaLanguage(item)

private val NAMED_CHANNELS = listOf(
  Regex("\\bcnn ?(international|int|i)\\b") to "English",
  Regex("^(?!.*\\barabic\\b).*\\bbbc world\\b") to "English",
  Regex("\\bsky news\\b(?! arabia)") to "English",
  Regex("^(?!.*\\b(adria|bulgaria|ht|financiero)\\b).*\\bbloomberg\\b") to "English",
  Regex("^(?!.*\\b(arabiya|arabic|aawaz|awaaz|bazar)\\b).*\\bcnbc\\b") to "English",
  Regex("\\bal ?jazeera\\b.*\\b(english|eng|en)\\b") to "English",
  Regex("\\bdw (english|news)\\b") to "English",
  Regex("\\bfrance ?24\\b.*\\b(english|eng|en)\\b") to "English",
  Regex("\\beuronews (english|world)\\b") to "English",
  Regex("\\b(zdf\\w*|ard|das erste|3sat|prosieben|sat\\.1|kabel eins|rtl television|rtl ?zwei|super rtl|rtl nitro)\\b") to "German",
)

private fun withNamedLanguage(item: MediaItem, hint: LanguageHint?): LanguageHint? {
  val name = fold(item.name)
  val named = NAMED_CHANNELS.firstOrNull { it.first.containsMatchIn(name) }?.second ?: return hint
  if (hint == null) return LanguageHint(named, "Channel name: ${item.name}")
  if (named in hint.languages) return hint
  return hint.copy(languages = hint.languages + named)
}

private fun tagHint(item: MediaItem): LanguageHint? {
  val prefix = "${item.kind.wire}:"
  val category = if (item.categoryId.startsWith(prefix)) item.categoryId.substring(prefix.length) else item.categoryName
  if (item.name.isNotEmpty()) taggedLabel(item.name, false)?.let { return hint(it, "title", item.name) }
  if (!category.isNullOrEmpty()) taggedLabel(category, true)?.let { return hint(it, "category", category) }
  return null
}

private fun hint(tag: TaggedLabel, from: String, raw: String): LanguageHint =
  if (!tag.region) {
    LanguageHint(tag.label, "Provider language tag from $from: $raw")
  } else {
    LanguageHint(tag.label, "Provider region tag from $from: $raw", REGION_LANGUAGES[tag.label].orEmpty())
  }

fun matchesLanguage(item: MediaItem, language: String): Boolean {
  if (language.isEmpty()) return true
  val labels = channelLanguage(item)?.languages.orEmpty()
  return if (language == UNKNOWN_LANGUAGE) labels.isEmpty() else language in labels
}

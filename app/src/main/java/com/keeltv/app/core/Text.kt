package com.keeltv.app.core

import java.text.Normalizer

private val MARKS = Regex("\\p{M}+")

fun fold(input: String): String =
  MARKS.replace(Normalizer.normalize(input, Normalizer.Form.NFD), "").lowercase()

private val APOSTROPHES = Regex("['’ʼ`´]")
private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

fun searchText(input: String): String =
  NOT_WORD.replace(APOSTROPHES.replace(fold(input), ""), " ").trim()

val PREFIX_CODES: Set<String> = setOf(
  "AD", "AE", "AF", "AL", "AM", "AR", "AT", "AU", "AZ", "BA", "BE", "BG", "BR", "BY", "CA", "CH",
  "CL", "CN", "CO", "CZ", "DE", "DK", "DZ", "EC", "EE", "EG", "ES", "FI", "FR", "GB", "GR", "HR",
  "HU", "ID", "IE", "IL", "IN", "IQ", "IR", "IS", "IT", "JP", "KR", "KW", "LB", "LT", "LU", "LV",
  "MA", "MD", "ME", "MK", "MT", "MX", "MY", "NG", "NL", "NO", "NZ", "PE", "PH", "PK", "PL", "PT",
  "QA", "RO", "RS", "RU", "SA", "SE", "SI", "SK", "SY", "TH", "TN", "TR", "TW", "UA", "UK", "US",
  "UY", "VE", "VN", "XK", "ZA",
  "BD", "CY", "KU", "LA", "LK", "QC", "SG",
  "EN", "EU", "4K", "8K", "UHD", "FHD", "HD", "SD", "EX-YU", "EXYU", "LATINO",
  "MULTI", "MULTISUB", "ALL", "ARAB", "AFR", "ASIA",
)

val KIND_MARKERS: Set<String> = setOf("VOD", "SRS", "SERIES", "SERIE", "MOVIES", "MOVIE", "LIVE", "TV")

private const val DECOR_CHARS = "★❖✪◉▣●✺✦✧✩✰☆◆◇■□▪▫•○►▶➤※»«|:#—–\\-"
private const val GLYPH_CHARS = "★❖✪◉▣●✺✦✧✩✰☆◆◇■□▪▫•○►▶➤※»«"

private val LEADING_DECOR = Regex("^[\\s$DECOR_CHARS]+")
private val TRAILING_DECOR = Regex("[\\s$DECOR_CHARS]+$")
private val GLYPH_PREFIX = Regex("^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s./-]{0,11}?\\s*[$GLYPH_CHARS]+\\s*")
private val PIPE_PREFIX = Regex("^[A-Z0-9]{2,6}(?:-[A-Z0-9]{1,6})?\\s*\\|\\s*")

private val CODE_PREFIX = Regex("^([\\p{L}\\p{N}]{2,6}(?:-[\\p{L}\\p{N}]{1,6}){0,2})\\s*(?::\\s*|[-–—]\\s+)")

private fun isPrefixCode(token: String): Boolean {
  val code = token.uppercase()
  if (known(code)) return true
  return '-' in code && code.split('-').any { known(it) }
}

private fun known(code: String) =
  code in PREFIX_CODES || code in KIND_MARKERS

private fun stripCodePrefix(s: String): String {
  val m = CODE_PREFIX.find(s) ?: return s
  if (!isPrefixCode(m.groupValues[1])) return s
  return s.substring(m.value.length)
}

private val TRAILING_YEAR = Regex("\\s*[-–—(\\[]\\s*(?:19|20)\\d{2}\\s*[)\\]]?\\s*$")

private val TAG = Regex(
  "^(?:(?:19|20)\\d{2}|multi[\\s-]*(?:audio|subs?|lang(?:uage)?s?)|dual[\\s-]*audio|(?:sub|dub)bed|multisub)$",
  RegexOption.IGNORE_CASE,
)

private val TAIL_GROUP = Regex("\\s*[(\\[]\\s*([^()\\[\\]]{1,24}?)\\s*[)\\]]\\s*$")
private val COUNTRY_TAIL = Regex("^[A-Z]{2,3}$")
private val MULTI_SPACE = Regex("\\s{2,}")

private fun stripTrailingTags(input: String): String {
  var s = input
  val kept = ArrayDeque<String>()
  for (i in 0 until 5) {
    val m = TAIL_GROUP.find(s) ?: break
    val inner = m.groupValues[1].trim()
    if (TAG.matches(inner)) { s = s.substring(0, m.range.first); continue }
    if (COUNTRY_TAIL.matches(inner)) { kept.addFirst("($inner)"); s = s.substring(0, m.range.first); continue }
    break
  }
  s = TRAILING_DECOR.replace(s, "")
  return if (kept.isNotEmpty() && s.isNotEmpty()) "$s ${kept.joinToString(" ")}" else s
}

private val SEPARATOR = Regex("^\\s*[#=*_]{3,}.*[#=*_]{3,}\\s*$")

fun isSeparatorName(name: String): Boolean = SEPARATOR.containsMatchIn(name)

fun cleanTitle(raw: String): String {
  if (raw.isEmpty()) return ""

  var s = LEADING_DECOR.replace(TRAILING_DECOR.replace(raw, ""), "")
  for (i in 0 until 3) {
    val stripped = PIPE_PREFIX.replace(GLYPH_PREFIX.replace(s, ""), "")
    val dashed = LEADING_DECOR.replace(stripCodePrefix(stripped), "")
    if (dashed == s || dashed.isEmpty()) break
    s = dashed
  }
  var undecorated = TRAILING_DECOR.replace(s, "")
  for (i in 0 until 4) {
    val next = TRAILING_DECOR.replace(TRAILING_YEAR.replace(stripTrailingTags(undecorated), ""), "")
    if (next == undecorated || next.isEmpty()) break
    undecorated = next
  }
  s = undecorated
  if (s.contains("  ")) s = MULTI_SPACE.replace(s, " ")
  s = s.trim()

  return if (s.isNotEmpty()) s else raw.trim()
}

private val HMS = Regex("^(?:(\\d{1,3}):)?(\\d{1,2}):(\\d{2})(?:\\.\\d+)?$")

fun parseHms(raw: String?): Int? {
  if (raw.isNullOrBlank()) return null
  val m = HMS.find(raw.trim()) ?: return null
  val secs = (m.groupValues[1].ifEmpty { "0" }).toInt() * 3600 +
    m.groupValues[2].toInt() * 60 + m.groupValues[3].toInt()
  return if (secs > 0) secs else null
}

private val ISO_DATE = Regex("^((?:19|20)\\d{2})-\\d{2}-\\d{2}")
private val TRAILING_YEAR_CAPTURE = Regex("[-–—(\\[]\\s*((?:19|20)\\d{2})\\s*[)\\]]?\\s*$")
private val BRACKETED_YEAR = Regex("[(\\[]\\s*((?:19|20)\\d{2})\\s*[)\\]]")

fun parseYear(raw: String?): Int? {
  if (raw.isNullOrEmpty()) return null
  ISO_DATE.find(raw)?.let { return it.groupValues[1].toInt() }
  TRAILING_YEAR_CAPTURE.find(raw)?.let { return it.groupValues[1].toInt() }
  return BRACKETED_YEAR.findAll(raw).lastOrNull()?.groupValues?.get(1)?.toInt()
}

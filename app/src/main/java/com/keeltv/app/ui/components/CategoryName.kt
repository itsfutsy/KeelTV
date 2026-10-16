package com.keeltv.app.ui.components

import com.keeltv.app.core.KIND_MARKERS
import com.keeltv.app.core.PREFIX_CODES

private const val DECOR = "★☆✪❖◉▣●■◆♦▪▸►|:"

private val CATEGORY_PREFIX = Regex("^([\\p{L}\\d-]{2,6})\\s*[$DECOR-]*\\s*")
private val CATEGORY_SUFFIX = Regex("\\s*[\\[(]([\\p{L}\\d-]{2,8})[\\])]\\s*$")
private val CATEGORY_DECOR = Regex("^[$DECOR\\s]+")

private val BRACKETED = Regex("[\\[(][^\\])]*[\\])]")

private val QUALITY_TOKENS = setOf("4K", "UHD", "FHD", "HD", "SD", "HEVC", "H265")

private val WORD = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'’-]*")

private fun titleCase(input: String): String = WORD.replace(input.lowercase()) { m ->
  val word = m.value
  when {
    word.uppercase() in QUALITY_TOKENS || word.all { it.isDigit() } -> word.uppercase()
    word.length <= 3 && word.all { it in 'a'..'z' } && word.uppercase() in PREFIX_CODES -> word.uppercase()
    else -> word.replaceFirstChar { it.uppercase() }
  }
}

data class ParsedCategory(val chips: List<String>, val label: String, val raw: String)

fun parseCategory(raw: String): ParsedCategory {
  var rest = CATEGORY_DECOR.replace(raw.trim(), "").trim()
  val chips = mutableListOf<String>()

  val suffix = CATEGORY_SUFFIX.find(rest)
  if (suffix != null && suffix.groupValues[1].uppercase() in PREFIX_CODES) {
    chips += suffix.groupValues[1].uppercase()
    rest = rest.substring(0, suffix.range.first).trim()
  }

  for (i in 0 until 3) {
    val m = CATEGORY_PREFIX.find(rest) ?: break
    val token = m.groupValues[1].uppercase()
    val marker = token in KIND_MARKERS
    val fenced = rest.substring(m.groupValues[1].length).trimStart().startsWith("|")
    if (!marker && !fenced && token !in PREFIX_CODES) break
    val remainder = CATEGORY_DECOR.replace(rest.substring(m.value.length), "").trim()
    if (BRACKETED.replace(remainder, "").isBlank()) break
    if (!marker && token !in chips) chips += token
    rest = remainder
  }

  rest = CATEGORY_DECOR.replace(rest, "").trim()
  return ParsedCategory(chips, titleCase(rest).ifEmpty { titleCase(raw) }, raw)
}

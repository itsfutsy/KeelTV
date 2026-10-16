package com.keeltv.app.core

import java.text.Normalizer

private val RESOLUTION = Regex(
  "(?<![\\p{L}\\p{N}])(8K|4K|UHD|2160P?|3840P?)(?![\\p{L}\\p{N}])",
  RegexOption.IGNORE_CASE,
)

fun pictureQuality(name: String, categoryName: String?): String? {
  val found = RESOLUTION.find(name) ?: categoryName?.let {
    RESOLUTION.find(Normalizer.normalize(it, Normalizer.Form.NFKC))
  } ?: return null
  return if (found.value.equals("8K", ignoreCase = true)) "8K" else "4K"
}

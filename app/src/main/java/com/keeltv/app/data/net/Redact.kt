package com.keeltv.app.data.net

private const val MASK = "***"

private val SECRET_PARAM = Regex("([?&](?:password|pass|token)=)[^&#\\s]*", RegexOption.IGNORE_CASE)
private val URL_IN_TEXT = Regex("\\bhttps?://[^\\s\"'<>()\\[\\]{}]+", RegexOption.IGNORE_CASE)
private val TRAILING_PUNCTUATION = Regex("[.,;:!?]+$")
private val STREAM_TAIL = Regex("^\\d+(?:\\.[A-Za-z0-9]{1,5})?$")

/** Path prefixes Xtream follows with `<username>/<password>`. */
private val CREDENTIAL_PATH_PREFIXES = setOf("live", "movie", "series", "timeshift", "hls")

fun redactUrl(url: String): String {
  val masked = SECRET_PARAM.replace(url) { "${it.groupValues[1]}$MASK" }
  val schemeEnd = masked.indexOf("://")
  if (schemeEnd < 0) return masked
  val pathStart = masked.indexOf('/', schemeEnd + 3)
  if (pathStart < 0) return masked
  val queryStart = masked.indexOfFirst(pathStart) { it == '?' || it == '#' }
  val path = masked.substring(pathStart, queryStart)
  val rest = masked.substring(queryStart)
  return masked.substring(0, pathStart) + maskCredentialPath(path) + rest
}

private inline fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int {
  for (i in from until length) if (predicate(this[i])) return i
  return length
}

private fun maskCredentialPath(path: String): String {
  val segments = path.split('/').toMutableList()
  val prefix = segments.getOrNull(1)?.lowercase()
  if (prefix != null && prefix in CREDENTIAL_PATH_PREFIXES && segments.size >= 5) {
    segments[3] = MASK
    return segments.joinToString("/")
  }
  // The bare legacy form `/<user>/<pass>/<streamId>[.ext]`, still served by older servers.
  if (segments.size == 4 && STREAM_TAIL.matches(segments[3])) {
    segments[2] = MASK
    return segments.joinToString("/")
  }
  return path
}

fun redactText(text: String): String = URL_IN_TEXT.replace(text) { match ->
  val trailing = TRAILING_PUNCTUATION.find(match.value)?.value ?: ""
  val url = if (trailing.isEmpty()) match.value else match.value.dropLast(trailing.length)
  redactUrl(url) + trailing
}

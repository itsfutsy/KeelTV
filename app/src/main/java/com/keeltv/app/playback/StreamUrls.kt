package com.keeltv.app.playback

/**
 * What to try, in order, for one live channel. The provider serves MPEG-TS at `.ts`, and most
 * servers also answer `.m3u8` on the same path. Asking for the TS first keeps the single
 * connection short and avoids a playlist round trip.
 */
fun liveCandidates(url: String): List<String> {
  val query = url.indexOf('?')
  val path = if (query < 0) url else url.substring(0, query)
  val tail = if (query < 0) "" else url.substring(query)
  val slash = path.lastIndexOf('/')
  val dot = path.lastIndexOf('.')
  val extension = if (dot > slash) path.substring(dot + 1).lowercase() else ""

  return when (extension) {
    "m3u8", "m3u" -> listOf(url)
    "" -> listOf("$path.ts$tail", "$path.m3u8$tail")
    else -> listOf(url, path.substring(0, dot) + ".m3u8" + tail)
  }
}

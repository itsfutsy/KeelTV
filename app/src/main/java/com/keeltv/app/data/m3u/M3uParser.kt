package com.keeltv.app.data.m3u

import com.keeltv.app.data.model.Category
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.data.xtream.NO_EPG_CHANNEL
import com.keeltv.app.data.xtream.indexed
import okio.BufferedSource
import java.text.Normalizer

private const val FALLBACK_GROUP = "Uncategorised"
private val NO_EPG_CHANNEL_FOLDED = NO_EPG_CHANNEL.lowercase()

private val GROUP_MOVIE = Regex("\\b(vod|movies?|films?|cinema)\\b")
private val GROUP_SERIES = Regex("\\b(series?|tv shows?|shows?|seasons?)\\b")
private val EPISODE = Regex("\\bs\\d{1,3}[\\s._-]*e\\d{1,3}\\b", RegexOption.IGNORE_CASE)
private val VIDEO_CONTAINER = Regex("\\.(mkv|mp4|avi|mov)$")
private val MARKS = Regex("[\\u0300-\\u036f]")

private fun groupKind(group: String): MediaKind? {
  if (group.isEmpty()) return null
  val normalised = MARKS.replace(Normalizer.normalize(group, Normalizer.Form.NFD), "").lowercase()
  val movie = GROUP_MOVIE.containsMatchIn(normalised)
  val series = GROUP_SERIES.containsMatchIn(normalised)
  return if (series) MediaKind.SERIES else if (movie) MediaKind.MOVIE else null
}

private fun urlPath(url: String): String {
  var end = url.length
  val hash = url.indexOf('#')
  if (hash != -1) end = hash
  val query = url.indexOf('?')
  if (query != -1 && query < end) end = query
  return if (end == url.length) url else url.substring(0, end)
}

/**
 * M3U has no notion of live vs VOD vs series, so this infers it. Playlists exported from an Xtream
 * server keep /live/, /movie/ and /series/ in the URL path; the rest is a heuristic.
 */
fun classifyEntry(name: String, group: String, url: String): MediaKind {
  val path = urlPath(url).lowercase()

  if (path.contains("/movie/")) return MediaKind.MOVIE
  if (path.contains("/series/")) return MediaKind.SERIES
  if (path.contains("/live/")) return MediaKind.LIVE

  groupKind(group)?.let { return it }

  if (EPISODE.containsMatchIn(name)) return MediaKind.SERIES
  if (VIDEO_CONTAINER.containsMatchIn(path)) return MediaKind.MOVIE

  return MediaKind.LIVE
}

fun hashUrl(url: String): Long {
  var h1 = 0x811c9dc5.toInt()
  var h2 = 0xc2b2ae35.toInt()
  for (c in url) {
    h1 = (h1 xor c.code) * 0x01000193
    h2 = (h2 xor c.code) * 0x85ebca6b.toInt()
  }
  h1 = h1 xor (h1 ushr 15)
  h2 = h2 xor (h2 ushr 13)
  val hi = (h1 ushr 11).toLong() and 0x1fffffL
  val lo = h2.toLong() and 0xffffffffL
  val value = hi * 0x100000000L + lo
  return if (value == 0L) 1L else value
}

private val DECORATION = Regex(
  "^[\\s|]*(?:[A-Z0-9]{1,5}[\\s|:-]*)?[★☆✪✫✬✭✮✯✰◉◎●○◆◇■□▪▫❖✦✧✶✷✸✹✺⁕※▶►➤‣•|]+[\\s|:-]*"
)
private val MULTI_SPACE = Regex("\\s{2,}")
private val YEAR = Regex("\\((19\\d{2}|20\\d{2})\\)")
private val EXT = Regex("^[a-z0-9]{2,5}$")

fun m3uCleanTitle(name: String): String {
  var out = name.trim()
  for (pass in 0 until 2) {
    val stripped = DECORATION.replace(out, "")
    if (stripped == out) break
    out = stripped
  }
  out = MULTI_SPACE.replace(out, " ").trim()
  return out.ifEmpty { name.trim() }
}

private fun containerExtension(url: String): String? {
  val path = urlPath(url)
  val dot = path.lastIndexOf('.')
  if (dot == -1 || dot < path.lastIndexOf('/')) return null
  val ext = path.substring(dot + 1).lowercase()
  return if (EXT.matches(ext)) ext else null
}

private fun nameFromUrl(url: String): String {
  val path = urlPath(url)
  val slash = path.lastIndexOf('/')
  val base = if (slash == -1) path else path.substring(slash + 1)
  if (base.isEmpty()) return url
  return runCatching { java.net.URLDecoder.decode(base, "UTF-8") }.getOrDefault(base)
}

private class ExtInf {
  var name = ""
  var tvgId = ""
  var tvgName = ""
  var language = ""
  var logo = ""
  var group = ""
  var hasArchive = false
  var archiveDays = 0
}

private fun isSpace(c: Char): Boolean = c == ' ' || c == '\t'

private fun isTruthyAttr(value: String): Boolean {
  val v = value.trim().lowercase()
  return v.isNotEmpty() && v != "0" && v != "false" && v != "none"
}

/**
 * Walk `key=value` pairs in `text[start, end)` and hand each one to `assign`.
 *
 * Only `"` counts as a quote. Single quotes deliberately do not: an apostrophe inside an unquoted
 * value (`tvg-name=L'Equipe`) is far more common in real playlists than a single-quoted attribute.
 */
private inline fun scanAttributes(text: String, start: Int, end: Int, assign: (String, String) -> Unit) {
  var i = start
  while (i < end) {
    val c = text[i]
    if (isSpace(c) || c == ',') { i++; continue }

    val keyStart = i
    while (i < end) {
      val k = text[i]
      if (k == '=' || isSpace(k)) break
      i++
    }
    if (i >= end || text[i] != '=') continue
    val key = text.substring(keyStart, i).lowercase()
    i++

    var value: String
    if (i < end && text[i] == '"') {
      i++
      val valueStart = i
      var escaped = false
      while (i < end) {
        val v = text[i]
        if (v == '\\' && i + 1 < end) { escaped = true; i += 2; continue }
        if (v == '"') break
        i++
      }
      value = text.substring(valueStart, i)
      if (escaped) value = Regex("\\\\(.)").replace(value, "$1")
      if (i < end) i++
    } else {
      val valueStart = i
      while (i < end && !isSpace(text[i]) && text[i] != ',') i++
      value = text.substring(valueStart, i)
    }

    assign(key, value)
  }
}

private fun parseExtInf(line: String, start: Int): ExtInf {
  val end = line.length
  // The display name is everything after the first comma that is *not* inside quotes. That single
  // rule keeps both `group-title="Kids, Family",My Show` and `-1,Lock, Stock` right, which a naive
  // first-comma or last-comma split gets wrong one way or the other.
  var split = -1
  var inQuote = false
  var i = start
  while (i < end) {
    val c = line[i]
    if (inQuote) {
      if (c == '\\') i++ else if (c == '"') inQuote = false
    } else if (c == '"') {
      inQuote = true
    } else if (c == ',') {
      split = i
      break
    }
    i++
  }
  // Unbalanced quotes: fall back to the last comma on the line, which is what the naive parsers
  // such playlists were written against would do.
  if (split == -1) {
    val last = line.lastIndexOf(',')
    if (last >= start) split = last
  }
  val attrEnd = if (split == -1) end else split

  val info = ExtInf()
  if (attrEnd < end) info.name = line.substring(attrEnd + 1, end).trim()
  scanAttributes(line, start, attrEnd) { key, value ->
    when (key) {
      "tvg-id", "channel-id" -> info.tvgId = value
      "tvg-name" -> info.tvgName = value
      "tvg-language" -> info.language = value
      "tvg-logo", "logo" -> info.logo = value
      "group-title", "group" -> info.group = value
      "catchup", "catchup-type", "catchup-source" ->
        if (isTruthyAttr(value)) info.hasArchive = true
      "catchup-days", "tvg-rec", "timeshift" -> if (isTruthyAttr(value)) {
        info.hasArchive = true
        value.trim().toIntOrNull()?.let { if (it > 0) info.archiveDays = maxOf(info.archiveDays, it) }
      }
    }
  }
  if (info.name.isEmpty()) info.name = info.tvgName
  return info
}

class M3uParseError(message: String) : Exception(message)

data class M3uResult(
  val items: Int,
  val categories: List<Category>,
  val epgUrl: String? = null,
)

private const val PLAYLIST_MAGIC = "#EXTM3U"

fun parseM3u(
  source: BufferedSource,
  sourceId: String,
  onProgress: ((Int) -> Unit)? = null,
  onItem: (MediaItem) -> Unit,
): M3uResult {
  var pending: ExtInf? = null
  var extGrp = ""
  var epgUrl = ""
  var checkedMagic = false
  var count = 0
  val taken = HashSet<String>()
  val categories = LinkedHashMap<String, Category>()
  val counts = HashMap<String, Int>()

  while (true) {
    val raw = source.readUtf8Line() ?: break
    var line = raw
    if (!checkedMagic) {
      checkedMagic = true
      if (line.isNotEmpty() && line[0] == '﻿') line = line.substring(1)
      if (!line.trimStart().startsWith(PLAYLIST_MAGIC)) {
        val snippet = line.take(120).replace(Regex("\\s+"), " ").trim()
        throw M3uParseError("That is not an M3U playlist: it starts with \"$snippet\" instead of \"#EXTM3U\".")
      }
    }
    line = line.trim()
    if (line.isEmpty()) continue

    if (line[0] == '#') {
      when {
        line.startsWith("#EXTINF:") -> pending = parseExtInf(line, 8)
        line.startsWith("#EXTGRP:") -> extGrp = line.substring(8).trim()
        epgUrl.isEmpty() && line.startsWith(PLAYLIST_MAGIC) ->
          scanAttributes(line, PLAYLIST_MAGIC.length, line.length) { key, value ->
            if (key == "url-tvg" || key == "x-tvg-url" || key == "tvg-url") {
              val first = value.split(',').firstOrNull()?.trim().orEmpty()
              if (epgUrl.isEmpty() && first.isNotEmpty()) epgUrl = first
            }
          }
      }
      continue
    }

    val url = line
    val info = pending ?: ExtInf()
    pending = null

    val name = info.name.ifEmpty { nameFromUrl(url) }
    val group = info.group.ifEmpty { extGrp.ifEmpty { FALLBACK_GROUP } }
    val kind = classifyEntry(name, group, url)

    val streamId = hashUrl(url)
    val id = "${kind.wire}:$streamId"
    if (!taken.add(id)) continue

    val categoryId = "${kind.wire}:$group"
    categories.getOrPut(categoryId) { Category(sourceId, categoryId, kind, m3uCleanTitle(group)) }
    counts[categoryId] = (counts[categoryId] ?: 0) + 1

    var item = MediaItem(
      sourceId = sourceId,
      id = id,
      kind = kind,
      name = name,
      title = m3uCleanTitle(name),
      categoryId = categoryId,
      categoryName = group,
      language = info.language.ifEmpty { null },
      logo = info.logo.ifEmpty { null },
      streamId = streamId,
      url = url,
    )
    if (kind == MediaKind.LIVE) {
      if (info.tvgId.isNotEmpty() && info.tvgId.lowercase() != NO_EPG_CHANNEL_FOLDED) {
        item = item.copy(epgChannelId = info.tvgId)
      }
      if (info.hasArchive) item = item.copy(hasArchive = true, archiveDays = info.archiveDays)
    } else {
      item = item.copy(
        containerExtension = containerExtension(url),
        year = YEAR.find(name)?.groupValues?.get(1)?.toInt(),
      )
    }
    onItem(item.indexed())
    count++
    if (count % 2000 == 0) onProgress?.invoke(count)
  }

  val sorted = categories.values
    .map { it.copy(count = counts[it.id] ?: 0) }
    .sortedWith(compareBy({ it.kind.ordinal }, { it.name.lowercase() }))

  return M3uResult(count, sorted, epgUrl.ifEmpty { null })
}

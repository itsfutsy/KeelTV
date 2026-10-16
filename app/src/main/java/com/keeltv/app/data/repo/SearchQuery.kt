package com.keeltv.app.data.repo

import com.keeltv.app.core.searchText
import com.keeltv.app.data.model.MediaKind

private const val KEY_INDEX = "index_media_item_sourceId_kind_search"
private const val GUIDE_INDEX = "index_media_item_sourceId_guideChannelId"

fun searchWords(query: String): List<String> = searchText(query).split(' ').filter { it.isNotEmpty() }

private fun searchWhere(
  sourceId: String,
  words: List<String>,
  kind: MediaKind,
  language: String,
): Pair<String, List<Any?>> {
  val clauses = mutableListOf("i.sourceId = ?", "i.kind = ?")
  val args = mutableListOf<Any?>(sourceId, kind.wire)
  for (word in words) {
    clauses += "instr(i.search, ?) > 0"
    args += word
  }
  languageFilter(language, clauses, args)
  return clauses.joinToString(" AND ") to args
}

fun searchQuery(sourceId: String, words: List<String>, kind: MediaKind, language: String, limit: Int): BrowseSql {
  val (where, args) = searchWhere(sourceId, words, kind, language)
  val phrase = words.joinToString(" ")
  return BrowseSql(
    "SELECT i.* FROM media_item i INDEXED BY $KEY_INDEX WHERE $where " +
      "ORDER BY CASE WHEN instr(i.search, ?) = 1 THEN 0 WHEN instr(' ' || i.search, ?) > 0 THEN 1 ELSE 2 END, " +
      "i.search LIMIT ?",
    args + phrase + " $phrase" + limit,
  )
}

fun searchCountQuery(sourceId: String, words: List<String>, kind: MediaKind, language: String): BrowseSql {
  val (where, args) = searchWhere(sourceId, words, kind, language)
  return BrowseSql("SELECT COUNT(*) FROM media_item i INDEXED BY $KEY_INDEX WHERE $where", args)
}

/**
 * A programme title as the key holds a name, near enough: lower case and apostrophes dropped.
 * SQLite only lowers ASCII, so an accented title is not folded the way a query is.
 */
private const val PROGRAMME_WORDS = "replace(replace(lower(p.title), '''', ''), '’', '')"

fun guideSearchQuery(
  sourceId: String,
  words: List<String>,
  language: String,
  at: Long,
  to: Long,
  limit: Int,
): BrowseSql {
  val titled = words.joinToString(" AND ") { "instr($PROGRAMME_WORDS, ?) > 0" }
  val named = words.joinToString(" AND ") { "instr(i.search, ?) > 0" }
  val clauses = mutableListOf("i.sourceId = ?", "i.kind = ?", "NOT ($named)")
  val args = mutableListOf<Any?>(sourceId, at, to)
  args.addAll(words)
  args.addAll(listOf(sourceId, MediaKind.LIVE.wire))
  args.addAll(words)
  languageFilter(language, clauses, args)
  return BrowseSql(
    "SELECT i.* FROM (" +
      "SELECT p.channelId, MIN(p.start) AS soonest FROM epg_programme p NOT INDEXED " +
      "WHERE p.sourceId = ? AND p.stop > ? AND p.start < ? AND $titled " +
      "GROUP BY p.channelId" +
      ") g CROSS JOIN media_item i INDEXED BY $GUIDE_INDEX ON i.guideChannelId = g.channelId " +
      "WHERE ${clauses.joinToString(" AND ")} ORDER BY g.soonest, i.sortTitle LIMIT ?",
    args + limit,
  )
}

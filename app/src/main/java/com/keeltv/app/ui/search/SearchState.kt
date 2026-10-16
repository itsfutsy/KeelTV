package com.keeltv.app.ui.search

import com.keeltv.app.core.fold
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.MediaKind

enum class SearchScope(val label: String, val kind: MediaKind?) {
  ALL("All", null),
  LIVE("Live TV", MediaKind.LIVE),
  MOVIES("Movies", MediaKind.MOVIE),
  SHOWS("TV Shows", MediaKind.SERIES),
}

const val MIN_QUERY = 2

const val SEARCH_DEBOUNCE_MS = 220L

const val RECENTS_MAX = 8

fun pushRecent(current: List<String>, query: String): List<String> {
  val q = query.trim()
  if (q.length < MIN_QUERY) return current
  val folded = fold(q)
  return (listOf(q) + current.filter { fold(it) != folded }).take(RECENTS_MAX)
}

fun groupByKind(items: List<MediaItem>): List<Pair<MediaKind, List<MediaItem>>> =
  listOf(MediaKind.LIVE, MediaKind.MOVIE, MediaKind.SERIES)
    .map { kind -> kind to items.filter { it.kind == kind } }
    .filter { it.second.isNotEmpty() }

fun countFor(items: List<MediaItem>, scope: SearchScope): Int =
  if (scope.kind == null) items.size else items.count { it.kind == scope.kind }

fun rowLabel(kind: MediaKind): String = when (kind) {
  MediaKind.LIVE -> "Live TV"
  MediaKind.MOVIE -> "Movies"
  MediaKind.SERIES -> "TV Shows"
}

fun matchLine(shown: Int, total: Int): String = when {
  total > shown -> "%,d of %,d matches".format(shown, total)
  total == 1 -> "1 match"
  else -> "%,d matches".format(total)
}

package com.keeltv.app.ui.library

import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.playback.formatDuration
import com.keeltv.app.ui.components.timeLeft

fun coarseDuration(millis: Long): String {
  val minutes = ((millis.coerceAtLeast(0) + 30_000) / 60_000).toInt()
  val h = minutes / 60
  val m = minutes % 60
  return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun progressThrough(positionMillis: Long, durationMillis: Long): Float =
  if (durationMillis <= 0) 0f else (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f)

fun watchCaption(positionMillis: Long, durationMillis: Long): String {
  if (durationMillis <= 0) return formatDuration(positionMillis)
  return timeLeft((durationMillis - positionMillis).coerceAtLeast(0) / 1000)
}

fun splitFavourites(items: List<MediaItem>): List<Pair<String, List<MediaItem>>> =
  listOf(
    "Channels" to MediaKind.LIVE,
    "Movies" to MediaKind.MOVIE,
    "TV Shows" to MediaKind.SERIES,
  ).map { (label, kind) -> label to items.filter { it.kind == kind } }
    .filter { it.second.isNotEmpty() }

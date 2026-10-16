package com.keeltv.app.ui.detail

import com.keeltv.app.data.model.Episode
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.SeriesDetail
import com.keeltv.app.data.model.WatchProgress

private const val FINISHED = 0.95f

fun formatDuration(totalSeconds: Long): String {
  if (totalSeconds < 0) return "0:00"
  val s = totalSeconds % 60
  val m = (totalSeconds / 60) % 60
  val h = totalSeconds / 3600
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun coarseDuration(seconds: Int): String {
  val total = (seconds + 30) / 60
  val h = total / 60
  val m = total % 60
  return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun episodeDuration(seconds: Int): String {
  val minutes = maxOf(1, (seconds + 30) / 60)
  return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
}

fun seasonLabel(season: Int): String = if (season == 0) "Specials" else "Season $season"

fun episodeCode(episode: Episode): String = "S${episode.season} E${episode.episodeNum}"

fun orderSeasons(seasons: List<Int>): List<Int> =
  seasons.distinct().sortedBy { if (it == 0) Int.MAX_VALUE else it }

private val EPISODE_PREFIX =
  Regex("^(?:.*?\\s[-–]\\s)?S\\d{1,2}\\s?E\\d{1,3}(?:\\s[-–:]\\s*|\\s+)(.+)$", RegexOption.IGNORE_CASE)

fun episodeTitle(episode: Episode): String {
  val raw = episode.title.trim()
  val matched = EPISODE_PREFIX.find(raw)?.groupValues?.get(1)?.trim()
  return matched?.takeIf { it.isNotEmpty() } ?: raw.ifEmpty { "Episode ${episode.episodeNum}" }
}

fun watchedFraction(progress: WatchProgress?): Float {
  if (progress == null || progress.duration <= 0) return 0f
  return (progress.position.toFloat() / progress.duration).coerceIn(0f, 1f)
}

fun isFinished(progress: WatchProgress?): Boolean = watchedFraction(progress) >= FINISHED

fun flatEpisodes(detail: SeriesDetail): List<Episode> =
  orderSeasons(detail.seasons).flatMap { season ->
    detail.episodes[season].orEmpty().sortedBy { it.episodeNum }
  }

data class NextUp(val episode: Episode, val startAtMs: Long = 0)

fun nextUp(detail: SeriesDetail, progress: List<WatchProgress>): NextUp? {
  val flat = flatEpisodes(detail)
  if (flat.isEmpty()) return null
  val byEpisode = progress.filter { it.episodeId.isNotEmpty() }.associateBy { it.episodeId }

  var bestIndex = -1
  var bestAt = -1L
  var best: WatchProgress? = null
  flat.forEachIndexed { index, episode ->
    val row = byEpisode[episode.id]
    if (row != null && row.updatedAt > bestAt) {
      bestAt = row.updatedAt
      bestIndex = index
      best = row
    }
  }

  val latest = best ?: return NextUp(flat[0])
  if (!isFinished(latest)) return NextUp(flat[bestIndex], latest.position)
  if (bestIndex + 1 < flat.size) return NextUp(flat[bestIndex + 1])
  return NextUp(flat[bestIndex])
}

fun enrich(base: MediaItem, extra: MediaItem?): MediaItem {
  if (extra == null) return base
  return base.copy(
    title = base.title,
    logo = extra.logo ?: base.logo,
    backdrop = extra.backdrop ?: base.backdrop,
    rating = extra.rating ?: base.rating,
    year = extra.year ?: base.year,
    plot = extra.plot?.takeIf { it.isNotBlank() } ?: base.plot,
    genre = extra.genre?.takeIf { it.isNotBlank() } ?: base.genre,
    cast = extra.cast?.takeIf { it.isNotBlank() } ?: base.cast,
    director = extra.director?.takeIf { it.isNotBlank() } ?: base.director,
    durationSecs = extra.durationSecs ?: base.durationSecs,
    containerExtension = extra.containerExtension ?: base.containerExtension,
    seasonCount = extra.seasonCount ?: base.seasonCount,
    addedAt = extra.addedAt ?: base.addedAt,
  )
}

/** Providers sometimes ship a sample length, so anything under fifteen minutes is dropped. */
fun runtimeLabel(item: MediaItem): String? {
  val secs = item.durationSecs ?: return null
  return if (secs >= 15 * 60) coarseDuration(secs) else null
}

private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")

fun youtubeId(raw: String?): String? {
  val value = raw?.trim().orEmpty()
  if (value.isEmpty()) return null
  if (YOUTUBE_ID.matches(value)) return value
  val fromUrl = runCatching {
    val uri = java.net.URI(value)
    if (uri.host?.endsWith("youtu.be") == true) {
      uri.path.trimStart('/')
    } else {
      uri.query?.split('&')?.firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
    }
  }.getOrNull()
  return fromUrl?.takeIf { YOUTUBE_ID.matches(it) }
}

fun openTrailer(context: android.content.Context, id: String) {
  val uri = android.net.Uri.parse("https://www.youtube.com/watch?v=$id")
  val packages = listOf("com.google.android.youtube.tv", "com.google.android.youtube", null)
  for (name in packages) {
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
      addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
      if (name != null) setPackage(name)
    }
    if (runCatching { context.startActivity(intent) }.isSuccess) return
  }
}

fun formatAdded(value: Long?): String? {
  if (value == null || value <= 0) return null
  val ms = if (value > 1_000_000_000_000L) value else value * 1000
  return runCatching {
    java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(ms))
  }.getOrNull()
}

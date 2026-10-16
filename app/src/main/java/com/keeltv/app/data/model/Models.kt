package com.keeltv.app.data.model

enum class MediaKind(val wire: String) {
  LIVE("live"), MOVIE("movie"), SERIES("series");

  companion object {
    fun of(wire: String): MediaKind? = entries.firstOrNull { it.wire == wire }
  }
}

enum class SourceKind(val wire: String) { XTREAM("xtream"), M3U("m3u") }

data class Episode(
  val id: String,
  val seriesId: String,
  val season: Int,
  val episodeNum: Int,
  val title: String,
  val plot: String? = null,
  val image: String? = null,
  val durationSecs: Int? = null,
  val containerExtension: String? = null,
  val streamId: Long,
)

data class SeriesDetail(
  val item: MediaItem,
  val seasons: List<Int>,
  val episodes: Map<Int, List<Episode>>,
)

data class NowNext(val now: EpgProgramme? = null, val next: EpgProgramme? = null)

data class EpgFeed(
  val id: String,
  val label: String,
  val url: String,
  val countries: List<String> = emptyList(),
  val single: Boolean = false,
)

enum class SyncPhase { IDLE, CATEGORIES, LIVE, MOVIES, SERIES, EPG, DONE, ERROR }

data class SyncProgress(
  val phase: SyncPhase = SyncPhase.IDLE,
  val message: String = "",
  val progress: Float? = null,
  val error: String? = null,
)

data class SourceStats(
  val liveCategories: Int = 0,
  val movieCategories: Int = 0,
  val seriesCategories: Int = 0,
  val liveItems: Int = 0,
  val movieItems: Int = 0,
  val seriesItems: Int = 0,
  val epgChannels: Int = 0,
  val epgProgrammes: Int = 0,
  val lastSync: Long? = null,
)

data class SourceProbe(val live: Int, val movies: Int, val series: Int, val counts: String)

data class SourceTest(val ok: Boolean, val message: String, val probe: SourceProbe? = null)

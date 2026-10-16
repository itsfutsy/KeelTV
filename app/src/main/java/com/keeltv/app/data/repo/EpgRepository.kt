package com.keeltv.app.data.repo

import com.keeltv.app.data.db.KeelTvDatabase
import com.keeltv.app.data.model.EpgChannel
import com.keeltv.app.data.model.EpgFeed
import com.keeltv.app.data.model.EpgFeedStatus
import com.keeltv.app.data.model.EpgProgramme
import com.keeltv.app.data.model.GuideOverride
import com.keeltv.app.data.model.NowNext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val NOW_NEXT_SECONDS = 6 * 60 * 60L

private const val LONGEST_PROGRAMME_SECONDS = 24 * 60 * 60L

class EpgRepository(
  private val db: KeelTvDatabase,
  private val epgIngest: EpgIngest,
) {
  suspend fun nowNext(sourceId: String, channelId: String, at: Long = nowSeconds()): NowNext =
    withContext(Dispatchers.IO) {
      val rows = db.epg().nowNext(sourceId, channelId, at)
      val now = rows.firstOrNull()?.takeIf { it.start <= at }
      val next = if (now == null) rows.firstOrNull() else rows.getOrNull(1)
      NowNext(now, next)
    }

  fun observeNowNext(sourceId: String, channelId: String, at: Long = nowSeconds()): Flow<NowNext> =
    db.epg().observeNowNext(sourceId, channelId, at).map { rows ->
      val now = rows.firstOrNull()?.takeIf { it.start <= at }
      NowNext(now, if (now == null) rows.firstOrNull() else rows.getOrNull(1))
    }

  suspend fun nowNextFor(
    sourceId: String,
    channelIds: List<String>,
    at: Long = nowSeconds(),
  ): Map<String, NowNext> = withContext(Dispatchers.IO) {
    val ids = channelIds.filter { it.isNotEmpty() }.distinct()
    if (ids.isEmpty()) return@withContext emptyMap()
    db.epg().window(sourceId, ids, at - LONGEST_PROGRAMME_SECONDS, at, at + NOW_NEXT_SECONDS)
      .groupBy { it.channelId }
      .mapValues { (_, rows) -> nowNextOf(rows, at) }
  }

  suspend fun grid(
    sourceId: String,
    channelIds: List<String>,
    from: Long,
    to: Long,
  ): Map<String, List<EpgProgramme>> = withContext(Dispatchers.IO) {
    if (channelIds.isEmpty() || to <= from) return@withContext emptyMap()
    db.epg()
      .window(sourceId, channelIds.distinct(), from - LONGEST_PROGRAMME_SECONDS, from, to)
      .groupBy { it.channelId }
  }

  suspend fun channel(sourceId: String, channelId: String, from: Long, to: Long): List<EpgProgramme> =
    withContext(Dispatchers.IO) { db.epg().channelWindow(sourceId, channelId, from, to) }

  fun feedStatus(sourceId: String): Flow<List<EpgFeedStatus>> = db.epg().observeFeedStatus(sourceId)

  suspend fun feedCatalogue(sourceId: String): Pair<List<EpgFeed>, List<String>> =
    epgIngest.feedCatalogue(sourceId)

  suspend fun searchChannels(sourceId: String, query: String, limit: Int = 40): List<EpgChannel> =
    withContext(Dispatchers.IO) {
      if (query.trim().length < 2) emptyList() else db.epg().searchChannels(sourceId, query.trim(), limit)
    }

  suspend fun setOverride(sourceId: String, itemId: String, channelId: String?) =
    withContext(Dispatchers.IO) {
      if (channelId == null) {
        db.library().clearOverride(sourceId, itemId)
      } else {
        db.library().setOverride(GuideOverride(sourceId, itemId, channelId))
      }
      db.catalog().setGuideChannel(sourceId, itemId, channelId)
    }
}

fun nowSeconds(): Long = System.currentTimeMillis() / 1000

fun nowNextOf(programmes: List<EpgProgramme>, at: Long): NowNext {
  var current: EpgProgramme? = null
  var upcoming: EpgProgramme? = null
  for (p in programmes) {
    if (p.start <= at && p.stop > at) {
      current = p
    } else if (p.start > at && (upcoming == null || p.start < upcoming.start)) {
      upcoming = p
    }
  }
  return NowNext(current, upcoming)
}

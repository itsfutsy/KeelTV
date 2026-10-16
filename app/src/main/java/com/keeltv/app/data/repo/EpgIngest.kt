package com.keeltv.app.data.repo

import com.keeltv.app.data.db.KeelTvDatabase
import com.keeltv.app.data.epg.ArchiveChannel
import com.keeltv.app.data.epg.DEFAULT_ARCHIVE_DAYS
import com.keeltv.app.data.epg.EPG_FEEDS
import com.keeltv.app.data.epg.GuideWindow
import com.keeltv.app.data.epg.WantedChannels
import com.keeltv.app.data.epg.autoFeeds
import com.keeltv.app.data.epg.guideWindow
import com.keeltv.app.data.epg.idCountry
import com.keeltv.app.data.epg.normaliseId
import com.keeltv.app.data.epg.normaliseName
import com.keeltv.app.data.epg.parseXmltv
import com.keeltv.app.data.epg.resolveFeed
import com.keeltv.app.data.model.EpgChannel
import com.keeltv.app.data.model.EpgFeed
import com.keeltv.app.data.model.EpgFeedStatus
import com.keeltv.app.data.model.EpgProgramme
import com.keeltv.app.data.model.Source
import com.keeltv.app.data.net.openStream
import com.keeltv.app.data.net.redactText
import com.keeltv.app.data.prefs.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.InputStreamReader

private const val PROGRAMME_BATCH = 2000

class EpgIngest(
  private val db: KeelTvDatabase,
  private val http: OkHttpClient,
) {
  suspend fun ingest(
    source: Source,
    providerUrl: String?,
    settings: Settings,
    onProgress: (String, Float?) -> Unit,
  ) = withContext(BackgroundWork) {
    val feeds = planFeeds(source, providerUrl, settings)
    if (feeds.isEmpty()) {
      onProgress("No guide to read", 1f)
      return@withContext
    }

    db.epg().clearGuide(source.id)
    db.epg().pruneFeedStatus(source.id, feeds.map { it.id })

    val now = System.currentTimeMillis() / 1000
    val window = guideWindow(now)
    val wanted = wantedChannels(source.id)
    val claimed = HashSet<String>()
    var totalProgrammes = 0
    val deepestArchive = db.catalog().maxArchiveDays(source.id, DEFAULT_ARCHIVE_DAYS)

    feeds.forEachIndexed { rank, feed ->
      onProgress("Checking ${feed.label}", null)
      val status = try {
        readFeed(source.id, feed, rank, claimed, wanted, window, now, onProgress)
      } catch (err: Exception) {
        EpgFeedStatus(
          sourceId = source.id,
          feedId = feed.id,
          label = feed.label,
          url = feed.url,
          feedRank = rank,
          error = redactText(err.message ?: err.toString()),
        )
      }
      db.epg().upsertFeedStatus(status)
      totalProgrammes += status.programmes
    }

    db.epg().pruneOld(source.id, window.oldest(deepestArchive))

    onProgress("Guide ready: ${claimed.size} channels, $totalProgrammes programmes", 1f)
  }

  private fun wantedChannels(sourceId: String): WantedChannels {
    val pins = db.library().overrides(sourceId).associate { it.itemId to it.channelId }
    val providerIds = db.catalog().liveProviderChannels(sourceId).map { it.name } + pins.values
    val archives = db.catalog().liveArchives(sourceId).map { row ->
      ArchiveChannel(
        providerId = pins[row.id] ?: row.epgChannelId,
        name = row.name,
        days = if (row.archiveDays > 0) row.archiveDays else DEFAULT_ARCHIVE_DAYS,
      )
    }
    return WantedChannels(providerIds, db.catalog().liveNames(sourceId).map { it.name }, archives)
  }

  private fun planFeeds(source: Source, providerUrl: String?, settings: Settings): List<EpgFeed> {
    val out = ArrayList<EpgFeed>()
    if (!providerUrl.isNullOrBlank()) {
      out.add(EpgFeed("provider", "${source.name} guide", providerUrl, emptyList(), single = true))
    }
    val auto = if (settings.epgFillAuto) {
      autoFeeds(db.catalog().liveNames(source.id).map { it.name }).map { it.id }
    } else {
      emptyList()
    }
    val refs = LinkedHashSet(auto) + settings.epgFillEnabled
    for (ref in refs) {
      if (ref in settings.epgFillDisabled) continue
      val feed = resolveFeed(ref) ?: continue
      if (out.none { it.url == feed.url }) out.add(feed)
    }
    return out
  }

  private fun readFeed(
    sourceId: String,
    feed: EpgFeed,
    rank: Int,
    claimed: MutableSet<String>,
    wanted: WantedChannels,
    window: GuideWindow,
    now: Long,
    onProgress: (String, Float?) -> Unit,
  ): EpgFeedStatus {
    http.openStream(feed.url).use { download ->
      val channels = LinkedHashMap<String, EpgChannel>()
      val archiveDays = HashMap<String, Int>()
      val programmes = ArrayList<EpgProgramme>(PROGRAMME_BATCH)
      val counts = HashMap<String, Int>()
      var kept = 0
      var lastTick = 0L

      parseXmltv(
        InputStreamReader(download.source.inputStream(), Charsets.UTF_8),
        onChannel = { c ->
          if (wanted.wants(c.id, c.displayName) && claimed.add(c.id)) {
            wanted.archiveDays(c.id, c.displayName).takeIf { it > 0 }?.let { archiveDays[c.id] = it }
            channels[c.id] = EpgChannel(
              sourceId = sourceId,
              channelId = c.id,
              name = c.displayName,
              icon = c.icon,
              feedRank = rank,
              normalisedName = normaliseName(c.displayName),
              normalisedId = normaliseId(c.id),
              country = idCountry(c.id),
            )
          }
        },
        onProgramme = { p ->
          if (p.channelId !in channels) return@parseXmltv
          if (!window.holds(p.start, p.stop, archiveDays[p.channelId] ?: 0)) return@parseXmltv
          programmes.add(p.copy(sourceId = sourceId))
          counts[p.channelId] = (counts[p.channelId] ?: 0) + 1
          kept++
          if (programmes.size >= PROGRAMME_BATCH) {
            db.epg().insertProgrammes(programmes)
            programmes.clear()
          }
        },
        onProgress = { chars ->
          val nowMs = System.currentTimeMillis()
          if (nowMs - lastTick < 250) return@parseXmltv
          lastTick = nowMs
          onProgress("Reading ${feed.label} ${"%.1f".format(chars / 1_000_000.0)} MB - $kept programmes", null)
        },
      )
      if (programmes.isNotEmpty()) db.epg().insertProgrammes(programmes)

      db.epg().insertChannels(channels.values.map { it.copy(programmeCount = counts[it.channelId] ?: 0) })

      return EpgFeedStatus(
        sourceId = sourceId,
        feedId = feed.id,
        label = feed.label,
        url = feed.url,
        feedRank = rank,
        channels = channels.size,
        programmes = kept,
        lastSync = now,
        etag = download.response.header("ETag"),
        lastModified = download.response.header("Last-Modified"),
      )
    }
  }

  suspend fun feedCatalogue(sourceId: String): Pair<List<EpgFeed>, List<String>> =
    withContext(Dispatchers.IO) {
      val auto = autoFeeds(db.catalog().liveNames(sourceId).map { it.name }).map { it.id }
      EPG_FEEDS to auto
    }
}

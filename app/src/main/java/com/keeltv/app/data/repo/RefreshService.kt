package com.keeltv.app.data.repo

import android.os.Process
import com.keeltv.app.core.CATALOG_RULES_VERSION
import com.keeltv.app.core.cleanTitle
import com.keeltv.app.core.channelLanguage
import com.keeltv.app.data.db.KeelTvDatabase
import com.keeltv.app.data.db.restoreCatalogIndexes
import com.keeltv.app.data.db.setCatalogIndexesAside
import com.keeltv.app.data.epg.ChannelMatcher
import com.keeltv.app.data.epg.placeholderGuideIds
import com.keeltv.app.data.m3u.parseM3u
import com.keeltv.app.data.model.MediaCategory
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.data.model.Source
import com.keeltv.app.data.model.SourceKind
import com.keeltv.app.data.model.SyncPhase
import com.keeltv.app.data.model.SyncProgress
import com.keeltv.app.data.net.openStream
import com.keeltv.app.data.net.redactText
import com.keeltv.app.data.xtream.indexed
import com.keeltv.app.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.Executors

private const val BATCH = 1000

private const val BATCHES_AHEAD = 4

private const val GUIDE_LINK_BATCH = 2000

internal val BackgroundWork: CoroutineDispatcher = backgroundThread("keeltv-background")

private val CatalogWrites: CoroutineDispatcher = backgroundThread("keeltv-writes")

private fun backgroundThread(name: String): CoroutineDispatcher =
  Executors.newSingleThreadExecutor { work ->
    Thread({
      Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
      work.run()
    }, name)
  }.asCoroutineDispatcher()

private const val RETRY_SECONDS = 3600L

private const val CHECK_EVERY_MS = 15 * 60 * 1000L

class RefreshService(
  private val db: KeelTvDatabase,
  private val sources: SourceRepository,
  private val settings: SettingsStore,
  private val http: OkHttpClient,
  private val epgIngest: EpgIngest,
  private val scope: CoroutineScope,
) {
  private val lock = Mutex()
  private val _progress = MutableStateFlow(SyncProgress())
  val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

  private fun report(phase: SyncPhase, message: String, value: Float? = null) {
    _progress.value = SyncProgress(phase, message, value)
  }

  suspend fun refresh(sourceId: String) {
    refreshCatalog(sourceId)
    scope.launch { runCatching { refreshEpg(sourceId) } }
  }

  suspend fun refreshCatalog(sourceId: String) = lock.withLock {
    val source = sources.byId(sourceId) ?: return@withLock
    try {
      val discovered = withContext(BackgroundWork) {
        val sql = db.openHelper.writableDatabase
        sql.setCatalogIndexesAside()
        try {
          db.catalog().clearCatalog(sourceId)
          val epgUrl = writingBatches { batches ->
            when (source.kind) {
              SourceKind.XTREAM -> refreshXtream(source, batches)
              SourceKind.M3U -> refreshM3u(source, batches)
            }
          }
          dropPlaceholderGuideIds(sourceId)
          report(_progress.value.phase, "Finishing up…", 1f)
          epgUrl
        } finally {
          sql.restoreCatalogIndexes()
        }
      }
      playlistEpgUrl = discovered
      settings.setCatalogRulesVersion(CATALOG_RULES_VERSION)
      report(SyncPhase.DONE, "Catalogue ready", 1f)
    } catch (err: Exception) {
      _progress.value = SyncProgress(
        SyncPhase.ERROR,
        "Could not read the catalogue.",
        null,
        redactText(err.message ?: err.toString()),
      )
      throw err
    }
  }

  private suspend fun <T> writingBatches(read: (SendChannel<CatalogBatch>) -> T): T = coroutineScope {
    val batches = Channel<CatalogBatch>(BATCHES_AHEAD)
    launch(CatalogWrites) {
      batches.consumeEach { db.catalog().writeBatch(it.items, it.memberships) }
    }
    try {
      read(batches)
    } finally {
      batches.close()
    }
  }

  fun restoreInterruptedLoad() {
    scope.launch {
      runCatching {
        lock.withLock { withContext(BackgroundWork) { db.openHelper.writableDatabase.restoreCatalogIndexes() } }
      }
    }
  }

  fun rederiveIfRulesChanged() {
    scope.launch {
      if (settings.catalogRulesVersion() >= CATALOG_RULES_VERSION) return@launch
      runCatching {
        lock.withLock {
          withContext(BackgroundWork) {
            var afterSource = ""
            var afterId = ""
            while (true) {
              val page = db.catalog().pageAfter(afterSource, afterId, BATCH)
              if (page.isEmpty()) break
              db.runInTransaction {
                for (item in page) {
                  val fresh = item.copy(title = cleanTitle(item.name)).indexed()
                  val tags = languageTags(fresh)
                  if (fresh.title == item.title && fresh.sortTitle == item.sortTitle &&
                    fresh.search == item.search && tags == item.languageTags
                  ) {
                    continue
                  }
                  db.catalog().setDerived(
                    item.sourceId, item.id, fresh.title, fresh.sortTitle, fresh.search, tags,
                  )
                  if (fresh.sortTitle != item.sortTitle) {
                    db.catalog().setMembershipSort(item.sourceId, item.id, fresh.sortTitle)
                  }
                }
              }
              afterSource = page.last().sourceId
              afterId = page.last().id
            }
            for (source in db.sources().all()) {
              dropPlaceholderGuideIds(source.id)
              resolveGuideChannels(source.id)
            }
          }
          settings.setCatalogRulesVersion(CATALOG_RULES_VERSION)
        }
      }
    }
  }

  suspend fun refreshEpg(sourceId: String) = lock.withLock {
    val source = sources.byId(sourceId) ?: return@withLock
    try {
      epgIngest.ingest(source, resolveEpgUrl(source, playlistEpgUrl), settings.settings.first()) { message, value ->
        report(SyncPhase.EPG, message, value)
      }
      withContext(BackgroundWork) { resolveGuideChannels(sourceId) }
      report(SyncPhase.DONE, "Guide ready", 1f)
    } catch (err: Exception) {
      _progress.value = SyncProgress(SyncPhase.ERROR, "Could not read the guide.", null, redactText(err.message.orEmpty()))
      throw err
    }
  }

  fun refreshEpgIfStale(sourceId: String) {
    val phase = _progress.value.phase
    if (phase != SyncPhase.IDLE && phase != SyncPhase.DONE && phase != SyncPhase.ERROR) return
    // A guide read opens a provider connection while the player holds one, and many providers
    // allow only one. Whatever is on screen wins; the next tick will find the guide still stale.
    if (playbackHolds > 0) return
    val now = System.currentTimeMillis() / 1000
    if (now - lastEpgAttempt < RETRY_SECONDS) return
    lastEpgAttempt = now
    scope.launch {
      val hours = settings.settings.first().epgAutoRefreshHours
      if (hours <= 0) return@launch
      val last = withContext(Dispatchers.IO) {
        db.epg().feedStatus(sourceId).mapNotNull { it.lastSync }.maxOrNull()
      }
      if (last != null && now - last < hours * 3600L) return@launch
      runCatching { refreshEpg(sourceId) }
    }
  }

  fun startGuideScheduler() {
    if (scheduler != null) return
    scheduler = scope.launch {
      while (true) {
        settings.settings.first().activeSourceId?.let { refreshEpgIfStale(it) }
        delay(CHECK_EVERY_MS)
      }
    }
  }

  fun holdForPlayback() {
    playbackHolds += 1
  }

  fun releasePlayback() {
    playbackHolds = (playbackHolds - 1).coerceAtLeast(0)
  }

  @Volatile
  private var playbackHolds = 0

  private var scheduler: Job? = null

  private var lastEpgAttempt = 0L

  private var playlistEpgUrl: String? = null

  private fun resolveEpgUrl(source: Source, discovered: String? = null): String? = when {
    !source.epgUrl.isNullOrBlank() -> source.epgUrl
    discovered != null -> discovered
    source.kind == SourceKind.XTREAM -> sources.clientFor(source)?.xmltvUrl()
    else -> null
  }

  private fun refreshXtream(source: Source, batches: SendChannel<CatalogBatch>): String? {
    val client = sources.clientFor(source) ?: return null
    report(SyncPhase.CATEGORIES, "Reading categories…")

    val names = HashMap<String, String>()
    var categoryCount = 0
    for (kind in MediaKind.entries) {
      val categories = client.categories(source.id, kind)
      db.catalog().insertCategories(categories)
      for (c in categories) names["${kind.wire}:${c.id}"] = c.name
      categoryCount += categories.size
    }
    report(SyncPhase.CATEGORIES, "$categoryCount categories indexed", 1f)

    for (kind in MediaKind.entries) {
      val phase = kind.syncPhase()
      val label = kind.label()
      report(phase, "Reading $label…")
      val writer = CatalogWriter(batches, source.id, names)
      client.allItems(
        source.id,
        kind,
        onBytes = { bytes ->
          report(phase, "Reading $label… ${"%.1f".format(bytes / 1048576.0)} MB")
        },
        onItem = writer::add,
      )
      writer.flush()
      report(phase, "${writer.items} $label indexed", 1f)
    }
    return null
  }

  private fun refreshM3u(source: Source, batches: SendChannel<CatalogBatch>): String? {
    report(SyncPhase.LIVE, "Downloading playlist…")
    http.openStream(source.url).use { download ->
      val writer = CatalogWriter(batches, source.id, emptyMap())
      val result = parseM3u(
        download.source,
        source.id,
        onProgress = { count -> report(SyncPhase.LIVE, "Reading playlist… $count entries") },
        onItem = writer::add,
      )
      writer.flush()
      db.catalog().insertCategories(result.categories)
      report(SyncPhase.LIVE, "${result.items} entries indexed", 1f)
      return result.epgUrl
    }
  }

  private fun dropPlaceholderGuideIds(sourceId: String) {
    val names = db.catalog().liveNames(sourceId).associate { it.id to it.name }
    val links = db.catalog().liveProviderChannels(sourceId).mapNotNull { row ->
      names[row.id]?.let { it to row.name }
    }
    val placeholders = placeholderGuideIds(links)
    if (placeholders.isNotEmpty()) db.catalog().dropProviderGuideIds(sourceId, placeholders.toList())
  }

  private fun resolveGuideChannels(sourceId: String) {
    val channels = db.epg().matcherChannels(sourceId)
    if (channels.isEmpty()) return
    val matcher = ChannelMatcher(channels)
    val pinned = db.library().overrides(sourceId).associate { it.itemId to it.channelId }
    val providerIds = db.catalog().liveProviderChannels(sourceId).associate { it.id to it.name }

    val moved = ArrayList<Pair<String, String?>>()
    for (item in db.catalog().liveGuideLinks(sourceId)) {
      val pin = pinned[item.id]
      val provider = providerIds[item.id]
      val resolved = when {
        pin != null && matcher.has(pin) -> pin
        provider != null && matcher.has(provider) -> provider
        else -> matcher.resolve(item.name)
      }
      if (resolved != item.guideChannelId) moved += item.id to resolved
    }
    for (batch in moved.chunked(GUIDE_LINK_BATCH)) {
      db.runInTransaction {
        for ((itemId, channelId) in batch) db.catalog().setGuideChannel(sourceId, itemId, channelId)
      }
    }
  }
}

private fun MediaKind.syncPhase(): SyncPhase = when (this) {
  MediaKind.LIVE -> SyncPhase.LIVE
  MediaKind.MOVIE -> SyncPhase.MOVIES
  MediaKind.SERIES -> SyncPhase.SERIES
}

private fun MediaKind.label(): String = when (this) {
  MediaKind.LIVE -> "channels"
  MediaKind.MOVIE -> "movies"
  MediaKind.SERIES -> "series"
}

private class CatalogBatch(val items: List<MediaItem>, val memberships: List<MediaCategory>)

/**
 * Buffers rows into batches for the writer and keeps the first row it sees for an item id.
 *
 * Xtream lists the same stream in several categories, so later rows only contribute a category
 * membership.
 */
private class CatalogWriter(
  private val batches: SendChannel<CatalogBatch>,
  private val sourceId: String,
  private val categoryNames: Map<String, String>,
) {
  private val seen = HashSet<String>()
  private var seenRows = 0
  private var itemBuffer = ArrayList<MediaItem>(BATCH)
  private var membershipBuffer = ArrayList<MediaCategory>(BATCH)
  var items = 0
    private set

  fun add(raw: MediaItem) {
    val categoryName = raw.categoryName ?: categoryNames["${raw.kind.wire}:${raw.categoryId}"]
    val withCategory = if (categoryName == raw.categoryName) raw else raw.copy(categoryName = categoryName)
    val ordinal = seenRows++
    membershipBuffer.add(
      MediaCategory(sourceId, raw.id, raw.kind, raw.categoryId, raw.sortTitle, ordinal),
    )
    if (seen.add(raw.id)) {
      itemBuffer.add(withCategory.copy(languageTags = languageTags(withCategory), ordinal = ordinal))
      items++
    }
    if (itemBuffer.size >= BATCH || membershipBuffer.size >= BATCH) flush()
  }

  fun flush() {
    if (itemBuffer.isEmpty() && membershipBuffer.isEmpty()) return
    batches.trySendBlocking(CatalogBatch(itemBuffer, membershipBuffer)).getOrThrow()
    itemBuffer = ArrayList(BATCH)
    membershipBuffer = ArrayList(BATCH)
  }
}

private fun languageTags(item: MediaItem): String? {
  val languages = channelLanguage(item)?.languages.orEmpty()
  if (languages.isEmpty()) return null
  return languages.joinToString("|", prefix = "|", postfix = "|")
}

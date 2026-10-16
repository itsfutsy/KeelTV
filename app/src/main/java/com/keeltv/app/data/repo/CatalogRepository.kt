package com.keeltv.app.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.InvalidationTracker
import com.keeltv.app.data.db.KeelTvDatabase
import com.keeltv.app.data.epg.DEFAULT_ARCHIVE_DAYS
import com.keeltv.app.data.model.Category
import com.keeltv.app.data.model.Episode
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.data.model.SeriesDetail
import com.keeltv.app.data.model.Source
import com.keeltv.app.data.model.SourceKind
import com.keeltv.app.data.model.SourceStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

const val SEARCH_CAP = 300

private const val MIN_SEARCH = 2

private const val GUIDE_LOOKAHEAD_SECONDS = 24 * 60 * 60L

data class SearchAnswer(val items: List<MediaItem>, val totals: Map<MediaKind, Int>) {
  fun narrowTo(kind: MediaKind): SearchAnswer =
    SearchAnswer(items.filter { it.kind == kind }, totals.filterKeys { it == kind })

  companion object {
    val NONE = SearchAnswer(emptyList(), emptyMap())
  }
}

private const val RECALLED = 8

private val PAGING = PagingConfig(pageSize = 60, prefetchDistance = 30, initialLoadSize = 120)

private val CHANNEL_PAGING = PagingConfig(
  pageSize = 256,
  prefetchDistance = 32,
  initialLoadSize = 256,
  enablePlaceholders = false,
)

private const val FACET_SAMPLE = 3000

private const val RECENT_CHANNEL_LIMIT = 20

object ChannelScope {
  const val ALL = "all"
  const val FAVOURITES = "favourites"
  const val RECENT = "recent"
  const val CATEGORY = "cat:"
}

class CatalogRepository(
  private val db: KeelTvDatabase,
  private val sources: SourceRepository,
) {
  fun categories(sourceId: String, kind: MediaKind, language: String = ""): Flow<List<Category>> =
    db.catalog().observeCategories(sourceId, kind, language)

  fun itemCount(sourceId: String, kind: MediaKind, language: String = ""): Flow<Int> {
    val key = CountKey(sourceId, kind, language)
    return db.catalog().observeItemCount(sourceId, kind, language)
      .onEach { count ->
        if (!watching) withContext(Dispatchers.IO) { watchCatalog() }
        synchronized(counts) { counts[key] = count }
      }
      .onStart { synchronized(counts) { counts[key] }?.let { emit(it) } }
  }

  fun pageKind(sourceId: String, kind: MediaKind, language: String = ""): Flow<PagingData<MediaItem>> =
    Pager(PAGING) { db.catalog().pageKind(sourceId, kind, language) }.flow

  fun pageBrowse(scope: BrowseScope): Flow<PagingData<MediaItem>> =
    Pager(PAGING) { db.catalog().pageBrowse(browseQuery(scope).asRoomQuery()) }.flow

  fun pageChannels(
    sourceId: String,
    language: String,
    collapse: Boolean,
    from: Int = 0,
  ): Flow<PagingData<FoldedChannel>> = Pager(CHANNEL_PAGING, initialKey = from) {
    foldedSource(collapse) { limit, offset -> db.catalog().liveChannels(sourceId, language, limit, offset) }
  }.flow

  fun pageCategoryChannels(
    sourceId: String,
    categoryId: String,
    language: String,
    collapse: Boolean,
    from: Int = 0,
  ): Flow<PagingData<FoldedChannel>> = Pager(CHANNEL_PAGING, initialKey = from) {
    foldedSource(collapse) { limit, offset ->
      db.catalog().liveInCategory(sourceId, categoryId, language, limit, offset)
    }
  }.flow

  private fun foldedSource(
    collapse: Boolean,
    window: suspend (limit: Int, offset: Int) -> List<MediaItem>,
  ): ChannelPagingSource {
    val source = ChannelPagingSource(collapse) { limit, offset ->
      withContext(Dispatchers.IO) {
        watchCatalog()
        window(limit, offset)
      }
    }
    synchronized(folded) { folded += source }
    source.registerInvalidatedCallback { synchronized(folded) { folded -= source } }
    return source
  }

  private val folded = HashSet<ChannelPagingSource>()

  suspend fun browseCount(scope: BrowseScope): Int = withContext(Dispatchers.IO) {
    db.catalog().browseCount(browseCountQuery(scope).asRoomQuery())
  }

  suspend fun facetOptions(scope: BrowseScope, limit: Int = FACET_SAMPLE): FacetOptions =
    withContext(Dispatchers.IO) {
      deriveFacets(db.catalog().facetSample(facetSampleQuery(scope, limit).asRoomQuery()))
    }

  suspend fun search(
    sourceId: String,
    query: String,
    kind: MediaKind? = null,
    language: String = "",
  ): SearchAnswer {
    val words = searchWords(query)
    if (words.joinToString(" ").length < MIN_SEARCH) return SearchAnswer.NONE
    return withContext(Dispatchers.IO) {
      watchCatalog()
      val catalog = db.catalog()
      val kinds = kind?.let { listOf(it) } ?: MediaKind.entries
      val totals = mutableMapOf<MediaKind, Int>()
      val items = kinds.flatMap { section ->
        val named = catalog.search(searchQuery(sourceId, words, section, language, SEARCH_CAP).asRoomQuery())
        if (named.size >= SEARCH_CAP) {
          totals[section] = catalog.searchCount(searchCountQuery(sourceId, words, section, language).asRoomQuery())
          return@flatMap named
        }
        val found = if (section == MediaKind.LIVE) {
          val at = nowSeconds()
          val guided = guideSearchQuery(
            sourceId, words, language,
            at = at,
            to = at + GUIDE_LOOKAHEAD_SECONDS,
            limit = SEARCH_CAP - named.size,
          )
          named + catalog.search(guided.asRoomQuery())
        } else {
          named
        }
        totals[section] = found.size
        found
      }
      SearchAnswer(items, totals).also { answer ->
        val key = AnswerKey(sourceId, words, kind, language)
        synchronized(recent) {
          recent.remove(key)
          recent[key] = answer
        }
      }
    }
  }

  fun recall(sourceId: String, query: String, kind: MediaKind?, language: String?): SearchAnswer? {
    val words = searchWords(query)
    fun held(section: MediaKind?): SearchAnswer? = recent.entries.lastOrNull { (key, _) ->
      key.sourceId == sourceId && key.words == words && key.kind == section &&
        (language == null || key.language == language)
    }?.value
    return synchronized(recent) {
      held(kind) ?: kind?.let { held(null)?.narrowTo(it) }
    }
  }

  private data class AnswerKey(
    val sourceId: String,
    val words: List<String>,
    val kind: MediaKind?,
    val language: String,
  )

  private val recent = object : LinkedHashMap<AnswerKey, SearchAnswer>() {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<AnswerKey, SearchAnswer>) = size > RECALLED
  }

  private data class CountKey(val sourceId: String, val kind: MediaKind, val language: String)

  private val counts = HashMap<CountKey, Int>()

  private val archives = HashMap<String, Int>()

  private val onCatalogWrite = object : InvalidationTracker.Observer(arrayOf("media_item")) {
    override fun onInvalidated(tables: Set<String>) {
      synchronized(recent) { recent.clear() }
      synchronized(counts) { counts.clear() }
      synchronized(archives) { archives.clear() }
      val stale = synchronized(folded) { folded.toList() }
      for (source in stale) source.invalidate()
    }
  }

  @Volatile private var watching = false

  /** Registers [onCatalogWrite] once, off the main thread since Room syncs its triggers to do it. */
  private fun watchCatalog() {
    if (watching) return
    db.invalidationTracker.addObserver(onCatalogWrite)
    watching = true
  }

  suspend fun item(sourceId: String, itemId: String): MediaItem? = db.catalog().item(sourceId, itemId)

  suspend fun channelWindow(
    sourceId: String,
    item: MediaItem,
    scope: String = "",
    language: String = "",
  ): ChannelWindow = withContext(Dispatchers.IO) {
    val scoped = when {
      scope == ChannelScope.FAVOURITES ->
        listWindow("Favourites", db.library().favouriteChannels(sourceId), item)
      scope == ChannelScope.RECENT ->
        listWindow("Recent", db.library().recentChannelList(sourceId, RECENT_CHANNEL_LIMIT), item)
      scope.startsWith(ChannelScope.CATEGORY) ->
        categoryWindow(sourceId, scope.removePrefix(ChannelScope.CATEGORY), item, language)
      scope == ChannelScope.ALL -> allWindow(sourceId, item, language)
      else -> null
    }
    if (scoped != null) return@withContext scoped

    val filed = if (item.categoryId.isBlank()) {
      null
    } else {
      categoryWindow(sourceId, item.categoryId, item, language)
    }
    filed
      ?: allWindow(sourceId, item, language)
      ?: allWindow(sourceId, item, "")
      ?: ChannelWindow("All channels", 1, 0) { _, _ -> listOf(item) }
  }

  private suspend fun categoryWindow(
    sourceId: String,
    categoryId: String,
    item: MediaItem,
    language: String,
  ): ChannelWindow? {
    val catalog = db.catalog()
    val tie = catalog.liveTieInCategory(sourceId, categoryId, language, item.sortTitle)
    val at = tie.indexOf(item.id)
    if (at < 0) return null
    val before = catalog.liveBeforeInCategory(sourceId, categoryId, language, item.sortTitle)
    val name = catalog.category(sourceId, MediaKind.LIVE, categoryId)?.name
      ?: item.categoryName
      ?: "All channels"
    return ChannelWindow(name, catalog.countLiveInCategory(sourceId, categoryId, language), before + at) { offset, limit ->
      catalog.liveInCategory(sourceId, categoryId, language, limit, offset)
    }
  }

  private suspend fun allWindow(sourceId: String, item: MediaItem, language: String): ChannelWindow? {
    val catalog = db.catalog()
    val tie = catalog.liveTie(sourceId, language, item.sortTitle)
    val at = tie.indexOf(item.id)
    if (at < 0) return null
    val before = catalog.liveBefore(sourceId, language, item.sortTitle)
    return ChannelWindow("All channels", catalog.countLive(sourceId, language), before + at) { offset, limit ->
      catalog.liveChannels(sourceId, language, limit, offset)
    }
  }

  private fun listWindow(name: String, rows: List<MediaItem>, item: MediaItem): ChannelWindow? {
    val at = rows.indexOfFirst { it.id == item.id }
    if (at < 0) return null
    return ChannelWindow(name, rows.size, at) { offset, limit -> rows.drop(offset).take(limit) }
  }

  suspend fun archiveDays(sourceId: String): Int {
    synchronized(archives) { archives[sourceId] }?.let { return it }
    return withContext(Dispatchers.IO) {
      watchCatalog()
      db.catalog().maxArchiveDays(sourceId, DEFAULT_ARCHIVE_DAYS)
        .also { synchronized(archives) { archives[sourceId] = it } }
    }
  }

  fun observeItem(sourceId: String, itemId: String): Flow<MediaItem?> =
    db.catalog().observeItem(sourceId, itemId)

  suspend fun seriesDetail(sourceId: String, itemId: String): SeriesDetail? = withContext(Dispatchers.IO) {
    val source = sources.byId(sourceId) ?: return@withContext null
    val client = sources.clientFor(source) ?: return@withContext null
    val seriesId = itemId.substringAfter(':')
    client.seriesDetail(sourceId, seriesId)
  }

  suspend fun movieDetail(sourceId: String, itemId: String): MediaItem? = withContext(Dispatchers.IO) {
    val source = sources.byId(sourceId) ?: return@withContext null
    val client = sources.clientFor(source) ?: return@withContext null
    runCatching { client.vodDetail(sourceId, itemId.substringAfter(':')) }.getOrNull()
  }

  suspend fun catchupUrl(
    source: Source,
    item: MediaItem,
    startSeconds: Long,
    stopSeconds: Long,
  ): String? = withContext(Dispatchers.IO) {
    if (source.kind != SourceKind.XTREAM || !item.hasArchive) return@withContext null
    val client = sources.clientFor(source) ?: return@withContext null
    val minutes = ((stopSeconds - startSeconds) / 60).toInt()
    client.timeshiftUrl(item.streamId, startSeconds, minutes)
  }

  suspend fun streamUrl(source: Source, item: MediaItem, episode: Episode? = null): String? {
    if (source.kind == SourceKind.M3U) return item.url
    val client = sources.clientFor(source) ?: return null
    return if (episode != null) {
      client.streamUrl(MediaKind.SERIES, episode.streamId, episode.containerExtension)
    } else {
      client.streamUrl(item.kind, item.streamId, item.containerExtension)
    }
  }

  suspend fun stats(sourceId: String): SourceStats = withContext(Dispatchers.IO) {
    watchCatalog()
    val catalog = db.catalog()
    SourceStats(
      liveCategories = catalog.countCategories(sourceId, MediaKind.LIVE),
      movieCategories = catalog.countCategories(sourceId, MediaKind.MOVIE),
      seriesCategories = catalog.countCategories(sourceId, MediaKind.SERIES),
      liveItems = countItems(sourceId, MediaKind.LIVE),
      movieItems = countItems(sourceId, MediaKind.MOVIE),
      seriesItems = countItems(sourceId, MediaKind.SERIES),
      epgChannels = db.epg().countChannels(sourceId),
      epgProgrammes = db.epg().countProgrammes(sourceId),
      lastSync = db.epg().feedStatus(sourceId).mapNotNull { it.lastSync }.maxOrNull(),
    )
  }

  private suspend fun countItems(sourceId: String, kind: MediaKind): Int {
    val key = CountKey(sourceId, kind, "")
    synchronized(counts) { counts[key] }?.let { return it }
    return db.catalog().countItems(sourceId, kind).also { synchronized(counts) { counts[key] = it } }
  }
}

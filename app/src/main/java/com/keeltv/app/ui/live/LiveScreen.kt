package com.keeltv.app.ui.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.foundation.layout.size
import androidx.tv.material3.Icon
import com.keeltv.app.core.LANGUAGE_OPTIONS
import com.keeltv.app.core.channelLanguage
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.data.model.NowNext
import com.keeltv.app.data.model.SourceKind
import com.keeltv.app.data.model.SyncPhase
import com.keeltv.app.data.repo.BrowseScope
import com.keeltv.app.data.repo.ChannelScope
import com.keeltv.app.data.repo.FOLD_BLOCK
import com.keeltv.app.data.repo.FoldedChannel
import com.keeltv.app.data.repo.SortKey
import com.keeltv.app.data.repo.foldChannels
import com.keeltv.app.data.repo.nowNextOf
import com.keeltv.app.ui.LocalActiveSource
import com.keeltv.app.ui.LocalContainer
import com.keeltv.app.ui.components.AmbientArt
import com.keeltv.app.ui.components.CategoryColumn
import com.keeltv.app.ui.components.ChannelRowSkeleton
import com.keeltv.app.ui.components.NoSource
import com.keeltv.app.ui.components.fadeFoot
import com.keeltv.app.ui.components.PivotScroll
import com.keeltv.app.ui.components.SearchField
import com.keeltv.app.ui.components.SegButton
import com.keeltv.app.ui.components.Segmented
import com.keeltv.app.ui.components.pivotTo
import com.keeltv.app.ui.components.rememberCategoryRows
import com.keeltv.app.ui.components.TvButton
import com.keeltv.app.ui.misc.EmptyState
import com.keeltv.app.ui.movies.LanguageSheet
import com.keeltv.app.ui.movies.RefineChip
import com.keeltv.app.ui.movies.languageName
import com.keeltv.app.ui.search.MIN_QUERY
import com.keeltv.app.ui.search.matchLine
import com.keeltv.app.ui.search.rememberSearch
import com.keeltv.app.ui.search.SEARCH_DEBOUNCE_MS
import com.keeltv.app.ui.theme.KeelTvTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val SCOPE_ALL = ChannelScope.ALL
private const val SCOPE_FAVOURITES = ChannelScope.FAVOURITES
private const val SCOPE_RECENT = ChannelScope.RECENT
private const val SCOPE_CATEGORY = ChannelScope.CATEGORY

private val SEARCH_WIDTH = 220.dp

private const val CATEGORY_SETTLE_MS = 260L

private const val LIST_AHEAD = 2 * 3600L

private const val SKELETON_ROWS = 12

@Composable
fun LiveScreen(onOpen: (itemId: String, scope: String, startAtMs: Long) -> Unit) {
  val container = LocalContainer.current
  val source = LocalActiveSource.current
  if (source == null) {
    NoSource("Live TV", "Add a source to see your channels.")
    return
  }

  val scope = rememberCoroutineScope()
  val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
  val language = settings?.languageFilter.orEmpty()
  val collapse = settings?.collapseDuplicates ?: true
  val sync by container.refresh.progress.collectAsStateWithLifecycle()

  // Every flow read here is held across recompositions. Room hands out a new flow per call and
  // collectAsStateWithLifecycle starts over on a new flow, so an unheld count was cancelled and
  // run again, over the whole lineup, on every page that landed and every tick of a guide read.
  val categories by remember(source.id, language) { container.catalog.categories(source.id, MediaKind.LIVE, language) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val shown by remember(source.id, language) { container.catalog.itemCount(source.id, MediaKind.LIVE, language) }
    .collectAsStateWithLifecycle(initialValue = 0)
  val favourites by remember(source.id) { container.library.favourites(source.id) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val recents by remember(source.id) { container.library.recentChannels(source.id) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  val liveFavourites = remember(favourites) { favourites.filter { it.kind == MediaKind.LIVE } }
  val favouriteIds = remember(favourites) { favourites.mapTo(HashSet()) { it.id } }

  var selected by rememberSaveable { mutableStateOf(SCOPE_ALL) }
  var guide by rememberSaveable { mutableStateOf(false) }
  var draft by rememberSaveable { mutableStateOf("") }
  var query by rememberSaveable { mutableStateOf("") }
  var menuFor by remember { mutableStateOf<MediaItem?>(null) }
  var languageOpen by remember { mutableStateOf(false) }
  val onHeading = remember(source.id) { MutableStateFlow(selected) }
  LaunchedEffect(onHeading) {
    onHeading.collectLatest { key ->
      delay(CATEGORY_SETTLE_MS)
      selected = key
    }
  }
  val rowFocus = remember { FocusRequester() }
  var awayFromGuide by remember { mutableStateOf(false) }
  LaunchedEffect(guide) {
    if (guide) {
      awayFromGuide = true
    } else if (awayFromGuide) {
      awayFromGuide = false
      runCatching { rowFocus.requestFocus() }
    }
  }

  LaunchedEffect(source.id) { container.refresh.refreshEpgIfStale(source.id) }

  var archiveDays by remember(source.id) { mutableStateOf(0) }
  LaunchedEffect(source.id, sync.phase) {
    archiveDays = runCatching { container.catalog.archiveDays(source.id) }.getOrDefault(0)
  }
  val catchUp = source.kind == SourceKind.XTREAM

  LaunchedEffect(draft) {
    delay(SEARCH_DEBOUNCE_MS)
    query = draft.trim()
  }
  val searching = query.length >= MIN_QUERY
  val answer = rememberSearch(source.id, query, MediaKind.LIVE, settings?.languageFilter)
  val searchPending = searching && answer == null
  val results = answer?.items.orEmpty()
  val matches = answer?.totals?.get(MediaKind.LIVE) ?: results.size
  val searchFocus = remember { FocusRequester() }
  BackHandler(enabled = draft.isNotEmpty()) {
    draft = ""
    query = ""
    runCatching { searchFocus.requestFocus() }
  }

  val listKey = if (searching) "q:$query" else selected
  val playScope = if (searching) "" else selected

  val opensAt = rememberSaveable(source.id, listKey) { intArrayOf(0, 0) }

  val channels: Flow<PagingData<FoldedChannel>> = when {
    searchPending -> remember { flowOf(pending()) }
    searching -> remember(results, collapse) { flowOf(loaded(foldChannels(results, 0, collapse))) }
    selected == SCOPE_FAVOURITES -> remember(liveFavourites, collapse) {
      flowOf(loaded(foldChannels(liveFavourites, 0, collapse)))
    }
    selected == SCOPE_RECENT -> remember(recents, collapse) {
      flowOf(loaded(foldChannels(recents, 0, collapse)))
    }
    selected.startsWith(SCOPE_CATEGORY) -> remember(source.id, selected, language, collapse) {
      container.catalog.pageCategoryChannels(
        sourceId = source.id,
        categoryId = selected.removePrefix(SCOPE_CATEGORY),
        language = language,
        collapse = collapse,
        from = opensAt[0],
      )
    }
    else -> remember(source.id, language, collapse) {
      container.catalog.pageChannels(source.id, language, collapse, opensAt[0])
    }
  }
  val items = channels.collectAsLazyPagingItems()

  val unfolded: Flow<PagingData<MediaItem>> = when {
    searchPending -> remember { flowOf(pending()) }
    searching -> remember(results) { flowOf(loaded(results)) }
    selected == SCOPE_FAVOURITES -> remember(liveFavourites) { flowOf(loaded(liveFavourites)) }
    selected == SCOPE_RECENT -> remember(recents) { flowOf(loaded(recents)) }
    selected.startsWith(SCOPE_CATEGORY) -> remember(source.id, selected, language) {
      container.catalog.pageBrowse(
        BrowseScope(
          sourceId = source.id,
          kind = MediaKind.LIVE,
          categoryId = selected.removePrefix(SCOPE_CATEGORY),
          language = language,
          sort = SortKey.NAME,
        ),
      )
    }
    else -> remember(source.id, language) { container.catalog.pageKind(source.id, MediaKind.LIVE, language) }
  }

  val play: (MediaItem) -> Unit = { item ->
    scope.launch { container.library.touchChannel(source.id, item) }
    onOpen(item.id, playScope, 0L)
  }

  val catchUpFrom: (MediaItem, Long) -> Unit = { item, startSeconds ->
    scope.launch { container.library.touchChannel(source.id, item) }
    onOpen(item.id, playScope, startSeconds * 1000)
  }

  val ambient = remember(source.id) { mutableStateOf<String?>(null) }
  LaunchedEffect(query, selected) { ambient.value = null }

  val rows = rememberCategoryRows(
    allKey = SCOPE_ALL,
    allLabel = "All channels",
    categories = categories,
    categoryKey = { SCOPE_CATEGORY + it },
  )
  LaunchedEffect(rows) {
    if (rows.size > 1 && selected.startsWith(SCOPE_CATEGORY) && rows.none { it.key == selected }) {
      selected = SCOPE_ALL
      onHeading.value = SCOPE_ALL
    }
  }

  val empty: @Composable () -> Unit = {
    val showAll = { selected = SCOPE_ALL; onHeading.value = SCOPE_ALL }
    val what = language.takeIf { it in LANGUAGE_OPTIONS }?.let { "$it channels" } ?: "matching channels"
    val group = rows.firstOrNull { it.key == selected && !it.whole }?.label
    when {
      searching -> EmptyState(
        title = "No channel or programme under that name",
        body = "Try a shorter part of the name.",
      )
      selected == SCOPE_FAVOURITES -> EmptyState(
        title = "No favourite channels yet",
        body = "Press and hold OK on any channel, then choose Add to favourites.",
        action = { TvButton("Show all channels", showAll) },
      )
      selected == SCOPE_RECENT -> EmptyState(
        title = "No recent channels yet",
        body = "Channels you play land here.",
        action = { TvButton("Show all channels", showAll) },
      )
      language.isNotEmpty() -> EmptyState(
        title = if (group != null) "No $what in $group" else "No $what",
        body = if (group != null) {
          "Its channels are in other languages. Show every language, or pick another group."
        } else {
          "This source's channels are in other languages."
        },
        action = {
          TvButton("Show every language", { scope.launch { container.settings.setLanguageFilter("") } })
        },
      )
      else -> EmptyState(
        title = "The provider left this group empty",
        body = "Nothing is broken. Walk the column for another group, or search by name.",
      )
    }
  }

  Box(Modifier.fillMaxSize()) {
    AmbientArt(url = { ambient.value }, strength = 0.46f)
    Column(Modifier.fillMaxSize().padding(KeelTvTheme.dimens.screenPadding)) {
      LiveBar(
        draft = draft,
        onDraft = { draft = it },
        anchor = searchFocus,
        placeholder = "Search channels",
        selected = selected,
        onSelect = { selected = it; onHeading.value = it },
        favourites = liveFavourites.size,
        recents = recents.size,
        guide = guide,
        language = language,
        updating = sync.phase == SyncPhase.EPG && !searching,
        note = when {
          searchPending -> null
          searching -> matchLine(results.size, matches)
          else -> null
        },
        onRefine = { languageOpen = true },
        onMode = { guide = it },
      )
      Spacer(Modifier.height(KeelTvTheme.space.s2))

      if (guide) {
        GuideGrid(
          sourceId = source.id,
          scopeKey = listKey,
          items = unfolded.collectAsLazyPagingItems(),
          archiveDays = if (catchUp) archiveDays else 0,
          empty = empty,
          onPlay = play,
          onCatchUp = catchUpFrom,
          onList = { guide = false },
          modifier = Modifier.weight(1f),
        )
      } else {
        Row(Modifier.weight(1f)) {
          CategoryColumn(
            rows = rows,
            selected = selected,
            total = shown,
            muted = searching,
            onFocus = { onHeading.value = it },
            onSelect = { draft = ""; query = ""; onHeading.value = it; selected = it },
            modifier = Modifier.width(KeelTvTheme.dimens.sideColumn).fillMaxHeight(),
          )
          Spacer(Modifier.width(KeelTvTheme.space.s4))
          ChannelList(
            sourceId = source.id,
            scopeKey = listKey,
            items = items,
            favouriteIds = favouriteIds,
            opensAt = opensAt,
            anchor = rowFocus,
            menuOpen = menuFor != null,
            empty = empty,
            onPlay = play,
            onMenu = { menuFor = it },
            onAmbient = { ambient.value = it },
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .fadeFoot(to = KeelTvTheme.colors.bgApp),
          )
        }
      }
    }

    if (languageOpen) {
      LanguageSheet(
        selected = language,
        onPick = { next ->
          languageOpen = false
          scope.launch { container.settings.setLanguageFilter(next) }
        },
        onDismiss = { languageOpen = false },
      )
    }

    val open = menuFor
    if (open != null) {
      ChannelMenu(
        sourceId = source.id,
        item = open,
        favourite = open.id in favouriteIds,
        onPlay = { menuFor = null; play(open) },
        onDismiss = { menuFor = null },
      )
    }
  }
}

@Composable
private fun LiveBar(
  draft: String,
  onDraft: (String) -> Unit,
  anchor: FocusRequester,
  placeholder: String,
  selected: String,
  onSelect: (String) -> Unit,
  favourites: Int,
  recents: Int,
  guide: Boolean,
  language: String,
  updating: Boolean,
  note: String?,
  onRefine: () -> Unit,
  onMode: (Boolean) -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
    modifier = Modifier.fillMaxWidth().focusRestorer().focusGroup(),
  ) {
    SearchField(
      value = draft,
      onValueChange = onDraft,
      placeholder = placeholder,
      modifier = Modifier.width(SEARCH_WIDTH),
      anchor = anchor,
    )
    val library = selected == SCOPE_FAVOURITES || selected == SCOPE_RECENT
    Segmented {
      SegButton("All", !library) { if (library) onSelect(SCOPE_ALL) }
      SegButton("Favourites", selected == SCOPE_FAVOURITES, favourites) { onSelect(SCOPE_FAVOURITES) }
      SegButton("Recent", selected == SCOPE_RECENT, recents) { onSelect(SCOPE_RECENT) }
    }
    Row(
      modifier = Modifier.weight(1f).padding(horizontal = KeelTvTheme.space.s1),
      horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2, Alignment.End),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (updating) {
        Icon(Icons.Outlined.Sync, contentDescription = null, tint = KeelTvTheme.colors.textTertiary, modifier = Modifier.size(15.dp))
      }
      Text(
        text = if (updating) "Updating guide" else note.orEmpty(),
        style = KeelTvTheme.type.small,
        color = KeelTvTheme.colors.textTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    RefineChip(listOfNotNull(language.takeIf { it.isNotEmpty() }?.let(::languageName)), onRefine)
    Segmented {
      SegButton("List", !guide) { onMode(false) }
      SegButton("Guide", guide) { onMode(true) }
    }
  }
}

/**
 * A list already in hand, as a pager that says it has finished loading. Without the load states a
 * static list reads as still loading for good, so an empty Favourites would never say it is empty.
 */
private fun <T : Any> loaded(list: List<T>): PagingData<T> = PagingData.from(
  list,
  LoadStates(LoadState.NotLoading(false), LoadState.NotLoading(true), LoadState.NotLoading(true)),
)

private fun <T : Any> pending(): PagingData<T> = PagingData.from(
  emptyList(),
  LoadStates(LoadState.Loading, LoadState.NotLoading(false), LoadState.NotLoading(false)),
)

@Composable
private fun ChannelList(
  sourceId: String,
  scopeKey: String,
  items: LazyPagingItems<FoldedChannel>,
  favouriteIds: Set<String>,
  opensAt: IntArray,
  anchor: FocusRequester,
  menuOpen: Boolean,
  empty: @Composable () -> Unit,
  onPlay: (MediaItem) -> Unit,
  onMenu: (MediaItem) -> Unit,
  onAmbient: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val now = rememberNowSeconds()
  val rows = rememberGuideRows(sourceId)
  val state = remember(scopeKey) { LazyListState() }

  val visited = rememberSaveable(scopeKey) { mutableStateOf(false) }
  val restored = remember(scopeKey) { mutableStateOf(false) }

  val mark = remember(scopeKey) { intArrayOf(-1) }
  val handBack = remember(scopeKey) { mutableStateOf(-1) }
  val asking = remember(scopeKey) { booleanArrayOf(false) }
  val rowAnchor = remember(scopeKey) { FocusRequester() }
  LaunchedEffect(menuOpen) {
    if (menuOpen) {
      handBack.value = mark[0]
    } else if (handBack.value >= 0) {
      runCatching { rowAnchor.requestFocus() }
      handBack.value = -1
    }
  }
  LaunchedEffect(handBack.value) {
    if (!asking[0] || handBack.value < 0) return@LaunchedEffect
    asking[0] = false
    runCatching { rowAnchor.requestFocus() }
    handBack.value = -1
  }

  LaunchedEffect(state, items) {
    snapshotFlow { state.firstVisibleItemIndex }.collect { index ->
      if (index >= items.itemCount) return@collect
      val row = items.peek(index) ?: return@collect
      val at = row.number - 1
      opensAt[0] = at - at % FOLD_BLOCK
    }
  }

  LaunchedEffect(scopeKey, items.itemCount) {
    if (restored.value || items.itemCount == 0 || !visited.value) return@LaunchedEffect
    restored.value = true
    val want = opensAt[1]
    val index = if (want <= 0) -1 else rowHolding(items, want)
    if (index < 0) {
      runCatching { anchor.requestFocus() }
    } else {
      asking[0] = true
      handBack.value = index
    }
  }

  if (items.itemCount == 0) {
    val loading = items.loadState.refresh is LoadState.Loading
    Column(modifier.clipToBounds()) {
      if (loading) repeat(SKELETON_ROWS) { ChannelRowSkeleton(Modifier.padding(bottom = 2.dp)) } else empty()
    }
    return
  }

  val picked = remember(scopeKey) { mutableStateMapOf<Int, Int>() }

  PivotScroll {
    LazyColumn(
      state = state,
      modifier = modifier.focusRequester(anchor).focusRestorer().focusGroup(),
    ) {
      items(
        count = items.itemCount,
        key = items.itemKey { it.item.id },
      ) { index ->
        val row = items[index] ?: return@items
        val at = (picked[row.number] ?: 0).coerceIn(0, row.feeds.size - 1)
        val shown = row.feeds[at]
        val channelId = shown.guideChannelId ?: shown.epgChannelId
        LaunchedEffect(channelId) { rows.want(channelId, now, now + LIST_AHEAD) }
        val slot = remember(channelId, rows) {
          if (channelId == null) null else rows.slot(channelId)
        }
        val listing = remember(slot, now) {
          { if (slot == null) NowNext() else slot.value?.let { p -> nowNextOf(p, now) } }
        }
        ChannelRow(
          item = shown,
          number = row.number + at,
          nowNext = listing,
          now = now,
          guided = channelId != null,
          favourite = shown.id in favouriteIds,
          language = remember(shown.id) { channelLanguage(shown)?.label },
          variants = row.labels,
          variantAt = at,
          onCycleVariant = if (row.feeds.size > 1) {
            { picked[row.number] = (at + 1) % row.feeds.size }
          } else {
            null
          },
          onPlay = { onPlay(shown) },
          onMenu = { onMenu(shown) },
          modifier = Modifier
            .padding(bottom = 2.dp)
            .then(if (index == handBack.value) Modifier.focusRequester(rowAnchor) else Modifier)
            .onFocusChanged {
              if (it.isFocused) {
                visited.value = true
                mark[0] = index
                opensAt[1] = row.number + at
                state.pivotTo(index)
                onAmbient(shown.logo)
              }
            },
        )
      }
    }
  }
}

private fun rowHolding(items: LazyPagingItems<FoldedChannel>, number: Int): Int {
  for (index in 0 until items.itemCount) {
    val row = items.peek(index) ?: continue
    if (number < row.number) return -1
    if (number < row.number + row.feeds.size) return index
  }
  return -1
}

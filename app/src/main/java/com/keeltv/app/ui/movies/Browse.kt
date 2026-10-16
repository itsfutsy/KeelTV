package com.keeltv.app.ui.movies

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.Text
import com.keeltv.app.core.LANGUAGE_OPTIONS
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.data.repo.BrowseScope
import com.keeltv.app.data.repo.FacetOptions
import com.keeltv.app.data.repo.Facets
import com.keeltv.app.data.repo.SortKey
import com.keeltv.app.data.repo.facetOptionsOf
import com.keeltv.app.data.repo.refine
import com.keeltv.app.ui.LocalActiveSource
import com.keeltv.app.ui.LocalContainer
import com.keeltv.app.ui.components.AmbientArt
import com.keeltv.app.ui.components.ButtonTone
import com.keeltv.app.ui.components.CategoryColumn
import com.keeltv.app.ui.components.MediaCard
import com.keeltv.app.ui.components.MediaCardSkeleton
import com.keeltv.app.ui.components.formatRating
import com.keeltv.app.ui.components.metaParts
import com.keeltv.app.ui.components.NoSource
import com.keeltv.app.ui.components.PivotScroll
import com.keeltv.app.ui.components.SearchField
import com.keeltv.app.ui.components.SegButton
import com.keeltv.app.ui.components.Segmented
import com.keeltv.app.ui.components.TvButton
import com.keeltv.app.ui.components.rowTo
import com.keeltv.app.ui.components.fadeFoot
import com.keeltv.app.ui.components.FACT_GAP
import com.keeltv.app.ui.detail.runtimeLabel
import com.keeltv.app.core.humanGenre
import com.keeltv.app.core.splitGenres
import androidx.compose.ui.text.font.FontWeight
import com.keeltv.app.ui.components.rememberCategoryRows
import com.keeltv.app.ui.components.shownTitle
import com.keeltv.app.ui.detail.enrich
import com.keeltv.app.ui.misc.EmptyState
import com.keeltv.app.ui.search.MIN_QUERY
import com.keeltv.app.ui.search.matchLine
import com.keeltv.app.ui.search.rememberSearch
import com.keeltv.app.ui.search.SEARCH_DEBOUNCE_MS
import com.keeltv.app.ui.theme.KeelTvTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val ALL = "all"
private const val FAVOURITES = "favourites"
private const val CATEGORY = "cat:"

private const val CATEGORY_SETTLE_MS = 260L

@Composable
fun BrowseScreen(kind: MediaKind, onOpen: (itemId: String) -> Unit) {
  val container = LocalContainer.current
  val scope = rememberCoroutineScope()
  val source = LocalActiveSource.current
    ?: return NoSource(title(kind), "Add a source to see ${noun(kind)}.")

  val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
  val language = settings?.languageFilter ?: ""

  val categories by remember(source.id, kind, language) { container.catalog.categories(source.id, kind, language) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val shown by remember(source.id, kind, language) { container.catalog.itemCount(source.id, kind, language) }
    .collectAsStateWithLifecycle(initialValue = null)
  val total = if (language.isEmpty()) {
    shown
  } else {
    remember(source.id, kind) { container.catalog.itemCount(source.id, kind) }
      .collectAsStateWithLifecycle(initialValue = null).value
  }
  val favourites by remember(source.id) { container.library.favourites(source.id) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val watching by remember(source.id) { container.library.continueWatching(source.id) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  val favouritesHere = remember(favourites, kind) { favourites.filter { it.kind == kind } }
  val favouriteIds = remember(favourites) { favourites.map { it.id }.toSet() }
  val progressById = remember(watching) {
    watching.sortedBy { it.updatedAt }.associate { row ->
      row.itemId to if (row.duration > 0) (row.position.toFloat() / row.duration).coerceIn(0f, 1f) else 0f
    }
  }

  var shelfKey by rememberSaveable(source.id, kind) { mutableStateOf(ALL) }
  val sort = SortKey.of(settings?.sort(kind))
  var facetWire by rememberSaveable(source.id, kind) { mutableStateOf("") }
  val facets = remember(facetWire) { Facets.of(facetWire) }
  var draft by rememberSaveable(source.id, kind) { mutableStateOf("") }
  var query by rememberSaveable(source.id, kind) { mutableStateOf("") }
  var refining by remember { mutableStateOf(false) }
  var columnAway by rememberSaveable(source.id, kind) { mutableStateOf(false) }
  val gridFocus = remember { FocusRequester() }
  val headingFocus = remember { FocusRequester() }
  val gridHasFocus = remember { mutableStateOf(false) }
  val categoryId = shelfKey.takeIf { it.startsWith(CATEGORY) }?.removePrefix(CATEGORY)

  val onHeading = remember(source.id, kind) { MutableStateFlow(shelfKey) }
  LaunchedEffect(onHeading) {
    onHeading.collectLatest { key ->
      delay(CATEGORY_SETTLE_MS)
      shelfKey = key
    }
  }

  val request by BrowseRequest.pending.collectAsStateWithLifecycle()
  LaunchedEffect(request) {
    val (wantedKind, genre) = request ?: return@LaunchedEffect
    if (wantedKind != kind) return@LaunchedEffect
    BrowseRequest.take(wantedKind to genre)
    shelfKey = ALL
    onHeading.value = ALL
    facetWire = Facets(genres = listOf(genre)).wire()
    container.settings.setBrowseSort(kind, SortKey.RATING.wire)
  }

  LaunchedEffect(draft) {
    delay(SEARCH_DEBOUNCE_MS)
    query = draft.trim()
  }
  val searching = query.length >= MIN_QUERY
  val answer = rememberSearch(source.id, query, kind, settings?.languageFilter)
  val searchPending = searching && answer == null
  val results = answer?.items.orEmpty()
  val matches = answer?.totals?.get(kind) ?: results.size

  suspend fun enterGrid(): Boolean {
    repeat(25) {
      runCatching { gridFocus.requestFocus() }
      if (gridHasFocus.value) return true
      delay(60)
    }
    return false
  }

  // Back puts the column back on the heading it was left on. Declared before the search handler
  // so that one, composed later, wins while a query is typed.
  var backToHeading by remember { mutableStateOf(false) }
  BackHandler(enabled = columnAway) {
    columnAway = false
    backToHeading = true
  }
  LaunchedEffect(columnAway) {
    if (columnAway || !backToHeading) return@LaunchedEffect
    backToHeading = false
    repeat(25) {
      if (runCatching { headingFocus.requestFocus() }.isSuccess) return@LaunchedEffect
      delay(60)
    }
  }

  val searchFocus = remember { FocusRequester() }
  BackHandler(enabled = draft.isNotEmpty()) {
    draft = ""
    query = ""
    runCatching { searchFocus.requestFocus() }
  }

  val paged: LazyPagingItems<MediaItem>? = when {
    searching || shelfKey == FAVOURITES -> null
    else -> {
      val queryScope = remember(source.id, kind, categoryId, language, facets, sort) {
        browseScope(source.id, kind, categoryId, language, facets, sort)
      }
      remember(queryScope) { container.catalog.pageBrowse(queryScope) }.collectAsLazyPagingItems()
    }
  }

  val held: List<MediaItem>? = when {
    searching -> results
    shelfKey == FAVOURITES -> favouritesHere
    else -> null
  }
  val plain = remember(held, facets, sort) { held?.let { refine(it, facets, sort) } }

  var options by remember(source.id, kind, categoryId, language, held) { mutableStateOf(FacetOptions()) }
  LaunchedEffect(refining, options.sampled) {
    if (!refining || options.sampled) return@LaunchedEffect
    options = if (held != null) {
      facetOptionsOf(held)
    } else {
      runCatching {
        container.catalog.facetOptions(browseScope(source.id, kind, categoryId, language, Facets(), sort))
      }.getOrDefault(FacetOptions(sampled = true))
    }
  }

  val filtered = facets.active > 0 || language.isNotEmpty()
  var counts by remember { mutableStateOf(Counts()) }
  LaunchedEffect(source.id, kind, categoryId, language, facets, held == null, total) {
    counts = Counts()
    if (held != null) return@LaunchedEffect
    val whole = if (categoryId == null) {
      total
    } else {
      runCatching {
        container.catalog.browseCount(browseScope(source.id, kind, categoryId, "", Facets(), sort))
      }.getOrNull()
    }
    val here = when {
      !filtered -> whole
      categoryId == null && facets.genres.isNotEmpty() -> null
      else -> runCatching {
        container.catalog.browseCount(browseScope(source.id, kind, categoryId, language, facets, sort))
      }.getOrNull()
    }
    counts = Counts(shown = here, total = whole)
  }

  val ambient = remember(kind) { mutableStateOf<String?>(null) }
  val spotlight = remember(kind) { mutableStateOf<MediaItem?>(null) }
  LaunchedEffect(query, shelfKey) {
    spotlight.value = null
    ambient.value = null
  }
  val details = remember(source.id) { mutableStateMapOf<String, MediaItem>() }
  LaunchedEffect(source.id) {
    snapshotFlow { spotlight.value }.collectLatest { item ->
      if (item == null || item.kind != MediaKind.MOVIE || item.id in details) return@collectLatest
      if (!item.plot.isNullOrBlank() && !item.genre.isNullOrBlank()) return@collectLatest
      delay(DETAIL_SETTLE_MS)
      runCatching { container.catalog.movieDetail(source.id, item.id) }.getOrNull()
        ?.let { details[item.id] = it }
    }
  }

  val rows = rememberCategoryRows(
    allKey = ALL,
    allLabel = "All ${noun(kind)}",
    categories = categories,
    categoryKey = { CATEGORY + it },
  )
  LaunchedEffect(rows) {
    if (rows.size > 1 && shelfKey.startsWith(CATEGORY) && rows.none { it.key == shelfKey }) {
      shelfKey = ALL
      onHeading.value = ALL
    }
  }
  val heading = if (columnAway) {
    rows.firstOrNull { it.key == shelfKey }?.let { listOfNotNull(it.chip, it.label).joinToString(" ") }
  } else {
    null
  }

  Box(
    Modifier
      .fillMaxSize()
      .focusProperties {
        onEnter = {
          if (requestedFocusDirection == FocusDirection.Right) {
            runCatching { (if (columnAway) gridFocus else headingFocus).requestFocus() }
          }
        }
      }
      .focusGroup(),
  ) {
    AmbientArt(url = { ambient.value })
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(KeelTvTheme.dimens.screenPadding),
    ) {
      BrowseBar(
        draft = draft,
        onDraft = { draft = it },
        anchor = searchFocus,
        placeholder = shown?.let { "Search ${"%,d".format(it)} ${noun(kind)}" } ?: "Search ${noun(kind)}",
        onFavourites = shelfKey == FAVOURITES,
        onShelf = { shelfKey = it; onHeading.value = it },
        favourites = favouritesHere.size,
        picks = refinePicks(language, facets, sort),
        onRefine = { refining = true },
      )
      Spacer(Modifier.height(KeelTvTheme.space.s2))
      Row(Modifier.weight(1f)) {
        if (!columnAway) {
          CategoryColumn(
            rows = rows,
            selected = shelfKey,
            total = shown ?: 0,
            muted = searching,
            headingFocus = headingFocus,
            right = gridFocus,
            onFocus = { onHeading.value = it },
            onSelect = { key ->
              draft = ""
              query = ""
              onHeading.value = key
              shelfKey = key
              scope.launch { if (enterGrid()) columnAway = true }
            },
            modifier = Modifier.width(KeelTvTheme.dimens.sideColumn).fillMaxHeight(),
          )
          Spacer(Modifier.width(KeelTvTheme.space.s4))
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
          Spotlight(
            item = { spotlight.value?.let { enrich(it, details[it.id]) } },
            note = when {
              searching && !searchPending && results.isNotEmpty() -> matchLine(plain.orEmpty().size, matches)
              searching -> null
              shelfKey == FAVOURITES -> if (facets.active > 0) countLine(plain?.size, favouritesHere.size) else null
              filtered -> countLine(counts.shown, counts.total)
              else -> null
            },
            heading = heading,
          )
          PosterGrid(
            paged = paged,
            plain = plain,
            favouriteIds = favouriteIds,
            progressById = progressById,
            shelfKey = if (searching) "q:$query" else shelfKey,
            group = rows.firstOrNull { it.key == shelfKey && !it.whole }?.label,
            language = language,
            searching = searching,
            searchPending = searchPending,
            filtered = facets.active > 0,
            narrowedFrom = held?.size ?: 0,
            columns = if (columnAway) KeelTvTheme.dimens.gridColumnsWide else KeelTvTheme.dimens.gridColumns,
            onOpen = onOpen,
            onClearFilters = { facetWire = "" },
            onRefine = { refining = true },
            onFocus = { item ->
              ambient.value = item.backdrop?.takeIf { it.isNotEmpty() } ?: item.logo
              spotlight.value = item
            },
            modifier = Modifier
              .focusRequester(gridFocus)
              .onFocusChanged { gridHasFocus.value = it.hasFocus }
              .focusProperties {
                onExit = {
                  if (requestedFocusDirection == FocusDirection.Left && !columnAway) {
                    runCatching { headingFocus.requestFocus() }
                  }
                }
              }
              .focusRestorer(),
          )
        }
      }
    }
  }

  if (refining) {
    BrowseRefine(
      summary = when {
        held == null -> counts.takeIf { it.shown != null || it.total != null }?.let { countLine(it.shown, it.total) }
        searching -> if (searchPending) null else matchLine(plain.orEmpty().size, matches)
        else -> countLine(plain?.size, held.size)
      },
      sort = sort,
      onSort = { next -> scope.launch { container.settings.setBrowseSort(kind, next.wire) } },
      language = language,
      onLanguage = { next -> scope.launch { container.settings.setLanguageFilter(next) } },
      options = options,
      facets = facets,
      onFacets = { facetWire = it.wire() },
      onDismiss = { refining = false },
    )
  }
}

private fun browseScope(
  sourceId: String,
  kind: MediaKind,
  categoryId: String?,
  language: String,
  facets: Facets,
  sort: SortKey,
): BrowseScope = BrowseScope(
  sourceId = sourceId,
  kind = kind,
  categoryId = categoryId,
  language = language,
  facets = facets,
  sort = sort,
)

private fun title(kind: MediaKind) = if (kind == MediaKind.SERIES) "TV Shows" else "Movies"

private fun noun(kind: MediaKind) = if (kind == MediaKind.SERIES) "series" else "movies"

private data class Counts(val shown: Int? = null, val total: Int? = null)

private fun countLine(shown: Int?, total: Int?): String {
  val titles = { n: Int -> if (n == 1) "1 title" else "${"%,d".format(n)} titles" }
  return when {
    shown != null && total != null && total > shown -> "${"%,d".format(shown)} of ${titles(total)}"
    shown != null -> titles(shown)
    total != null -> "Filtered from ${titles(total)}"
    else -> "Filtered"
  }
}

@Composable
private fun BrowseBar(
  draft: String,
  onDraft: (String) -> Unit,
  anchor: FocusRequester,
  placeholder: String,
  onFavourites: Boolean,
  onShelf: (String) -> Unit,
  favourites: Int,
  picks: List<String>,
  onRefine: () -> Unit,
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
      modifier = Modifier.weight(1f),
      anchor = anchor,
    )
    Segmented {
      SegButton("All", !onFavourites) { if (onFavourites) onShelf(ALL) }
      SegButton("Favourites", onFavourites, favourites) { onShelf(FAVOURITES) }
    }
    RefineChip(picks, onRefine)
  }
}

private const val DETAIL_SETTLE_MS = 450L

private val SPOTLIGHT_HEIGHT = 44.dp

@Composable
private fun Spotlight(item: () -> MediaItem?, note: String?, heading: String?) {
  val colors = KeelTvTheme.colors
  val shown = item()
  Column(
    modifier = Modifier.fillMaxWidth().height(SPOTLIGHT_HEIGHT),
    verticalArrangement = Arrangement.Center,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      when {
        shown != null -> Text(
          text = shownTitle(shown),
          style = KeelTvTheme.type.heading,
          color = colors.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        note != null -> Text(
          text = note,
          style = KeelTvTheme.type.small,
          color = colors.textSecondary,
          maxLines = 1,
          modifier = Modifier.weight(1f),
        )
        else -> Spacer(Modifier.weight(1f))
      }
      if (heading != null) {
        Text(
          text = heading,
          style = KeelTvTheme.type.small,
          color = colors.textTertiary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(start = KeelTvTheme.space.s4),
        )
      }
    }
    if (shown == null) return@Column
    val facts = buildList {
      addAll(metaParts(shown))
      runtimeLabel(shown)?.let { add(it) }
      formatRating(shown.rating)?.let { add("★ $it") }
      shown.genre?.takeIf { it.isNotBlank() }?.let { genre ->
        add(splitGenres(genre).map(::humanGenre).distinct().take(3).joinToString(", "))
      }
      shown.plot?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    if (facts.isNotEmpty()) {
      Text(
        text = facts.joinToString(FACT_GAP),
        style = KeelTvTheme.type.micro.copy(fontWeight = FontWeight.Normal),
        color = colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun PosterGrid(
  paged: LazyPagingItems<MediaItem>?,
  plain: List<MediaItem>?,
  favouriteIds: Set<String>,
  progressById: Map<String, Float>,
  shelfKey: String,
  group: String?,
  language: String,
  searching: Boolean,
  searchPending: Boolean,
  filtered: Boolean,
  narrowedFrom: Int,
  columns: Int,
  onOpen: (String) -> Unit,
  onClearFilters: () -> Unit,
  onRefine: () -> Unit,
  onFocus: (MediaItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  val gridState = rememberLazyGridState()
  val count = plain?.size ?: paged?.itemCount ?: 0
  // Placeholders stand in for a first page that has not arrived, never for tiles that have. A
  // write to the catalogue invalidates the pager, and one that walks the table in batches does so
  // once a batch; swapping the tiles for placeholders on each of those throws away the one the
  // remote is standing on, and the screen flickers and goes dead until the writes stop. A search
  // is the exception: the results on screen answer the last query, not this one.
  val loading = searchPending ||
    (plain == null && count == 0 && paged?.loadState?.refresh is LoadState.Loading)

  val focusedIndex = rememberSaveable(shelfKey) { intArrayOf(-1) }
  val restoreTo = remember(shelfKey) { focusedIndex[0].takeIf { it >= 0 } }
  val restorer = remember(shelfKey) { FocusRequester() }
  var restored by remember(shelfKey) { mutableStateOf(restoreTo == null) }

  LaunchedEffect(shelfKey, count) {
    if (restored || restoreTo == null || count <= restoreTo) return@LaunchedEffect
    gridState.scrollToItem(restoreTo)
    repeat(10) {
      if (runCatching { restorer.requestFocus() }.isSuccess) {
        restored = true
        return@LaunchedEffect
      }
      delay(60)
    }
    restored = true
  }

  if (count == 0 && !loading) {
    Empty(
      shelfKey = shelfKey,
      group = group,
      language = language,
      searching = searching,
      filtered = filtered,
      narrowedFrom = narrowedFrom,
      onClearFilters = onClearFilters,
      onRefine = onRefine,
    )
    return
  }

  val settled by animateFloatAsState(
    targetValue = if (loading) 0.55f else 1f,
    animationSpec = tween(260),
    label = "gridEntry",
  )

  PivotScroll {
    LazyVerticalGrid(
      columns = GridCells.Fixed(columns),
      state = gridState,
      modifier = modifier
        .fillMaxSize()
        .fadeFoot(64.dp, KeelTvTheme.colors.bgApp)
        .graphicsLayer { alpha = settled },
      contentPadding = PaddingValues(bottom = 22.dp, top = 12.dp, start = 2.dp, end = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
      verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s4),
    ) {
      if (loading) {
        items(SKELETONS, key = { "skeleton$it" }) { MediaCardSkeleton(Modifier.fillMaxWidth()) }
        return@LazyVerticalGrid
      }
      items(
        count = count,
        key = { index -> plain?.getOrNull(index)?.id ?: paged?.peek(index)?.id ?: "slot$index" },
      ) { index ->
        val item = plain?.getOrNull(index) ?: paged?.get(index)
        if (item == null) {
          MediaCardSkeleton(Modifier.fillMaxWidth())
        } else {
          MediaCard(
            item = item,
            onClick = { onOpen(item.id) },
            progress = progressById[item.id] ?: 0f,
            favourite = item.id in favouriteIds,
            badge = language.isEmpty(),
            modifier = Modifier
              .fillMaxWidth()
              .onFocusChanged {
                if (it.isFocused) {
                  gridState.rowTo(index, columns)
                  focusedIndex[0] = index
                  onFocus(item)
                }
              }
              .then(if (index == restoreTo && !restored) Modifier.focusRequester(restorer) else Modifier),
          )
        }
      }
    }
  }
}

private const val SKELETONS = 21

@Composable
private fun Empty(
  shelfKey: String,
  group: String?,
  language: String,
  searching: Boolean,
  filtered: Boolean,
  narrowedFrom: Int,
  onClearFilters: () -> Unit,
  onRefine: () -> Unit,
) {
  Column(Modifier.fillMaxSize().padding(top = KeelTvTheme.space.s6)) {
    when {
      narrowedFrom > 0 -> EmptyState(
        title = "Nothing matches those filters",
        body = "None of these titles carries the genre, year or rating asked for. Loosen the " +
          "year range, or drop a genre.",
        action = { TvButton("Clear filters", onClearFilters, tone = ButtonTone.Ghost) },
      )
      searching -> EmptyState(
        title = "No title under that name",
        body = "Providers name things strangely. A film can arrive as \"TITLE 2019 MULTI-SUB 4K\". " +
          "Try a shorter fragment without the year.",
      )
      shelfKey == FAVOURITES -> EmptyState(
        title = "Nothing is in your favourites yet",
        body = "Star a title on its own page and it lands here.",
      )
      filtered -> EmptyState(
        title = "Nothing matches those filters",
        body = "Providers do not tag every section with a genre or a year. Loosen the year " +
          "range, or drop a genre.",
        action = { TvButton("Clear filters", onClearFilters, tone = ButtonTone.Ghost) },
      )
      language.isNotEmpty() -> EmptyState(
        title = language.takeIf { it in LANGUAGE_OPTIONS }
          ?.let { if (group != null) "No $it titles in $group" else "No $it titles" }
          ?: "Nothing here matches that language",
        body = "Its titles are in other languages. Choose another language, or another group.",
        action = { TvButton("Change language", onRefine, tone = ButtonTone.Ghost) },
      )
      shelfKey.startsWith(CATEGORY) -> EmptyState(
        title = "The provider left this group empty",
        body = "Nothing is broken. Walk the column for another group, or search by name.",
      )
      else -> EmptyState(
        title = "This section came back empty",
        body = "Nothing is broken. Refresh the source in Settings.",
      )
    }
  }
}

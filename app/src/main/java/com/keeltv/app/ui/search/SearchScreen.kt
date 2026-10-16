package com.keeltv.app.ui.search

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keeltv.app.core.LANGUAGE_FILTER_CHOICES
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.data.model.SourceStats
import com.keeltv.app.ui.AppIntent
import com.keeltv.app.ui.AppIntents
import com.keeltv.app.ui.LocalActiveSource
import com.keeltv.app.ui.LocalContainer
import com.keeltv.app.ui.components.MediaCard
import com.keeltv.app.ui.components.MediaCardSkeleton
import com.keeltv.app.ui.components.ScreenHeader
import com.keeltv.app.ui.components.fadeEnd
import com.keeltv.app.ui.components.SearchField
import com.keeltv.app.ui.components.SkeletonBox
import com.keeltv.app.ui.misc.ChannelTile
import com.keeltv.app.ui.misc.EmptyState
import com.keeltv.app.ui.misc.RowLabel
import com.keeltv.app.ui.misc.TvChip
import com.keeltv.app.ui.theme.KeelTvTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val NOW_NEXT_CAP = 24

@Composable
fun SearchScreen(onOpen: (itemId: String) -> Unit, onPlay: (itemId: String) -> Unit) {
  val container = LocalContainer.current
  val source = LocalActiveSource.current
  val scope = rememberCoroutineScope()
  val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)

  var draft by rememberSaveable { mutableStateOf("") }
  var query by rememberSaveable { mutableStateOf("") }
  var kindScope by rememberSaveable { mutableStateOf(SearchScope.ALL) }
  var languageOpen by remember { mutableStateOf(false) }
  var nowNext by remember { mutableStateOf<Map<String, Pair<String?, String?>>>(emptyMap()) }
  var stats by remember { mutableStateOf<SourceStats?>(null) }

  val language = settings?.languageFilter.orEmpty()
  val recents = settings?.recentSearches.orEmpty()

  LaunchedEffect(source?.id) {
    stats = source?.let { runCatching { container.catalog.stats(it.id) }.getOrNull() }
  }

  LaunchedEffect(draft) {
    delay(SEARCH_DEBOUNCE_MS)
    query = draft.trim()
  }

  val spoken by AppIntents.pending.collectAsStateWithLifecycle()
  LaunchedEffect(spoken) {
    val request = spoken as? AppIntent.Search ?: return@LaunchedEffect
    AppIntents.consume(request)
    draft = request.query
    query = request.query
  }

  val answer = rememberSearch(source?.id, query, kindScope.kind, settings?.languageFilter)
  val searching = query.length >= MIN_QUERY && answer == null
  val results = answer?.items.orEmpty()
  val counts = answer?.totals.orEmpty()

  LaunchedEffect(results) {
    val id = source?.id
    val channels = results.filter { it.kind == MediaKind.LIVE && it.guideChannelId != null }.take(NOW_NEXT_CAP)
    if (id == null || channels.isEmpty()) {
      nowNext = emptyMap()
      return@LaunchedEffect
    }
    val listings = runCatching {
      container.epg.nowNextFor(id, channels.mapNotNull { it.guideChannelId })
    }.getOrDefault(emptyMap())
    nowNext = channels.associate { item ->
      val listing = listings[item.guideChannelId]
      item.id to (listing?.now?.title to listing?.next?.title)
    }
  }

  var opened by rememberSaveable { mutableStateOf<String?>(null) }
  val openedFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) {
    if (opened == null) return@LaunchedEffect
    repeat(10) {
      if (runCatching { openedFocus.requestFocus() }.isSuccess) {
        opened = null
        return@LaunchedEffect
      }
      delay(60)
    }
    opened = null
  }

  val fieldFocus = remember { FocusRequester() }
  val languageFocus = remember { FocusRequester() }

  val grouped = remember(results) { groupByKind(results) }
  val space = KeelTvTheme.space

  fun keep() {
    scope.launch { container.settings.setRecentSearches(pushRecent(recents, query)) }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = KeelTvTheme.dimens.screenPadding,
    verticalArrangement = Arrangement.spacedBy(space.s4),
  ) {
    item("head") {
      ScreenHeader(
        title = "Search",
        subtitle = source?.let { "Searching ${it.name} on this device" }
          ?: "Add a source to search it.",
      )
    }

    item("field") {
      SearchField(
        value = draft,
        onValueChange = { draft = it },
        placeholder = "Search every channel, movie and series",
        modifier = Modifier.fillMaxWidth(),
        anchor = fieldFocus,
      )
    }

    item("scopes") {
      Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(space.s2),
          modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer(),
        ) {
          items(SearchScope.entries.size) { index ->
            val option = SearchScope.entries[index]
            TvChip(option.label, option == kindScope, { kindScope = option })
          }
          item {
            TvChip(
              label = LANGUAGE_FILTER_CHOICES.firstOrNull { it.first == language }?.second ?: "All languages",
              selected = language.isNotEmpty(),
              onClick = { languageOpen = !languageOpen },
              modifier = Modifier.focusRequester(languageFocus),
            )
          }
        }

        if (languageOpen) {
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(space.s2),
            modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer(),
          ) {
            items(LANGUAGE_FILTER_CHOICES.size) { index ->
              val (value, label) = LANGUAGE_FILTER_CHOICES[index]
              TvChip(label, value == language, {
                scope.launch { container.settings.setLanguageFilter(value) }
                runCatching { languageFocus.requestFocus() }
                languageOpen = false
              })
            }
          }
        }
      }
    }

    if (query.length < MIN_QUERY) {
      if (recents.isNotEmpty()) {
        item("recents") {
          Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
            RowLabel("Recent searches")
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(space.s2),
              modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer(),
            ) {
              items(recents.size) { index ->
                val recent = recents[index]
                TvChip(recent, false, {
                  runCatching { fieldFocus.requestFocus() }
                  draft = recent
                  query = recent
                })
              }
              item {
                TvChip("Clear", false, {
                  runCatching { fieldFocus.requestFocus() }
                  scope.launch { container.settings.setRecentSearches(emptyList()) }
                })
              }
            }
          }
        }
      }
      item("idle") {
        EmptyState(
          title = "Find it by name",
          body = stats?.let { titlesIndexed(it) }
            ?: "Everything your provider ships is indexed on this device. Two characters is enough to start.",
        )
      }
    } else if (searching) {
      val kinds = kindScope.kind?.let { listOf(it) } ?: listOf(MediaKind.LIVE, MediaKind.MOVIE, MediaKind.SERIES)
      kinds.forEach { kind -> item("busy-${kind.wire}") { SkeletonResultRow(kind) } }
    } else if (results.isEmpty()) {
      item("none") {
        EmptyState(
          title = if (language.isNotEmpty()) "Filtered down to nothing" else "No title under that name",
          body = if (language.isNotEmpty()) {
            "No titles match these filters. Choose another language or clear the filters to see more."
          } else {
            "Providers name things strangely. A movie can arrive as \"TITLE 2019 MULTI-SUB 4K\". " +
              "Try a shorter fragment without the year."
          },
        )
      }
    } else {
      grouped.forEach { (kind, items) ->
        item("row-${kind.wire}") {
          Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
            RowLabel(rowLabel(kind), count = matchLine(items.size, counts[kind] ?: items.size))
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(space.s3),
              contentPadding = PaddingValues(start = 8.dp, end = 48.dp, top = 10.dp, bottom = 6.dp),
              modifier = Modifier
                .fillMaxWidth()
                .fadeEnd(to = KeelTvTheme.colors.bgApp)
                .focusGroup()
                .focusRestorer(),
            ) {
              items(items.size) { index ->
                val item = items[index]
                val restore = if (item.id == opened) Modifier.focusRequester(openedFocus) else Modifier
                if (kind == MediaKind.LIVE) {
                  val listing = nowNext[item.id]
                  ChannelTile(
                    item = item,
                    onClick = { opened = item.id; keep(); onPlay(item.id) },
                    modifier = restore,
                    now = listing?.first,
                    next = listing?.second,
                  )
                } else {
                  MediaCard(
                    item = item,
                    onClick = { opened = item.id; keep(); onOpen(item.id) },
                    modifier = restore.width(KeelTvTheme.dimens.cardWidth),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun titlesIndexed(stats: SourceStats): String {
  val total = stats.liveItems + stats.movieItems + stats.seriesItems
  return if (total > 0) {
    "%,d titles are indexed on this device. Two characters is enough.".format(total)
  } else {
    "Everything your provider ships is indexed on this device. Two characters is enough to start."
  }
}

@Composable
private fun SkeletonResultRow(kind: MediaKind) {
  val space = KeelTvTheme.space
  Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
    SkeletonBox(Modifier.width(96.dp).height(16.dp), RoundedCornerShape(3.dp))
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(space.s3),
      contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
      userScrollEnabled = false,
      modifier = Modifier.fillMaxWidth(),
    ) {
      items(SKELETON_TILES) {
        if (kind == MediaKind.LIVE) {
          SkeletonBox(Modifier.width(256.dp).height(64.dp), RoundedCornerShape(KeelTvTheme.radii.control))
        } else {
          MediaCardSkeleton(Modifier.width(KeelTvTheme.dimens.cardWidth))
        }
      }
    }
  }
}

private const val SKELETON_TILES = 10

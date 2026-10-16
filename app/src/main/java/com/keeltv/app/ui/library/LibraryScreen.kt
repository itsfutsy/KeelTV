package com.keeltv.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.data.model.NowNext
import com.keeltv.app.data.model.WatchProgress
import com.keeltv.app.ui.LocalActiveSource
import com.keeltv.app.ui.LocalContainer
import com.keeltv.app.ui.Section
import com.keeltv.app.ui.components.ButtonTone
import com.keeltv.app.ui.components.MediaCard
import com.keeltv.app.ui.components.NoSource
import com.keeltv.app.ui.components.ScreenHeader
import com.keeltv.app.ui.components.TvButton
import com.keeltv.app.ui.components.fractionThrough
import com.keeltv.app.ui.misc.ChannelTile
import com.keeltv.app.ui.misc.ShelfEmpty
import com.keeltv.app.ui.misc.RowLabel
import com.keeltv.app.ui.misc.StillCard
import com.keeltv.app.ui.theme.KeelTvTheme
import kotlinx.coroutines.launch

private sealed interface CardMenu {
  val title: String

  data class Watching(val entry: WatchProgress) : CardMenu {
    override val title: String get() = entry.title
  }

  data class Favourite(val item: MediaItem) : CardMenu {
    override val title: String get() = item.title
  }
}

@Composable
fun LibraryScreen(
  onOpen: (itemId: String) -> Unit,
  onPlay: (itemId: String, episodeId: String?, startAtMs: Long) -> Unit,
  onBrowse: (Section) -> Unit,
) {
  val container = LocalContainer.current
  val scope = rememberCoroutineScope()
  val source = LocalActiveSource.current
  if (source == null) {
    NoSource("Library", "Add a source to build a library.")
    return
  }

  val favourites by remember(source.id) { container.library.favourites(source.id) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val watching by remember(source.id) { container.library.continueWatching(source.id) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val recent by remember(source.id) { container.library.recentChannels(source.id) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  var menu by remember { mutableStateOf<CardMenu?>(null) }
  var listings by remember { mutableStateOf<Map<String, NowNext>>(emptyMap()) }
  val groups = remember(favourites) { splitFavourites(favourites) }
  val space = KeelTvTheme.space
  val now = remember(listings) { System.currentTimeMillis() / 1000 }

  LaunchedEffect(favourites, recent) {
    val channels = (favourites.filter { it.kind == MediaKind.LIVE } + recent)
      .filter { it.guideChannelId != null }
      .distinctBy { it.id }
    val window = runCatching {
      container.epg.nowNextFor(source.id, channels.mapNotNull { it.guideChannelId })
    }.getOrDefault(emptyMap())
    listings = channels.mapNotNull { item -> window[item.guideChannelId]?.let { item.id to it } }.toMap()
  }

  fun open(item: MediaItem) {
    if (item.kind == MediaKind.LIVE) onPlay(item.id, null, 0L) else onOpen(item.id)
  }

  @Composable
  fun Channel(item: MediaItem, onLongClick: (() -> Unit)? = null) {
    val listing = listings[item.id]
    val on = listing?.now
    ChannelTile(
      item = item,
      onClick = { open(item) },
      onLongClick = onLongClick,
      now = on?.title,
      next = listing?.next?.title,
      progress = on?.let { fractionThrough(it.start, it.stop, now) },
    )
  }

  Box(Modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = KeelTvTheme.dimens.screenPadding,
      verticalArrangement = Arrangement.spacedBy(space.s6),
    ) {
      item("head") { ScreenHeader(title = "Library") }

      if (watching.isNotEmpty()) {
        item("continue") {
          Shelf("Continue watching") {
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(space.s4),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
              modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer(),
            ) {
              items(watching.size) { index ->
                val entry = watching[index]
                StillCard(
                  title = entry.title,
                  image = entry.image,
                  progress = progressThrough(entry.position, entry.duration),
                  caption = watchCaption(entry.position, entry.duration),
                  onClick = { onPlay(entry.itemId, entry.episodeId.ifEmpty { null }, entry.position) },
                  onLongClick = { menu = CardMenu.Watching(entry) },
                )
              }
            }
          }
        }
      }

      if (recent.isNotEmpty()) {
        item("recent") {
          Shelf("Recent channels") {
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(space.s4),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
              modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer(),
            ) {
              items(recent.size) { index -> Channel(recent[index]) }
            }
          }
        }
      }

      groups.forEach { (label, items) ->
        item("fav-$label") {
          Shelf(favouritesLabel(items.first().kind)) {
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(space.s4),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
              modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer(),
            ) {
              items(items.size) { index ->
                val item = items[index]
                if (item.kind == MediaKind.LIVE) {
                  Channel(item, onLongClick = { menu = CardMenu.Favourite(item) })
                } else {
                  MediaCard(
                    item = item,
                    onClick = { open(item) },
                    onLongClick = { menu = CardMenu.Favourite(item) },
                    modifier = Modifier.width(KeelTvTheme.dimens.cardWidth),
                  )
                }
              }
            }
          }
        }
      }

      if (watching.isEmpty()) {
        item("continue-empty") {
          Shelf("Continue watching") {
            ShelfEmpty(
              body = "Stop a film or an episode partway and it waits here, at the second you left.",
              action = { TvButton("Browse movies", { onBrowse(Section.MOVIES) }) },
            )
          }
        }
      }
      if (favourites.isEmpty()) {
        item("favourites-empty") {
          Shelf("Favourites") {
            ShelfEmpty(
              body = "Hold OK on a channel, or press Favourite on a title page, and it waits here.",
              action = { TvButton("Browse channels", { onBrowse(Section.LIVE) }) },
            )
          }
        }
      }
    }

    val active = menu
    if (active != null) {
      CardMenuPanel(
        menu = active,
        onDismiss = { menu = null },
        onResume = { entry ->
          menu = null
          onPlay(entry.itemId, entry.episodeId.ifEmpty { null }, entry.position)
        },
        onForget = { entry ->
          menu = null
          scope.launch {
            container.library.clearProgress(source.id, entry.itemId, entry.episodeId)
          }
        },
        onUnstar = { item ->
          menu = null
          scope.launch { container.library.toggleFavourite(source.id, item.id) }
        },
      )
    }
  }
}

@Composable
private fun Shelf(title: String, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2)) {
    RowLabel(title)
    content()
  }
}

private fun favouritesLabel(kind: MediaKind): String = when (kind) {
  MediaKind.LIVE -> "Favourite channels"
  MediaKind.MOVIE -> "Favourite movies"
  MediaKind.SERIES -> "Favourite shows"
}

@Composable
private fun CardMenuPanel(
  menu: CardMenu,
  onDismiss: () -> Unit,
  onResume: (WatchProgress) -> Unit,
  onForget: (WatchProgress) -> Unit,
  onUnstar: (MediaItem) -> Unit,
) {
  val focus = remember { FocusRequester() }
  LaunchedEffect(menu) { runCatching { focus.requestFocus() } }
  BackHandler(enabled = true) { onDismiss() }

  Box(
    modifier = Modifier.fillMaxSize().background(KeelTvTheme.colors.bgOverlay),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .width(420.dp)
        .clip(RoundedCornerShape(KeelTvTheme.radii.modal))
        .background(KeelTvTheme.colors.bgSurface)
        .padding(KeelTvTheme.space.s6)
        .focusRequester(focus)
        .focusGroup(),
      verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
    ) {
      Text(menu.title, style = KeelTvTheme.type.sub, color = KeelTvTheme.colors.textPrimary)
      when (menu) {
        is CardMenu.Watching -> {
          TvButton(
            text = "Resume",
            onClick = { onResume(menu.entry) },
            tone = ButtonTone.Primary,
            modifier = Modifier.fillMaxWidth(),
          )
          TvButton(
            text = "Remove from Continue Watching",
            onClick = { onForget(menu.entry) },
            modifier = Modifier.fillMaxWidth(),
          )
        }
        is CardMenu.Favourite -> {
          TvButton(
            text = "Remove from favourites",
            onClick = { onUnstar(menu.item) },
            tone = ButtonTone.Primary,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
      TvButton("Cancel", onDismiss, tone = ButtonTone.Plain, modifier = Modifier.fillMaxWidth())
    }
  }
}

package com.keeltv.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.keeltv.app.ui.components.FACT_GAP
import com.keeltv.app.ui.components.HeroArt
import com.keeltv.app.ui.components.dissolveEnd
import com.keeltv.app.ui.components.formatRating
import com.keeltv.app.ui.components.timeLeft
import com.keeltv.app.ui.misc.StillCard
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import coil3.compose.AsyncImage
import com.keeltv.app.core.humanGenre
import com.keeltv.app.core.splitGenres
import com.keeltv.app.data.model.Episode
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.data.model.SeriesDetail
import com.keeltv.app.data.model.WatchProgress
import com.keeltv.app.ui.LocalActiveSource
import com.keeltv.app.ui.LocalContainer
import com.keeltv.app.ui.components.AmbientArt
import com.keeltv.app.ui.components.ArtlessTile
import com.keeltv.app.ui.components.ButtonTone
import com.keeltv.app.ui.components.NoSource
import com.keeltv.app.ui.components.FocusRow
import com.keeltv.app.ui.components.shownTitle
import com.keeltv.app.ui.components.SkeletonBox
import com.keeltv.app.ui.components.SkeletonLine
import com.keeltv.app.ui.components.TvButton
import com.keeltv.app.ui.components.seasonsLabel
import com.keeltv.app.ui.misc.TvChip
import com.keeltv.app.ui.theme.KeelTvTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RESUME_FLOOR_MS = 30_000L

@Composable
fun DetailScreen(
  itemId: String,
  onPlay: (itemId: String, episodeId: String?, startAtMs: Long) -> Unit,
  onGenre: (kind: MediaKind, genre: String) -> Unit,
  onBack: () -> Unit,
) {
  val container = LocalContainer.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val source = LocalActiveSource.current ?: return NoSource("Details", "Add a source to see this title.")

  // Held across recompositions: Room hands out a new flow per call, and an unheld one is read
  // again from the start every time the screen recomposes.
  val cached by remember(source.id, itemId) { container.catalog.observeItem(source.id, itemId) }
    .collectAsStateWithLifecycle(initialValue = null)
  val favourite by remember(source.id, itemId) { container.library.isFavourite(source.id, itemId) }
    .collectAsStateWithLifecycle(initialValue = false)
  val watching by remember(source.id) { container.library.continueWatching(source.id) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  var extra by remember(itemId) { mutableStateOf<MediaItem?>(null) }
  var series by remember(itemId) { mutableStateOf<SeriesDetail?>(null) }
  var loading by remember(itemId) { mutableStateOf(true) }
  var attempt by remember(itemId) { mutableStateOf(0) }
  var chosenSeason by remember(itemId) { mutableStateOf<Int?>(null) }

  val base = cached ?: return Loading()
  val detail = remember(base, extra) { enrich(base, extra) }
  val isSeries = detail.kind == MediaKind.SERIES
  val progress = remember(watching, itemId) { watching.filter { it.itemId == itemId } }

  LaunchedEffect(itemId, isSeries, attempt) {
    loading = true
    if (isSeries) {
      val fetched = runCatching { container.catalog.seriesDetail(source.id, itemId) }.getOrNull()
      series = fetched
      extra = fetched?.item
    } else {
      extra = runCatching { container.catalog.movieDetail(source.id, itemId) }.getOrNull()
    }
    loading = false
  }

  val seasons = remember(series) { orderSeasons(series?.seasons.orEmpty()) }
  val season = chosenSeason?.takeIf { it in seasons }
    ?: seasons.firstOrNull { series?.episodes?.get(it)?.isNotEmpty() == true }
    ?: seasons.firstOrNull()
  val episodes = remember(series, season) {
    season?.let { series?.episodes?.get(it)?.sortedBy { e -> e.episodeNum } }.orEmpty()
  }

  val next = remember(series, progress) { series?.let { nextUp(it, progress) } }
  val filmProgress = progress.firstOrNull { it.episodeId.isEmpty() }
  val resumeMs = if (isSeries) {
    next?.startAtMs?.takeIf { it > RESUME_FLOOR_MS } ?: 0L
  } else {
    filmProgress?.takeIf { !isFinished(it) }?.position?.takeIf { it > RESUME_FLOOR_MS } ?: 0L
  }
  val episodeId = if (isSeries) next?.episode?.id else null
  val canPlay = !isSeries || next != null || (!loading && seasons.isEmpty())

  val playFocus = remember { FocusRequester() }
  LaunchedEffect(itemId) {
    repeat(10) {
      if (runCatching { playFocus.requestFocus() }.isSuccess) return@LaunchedEffect
      delay(60)
    }
  }

  val backdrop = detail.backdrop?.takeIf { it.isNotEmpty() }
  val art = backdrop ?: detail.logo?.takeIf { it.isNotEmpty() }
  val leftMs = when {
    resumeMs <= 0 -> 0L
    isSeries -> progress.firstOrNull { it.episodeId == episodeId }?.let { (it.duration - it.position).coerceAtLeast(0) } ?: 0L
    else -> filmProgress?.let { (it.duration - it.position).coerceAtLeast(0) } ?: 0L
  }
  val resumeFraction = when {
    resumeMs <= 0 -> 0f
    isSeries -> watchedFraction(progress.firstOrNull { it.episodeId == episodeId })
    else -> watchedFraction(filmProgress)
  }

  Box(Modifier.fillMaxSize()) {
    AmbientArt(url = { art }, strength = 0.5f, softness = 0.8f)
    if (backdrop != null) {
      HeroArt(
        url = { backdrop },
        modifier = Modifier
          .align(Alignment.TopEnd)
          .fillMaxWidth(if (isSeries) 0.56f else 0.64f)
          .aspectRatio(16f / 9f),
      )
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = 32.dp),
    ) {
      item(key = "spread") {
        Spread(
          item = detail,
          poster = backdrop == null,
          compact = isSeries,
          loading = loading,
          canPlay = canPlay,
          playLabel = playLabel(isSeries, next?.episode, resumeMs),
          playDetail = if (!isSeries && leftMs > 0) timeLeft(leftMs / 1000) else null,
          resumeFraction = if (isSeries) 0f else resumeFraction,
          resumeMs = resumeMs,
          favourite = favourite,
          playFocus = playFocus,
          onPlay = { onPlay(itemId, episodeId, resumeMs) },
          onStartOver = { onPlay(itemId, episodeId, 0L) },
          onFavourite = { scope.launch { container.library.toggleFavourite(source.id, itemId) } },
          onGenre = { genre -> onGenre(detail.kind, genre) },
          onTrailer = youtubeId(detail.trailer)?.let { id -> { openTrailer(context, id) } },
        )
      }

      if (!isSeries) return@LazyColumn

      if (seasons.isNotEmpty()) {
        item(key = "seasons") {
          SeasonBar(
            seasons = seasons,
            active = season,
            countOf = { series?.episodes?.get(it)?.size ?: 0 },
            onPick = { chosenSeason = it },
          )
        }
      }

      if (episodes.isEmpty()) {
        item(key = "no-episodes") {
          NoEpisodes(loading = loading, onRetry = { attempt++ })
        }
      } else {
        item(key = "episodes-$season") {
          EpisodeStrip(
            episodes = episodes,
            progressOf = { episode -> progress.firstOrNull { it.episodeId == episode.id } },
            startAt = episodes.indexOfFirst { it.id == episodeId }.coerceAtLeast(0),
            onPlay = { episode, row ->
              val at = row?.takeIf { !isFinished(it) }?.position?.takeIf { p -> p > RESUME_FLOOR_MS } ?: 0L
              onPlay(itemId, episode.id, at)
            },
          )
        }
      }
    }
  }
}

private fun playLabel(isSeries: Boolean, episode: Episode?, resumeMs: Long): String {
  val verb = if (resumeMs > 0) "Resume" else "Play"
  if (!isSeries || episode == null) return verb
  return "$verb ${episodeCode(episode)}"
}

@Composable
private fun Loading() {
  Box(Modifier.fillMaxSize().background(KeelTvTheme.colors.bgApp))
}

private val EDGE = 56.dp

@Composable
private fun Spread(
  item: MediaItem,
  poster: Boolean,
  compact: Boolean,
  loading: Boolean,
  canPlay: Boolean,
  playLabel: String,
  playDetail: String?,
  resumeFraction: Float,
  resumeMs: Long,
  favourite: Boolean,
  playFocus: FocusRequester,
  onPlay: () -> Unit,
  onStartOver: () -> Unit,
  onFavourite: () -> Unit,
  onGenre: (String) -> Unit,
  onTrailer: (() -> Unit)?,
) {
  val colors = KeelTvTheme.colors
  val title = shownTitle(item)
  val genres = remember(item.genre) {
    splitGenres(item.genre).map(::humanGenre).filter { it.length > 1 }.distinct().take(if (compact) 3 else 6)
  }

  Row(
    modifier = Modifier.fillMaxWidth().padding(start = EDGE, end = EDGE, top = if (compact) 48.dp else 56.dp),
    verticalAlignment = Alignment.Top,
  ) {
    if (poster) {
      Poster(item, Modifier.padding(end = KeelTvTheme.space.s6).width(132.dp).height(198.dp))
    }

    Column(
      modifier = Modifier.widthIn(max = if (compact) 420.dp else 460.dp),
      verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 14.dp),
    ) {
      Text(
        text = title,
        style = if (title.length > 40) KeelTvTheme.type.display.copy(fontSize = 30.sp, lineHeight = 34.sp) else KeelTvTheme.type.display,
        color = colors.textPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s4),
      ) {
        val facts = buildList {
          item.year?.let { add(it.toString()) }
          runtimeLabel(item)?.let { add(it) }
          if (item.kind == MediaKind.SERIES) item.seasonCount?.let { add(seasonsLabel(it)) }
          formatRating(item.rating)?.let { add("★ $it") }
        }
        if (facts.isNotEmpty()) {
          Text(
            text = facts.joinToString(FACT_GAP),
            style = KeelTvTheme.type.control.copy(fontWeight = FontWeight.Normal),
            color = colors.textSecondary,
            maxLines = 1,
          )
        }
        if (compact && genres.isNotEmpty()) GenreRow(genres, onGenre)
      }

      Plot(text = item.plot, loading = loading, lines = if (compact) 3 else 4)

      if (!compact && genres.isNotEmpty()) GenreRow(genres, onGenre)

      Row(
        horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
        modifier = Modifier.padding(top = if (compact) 6.dp else 18.dp).focusGroup(),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2)) {
          TvButton(
            text = playLabel,
            onClick = onPlay,
            tone = ButtonTone.Primary,
            enabled = canPlay,
            icon = Icons.Filled.PlayArrow,
            detail = playDetail,
            large = true,
            modifier = Modifier.focusRequester(playFocus),
          )
          if (resumeFraction > 0.02f) ResumeBar(resumeFraction, Modifier.fillMaxWidth())
        }
        if (resumeMs > 0) {
          TvButton("Start over", onStartOver, enabled = canPlay, icon = Icons.Outlined.Replay, large = true)
        }
        if (onTrailer != null) {
          TvButton("Trailer", onTrailer, large = true)
        }
        TvButton(
          text = if (favourite) "In favourites" else "Favourite",
          onClick = onFavourite,
          icon = if (favourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
          large = true,
        )
      }

      if (!compact) Credits(item, Modifier.padding(top = KeelTvTheme.space.s4))
    }
  }
}

@Composable
private fun ResumeBar(fraction: Float, modifier: Modifier = Modifier) {
  val colors = KeelTvTheme.colors
  Box(
    modifier
      .height(3.dp)
      .drawBehind {
        val round = CornerRadius(size.height / 2f)
        drawRoundRect(colors.lineBright, cornerRadius = round)
        drawRoundRect(colors.accent, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = round)
      },
  )
}

@Composable
private fun Plot(text: String?, loading: Boolean, lines: Int) {
  val colors = KeelTvTheme.colors
  if (text.isNullOrBlank() && loading) {
    Column(verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3)) {
      SkeletonLine(0.96f, 12.dp)
      SkeletonLine(0.92f, 12.dp)
      SkeletonLine(0.52f, 12.dp)
    }
    return
  }
  Text(
    text = text?.takeIf { it.isNotBlank() } ?: "No synopsis came with this record.",
    style = KeelTvTheme.type.body.copy(lineHeight = 23.sp),
    color = if (text.isNullOrBlank()) colors.textTertiary else colors.textSecondary,
    maxLines = lines,
    overflow = TextOverflow.Ellipsis,
  )
}

@Composable
private fun GenreRow(genres: List<String>, onGenre: (String) -> Unit) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
    modifier = Modifier.focusGroup(),
  ) {
    genres.forEach { genre -> TvChip(genre, false, { onGenre(genre) }, compact = true) }
  }
}

@Composable
private fun Poster(item: MediaItem, modifier: Modifier = Modifier) {
  val colors = KeelTvTheme.colors
  val shape = RoundedCornerShape(KeelTvTheme.radii.control)
  val artwork = !item.logo.isNullOrEmpty()
  var landed by remember(item.logo) { mutableStateOf(false) }
  Box(modifier.clip(shape).background(colors.bgSurface)) {
    if (artwork) {
      if (!landed) SkeletonBox(Modifier.fillMaxSize(), shape)
    } else {
      ArtlessTile(
        seed = item.id,
        label = item.title.ifEmpty { item.name },
        modifier = Modifier.fillMaxSize(),
      )
    }
    if (artwork) {
      AsyncImage(
        model = item.logo,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onSuccess = { landed = true },
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

@Composable
private fun Credits(item: MediaItem, modifier: Modifier = Modifier) {
  val colors = KeelTvTheme.colors
  val rows = buildList {
    item.director?.trim()?.takeIf { it.isNotEmpty() }?.let { add("Director" to it) }
    item.cast?.trim()?.takeIf { it.isNotEmpty() }?.let { add("Cast" to it) }
  }
  val tail = listOfNotNull(
    formatAdded(item.addedAt)?.let { "Added $it" },
    item.containerExtension?.takeIf { it.isNotEmpty() }?.uppercase(),
  )
  if (rows.isEmpty() && tail.isEmpty()) return

  Column(modifier = modifier.widthIn(max = 540.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
    rows.forEach { (label, value) ->
      Text(
        text = buildAnnotatedString {
          withStyle(SpanStyle(color = colors.textTertiary)) { append(label) }
          append("\u2002 ")
          append(value)
        },
        style = KeelTvTheme.type.small,
        color = colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (tail.isNotEmpty()) {
      Text(tail.joinToString(FACT_GAP), style = KeelTvTheme.type.small, color = colors.textTertiary, maxLines = 1)
    }
  }
}

@Composable
private fun SeasonBar(
  seasons: List<Int>,
  active: Int?,
  countOf: (Int) -> Int,
  onPick: (Int) -> Unit,
) {
  val colors = KeelTvTheme.colors
  LazyRow(
    modifier = Modifier.fillMaxWidth().padding(top = KeelTvTheme.space.s5).focusGroup(),
    contentPadding = PaddingValues(horizontal = EDGE),
    horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
  ) {
    items(seasons.size, key = { seasons[it] }) { index ->
      val season = seasons[index]
      val count = countOf(season)
      val on = season == active
      FocusRow(
        onClick = { if (count > 0) onPick(season) },
        selected = on,
        enabled = count > 0,
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.height(34.dp),
      ) { focused ->
        Row(
          modifier = Modifier.padding(horizontal = 13.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
        ) {
          Text(
            text = seasonLabel(season),
            style = KeelTvTheme.type.control.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
            color = when {
              count == 0 -> colors.textDisabled
              focused -> colors.onFocus
              on -> colors.accent
              else -> colors.textSecondary
            },
            maxLines = 1,
          )
          if (count > 0) {
            Text(
              "$count",
              style = KeelTvTheme.type.control,
              color = when {
                focused -> colors.onFocusMuted
                on -> colors.accent.copy(alpha = 0.7f)
                else -> colors.textTertiary
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun EpisodeStrip(
  episodes: List<Episode>,
  progressOf: (Episode) -> WatchProgress?,
  startAt: Int,
  onPlay: (Episode, WatchProgress?) -> Unit,
) {
  val state = rememberLazyListState(initialFirstVisibleItemIndex = (startAt - 1).coerceAtLeast(0))
  LazyRow(
    state = state,
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = KeelTvTheme.space.s3)
      .dissolveEnd()
      .focusGroup()
      .focusRestorer(),
    contentPadding = PaddingValues(start = EDGE, end = EDGE, top = 10.dp, bottom = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s4),
  ) {
    items(episodes.size, key = { episodes[it].id }) { index ->
      val episode = episodes[index]
      val row = progressOf(episode)
      EpisodeCard(episode, row, onPlay = { onPlay(episode, row) })
    }
  }
}

@Composable
private fun EpisodeCard(episode: Episode, progress: WatchProgress?, onPlay: () -> Unit) {
  val colors = KeelTvTheme.colors
  val fraction = watchedFraction(progress)
  val done = isFinished(progress)
  val duration = episode.durationSecs?.takeIf { it > 0 }
  StillCard(
    title = episodeTitle(episode),
    image = episode.image,
    progress = if (done) 1f else fraction,
    caption = when {
      done -> "Watched"
      progress != null && progress.duration > 0 && fraction > 0.02f ->
        timeLeft((progress.duration - progress.position).coerceAtLeast(0) / 1000)
      duration != null -> episodeDuration(duration)
      else -> ""
    },
    number = episode.episodeNum,
    seed = episode.id,
    onClick = onPlay,
    width = 176.dp,
  )
}

@Composable
private fun NoEpisodes(loading: Boolean, onRetry: () -> Unit) {
  val colors = KeelTvTheme.colors
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = EDGE, vertical = KeelTvTheme.space.s6),
    verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
  ) {
    if (loading) {
      Text("Reading the episode list…", style = KeelTvTheme.type.sub, color = colors.textSecondary)
      return@Column
    }
    Text("No episode list from this provider", style = KeelTvTheme.type.sub, color = colors.textPrimary)
    Text(
      "The series is in the catalogue but its season data came back empty. Some providers fill " +
        "this in lazily, so a retry sometimes works.",
      style = KeelTvTheme.type.small,
      color = colors.textSecondary,
    )
    TvButton("Retry", onRetry)
  }
}

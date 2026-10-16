package com.keeltv.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.keeltv.app.ui.AppIntent
import com.keeltv.app.ui.AppIntents
import com.keeltv.app.ui.components.shownTitle
import com.keeltv.app.ui.components.parseCategory
import com.keeltv.app.ui.misc.TvProgressBar
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.keeltv.app.data.model.Episode
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.data.model.NowNext
import com.keeltv.app.data.model.SourceKind
import com.keeltv.app.data.prefs.Settings
import com.keeltv.app.data.model.EpgProgramme
import com.keeltv.app.data.repo.ChannelWindow
import com.keeltv.app.playback.PlaybackController
import com.keeltv.app.playback.PlaybackCopy
import com.keeltv.app.playback.PlaybackRequest
import com.keeltv.app.playback.Progress
import com.keeltv.app.playback.TimeShift
import com.keeltv.app.playback.liveCandidates
import com.keeltv.app.playback.scrubStepMs
import com.keeltv.app.ui.LocalActiveSource
import com.keeltv.app.ui.LocalContainer
import com.keeltv.app.ui.components.ButtonTone
import com.keeltv.app.ui.components.TvButton
import com.keeltv.app.ui.theme.KeelTvTheme
import com.keeltv.app.playback.formatDuration
import com.keeltv.app.ui.components.clock
import com.keeltv.app.ui.components.dayLabel
import com.keeltv.app.ui.detail.episodeCode
import com.keeltv.app.ui.detail.episodeTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val BANNER_MS = 6_000L
private const val CONTROLS_MS = 5_000L

private const val NUMBER_MS = 2_200L

private const val NUMBER_DIGITS = 5

private enum class Overlay { NONE, CHANNELS, TRACKS }

private data class Playing(
  val item: MediaItem,
  val episode: Episode? = null,
  val catchUp: EpgProgramme? = null,
)

private class Clock {
  var positionMs = 0L
  var durationMs = 0L
  var bufferedMs = 0L
}

@Composable
fun PlayerScreen(
  itemId: String,
  episodeId: String?,
  startAtMs: Long,
  channelScope: String = "",
  onBack: () -> Unit,
) {
  val container = LocalContainer.current
  val source = LocalActiveSource.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())

  val controller = remember { PlaybackController(context, container.http, scope) }
  val clock = remember { Clock() }
  val rootFocus = remember { FocusRequester() }
  val seekFocus = remember { FocusRequester() }

  var playing by remember { mutableStateOf<Playing?>(null) }
  var channels by remember { mutableStateOf<ChannelWindow?>(null) }
  var channelIndex by remember { mutableIntStateOf(0) }
  var siblings by remember { mutableStateOf<List<Episode>>(emptyList()) }
  var nowNext by remember { mutableStateOf<NowNext?>(null) }
  var resolveError by remember { mutableStateOf<String?>(null) }

  var overlay by remember { mutableStateOf(Overlay.NONE) }
  var chrome by remember { mutableStateOf(true) }
  var wake by remember { mutableIntStateOf(0) }
  var digits by remember { mutableStateOf("") }
  var scrubTo by remember { mutableStateOf<Long?>(null) }
  var positionMs by remember { mutableLongStateOf(0L) }
  var durationMs by remember { mutableLongStateOf(0L) }
  var bufferedMs by remember { mutableLongStateOf(0L) }
  var clockSeconds by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
  var openingAt by remember { mutableLongStateOf(0L) }

  val item = playing?.item
  val catchUp = playing?.catchUp
  val live = item?.kind == MediaKind.LIVE && catchUp == null
  val channel = item?.kind == MediaKind.LIVE
  val state = controller.state

  LaunchedEffect(itemId, episodeId, startAtMs, source?.id) {
    val src = source ?: return@LaunchedEffect
    val found = container.catalog.item(src.id, itemId)
    if (found == null) {
      resolveError = "That item is no longer in the catalogue."
      return@LaunchedEffect
    }
    val episode = episodeId?.let { id ->
      val detail = runCatching { container.catalog.seriesDetail(src.id, itemId) }.getOrNull()
      val flat = detail?.seasons.orEmpty().flatMap { detail?.episodes?.get(it).orEmpty() }
      siblings = flat
      flat.firstOrNull { it.id == id }
    }
    val archive = if (found.kind == MediaKind.LIVE && startAtMs > 0) {
      programmeAt(container, src.id, found, startAtMs / 1000)
    } else {
      null
    }
    playing = Playing(found, episode, archive)
  }

  LaunchedEffect(itemId, source?.id, settings.languageFilter) {
    val src = source ?: return@LaunchedEffect
    val opened = container.catalog.item(src.id, itemId) ?: return@LaunchedEffect
    if (opened.kind != MediaKind.LIVE) return@LaunchedEffect
    val window = runCatching {
      container.catalog.channelWindow(src.id, opened, channelScope, settings.languageFilter)
    }.getOrNull()
    channels = window
    channelIndex = window?.startIndex?.coerceAtLeast(0) ?: 0
  }

  LaunchedEffect(playing) {
    val src = source ?: return@LaunchedEffect
    val now = playing ?: return@LaunchedEffect
    resolveError = null
    val archive = now.catchUp
    val url = runCatching {
      if (archive != null) {
        container.catalog.catchupUrl(src, now.item, archive.start, archive.stop)
      } else {
        container.catalog.streamUrl(src, now.item, now.episode)
      }
    }.getOrNull()
    if (url == null) {
      resolveError = if (archive != null) {
        "This channel does not keep a recording of that programme."
      } else {
        "No stream URL for this item."
      }
      return@LaunchedEffect
    }
    val isLive = now.item.kind == MediaKind.LIVE && archive == null
    val resume = if (now.item.kind == MediaKind.LIVE) 0L else resumeFor(container, src.id, now, startAtMs)
    openingAt = resume
    controller.open(
      PlaybackRequest(
        // A catch-up recording is a finite file, so it is opened as one and never as a live pipe,
        // but a channel url is a channel url: some servers only answer the playlist form.
        candidates = if (now.item.kind == MediaKind.LIVE) liveCandidates(url) else listOf(url),
        live = isLive,
        startAtMs = resume,
        title = archive?.title ?: now.episode?.title ?: now.item.title.ifBlank { now.item.name },
      ),
    )
    if (now.item.kind == MediaKind.LIVE) container.library.touchChannel(src.id, now.item)
    wake += 1
  }

  LaunchedEffect(item?.id, channel) {
    val src = source ?: return@LaunchedEffect
    val guideId = item?.guideChannelId ?: item?.epgChannelId
    if (!channel || guideId == null) {
      nowNext = null
      return@LaunchedEffect
    }
    while (true) {
      nowNext = runCatching { container.epg.nowNext(src.id, guideId) }.getOrNull()
      delay(60_000)
    }
  }

  LaunchedEffect(live) {
    while (live) {
      clockSeconds = System.currentTimeMillis() / 1000
      delay(15_000)
    }
  }

  LaunchedEffect(settings.playback) { controller.applyPreferences(settings.playback) }

  // The play head, read rather than pushed: ExoPlayer has no position callback.
  LaunchedEffect(controller.player) {
    while (true) {
      controller.player?.let { p ->
        positionMs = p.currentPosition
        durationMs = if (p.duration > 0) p.duration else 0
        bufferedMs = p.bufferedPosition
        clock.positionMs = positionMs
        clock.durationMs = durationMs
        clock.bufferedMs = bufferedMs
      }
      delay(500)
    }
  }

  DisposableEffect(Unit) {
    container.refresh.holdForPlayback()
    onDispose { container.refresh.releasePlayback() }
  }

  LaunchedEffect(playing, live) {
    val src = source ?: return@LaunchedEffect
    val now = playing ?: return@LaunchedEffect
    if (now.item.kind == MediaKind.LIVE) return@LaunchedEffect
    while (true) {
      delay(Progress.SAVE_EVERY_MS)
      saveProgress(container, src.id, now, clock.positionMs, clock.durationMs)
    }
  }

  val exitScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
  DisposableEffect(Unit) {
    onDispose {
      val src = source
      val now = playing
      controller.release()
      exitScope.launch {
        if (src != null && now != null && now.item.kind != MediaKind.LIVE) {
          saveProgress(container, src.id, now, clock.positionMs, clock.durationMs)
        }
        exitScope.cancel()
      }
    }
  }

  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(lifecycle) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_STOP -> controller.suspendPlayback()
        Lifecycle.Event.ON_START -> controller.resumePlayback()
        else -> Unit
      }
    }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
  }

  val next = siblings.indexOfFirst { it.id == playing?.episode?.id }
    .takeIf { it >= 0 }
    ?.let { siblings.getOrNull(it + 1) }

  val lengthMs = if (durationMs > 0) durationMs else catchUp?.let { (it.stop - it.start) * 1000 } ?: 0

  controller.onEnded = onEnded@{
    val src = source ?: return@onEnded
    val now = playing ?: return@onEnded
    if (now.catchUp != null) {
      playing = now.copy(catchUp = null)
      return@onEnded
    }
    exitScope.launch {
      saveProgress(container, src.id, now, clock.durationMs, clock.durationMs)
    }
    if (next != null) playing = now.copy(episode = next) else onBack()
  }

  fun zap(step: Int) {
    val window = channels ?: return
    if (window.size < 2) return
    val target = window.step(channelIndex, step)
    scope.launch {
      val next = window.at(target) ?: return@launch
      channelIndex = target
      overlay = Overlay.NONE
      playing = Playing(next)
    }
  }

  fun watchFromStart() {
    val current = playing ?: return
    val programme = nowNext?.now ?: return
    if (!current.item.hasArchive || current.catchUp != null) return
    overlay = Overlay.NONE
    playing = current.copy(catchUp = programme)
  }

  fun scrub(deltaMs: Long) {
    val limit = if (lengthMs > 0) lengthMs - 1_000 else Long.MAX_VALUE
    scrubTo = ((scrubTo ?: positionMs) + deltaMs).coerceIn(0, limit)
    wake += 1
  }

  LaunchedEffect(scrubTo) {
    val target = scrubTo ?: return@LaunchedEffect
    delay(350)
    controller.player?.seekTo(target)
    positionMs = target
    scrubTo = null
  }

  LaunchedEffect(digits) {
    val typed = digits
    if (typed.isEmpty()) return@LaunchedEffect
    delay(NUMBER_MS)
    val window = channels
    val wanted = typed.toIntOrNull()
    if (window != null && wanted != null && wanted >= 1 && wanted <= window.size) {
      window.at(wanted - 1)?.let { target ->
        channelIndex = wanted - 1
        playing = Playing(target)
      }
    }
    digits = ""
  }

  LaunchedEffect(wake, overlay, state.error) {
    chrome = true
    if (overlay != Overlay.NONE || state.error != null || resolveError != null) return@LaunchedEffect
    delay(if (live) BANNER_MS else CONTROLS_MS)
    chrome = false
  }

  LaunchedEffect(overlay, chrome, live, state.error, resolveError) {
    if (overlay != Overlay.NONE) return@LaunchedEffect
    if (state.error != null || resolveError != null) return@LaunchedEffect
    if (!live && chrome) runCatching { seekFocus.requestFocus() }
    else runCatching { rootFocus.requestFocus() }
  }

  val archiveHere = live && playing?.item?.hasArchive == true && nowNext?.now != null &&
    source?.kind == SourceKind.XTREAM

  val ahead = if (live) TimeShift.aheadMs(positionMs, bufferedMs) else 0L
  val shifted = live && (state.paused || ahead > TimeShift.LIVE_EDGE_MS)

  fun handleKey(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    // Back is answered here, on the key itself, rather than left to the activity. MEASURED: with
    // a row of the channel list focused, the first press of Back reached this hierarchy, was not
    // taken by anything in it, and still never arrived at the activity as a back press; only the
    // second did. Taking the key down here means the first press closes the list, and the
    // activity, which never saw the key down, does not act on the key up as well.
    if (event.key == Key.Back) {
      if (overlay != Overlay.NONE) overlay = Overlay.NONE else onBack()
      return true
    }
    if (overlay != Overlay.NONE) return false

    val failed = state.error != null || resolveError != null
    if (failed && !live) return false
    if (failed) {
      val digit = digitOf(event.key)
      if (digit != null) {
        digits = (digits + digit).take(NUMBER_DIGITS)
        return true
      }
      return when (event.key) {
        Key.ChannelUp -> { zap(1); true }
        Key.ChannelDown -> { zap(-1); true }
        else -> false
      }
    }

    if (live) {
      val digit = digitOf(event.key)
      if (digit != null && (channels?.size ?: 0) > 1) {
        digits = (digits + digit).take(NUMBER_DIGITS)
        return true
      }
      when (event.key) {
        Key.DirectionUp, Key.DirectionDown -> { overlay = Overlay.CHANNELS; return true }
        Key.ChannelUp -> { zap(1); return true }
        Key.ChannelDown -> { zap(-1); return true }
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause,
        -> { controller.togglePlay(); wake += 1; return true }
        Key.Menu, Key.Captions -> { overlay = Overlay.TRACKS; return true }
        Key.DirectionLeft, Key.MediaRewind -> {
          val step = scrubStepMs(event.nativeKeyEvent.repeatCount)
          if (archiveHere && !shifted) watchFromStart() else controller.shift(-step)
          wake += 1
          return true
        }
        Key.DirectionRight, Key.MediaFastForward -> {
          if (shifted) controller.shift(scrubStepMs(event.nativeKeyEvent.repeatCount))
          wake += 1
          return true
        }
        Key.Info -> { wake += 1; return true }
        else -> return false
      }
    }

    when (event.key) {
      Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { controller.togglePlay(); wake += 1; return true }
      Key.MediaFastForward -> { scrub(30_000); return true }
      Key.MediaRewind -> { scrub(-10_000); return true }
      Key.Captions -> { overlay = Overlay.TRACKS; return true }
      Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
      Key.DirectionCenter, Key.Enter,
      -> { if (!chrome) { wake += 1; return true }; return false }
      else -> return false
    }
  }

  val failure = resolveError ?: state.error
  var shown by remember(playing) { mutableStateOf(false) }
  LaunchedEffect(playing, state.playing) { if (state.playing) shown = true }
  val opening = !live && playing != null && failure == null && !shown &&
    (state.loading || state.reconnecting)

  Box(
    Modifier
      .fillMaxSize()
      .background(Color.Black)
      .focusRequester(rootFocus)
      .focusable(enabled = failure == null)
      .onPreviewKeyEvent { handleKey(it) },
  ) {
    PlayerSurface(controller.player, settings.playback.appearance, Modifier.fillMaxSize())

    val art = playing?.let { now -> if (now.catchUp != null) null else artFor(now) }
    AnimatedVisibility(
      visible = opening,
      enter = fadeIn(),
      exit = fadeOut(tween(320)),
      modifier = Modifier.fillMaxSize(),
    ) {
      Opening(
        art = art,
        title = playing?.let { titleOf(it) }.orEmpty(),
        note = when {
          state.reconnecting -> "Trying again"
          openingAt > 0 -> "Resuming at ${formatDuration(openingAt)}"
          else -> "Opening"
        },
      )
    }
    if ((state.loading || state.reconnecting) && !opening) {
      Busy(
        note = when {
          state.reconnecting -> "Reconnecting…"
          state.stalled -> "Buffering…"
          else -> null
        },
        modifier = Modifier.align(Alignment.Center),
      )
    }

    if (failure != null) {
      val shown = playing
      val undecodable = resolveError == null && state.undecodable
      val lookFor = shown?.takeIf { it.item.kind != MediaKind.LIVE }
        ?.let { shownTitle(it.item) }
      Failure(
        art = if (shown == null || shown.catchUp != null) null else artFor(shown),
        title = shown?.let { titleOf(it) },
        live = channel,
        undecodable = undecodable,
        message = failure,
        onRetry = { if (resolveError != null) playing = playing else controller.retry() },
        onBack = onBack,
        searchFor = if (undecodable) lookFor else null,
        onSearch = { words -> AppIntents.offer(AppIntent.Search(words)) },
      )
    }

    if (digits.isNotEmpty()) {
      NumberEntry(
        digits = digits,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(48.dp)
          .clip(RoundedCornerShape(KeelTvTheme.radii.panel)),
      )
    }

    AnimatedVisibility(
      visible = chrome && failure == null && item != null && live && overlay == Overlay.NONE,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.fillMaxSize(),
    ) {
      val current = item ?: return@AnimatedVisibility
      InfoBanner(
        item = current,
        number = channelIndex + 1,
        categoryName = (channels?.categoryName ?: current.categoryName.orEmpty())
          .let { raw -> parseCategory(raw).let { (it.chips + it.label).joinToString(" ") } },
        nowNext = nowNext,
        clockSeconds = clockSeconds,
        paused = shifted && state.paused,
        behindMs = if (shifted) state.behindLiveMs else 0L,
        notice = if (state.audioUndecodable) PlaybackCopy.NO_AUDIO else null,
        archiveHint = archiveHere && !shifted,
      )
    }

    AnimatedVisibility(
      visible = chrome && failure == null && item != null && !live && overlay != Overlay.TRACKS && !opening,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.fillMaxSize(),
    ) {
      val current = playing ?: return@AnimatedVisibility
      val recording = current.catchUp
      val channelName = current.item.title.ifBlank { current.item.name }
      VodControls(
        title = recording?.title
          ?: current.episode?.let { episodeTitle(it) }
          ?: channelName,
        subtitle = when {
          recording != null ->
            "${dayLabel(recording.start)} ${clock(recording.start)} \u2013 ${clock(recording.stop)}"
          current.episode != null -> "${current.item.title}\u2003${episodeCode(current.episode)}"
          else -> null
        },
        context = recording?.let { "Catch-up from $channelName" },
        notice = if (state.audioUndecodable) PlaybackCopy.NO_AUDIO else null,
        timeAt = if (recording != null) {
          { ms -> clock(recording.start + ms / 1000) }
        } else {
          ::formatDuration
        },
        positionMs = positionMs,
        durationMs = lengthMs,
        bufferedMs = bufferedMs,
        scrubbingTo = scrubTo,
        playing = state.playing,
        nextLabel = next?.title,
        seekFocus = seekFocus,
        onScrub = ::scrub,
        onTogglePlay = { controller.togglePlay(); wake += 1 },
        onTracks = { overlay = Overlay.TRACKS },
        onNext = { next?.let { playing = current.copy(episode = it) } },
        onGoLive = if (recording != null) {
          { playing = current.copy(catchUp = null) }
        } else {
          null
        },
      )
    }

    AnimatedVisibility(
      visible = overlay == Overlay.CHANNELS,
      enter = fadeIn() + slideInHorizontally { -it / 3 },
      exit = fadeOut() + slideOutHorizontally { -it / 3 },
      modifier = Modifier.align(Alignment.CenterStart),
    ) {
      val src = source ?: return@AnimatedVisibility
      ChannelListPanel(
        categoryName = channels?.categoryName
          ?.let { raw -> parseCategory(raw).let { (it.chips + it.label).joinToString(" ") } }
          ?: "Channels",
        window = channels,
        currentIndex = channelIndex,
        sourceId = src.id,
        epg = container.epg,
        onPick = { index, picked ->
          overlay = Overlay.NONE
          channelIndex = index
          if (picked.id != item?.id) playing = Playing(picked) else wake += 1
        },
      )
    }

    AnimatedVisibility(
      visible = overlay == Overlay.TRACKS,
      enter = fadeIn() + slideInHorizontally { it / 3 },
      exit = fadeOut() + slideOutHorizontally { it / 3 },
      modifier = Modifier.align(Alignment.CenterEnd),
    ) {
      TrackMenu(
        tracks = state.tracks,
        audioId = state.audioId,
        subtitleId = state.subtitleId,
        appearance = settings.playback.appearance,
        onSelect = { kind, id -> controller.select(kind, id) },
        onAppearance = { scope.launch { container.settings.setCaptionAppearance(it) } },
      )
    }
  }

  androidx.activity.compose.BackHandler(enabled = true) {
    when {
      overlay != Overlay.NONE -> overlay = Overlay.NONE
      else -> onBack()
    }
  }
}

private suspend fun programmeAt(
  container: com.keeltv.app.AppContainer,
  sourceId: String,
  item: MediaItem,
  atSeconds: Long,
): EpgProgramme? {
  val channelId = item.guideChannelId ?: item.epgChannelId ?: return null
  return runCatching { container.epg.channel(sourceId, channelId, atSeconds, atSeconds + 1) }
    .getOrNull()
    ?.firstOrNull { it.start <= atSeconds && it.stop > atSeconds }
}

private suspend fun resumeFor(
  container: com.keeltv.app.AppContainer,
  sourceId: String,
  now: Playing,
  routeStartAtMs: Long,
): Long {
  if (routeStartAtMs > 0) return routeStartAtMs
  val saved = container.library.progressFor(sourceId, now.item.id, now.episode?.id.orEmpty())
    ?: return 0
  return Progress.resumeFrom(saved.position, saved.duration)
}

private suspend fun saveProgress(
  container: com.keeltv.app.AppContainer,
  sourceId: String,
  now: Playing,
  positionMs: Long,
  durationMs: Long,
) {
  val episodeId = now.episode?.id.orEmpty()
  if (Progress.isFinished(positionMs, durationMs)) {
    container.library.clearProgress(sourceId, now.item.id, episodeId)
    return
  }
  if (!Progress.shouldSave(positionMs, durationMs)) return
  container.library.saveProgress(sourceId, now.item, positionMs, durationMs, episodeId)
}

private fun digitOf(key: Key): Char? = when (key) {
  Key.Zero, Key.NumPad0 -> '0'
  Key.One, Key.NumPad1 -> '1'
  Key.Two, Key.NumPad2 -> '2'
  Key.Three, Key.NumPad3 -> '3'
  Key.Four, Key.NumPad4 -> '4'
  Key.Five, Key.NumPad5 -> '5'
  Key.Six, Key.NumPad6 -> '6'
  Key.Seven, Key.NumPad7 -> '7'
  Key.Eight, Key.NumPad8 -> '8'
  Key.Nine, Key.NumPad9 -> '9'
  else -> null
}

@Composable
private fun Busy(note: String?, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(KeelTvTheme.radii.panel))
      .background(KeelTvTheme.colors.bgOverlay)
      .padding(horizontal = KeelTvTheme.space.s6, vertical = KeelTvTheme.space.s4),
    horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Spinner()
    Text(
      text = note ?: "Opening…",
      style = KeelTvTheme.type.body,
      color = KeelTvTheme.colors.textSecondary,
    )
  }
}

private fun titleOf(playing: Playing): String =
  playing.catchUp?.title
    ?: playing.episode?.let { episodeTitle(it) }
    ?: playing.item.title.ifBlank { playing.item.name }

private fun artFor(playing: Playing): String? =
  playing.episode?.image?.takeIf { it.isNotBlank() }
    ?: playing.item.backdrop?.takeIf { it.isNotBlank() }
    ?: playing.item.logo?.takeIf { it.isNotBlank() && playing.item.kind != MediaKind.LIVE }

@Composable
private fun HeldArt(url: String?, strength: Float) {
  Box(Modifier.fillMaxSize().background(DARK)) {
    if (url != null) {
      AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center,
        modifier = Modifier
          .fillMaxWidth(0.62f)
          .fillMaxHeight(0.8f)
          .align(Alignment.TopEnd)
          .graphicsLayer { alpha = strength },
      )
    }
    Box(
      Modifier.fillMaxSize().background(
        Brush.horizontalGradient(
          0f to DARK,
          0.4f to DARK,
          0.55f to DARK.copy(alpha = 0.7f),
          0.8f to Color.Transparent,
        ),
      ),
    )
    Box(
      Modifier.fillMaxSize().background(
        Brush.verticalGradient(0.45f to Color.Transparent, 0.8f to DARK),
      ),
    )
  }
}

private val DARK = Color(0xFF0A0A0C)

@Composable
private fun Opening(art: String?, title: String, note: String) {
  Box(Modifier.fillMaxSize()) {
    HeldArt(art, strength = 0.5f)
    Column(
      modifier = Modifier.align(Alignment.CenterStart).padding(start = 56.dp).width(440.dp),
      verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
    ) {
      Text(
        text = title,
        style = KeelTvTheme.type.display.copy(fontSize = 34.sp, lineHeight = 38.sp),
        color = KeelTvTheme.colors.textPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(note, style = KeelTvTheme.type.control.copy(fontWeight = FontWeight.Normal), color = KeelTvTheme.colors.textSecondary)
      TvProgressBar(value = null, modifier = Modifier.padding(top = KeelTvTheme.space.s2).width(180.dp), height = 3.dp)
    }
  }
}

@Composable
private fun Failure(
  art: String?,
  title: String?,
  live: Boolean,
  undecodable: Boolean,
  message: String,
  onRetry: () -> Unit,
  onBack: () -> Unit,
  searchFor: String?,
  onSearch: (String) -> Unit,
) {
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
  val colors = KeelTvTheme.colors

  Box(Modifier.fillMaxSize()) {
    HeldArt(art, strength = 0.4f)
    Column(
      modifier = Modifier.align(Alignment.CenterStart).padding(start = 56.dp).width(480.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      if (title != null) {
        Text(title, style = KeelTvTheme.type.control.copy(fontWeight = FontWeight.Normal), color = colors.textTertiary, maxLines = 1)
      }
      Text(
        text = when {
          undecodable && live -> "This channel won't play on this TV"
          undecodable -> "This copy won't play on this TV"
          live -> "This channel did not respond"
          else -> "This title did not start"
        },
        style = KeelTvTheme.type.title.copy(fontSize = 28.sp, lineHeight = 34.sp),
        color = colors.textPrimary,
      )
      Text(
        text = when {
          undecodable && live ->
            "Its video is in a format this TV can't decode, so trying again won't help. Another feed of the channel may play."
          undecodable ->
            "Its video is in a format this TV can't decode, so trying again won't help. Another copy in your catalogue may play."
          else -> message
        },
        style = KeelTvTheme.type.body,
        color = colors.textSecondary,
      )
      Row(
        modifier = Modifier.padding(top = KeelTvTheme.space.s2),
        horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
      ) {
        if (undecodable) {
          TvButton("Back", onBack, tone = ButtonTone.Primary, large = true, modifier = Modifier.focusRequester(focus))
          if (searchFor != null) {
            TvButton("Search for $searchFor", { onSearch(searchFor) }, icon = Icons.Outlined.Search, large = true)
          }
        } else {
          TvButton("Retry", onRetry, tone = ButtonTone.Primary, large = true, modifier = Modifier.focusRequester(focus))
          TvButton("Back", onBack, large = true)
        }
      }
    }
  }
}

package com.keeltv.app.ui.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.keeltv.app.ui.components.fadeFoot
import com.keeltv.app.ui.components.timeLeft
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.keeltv.app.data.epg.KEEP_PAST_SECONDS
import com.keeltv.app.data.epg.archiveSeconds
import com.keeltv.app.data.model.EpgProgramme
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.ui.components.FocusRow
import com.keeltv.app.ui.components.clock
import com.keeltv.app.ui.components.dayHeading
import com.keeltv.app.ui.components.dayLabel
import com.keeltv.app.ui.components.dayMark
import com.keeltv.app.ui.components.fractionThrough
import com.keeltv.app.ui.theme.KeelTvTheme
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

private val CHANNEL_WIDTH = 168.dp
private val GUTTER = CHANNEL_WIDTH + 16.dp
private val ROW_HEIGHT = 46.dp
private val RULER_HEIGHT = 24.dp

private const val HORIZON_SECONDS = 2 * 24 * 3600L

private const val DAY_SECONDS = 24 * 3600L

@Composable
fun GuideGrid(
  sourceId: String,
  scopeKey: String,
  items: LazyPagingItems<MediaItem>,
  archiveDays: Int,
  empty: @Composable () -> Unit,
  onPlay: (MediaItem) -> Unit,
  onCatchUp: (MediaItem, Long) -> Unit,
  onList: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val now = rememberNowSeconds(15_000)
  val rows = rememberGuideRows(sourceId)
  val state = rememberSaveable(scopeKey, saver = LazyListState.Saver) { LazyListState() }
  val focus = remember { FocusRequester() }
  val toolbarFocus = remember { FocusRequester() }

  val floor = remember(now, archiveDays) {
    floorTo(now - KEEP_PAST_SECONDS - archiveSeconds(archiveDays), GUIDE_STEP_SECONDS)
  }

  var row by rememberSaveable(scopeKey) { mutableStateOf(0) }
  var at by rememberSaveable(scopeKey) { mutableStateOf(System.currentTimeMillis() / 1000) }
  var span by rememberSaveable { mutableStateOf(GUIDE_SPAN_SECONDS) }
  var onToolbar by remember { mutableStateOf(false) }
  var start by rememberSaveable(scopeKey) {
    mutableStateOf(floorTo(System.currentTimeMillis() / 1000 - GUIDE_STEP_SECONDS, GUIDE_STEP_SECONDS))
  }
  val end = start + span

  val hasRows = items.itemCount > 0
  LaunchedEffect(onToolbar, hasRows) {
    if (!hasRows) return@LaunchedEffect
    runCatching { if (onToolbar) toolbarFocus.requestFocus() else focus.requestFocus() }
  }
  BackHandler(enabled = true) { if (onToolbar) onToolbar = false else onList() }

  LaunchedEffect(row) {
    val info = state.layoutInfo
    val target = info.visibleItemsInfo.firstOrNull { it.index == row }
    if (target == null) {
      state.animateScrollToItem(row)
      return@LaunchedEffect
    }
    val over = target.offset + target.size - info.viewportEndOffset
    if (target.offset < info.viewportStartOffset) {
      state.animateScrollBy((target.offset - info.viewportStartOffset).toFloat())
    } else if (over > 0) {
      state.animateScrollBy(over.toFloat())
    }
  }

  val focusedItem = if (items.itemCount > row) items[row] else null
  val focusedChannel = focusedItem?.guideChannelId ?: focusedItem?.epgChannelId
  val focusedSlots = focusedChannel?.let { rows.slot(it).value }?.let { layoutRow(it, start, end) }.orEmpty()
  val focusedProgramme = focusedSlots.getOrNull(slotIndexAt(focusedSlots, at))?.programme
  val archived = focusedItem?.hasArchive == true && archiveDays > 0

  fun stepTime(direction: Int) {
    val lo = maxOf(floor, at - STEP_WINDOW_SECONDS)
    val hi = minOf(now + HORIZON_SECONDS, at + STEP_WINDOW_SECONDS)
    val slots = focusedChannel?.let { rows.slot(it).value }
      ?.let { layoutRow(it, lo, hi) }
      .orEmpty()
    val next = if (slots.isEmpty()) {
      (at + direction * GUIDE_STEP_SECONDS).coerceAtLeast(floor)
    } else {
      val index = slotIndexAt(slots, at)
      val target = (if (index < 0) 0 else index + direction).coerceIn(0, slots.size - 1)
      val slot = slots[target]
      if (slot.programme == null) {
        (floorTo(at, GUIDE_STEP_SECONDS) + direction * GUIDE_STEP_SECONDS)
          .coerceIn(slot.start, slot.stop - 1)
      } else {
        slot.start + 1
      }
    }
    at = next
    start = scrolledWindow(start, next, floor, span)
  }

  fun goTo(target: Long) {
    val clamped = target.coerceIn(floor, now + HORIZON_SECONDS)
    at = clamped
    start = floorTo(clamped - GUIDE_STEP_SECONDS, GUIDE_STEP_SECONDS).coerceAtLeast(floor)
  }

  fun openFocused() {
    val item = focusedItem ?: return
    val programme = focusedProgramme
    if (archived && programme != null && programme.stop <= now) onCatchUp(item, programme.start)
    else onPlay(item)
  }

  if (!hasRows) {
    Box(modifier.fillMaxSize()) {
      if (items.loadState.refresh !is LoadState.Loading) empty()
    }
    return
  }

  Column(modifier.fillMaxSize()) {
    GuideHeader(
      item = focusedItem,
      programme = focusedProgramme,
      now = now,
      archived = archived,
      day = dayHeading(at),
      span = span,
      anchor = toolbarFocus,
      onLeave = { onToolbar = false },
      onDay = { days -> goTo(at + days * DAY_SECONDS) },
      onNow = { goTo(System.currentTimeMillis() / 1000) },
      onSpan = {
        val next = SPANS[(SPANS.indexOf(span).coerceAtLeast(0) + 1) % SPANS.size]
        span = next
        start = floorTo(at - GUIDE_STEP_SECONDS, GUIDE_STEP_SECONDS).coerceAtLeast(floor)
      },
    )
    Spacer(Modifier.height(KeelTvTheme.space.s3))

    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
      val timeline = maxWidth - GUTTER
      val perSecond = timeline / span.toFloat()
      val nowX = if (now in start until end) GUTTER + perSecond * (now - start).toFloat() else null

      Column(Modifier.fillMaxSize()) {
        Ruler(start, end, perSecond, now)

        Box(
          modifier = Modifier
            .fillMaxSize()
            .fadeFoot(40.dp, KeelTvTheme.colors.bgApp)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
              if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
              when (event.key) {
                Key.DirectionUp -> { if (row > 0) row -= 1 else onToolbar = true; true }
                Key.DirectionDown -> { if (row < items.itemCount - 1) row += 1; true }
                Key.DirectionLeft -> { stepTime(-1); true }
                Key.DirectionRight -> { stepTime(1); true }
                Key.MediaRewind -> { goTo(at - DAY_SECONDS); true }
                Key.MediaFastForward -> { goTo(at + DAY_SECONDS); true }
                Key.DirectionCenter, Key.Enter -> { openFocused(); true }
                else -> false
              }
            },
        ) {
          if (nowX != null) {
            Box(
              Modifier
                .offset(x = nowX)
                .width(NOW_LINE)
                .fillMaxHeight()
                .background(KeelTvTheme.colors.accent),
            )
          }
          LazyColumn(state = state) {
            items(items.itemCount) { index ->
              val item = items[index]
              val channelId = item?.guideChannelId ?: item?.epgChannelId
              LaunchedEffect(channelId, start, span) {
                rows.want(channelId, start, end + span)
              }
              val slot = remember(channelId, rows) {
                if (channelId == null) null else rows.slot(channelId)
              }
              val listing = remember(slot) { { slot?.value } }
              GuideRowView(
                item = item,
                channelId = channelId,
                programmes = listing,
                from = start,
                to = end,
                perSecond = perSecond,
                now = now,
                archive = item?.hasArchive == true && archiveDays > 0,
                focusedRow = index == row,
                focusAt = if (index == row && !onToolbar) at else null,
              )
            }
          }
        }
      }
    }
  }
}

private val NOW_LINE = 2.dp

private const val STEP_WINDOW_SECONDS = 6 * 3600L

private val SPANS = listOf(3600L, 2 * 3600L, 4 * 3600L)

private fun spanLabel(span: Long): String {
  val hours = span / 3600
  return if (hours == 1L) "1 hour" else "$hours hours"
}

@Composable
private fun GuideHeader(
  item: MediaItem?,
  programme: EpgProgramme?,
  now: Long,
  archived: Boolean,
  day: String,
  span: Long,
  anchor: FocusRequester,
  onLeave: () -> Unit,
  onDay: (Int) -> Unit,
  onNow: () -> Unit,
  onSpan: () -> Unit,
) {
  val colors = KeelTvTheme.colors
  val past = programme != null && programme.stop <= now
  val live = programme != null && programme.start <= now && programme.stop > now
  val name = item?.let { it.title.ifEmpty { it.name } }
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s6),
    verticalAlignment = Alignment.Top,
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(
        text = programme?.title ?: name.orEmpty(),
        style = KeelTvTheme.type.section,
        color = colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (programme != null) {
          Text(
            text = "${dayLabel(programme.start)} ${clock(programme.start)} \u2013 ${clock(programme.stop)}",
            style = KeelTvTheme.type.small,
            color = colors.textSecondary,
            maxLines = 1,
          )
          if (live) {
            Text(timeLeft(programme.stop - now), style = KeelTvTheme.type.small, color = colors.textTertiary, maxLines = 1)
          }
          if (archived && past) {
            Text("Watch from start", style = KeelTvTheme.type.small, color = colors.accent, maxLines = 1)
          }
        }
        if (name != null && programme != null) {
          Text(
            text = name,
            style = KeelTvTheme.type.small,
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Text(
        text = programme?.description
          ?: if (programme == null) "This channel has no guide." else "No synopsis.",
        style = KeelTvTheme.type.small.copy(lineHeight = 19.sp),
        color = colors.textSecondary,
        minLines = 2,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 520.dp),
      )
    }

    Row(
      modifier = Modifier
        .focusGroup()
        .onPreviewKeyEvent { event ->
          if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionDown) {
            return@onPreviewKeyEvent false
          }
          onLeave()
          true
        },
      horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier = Modifier
          .height(34.dp)
          .background(colors.bgRaised, RoundedCornerShape(KeelTvTheme.radii.control))
          .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        DayArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous day") { onDay(-1) }
        GuideChip(day, Modifier.focusRequester(anchor), onClick = onNow)
        DayArrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next day") { onDay(1) }
      }
      Box(
        Modifier
          .height(34.dp)
          .background(colors.bgRaised, RoundedCornerShape(KeelTvTheme.radii.control))
          .padding(3.dp),
      ) {
        GuideChip(spanLabel(span), onClick = onSpan)
      }
    }
  }
}

@Composable
private fun DayArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
  FocusRow(
    onClick = onClick,
    shape = RoundedCornerShape(KeelTvTheme.radii.row),
    modifier = Modifier.size(28.dp),
  ) { focused ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (focused) KeelTvTheme.colors.onFocus else KeelTvTheme.colors.textSecondary,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
private fun GuideChip(
  text: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  FocusRow(
    onClick = onClick,
    shape = RoundedCornerShape(KeelTvTheme.radii.row),
    modifier = modifier.height(28.dp),
  ) { focused ->
    Text(
      text = text,
      style = KeelTvTheme.type.small.copy(fontWeight = FontWeight.Medium),
      color = if (focused) KeelTvTheme.colors.onFocus else KeelTvTheme.colors.textPrimary,
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 10.dp),
    )
  }
}

@Composable
private fun Ruler(from: Long, to: Long, perSecond: Dp, now: Long) {
  val colors = KeelTvTheme.colors
  Box(Modifier.fillMaxWidth().height(RULER_HEIGHT)) {
    for (tick in ticks(from, to - GUIDE_STEP_SECONDS / 2)) {
      val midnight = isMidnight(tick)
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
        modifier = Modifier
          .offset(x = GUTTER + perSecond * (tick - from).toFloat())
          .align(Alignment.CenterStart),
      ) {
        Text(text = clock(tick), style = KeelTvTheme.type.micro, color = colors.textTertiary, maxLines = 1)
        if (midnight) {
          Text(
            text = dayMark(tick),
            style = KeelTvTheme.type.label,
            color = colors.accent,
            maxLines = 1,
            modifier = Modifier
              .background(colors.accentDim, RoundedCornerShape(KeelTvTheme.radii.chip))
              .padding(horizontal = 4.dp),
          )
        }
      }
    }
    if (now in from until to) {
      Layout(
        content = {
          Text(
            text = clock(now),
            style = KeelTvTheme.type.label,
            color = colors.onAccent,
            maxLines = 1,
            modifier = Modifier
              .background(colors.accent, RoundedCornerShape(5.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp),
          )
        },
        modifier = Modifier.fillMaxSize(),
      ) { measurables, constraints ->
        val tab = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val x = (GUTTER + perSecond * (now - from).toFloat() + NOW_LINE / 2).roundToPx() - tab.width / 2
        layout(constraints.maxWidth, constraints.maxHeight) {
          tab.place(x.coerceIn(0, constraints.maxWidth - tab.width), (constraints.maxHeight - tab.height) / 2)
        }
      }
    }
  }
}

private fun isMidnight(epochSeconds: Long): Boolean =
  Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalTime() == LocalTime.MIDNIGHT

@Composable
private fun GuideRowView(
  item: MediaItem?,
  channelId: String?,
  programmes: () -> List<EpgProgramme>?,
  from: Long,
  to: Long,
  perSecond: Dp,
  now: Long,
  archive: Boolean,
  focusedRow: Boolean,
  focusAt: Long?,
) {
  val colors = KeelTvTheme.colors
  Row(Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
    Row(
      modifier = Modifier
        .width(CHANNEL_WIDTH)
        .fillMaxHeight()
        .padding(vertical = 2.dp)
        .background(if (focusedRow) FOCUSED_ROW else Color.Transparent, RoundedCornerShape(KeelTvTheme.radii.card))
        .padding(horizontal = KeelTvTheme.space.s2),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (item != null) {
        ChannelLogo(item, width = 44.dp, height = 30.dp)
        Spacer(Modifier.width(10.dp))
        Text(
          text = item.title.ifEmpty { item.name },
          style = KeelTvTheme.type.small.copy(
            lineHeight = 16.sp,
            fontWeight = if (focusedRow) FontWeight.SemiBold else FontWeight.Medium,
          ),
          color = if (focusedRow) colors.textPrimary else colors.textSecondary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (archive) {
          Spacer(Modifier.width(KeelTvTheme.space.s2))
          Icon(
            imageVector = Icons.Outlined.Replay,
            contentDescription = "Catch up",
            tint = colors.textTertiary,
            modifier = Modifier.size(13.dp),
          )
        }
      }
    }
    Spacer(Modifier.width(GUTTER - CHANNEL_WIDTH))

    Box(Modifier.fillMaxHeight().weight(1f)) {
      val listing = programmes()
      if (listing == null) {
        Text(
          text = if (item == null || channelId != null) "…" else "No guide",
          style = KeelTvTheme.type.small,
          color = colors.textTertiary,
          modifier = Modifier.align(Alignment.CenterStart).padding(start = KeelTvTheme.space.s3),
        )
      } else {
        for (slot in layoutRow(listing, from, to)) {
          GuideCell(
            slot = slot,
            x = perSecond * (slot.start - from).toFloat(),
            width = perSecond * (slot.stop - slot.start).toFloat(),
            perSecond = perSecond,
            from = from,
            now = now,
            archive = archive,
            focused = focusAt != null && focusAt >= slot.start && focusAt < slot.stop,
          )
        }
      }
    }
  }
}

private val FOCUSED_ROW = Color(0x0FF2F2F4)

private val PAST = Color(0x09F2F2F4)
private val LATER = Color(0xFF1A1B20)
private val PAST_TIME = Color(0xFF6C6F78)

@Composable
private fun GuideCell(
  slot: GuideSlot,
  x: Dp,
  width: Dp,
  perSecond: Dp,
  from: Long,
  now: Long,
  archive: Boolean,
  focused: Boolean,
) {
  val colors = KeelTvTheme.colors
  val programme = slot.programme
  val past = slot.stop <= now
  val live = slot.start <= now && slot.stop > now

  val background = when {
    programme == null -> Color.Transparent
    focused -> colors.focus
    live -> colors.bgHover
    past -> PAST
    else -> LATER
  }
  val titleColour = when {
    focused -> colors.onFocus
    past -> colors.textTertiary
    live -> colors.textPrimary
    else -> colors.textSecondary
  }
  val timeColour = when {
    focused -> colors.onFocusMuted
    past -> PAST_TIME
    else -> colors.textTertiary
  }
  val line = if (live) perSecond * (now - slot.start).toFloat() else null
  val accent = colors.accent

  val radius = KeelTvTheme.radii.row
  Box(
    modifier = Modifier
      .offset(x = x)
      .width((width - 3.dp).coerceAtLeast(1.dp))
      .fillMaxHeight()
      .padding(vertical = if (focused) 0.dp else 2.dp)
      .drawBehind {
        drawRoundRect(background, cornerRadius = CornerRadius(radius.toPx()))
        if (line != null) {
          drawRect(accent, topLeft = Offset(line.toPx(), 0f), size = Size(NOW_LINE.toPx(), size.height))
        }
      },
    contentAlignment = Alignment.CenterStart,
  ) {
    if (programme != null && width > 38.dp) {
      Column(
        Modifier.padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
      ) {
        Text(
          text = programme.title,
          style = KeelTvTheme.type.small.copy(
            fontWeight = when {
              focused -> FontWeight.SemiBold
              live -> FontWeight.Medium
              else -> FontWeight.Normal
            },
          ),
          color = titleColour,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (width > 72.dp) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = if (programme.start < from) "Since ${clock(programme.start)}" else clock(programme.start),
              style = KeelTvTheme.type.label.copy(fontWeight = FontWeight.Normal),
              color = timeColour,
              maxLines = 1,
            )
            if (past && archive && width > 168.dp) {
              Spacer(Modifier.width(KeelTvTheme.space.s2))
              Text(
                text = "Catch up",
                style = KeelTvTheme.type.label.copy(fontWeight = FontWeight.Normal),
                color = if (focused) colors.onFocusMuted else colors.textTertiary,
                maxLines = 1,
              )
            }
          }
        }
      }
    }
  }
}

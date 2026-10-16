package com.keeltv.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.keeltv.app.ui.components.timeLeft
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.NowNext
import com.keeltv.app.data.repo.ChannelWindow
import com.keeltv.app.data.repo.EpgRepository
import com.keeltv.app.playback.formatDuration
import com.keeltv.app.ui.components.FocusRow
import com.keeltv.app.ui.components.clock
import com.keeltv.app.ui.components.fractionThrough
import com.keeltv.app.ui.theme.KeelTvTheme

@Composable
fun InfoBanner(
  item: MediaItem,
  number: Int,
  categoryName: String,
  nowNext: NowNext?,
  clockSeconds: Long,
  paused: Boolean,
  behindMs: Long,
  notice: String? = null,
  archiveHint: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val colors = KeelTvTheme.colors
  val space = KeelTvTheme.space

  Box(modifier.fillMaxSize().background(risingScrim())) {
    Box(
      Modifier
        .align(Alignment.TopEnd)
        .fillMaxWidth(0.34f)
        .fillMaxHeight(0.26f)
        .cornerShade(Alignment.TopEnd),
    )
    Row(
      modifier = Modifier.align(Alignment.TopEnd).padding(top = 30.dp, end = 56.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LiveMark(paused = paused, behindMs = behindMs)
      Text(
        text = clock(clockSeconds),
        style = KeelTvTheme.type.sub.copy(fontSize = 20.sp, lineHeight = 26.sp),
        color = colors.textPrimary,
      )
    }

    Row(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .fillMaxWidth()
        .padding(start = 56.dp, end = 56.dp, bottom = 38.dp),
      horizontalArrangement = Arrangement.spacedBy(22.dp),
      verticalAlignment = Alignment.Bottom,
    ) {
      ChannelLogo(item.logo, item.title.ifBlank { item.name }, size = 60.dp, modifier = Modifier.padding(bottom = 2.dp))

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(space.s2),
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(space.s3),
          verticalAlignment = Alignment.Bottom,
        ) {
          Text(
            text = number.toString(),
            style = KeelTvTheme.type.body,
            color = colors.textTertiary,
            modifier = Modifier.alignByBaseline(),
          )
          Text(
            text = item.title.ifBlank { item.name },
            style = KeelTvTheme.type.title.copy(lineHeight = 28.sp),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
          )
          Text(
            text = categoryName,
            style = KeelTvTheme.type.small,
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alignByBaseline(),
          )
        }

        val now = nowNext?.now
        if (now == null) {
          Text("This channel has no guide.", style = KeelTvTheme.type.body, color = colors.textTertiary)
        } else {
          Text(
            text = now.title,
            style = KeelTvTheme.type.sub.copy(fontSize = 18.sp, lineHeight = 24.sp),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Row(
            modifier = Modifier.widthIn(max = 520.dp),
            horizontalArrangement = Arrangement.spacedBy(space.s3),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(clock(now.start), style = KeelTvTheme.type.micro, color = colors.textTertiary)
            ProgressRail(
              fraction = fractionThrough(now.start, now.stop, clockSeconds),
              modifier = Modifier.weight(1f),
            )
            Text(clock(now.stop), style = KeelTvTheme.type.micro, color = colors.textTertiary)
            Text(
              text = timeLeft(now.stop - clockSeconds),
              style = KeelTvTheme.type.small,
              color = colors.textSecondary,
              modifier = Modifier.padding(start = space.s1),
            )
          }
        }
        nowNext?.next?.let {
          Row(horizontalArrangement = Arrangement.spacedBy(space.s3)) {
            Text("Next", style = KeelTvTheme.type.small, color = colors.textTertiary)
            Text(clock(it.start), style = KeelTvTheme.type.small, color = colors.textTertiary)
            Text(
              text = it.title,
              style = KeelTvTheme.type.small,
              color = colors.textSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        if (notice != null) {
          Text(notice, style = KeelTvTheme.type.small, color = colors.danger)
        }
      }

      Column(
        modifier = Modifier.padding(bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (archiveHint) KeyHint(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "From the start")
        KeyHint(Icons.Outlined.KeyboardArrowUp, "Channels")
      }
    }
  }
}

@Composable
private fun LiveMark(paused: Boolean, behindMs: Long) {
  val colors = KeelTvTheme.colors
  val behind = behindMs >= 1_000
  Row(
    modifier = Modifier
      .height(24.dp)
      .background(MARK, RoundedCornerShape(7.dp))
      .padding(horizontal = 9.dp),
    horizontalArrangement = Arrangement.spacedBy(7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    when {
      paused -> Icon(Icons.Filled.Pause, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(13.dp))
      behind -> Unit
      else -> Box(Modifier.size(7.dp).background(colors.accent, CircleShape))
    }
    val words = when {
      paused && behind -> "Paused, ${formatDuration(behindMs)} behind"
      paused -> "Paused"
      behind -> "${formatDuration(behindMs)} behind live"
      else -> "Live"
    }
    Text(words, style = KeelTvTheme.type.micro.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
    if (behind) {
      Text("Hold right for live", style = KeelTvTheme.type.micro, color = colors.textTertiary)
    }
  }
}

private val MARK = Color(0x9E0F0F11)

@Composable
private fun KeyHint(icon: ImageVector, label: String) {
  val colors = KeelTvTheme.colors
  Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(
      Modifier
        .size(22.dp)
        .border(1.5.dp, KEY_EDGE, RoundedCornerShape(6.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
    }
    Text(label, style = KeelTvTheme.type.micro.copy(fontWeight = FontWeight.Normal), color = colors.textSecondary)
  }
}

private val KEY_EDGE = Color(0x66F2F2F4)

@Composable
fun NumberEntry(digits: String, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .background(KeelTvTheme.colors.bgOverlay)
      .padding(horizontal = KeelTvTheme.space.s7, vertical = KeelTvTheme.space.s4),
  ) {
    Text(digits, style = KeelTvTheme.type.display, color = KeelTvTheme.colors.textPrimary)
  }
}

@Composable
fun ChannelListPanel(
  categoryName: String,
  window: ChannelWindow?,
  currentIndex: Int,
  sourceId: String,
  epg: EpgRepository,
  onPick: (Int, MediaItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val focus = remember { FocusRequester() }
  var landed by remember(currentIndex) { mutableStateOf(false) }

  LaunchedEffect(window, currentIndex) {
    if (window == null) return@LaunchedEffect
    listState.scrollToItem(currentIndex.coerceIn(0, (window.size - 1).coerceAtLeast(0)))
  }
  LaunchedEffect(landed) {
    if (landed) runCatching { focus.requestFocus() }
  }

  val app = KeelTvTheme.colors.bgApp
  Column(
    modifier = modifier
      .width(PANEL_WIDTH)
      .fillMaxHeight()
      .background(
        Brush.horizontalGradient(
          0f to app,
          0.72f to app,
          1f to app.copy(alpha = 0f),
        ),
      )
      .padding(start = 28.dp, end = 56.dp, top = 22.dp, bottom = 22.dp),
  ) {
    Text(
      text = categoryName,
      style = KeelTvTheme.type.small,
      color = KeelTvTheme.colors.textTertiary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(start = KeelTvTheme.space.s2, bottom = KeelTvTheme.space.s3),
    )
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s1),
    ) {
      items(window?.size ?: 0) { index ->
        var channel by remember(index) { mutableStateOf<MediaItem?>(null) }
        LaunchedEffect(window, index) {
          channel = window?.at(index)
          if (index == currentIndex && channel != null) landed = true
        }
        val row = channel
        if (row == null) {
          Spacer(Modifier.fillMaxWidth().height(ROW_HEIGHT))
        } else {
          ChannelRow(
            channel = row,
            number = index + 1,
            selected = index == currentIndex,
            sourceId = sourceId,
            epg = epg,
            onPick = { onPick(index, row) },
            modifier = if (index == currentIndex) Modifier.focusRequester(focus) else Modifier,
          )
        }
      }
    }
  }
}

internal val PANEL_WIDTH = 372.dp

private val ROW_HEIGHT = 42.dp

@Composable
private fun ChannelRow(
  channel: MediaItem,
  number: Int,
  selected: Boolean,
  sourceId: String,
  epg: EpgRepository,
  onPick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var nowNext by remember(channel.id) { mutableStateOf<NowNext?>(null) }
  val guideId = channel.guideChannelId ?: channel.epgChannelId
  val at = remember(channel.id) { System.currentTimeMillis() / 1000 }

  LaunchedEffect(channel.id, guideId) {
    if (guideId != null) nowNext = runCatching { epg.nowNext(sourceId, guideId) }.getOrNull()
  }

  FocusRow(
    onClick = onPick,
    selected = selected,
    shape = RoundedCornerShape(KeelTvTheme.radii.card),
    modifier = modifier.fillMaxWidth().height(ROW_HEIGHT),
  ) { focused ->
    val colors = KeelTvTheme.colors
    Row(
      modifier = Modifier.fillMaxSize().padding(horizontal = KeelTvTheme.space.s2),
      horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = number.toString(),
        style = KeelTvTheme.type.micro,
        color = when {
          focused -> colors.onFocusMuted
          selected -> colors.accent
          else -> colors.textTertiary
        },
        maxLines = 1,
        textAlign = TextAlign.End,
        modifier = Modifier.width(38.dp),
      )
      ChannelLogo(channel.logo, channel.title.ifBlank { channel.name }, size = 26.dp)
      Column(Modifier.weight(1f)) {
        Text(
          text = channel.title.ifBlank { channel.name },
          style = KeelTvTheme.type.small.copy(fontWeight = FontWeight.Medium),
          color = when {
            focused -> colors.onFocus
            selected -> colors.accent
            else -> colors.textPrimary
          },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val now = nowNext?.now
        Text(
          text = now?.title ?: " ",
          style = KeelTvTheme.type.micro.copy(fontWeight = FontWeight.Normal),
          color = if (focused) colors.onFocusMuted else colors.textTertiary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (now != null) {
          Spacer(Modifier.height(3.dp))
          val fraction = fractionThrough(now.start, now.stop, at)
          Box(
            Modifier
              .fillMaxWidth(0.5f)
              .height(3.dp)
              .drawBehind {
                val round = CornerRadius(size.height / 2f)
                drawRoundRect(if (focused) colors.onFocus else colors.lineBright, cornerRadius = round)
                drawRoundRect(colors.accent, size = Size(size.width * fraction, size.height), cornerRadius = round)
              },
          )
        }
      }
    }
  }
}

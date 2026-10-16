package com.keeltv.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Forward30
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.keeltv.app.playback.formatDuration
import com.keeltv.app.playback.scrubStepMs
import com.keeltv.app.ui.components.TvButton
import com.keeltv.app.ui.components.clock
import com.keeltv.app.ui.theme.KeelTvTheme

@Composable
fun VodControls(
  title: String,
  subtitle: String?,
  context: String?,
  notice: String?,
  positionMs: Long,
  durationMs: Long,
  bufferedMs: Long,
  scrubbingTo: Long?,
  playing: Boolean,
  nextLabel: String?,
  seekFocus: FocusRequester,
  onScrub: (Long) -> Unit,
  onTogglePlay: () -> Unit,
  onTracks: () -> Unit,
  onNext: () -> Unit,
  modifier: Modifier = Modifier,
  onGoLive: (() -> Unit)? = null,
  timeAt: (Long) -> String = ::formatDuration,
) {
  val colors = KeelTvTheme.colors
  val space = KeelTvTheme.space
  val shown = scrubbingTo ?: positionMs

  Box(modifier.fillMaxSize().background(risingScrim())) {
    if (context != null) {
      Box(
        Modifier
          .align(Alignment.TopStart)
          .fillMaxWidth(0.4f)
          .fillMaxHeight(0.26f)
          .cornerShade(Alignment.TopStart),
      )
      Row(
        modifier = Modifier.align(Alignment.TopStart).padding(start = 56.dp, top = 30.dp),
        horizontalArrangement = Arrangement.spacedBy(space.s2),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Outlined.Replay, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
        Text(context, style = KeelTvTheme.type.small, color = colors.textSecondary, maxLines = 1)
      }
    }

    Column(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .fillMaxWidth()
        .padding(start = 56.dp, end = 56.dp, bottom = 22.dp),
    ) {
      Text(
        text = title,
        style = KeelTvTheme.type.title,
        color = colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (subtitle != null) {
        Text(
          subtitle,
          style = KeelTvTheme.type.small,
          color = colors.textSecondary,
          maxLines = 1,
          modifier = Modifier.padding(top = space.s1),
        )
      }
      if (notice != null) {
        Text(notice, style = KeelTvTheme.type.small, color = colors.danger, modifier = Modifier.padding(top = space.s1))
      }

      SeekBar(
        positionMs = shown,
        durationMs = durationMs,
        bufferedMs = bufferedMs,
        scrubbing = scrubbingTo != null,
        label = timeAt(shown),
        focusRequester = seekFocus,
        onScrub = onScrub,
        onTogglePlay = onTogglePlay,
        modifier = Modifier.padding(top = 30.dp),
      )

      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(timeAt(shown), style = KeelTvTheme.type.small, color = colors.textSecondary, modifier = Modifier.weight(1f))
        if (durationMs > 0) {
          val left = (durationMs - shown).coerceAtLeast(0)
          Text("${formatDuration(left)} left", style = KeelTvTheme.type.small, color = colors.textSecondary)
          Text(
            text = "Ends at ${clock(System.currentTimeMillis() / 1000 + left / 1000)}",
            style = KeelTvTheme.type.small,
            color = colors.textTertiary,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(top = space.s2),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        GlyphButton(
          if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          if (playing) "Pause" else "Play",
          onTogglePlay,
        )
        GlyphButton(Icons.Outlined.Replay10, "Back ten seconds", onClick = { onScrub(-10_000) })
        GlyphButton(Icons.Outlined.Forward30, "Forward thirty seconds", onClick = { onScrub(30_000) })
        Box(Modifier.weight(1f))
        if (onGoLive != null) TvButton("Watch live", onGoLive, icon = Icons.Outlined.LiveTv)
        if (nextLabel != null) TvButton("Next episode", onNext, icon = Icons.Outlined.SkipNext)
        TvButton("Audio and subtitles", onTracks, icon = Icons.Outlined.ClosedCaption)
      }
    }
  }
}

@Composable
private fun SeekBar(
  positionMs: Long,
  durationMs: Long,
  bufferedMs: Long,
  scrubbing: Boolean,
  label: String,
  focusRequester: FocusRequester,
  onScrub: (Long) -> Unit,
  onTogglePlay: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = KeelTvTheme.colors
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
  val buffered = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
  val accent = colors.accent
  val knob = colors.focus

  Layout(
    content = {
      Box(
        Modifier
          .fillMaxWidth()
          .height(20.dp)
          .focusRequester(focusRequester)
          .focusable(interactionSource = interaction)
          .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            val repeat = event.nativeKeyEvent.repeatCount
            when (event.key) {
              Key.DirectionLeft -> { onScrub(-scrubStepMs(repeat)); true }
              Key.DirectionRight -> { onScrub(scrubStepMs(repeat)); true }
              Key.DirectionCenter, Key.Enter -> { onTogglePlay(); true }
              else -> false
            }
          }
          .drawBehind {
            val track = 4.dp.toPx()
            val top = (size.height - track) / 2f
            val round = CornerRadius(track / 2f)
            drawRoundRect(PLAYED_TRACK, Offset(0f, top), Size(size.width, track), round)
            drawRoundRect(LOADED, Offset(0f, top), Size(size.width * buffered, track), round)
            drawRoundRect(accent, Offset(0f, top), Size(size.width * fraction, track), round)
            if (focused || scrubbing) {
              val r = 9.dp.toPx()
              drawCircle(SHADOW, r + 3.dp.toPx(), Offset(size.width * fraction, size.height / 2f + 2.dp.toPx()))
              drawCircle(knob, r, Offset(size.width * fraction, size.height / 2f))
            }
          },
      )
      if (scrubbing) {
        Text(
          text = label,
          style = KeelTvTheme.type.small.copy(fontWeight = FontWeight.SemiBold),
          color = colors.onFocus,
          maxLines = 1,
          modifier = Modifier
            .background(knob, RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        )
      }
    },
    modifier = modifier.fillMaxWidth(),
  ) { measurables, constraints ->
    val bar = measurables[0].measure(constraints)
    val bubble = measurables.getOrNull(1)?.measure(constraints.copy(minWidth = 0))
    layout(bar.width, bar.height) {
      bar.place(0, 0)
      if (bubble != null) {
        val x = (bar.width * fraction).toInt() - bubble.width / 2
        bubble.place(x.coerceIn(0, bar.width - bubble.width), -bubble.height - 6.dp.roundToPx())
      }
    }
  }
}

private val PLAYED_TRACK = Color(0x33F2F2F4)
private val LOADED = Color(0x4DF2F2F4)
private val SHADOW = Color(0x66000000)

@Composable
fun GlyphButton(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  active: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val colors = KeelTvTheme.colors
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()

  Box(
    modifier = modifier
      .size(40.dp)
      .background(if (focused) colors.focus else Color.Transparent, CircleShape)
      .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = when {
        focused -> colors.onFocus
        active -> colors.accent
        else -> colors.textSecondary
      },
      modifier = Modifier.size(22.dp),
    )
  }
}

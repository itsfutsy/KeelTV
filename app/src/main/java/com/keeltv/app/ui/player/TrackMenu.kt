package com.keeltv.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.keeltv.app.playback.CaptionAppearance
import com.keeltv.app.playback.PlayerTrack
import com.keeltv.app.playback.TrackKind
import com.keeltv.app.playback.trackDetail
import com.keeltv.app.playback.trackLabel
import com.keeltv.app.ui.components.FocusRow
import com.keeltv.app.ui.theme.KeelTvTheme

private val SIZES = listOf(75, 100, 125, 150, 200)
private val BACKGROUNDS = listOf(0f, 0.4f, 0.6f, 0.9f)

@Composable
fun TrackMenu(
  tracks: List<PlayerTrack>,
  audioId: String?,
  subtitleId: String?,
  appearance: CaptionAppearance,
  onSelect: (TrackKind, String?) -> Unit,
  onAppearance: (CaptionAppearance) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = KeelTvTheme.colors
  val space = KeelTvTheme.space
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

  val audio = tracks.filter { it.kind == TrackKind.AUDIO }
  val subtitles = tracks.filter { it.kind == TrackKind.SUBTITLE }

  LazyColumn(
    modifier = modifier
      .width(420.dp)
      .fillMaxHeight()
      .background(KeelTvTheme.colors.bgApp.copy(alpha = 0.97f))
      .padding(start = KeelTvTheme.space.s4, end = 28.dp, top = 22.dp, bottom = 22.dp),
    verticalArrangement = Arrangement.spacedBy(space.s1),
  ) {
    item {
      Text(
        "Audio and subtitles",
        style = KeelTvTheme.type.section,
        color = colors.textPrimary,
        modifier = Modifier.padding(bottom = space.s4),
      )
    }

    item { SectionLabel("Audio") }
    if (audio.isEmpty()) {
      item { Hint("Source audio") }
    } else {
      items(audio.size) { i ->
        val track = audio[i]
        val checked = track.id == audioId
        CheckRow(
          label = trackLabel(track),
          detail = trackDetail(track),
          checked = checked,
          enabled = track.supported,
          onClick = { onSelect(TrackKind.AUDIO, track.id) },
          modifier = if (i == 0) Modifier.focusRequester(focus) else Modifier,
        )
      }
    }

    item { SectionLabel("Captions") }
    item {
      CheckRow(
        label = "Off",
        detail = "",
        checked = subtitleId == null,
        onClick = { onSelect(TrackKind.SUBTITLE, null) },
        modifier = if (audio.isEmpty()) Modifier.focusRequester(focus) else Modifier,
      )
    }
    if (subtitles.isEmpty()) {
      item { Hint("No embedded captions detected.") }
    } else {
      items(subtitles.size) { i ->
        val track = subtitles[i]
        CheckRow(
          label = trackLabel(track),
          detail = trackDetail(track),
          checked = track.id == subtitleId,
          enabled = track.supported,
          onClick = { onSelect(TrackKind.SUBTITLE, track.id) },
        )
      }
    }

    item { SectionLabel("Text appearance") }
    item {
      StepRow("Text size", "${appearance.size}%") {
        onAppearance(appearance.copy(size = SIZES.next(appearance.size)))
      }
    }
    item {
      StepRow("Text color", if (appearance.color == CaptionAppearance.YELLOW) "Yellow" else "White") {
        val next = if (appearance.color == CaptionAppearance.WHITE) CaptionAppearance.YELLOW else CaptionAppearance.WHITE
        onAppearance(appearance.copy(color = next))
      }
    }
    item {
      StepRow("Background", "${Math.round(appearance.background * 100)}%") {
        onAppearance(appearance.copy(background = BACKGROUNDS.nextStep(appearance.background)))
      }
    }
    item {
      StepRow("Reset appearance", "") { onAppearance(CaptionAppearance()) }
    }
  }
}

private fun List<Int>.next(current: Int): Int {
  val at = indexOfFirst { it >= current }
  return if (at < 0 || at == lastIndex) first() else this[at + 1]
}

private fun List<Float>.nextStep(current: Float): Float {
  val at = indexOfFirst { it >= current - 0.001f }
  return if (at < 0 || at == lastIndex) first() else this[at + 1]
}

@Composable
private fun SectionLabel(text: String) {
  Text(
    text = text,
    style = KeelTvTheme.type.small,
    color = KeelTvTheme.colors.textTertiary,
    modifier = Modifier.padding(top = KeelTvTheme.space.s5, bottom = KeelTvTheme.space.s2),
  )
}

@Composable
private fun Hint(text: String) {
  Text(
    text = text,
    style = KeelTvTheme.type.small,
    color = KeelTvTheme.colors.textTertiary,
    modifier = Modifier.padding(horizontal = KeelTvTheme.space.s3, vertical = KeelTvTheme.space.s2),
  )
}

@Composable
private fun CheckRow(
  label: String,
  detail: String,
  checked: Boolean,
  enabled: Boolean = true,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  FocusRow(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) { focused ->
    Row(
      modifier = Modifier.fillMaxWidth().padding(KeelTvTheme.space.s3),
      horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        if (checked) {
          Icon(
            Icons.Outlined.Check,
            contentDescription = null,
            tint = if (focused) KeelTvTheme.colors.onFocus else KeelTvTheme.colors.accent,
            modifier = Modifier.size(18.dp),
          )
        }
      }
      Column(Modifier.weight(1f)) {
        Text(
          text = label,
          style = KeelTvTheme.type.body,
          color = when {
            !enabled -> KeelTvTheme.colors.textDisabled
            focused -> KeelTvTheme.colors.onFocus
            checked -> KeelTvTheme.colors.accent
            else -> KeelTvTheme.colors.textSecondary
          },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (detail.isNotEmpty()) {
          Text(
            text = detail,
            style = KeelTvTheme.type.micro,
            color = if (focused) KeelTvTheme.colors.onFocusMuted else KeelTvTheme.colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun StepRow(label: String, value: String, onClick: () -> Unit) {
  FocusRow(onClick = onClick, modifier = Modifier.fillMaxWidth()) { focused ->
    Row(
      modifier = Modifier.fillMaxWidth().padding(KeelTvTheme.space.s3),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        style = KeelTvTheme.type.body,
        color = if (focused) KeelTvTheme.colors.onFocus else KeelTvTheme.colors.textSecondary,
        modifier = Modifier.weight(1f),
      )
      Text(value, style = KeelTvTheme.type.small, color = if (focused) KeelTvTheme.colors.onFocusMuted else KeelTvTheme.colors.accent)
    }
  }
}

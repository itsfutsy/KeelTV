package com.keeltv.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.NowNext
import com.keeltv.app.ui.components.FocusRow
import com.keeltv.app.ui.components.clock
import com.keeltv.app.ui.components.dayLabel
import com.keeltv.app.ui.components.fractionThrough
import com.keeltv.app.ui.components.timeLeft
import com.keeltv.app.ui.theme.KeelTvTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Tv

@Composable
fun ChannelRow(
  item: MediaItem,
  number: Int,
  nowNext: () -> NowNext?,
  now: Long,
  guided: Boolean,
  favourite: Boolean,
  language: String?,
  onPlay: () -> Unit,
  onMenu: () -> Unit,
  modifier: Modifier = Modifier,
  variants: List<String> = emptyList(),
  variantAt: Int = 0,
  onCycleVariant: (() -> Unit)? = null,
) {
  val colors = KeelTvTheme.colors
  val space = KeelTvTheme.space
  val name = item.title.ifEmpty { item.name }
  val tags = remember(name, language, variants, variantAt) {
    buildList {
      language?.let { add(it) }
      val quality = variants.getOrNull(variantAt).orEmpty()
      if (quality.length > 1 && !name.contains(quality, ignoreCase = true)) add(quality)
      if (variants.size > 1) {
        val more = variants.size - 1
        add(if (more == 1) "+1 feed" else "+$more feeds")
      }
    }.joinToString(TAG_GAP)
  }

  FocusRow(
    onClick = onPlay,
    onLongClick = onMenu,
    modifier = modifier
      .fillMaxWidth()
      .height(KeelTvTheme.dimens.channelRowHeight)
      .onPreviewKeyEvent { event ->
        if (onCycleVariant == null || variants.size < 2) return@onPreviewKeyEvent false
        if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionRight) {
          return@onPreviewKeyEvent false
        }
        onCycleVariant()
        true
      },
    shape = RoundedCornerShape(KeelTvTheme.radii.card),
  ) { focused ->
    val lit = remember { mutableStateOf(false) }
    SideEffect { lit.value = focused }
    Row(
      modifier = Modifier.fillMaxSize().padding(end = space.s3),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      BasicText(
        text = number.toString(),
        style = KeelTvTheme.type.small.copy(textAlign = TextAlign.End),
        color = { if (lit.value) colors.onFocusMuted else colors.textTertiary },
        maxLines = 1,
        modifier = Modifier.width(38.dp),
      )
      ChannelLogo(
        item,
        width = 52.dp,
        height = 34.dp,
        modifier = Modifier.padding(start = space.s3, end = space.s3),
      )

      Column(if (guided) Modifier.width(NAME_WIDTH) else Modifier.weight(1f)) {
        BasicText(
          text = name,
          style = KeelTvTheme.type.control.copy(fontSize = 15.sp, lineHeight = 20.sp),
          color = { if (lit.value) colors.onFocus else colors.textPrimary },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (favourite) {
            Icon(
              imageVector = Icons.Filled.Star,
              contentDescription = "Favourite",
              tint = colors.accent,
              modifier = Modifier.padding(end = 6.dp).size(12.dp),
            )
          }
          if (tags.isNotEmpty()) {
            BasicText(
              text = tags,
              style = KeelTvTheme.type.micro,
              color = { if (lit.value) colors.onFocusMuted else colors.textTertiary },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
          }
          if (item.hasArchive) {
            Icon(
              imageVector = Icons.Outlined.Replay,
              contentDescription = "Catch up",
              tint = if (focused) colors.onFocusMuted else colors.textTertiary,
              modifier = Modifier.padding(start = if (tags.isEmpty() && !favourite) 0.dp else space.s2).size(13.dp),
            )
          }
        }
      }

      if (guided) {
        Column(Modifier.weight(1f).padding(start = space.s3)) {
          NowNextLines(nowNext = nowNext, now = now, lit = lit)
        }
      }
    }
  }
}

private val NAME_WIDTH = 180.dp

private const val TAG_GAP = "\u2002\u2009"

@Composable
private fun NowNextLines(nowNext: () -> NowNext?, now: Long, lit: State<Boolean>) {
  val colors = KeelTvTheme.colors
  val listing = nowNext()
  val current = listing?.now
  val next = listing?.next
  if (listing != null && current == null && next == null) {
    if (lit.value) {
      Text(
        text = "No listings for this channel",
        style = KeelTvTheme.type.small,
        color = colors.onFocusMuted,
        maxLines = 1,
      )
    }
    return
  }
  if (current == null) {
    if (next != null && lit.value) {
      Text(
        text = "Next ${clock(next.start)}  ${next.title}",
        style = KeelTvTheme.type.small,
        color = colors.onFocusMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    return
  }

  BasicText(
    text = current.title,
    style = KeelTvTheme.type.control.copy(fontWeight = FontWeight.Normal),
    color = { if (lit.value) colors.onFocus else colors.textSecondary },
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(top = 4.dp),
  ) {
    ProgressBar(
      fraction = fractionThrough(current.start, current.stop, now),
      lit = lit,
      modifier = Modifier.padding(end = KeelTvTheme.space.s2),
    )
    BasicText(
      text = timeLeft(current.stop - now),
      style = KeelTvTheme.type.micro,
      color = { if (lit.value) colors.onFocusMuted else colors.textTertiary },
      maxLines = 1,
    )
    if (next != null && lit.value) {
      Text(
        text = ", then ${clock(next.start)} ${next.title}",
        style = KeelTvTheme.type.micro,
        color = colors.onFocusMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun ChannelLogo(
  item: MediaItem,
  width: androidx.compose.ui.unit.Dp = 46.dp,
  height: androidx.compose.ui.unit.Dp = 30.dp,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .size(width, height)
      .clip(RoundedCornerShape(KeelTvTheme.radii.row))
      .background(KeelTvTheme.colors.bgRaised),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Outlined.Tv,
      contentDescription = null,
      tint = KeelTvTheme.colors.textDisabled,
      modifier = Modifier.size(14.dp),
    )
    if (!item.logo.isNullOrEmpty()) {
      AsyncImage(
        model = item.logo,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().padding(KeelTvTheme.space.s1),
      )
    }
  }
}

@Composable
private fun ProgressBar(fraction: Float, lit: State<Boolean>, modifier: Modifier = Modifier) {
  val colors = KeelTvTheme.colors
  Spacer(
    modifier
      .width(56.dp)
      .height(4.dp)
      .drawBehind {
        val on = lit.value
        val tall = if (on) size.height else size.height * 0.75f
        val top = (size.height - tall) / 2f
        val radius = CornerRadius(tall / 2f)
        drawRoundRect(
          color = if (on) colors.onFocus else colors.lineBright,
          topLeft = Offset(0f, top),
          size = Size(size.width, tall),
          cornerRadius = radius,
        )
        if (fraction > 0f) {
          drawRoundRect(
            color = colors.accent,
            topLeft = Offset(0f, top),
            size = Size(size.width * fraction, tall),
            cornerRadius = radius,
          )
        }
      },
  )
}

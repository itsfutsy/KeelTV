package com.keeltv.app.ui.misc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import com.keeltv.app.ui.components.ArtlessTile
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.ui.components.FocusRow
import com.keeltv.app.ui.components.tvLongPress
import com.keeltv.app.ui.theme.KeelTvTheme

@Composable
private fun Art(url: String?, label: String, modifier: Modifier) {
  Box(modifier.background(KeelTvTheme.colors.bgRaised), contentAlignment = Alignment.Center) {
    Text(
      text = label.take(2).uppercase(),
      style = KeelTvTheme.type.sub,
      color = KeelTvTheme.colors.textDisabled,
    )
    if (!url.isNullOrBlank()) {
      AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

@Composable
private fun ProgressTrack(progress: Float, modifier: Modifier = Modifier) {
  val accent = KeelTvTheme.colors.accent
  Box(
    modifier
      .fillMaxWidth()
      .height(4.dp)
      .drawBehind {
        drawRect(TRACK)
        drawRect(accent, size = Size(size.width * progress.coerceIn(0f, 1f), size.height))
      },
  )
}

private val TRACK = Color(0xB30F0F11)

@Composable
fun ChannelTile(
  item: MediaItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: (() -> Unit)? = null,
  now: String? = null,
  next: String? = null,
  progress: Float? = null,
) {
  val colors = KeelTvTheme.colors
  FocusRow(
    onClick = onClick,
    resting = TILE,
    shape = RoundedCornerShape(KeelTvTheme.radii.control),
    modifier = modifier.width(256.dp).height(64.dp).tvLongPress(onLongClick),
  ) { focused ->
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = KeelTvTheme.space.s3),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
    ) {
      com.keeltv.app.ui.live.ChannelLogo(item, width = 62.dp, height = 42.dp)
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = item.title.ifEmpty { item.name },
          style = KeelTvTheme.type.control,
          color = if (focused) colors.onFocus else colors.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = now ?: next?.let { "Next $it" } ?: "No listings",
          style = KeelTvTheme.type.micro.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
          color = when {
            focused -> colors.onFocusMuted
            now == null -> colors.textDisabled
            else -> colors.textTertiary
          },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (progress != null) {
          Box(
            Modifier
              .width(64.dp)
              .height(3.dp)
              .drawBehind {
                val round = CornerRadius(size.height / 2f)
                drawRoundRect(if (focused) colors.onFocus else colors.lineBright, cornerRadius = round)
                drawRoundRect(
                  colors.accent,
                  size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
                  cornerRadius = round,
                )
              },
          )
        }
      }
    }
  }
}

private val TILE = Color(0x0DF2F2F4)

@Composable
fun StillCard(
  title: String,
  image: String?,
  progress: Float,
  caption: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: (() -> Unit)? = null,
  width: Dp = 208.dp,
  number: Int? = null,
  seed: String? = null,
) {
  val colors = KeelTvTheme.colors
  val interaction = remember { MutableInteractionSource() }
  val lit = interaction.collectIsFocusedAsState()
  val focused = lit.value
  val scale by animateFloatAsState(
    targetValue = if (focused) 1.05f else 1f,
    animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
    label = "stillScale",
  )
  val radius = KeelTvTheme.radii.card
  val shape = RoundedCornerShape(radius)
  val ring = colors.focus
  val height = width * 9 / 16

  Column(
    modifier = modifier
      .width(width)
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .drawWithContent {
        drawContent()
        if (!lit.value) return@drawWithContent
        val stroke = 3.dp.toPx()
        val out = 2.dp.toPx() + stroke / 2f
        drawRoundRect(
          color = ring,
          topLeft = Offset(-out, -out),
          size = Size(size.width + out * 2f, height.toPx() + out * 2f),
          cornerRadius = CornerRadius(radius.toPx() + out),
          style = Stroke(stroke),
        )
      }
      .tvLongPress(onLongClick)
      .clickable(interactionSource = interaction, indication = null, onClick = onClick),
  ) {
    Box(Modifier.fillMaxWidth().height(height).clip(shape)) {
      if (image.isNullOrBlank() && seed != null) {
        ArtlessTile(seed = seed, label = number?.let { "E$it" } ?: title, modifier = Modifier.fillMaxSize(), dense = number == null)
      } else {
        Art(url = image, label = title, modifier = Modifier.fillMaxSize())
      }
      if (progress > 0.02f) ProgressTrack(progress, Modifier.align(Alignment.BottomCenter))
    }
    Text(
      text = if (number == null) AnnotatedString(title) else buildAnnotatedString {
        withStyle(SpanStyle(color = if (focused) colors.textSecondary else colors.textTertiary)) { append("$number\u2002") }
        append(title)
      },
      style = KeelTvTheme.type.control.copy(
        fontWeight = if (focused) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
      ),
      color = if (focused) colors.textPrimary else colors.textSecondary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(top = if (focused) 12.dp else 8.dp),
    )
    Text(
      caption,
      style = KeelTvTheme.type.micro.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
      color = if (focused) colors.textSecondary else colors.textTertiary,
      maxLines = 1,
    )
  }
}

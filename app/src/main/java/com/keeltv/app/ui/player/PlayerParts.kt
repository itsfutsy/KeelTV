package com.keeltv.app.ui.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.keeltv.app.playback.CaptionAppearance
import com.keeltv.app.ui.theme.KeelTvTheme
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(player: ExoPlayer?, appearance: CaptionAppearance, modifier: Modifier = Modifier) {
  AndroidView(
    modifier = modifier,
    factory = { context ->
      PlayerView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
        )
        useController = false
        setShutterBackgroundColor(android.graphics.Color.BLACK)
        keepScreenOn = true
        isFocusable = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
      }
    },
    update = { view ->
      if (view.player !== player) view.player = player
      view.subtitleView?.applyAppearance(appearance)
    },
  )
}

@OptIn(UnstableApi::class)
private fun SubtitleView.applyAppearance(appearance: CaptionAppearance) {
  val text = if (appearance.color == CaptionAppearance.YELLOW) 0xFFFFE14D.toInt() else android.graphics.Color.WHITE
  val background = Color.Black.copy(alpha = appearance.background.coerceIn(0f, 1f)).toArgb()
  setApplyEmbeddedStyles(false)
  setApplyEmbeddedFontSizes(false)
  setStyle(
    CaptionStyleCompat(
      text,
      background,
      android.graphics.Color.TRANSPARENT,
      CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
      android.graphics.Color.BLACK,
      null,
    ),
  )
  setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * (appearance.size / 100f))
}

@Composable
fun ChannelLogo(url: String?, name: String, size: Dp = 52.dp, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .size(width = size * 1.46f, height = size)
      .clip(RoundedCornerShape(KeelTvTheme.radii.control))
      .background(KeelTvTheme.colors.bgRaised),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = initialsFor(name),
      style = KeelTvTheme.type.sub,
      color = KeelTvTheme.colors.textTertiary,
    )
    if (!url.isNullOrBlank()) {
      AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().padding(KeelTvTheme.space.s1),
      )
    }
  }
}

fun initialsFor(name: String): String {
  val words = name.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
  return when {
    words.isEmpty() -> "?"
    words.size == 1 -> words[0].take(2).uppercase(Locale.ROOT)
    else -> (words[0].take(1) + words[1].take(1)).uppercase(Locale.ROOT)
  }
}

@Composable
fun ProgressRail(fraction: Float, modifier: Modifier = Modifier, height: Dp = 3.dp) {
  Box(
    modifier = modifier
      .height(height)
      .clip(RoundedCornerShape(height / 2))
      .background(KeelTvTheme.colors.lineBright),
  ) {
    Box(
      Modifier
        .fillMaxWidth(fraction.coerceIn(0f, 1f))
        .fillMaxSize()
        .clip(RoundedCornerShape(height / 2))
        .background(KeelTvTheme.colors.accent),
    )
  }
}

fun risingScrim(): Brush = Brush.verticalGradient(
  0f to Color.Transparent,
  0.34f to Color.Transparent,
  0.56f to SHADE.copy(alpha = 0.72f),
  0.74f to SHADE.copy(alpha = 0.95f),
  0.88f to SHADE.copy(alpha = 0.98f),
  1f to SHADE.copy(alpha = 0.99f),
)

fun Modifier.cornerShade(corner: Alignment): Modifier = drawWithCache {
  val right = corner == Alignment.TopEnd
  val centre = Offset(if (right) size.width else 0f, 0f)
  val brush = Brush.radialGradient(
    0f to SHADE.copy(alpha = 0.6f),
    0.5f to SHADE.copy(alpha = 0.3f),
    1f to Color.Transparent,
    center = centre,
    radius = size.height.coerceAtLeast(1f),
  )
  onDrawBehind {
    scale(scaleX = size.width / size.height.coerceAtLeast(1f), scaleY = 1f, pivot = centre) {
      drawCircle(brush, radius = size.height, center = centre)
    }
  }
}

private val SHADE = Color(0xFF08080A)

@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 22.dp) {
  val spin = rememberInfiniteTransition(label = "spinner")
  val angle by spin.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing)),
    label = "angle",
  )
  val accent = KeelTvTheme.colors.accent
  val track = KeelTvTheme.colors.lineBright
  Canvas(modifier.size(size)) {
    val line = Stroke(width = 2.5.dp.toPx())
    drawArc(track, 0f, 360f, false, style = line)
    drawArc(accent, angle, 90f, false, style = line)
  }
}

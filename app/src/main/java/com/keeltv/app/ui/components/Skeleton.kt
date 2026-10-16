package com.keeltv.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.keeltv.app.ui.theme.KeelTvTheme

@Composable
fun SkeletonBox(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(KeelTvTheme.radii.card),
) {
  val colors = KeelTvTheme.colors
  val transition = rememberInfiniteTransition(label = "skeleton")
  val phase = transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
    label = "phase",
  )
  val crest = colors.bgHover
  val band = remember(crest) { arrayOfNulls<Any>(2) }
  Box(
    modifier
      .clip(shape)
      .background(colors.bgSurface)
      .drawBehind {
        val width = size.width * 0.7f
        val brush = if (band[0] == width) {
          band[1] as Brush
        } else {
          Brush.linearGradient(
            colors = listOf(Color.Transparent, crest, Color.Transparent),
            start = Offset.Zero,
            end = Offset(width, size.height),
          ).also { band[0] = width; band[1] = it }
        }
        val x = -width + (size.width + 2 * width) * phase.value
        translate(left = x) { drawRect(brush = brush, size = Size(width, size.height)) }
      },
  )
}

@Composable
fun SkeletonLine(width: Float, height: Dp = 14.dp, modifier: Modifier = Modifier) {
  SkeletonBox(
    modifier = modifier.fillMaxWidth(width).height(height),
    shape = RoundedCornerShape(3.dp),
  )
}

@Composable
fun MediaCardSkeleton(modifier: Modifier = Modifier) {
  val type = KeelTvTheme.type
  Column(modifier) {
    SkeletonBox(Modifier.fillMaxWidth().aspectRatio(2f / 3f), RoundedCornerShape(KeelTvTheme.radii.row))
    Column(
      Modifier
        .padding(top = 8.dp)
        .height(type.small.lineHeight.value.dp * 2 + type.micro.lineHeight.value.dp + 2.dp),
    ) {
      SkeletonLine(0.82f, 11.dp, Modifier.padding(top = 3.dp))
      SkeletonLine(0.38f, 9.dp, Modifier.padding(top = 8.dp))
    }
  }
}

@Composable
fun ChannelRowSkeleton(modifier: Modifier = Modifier) {
  val space = KeelTvTheme.space
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(KeelTvTheme.dimens.channelRowHeight)
      .padding(start = 38.dp + space.s3, end = space.s3),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SkeletonBox(Modifier.size(52.dp, 34.dp), RoundedCornerShape(KeelTvTheme.radii.row))
    Column(Modifier.padding(start = space.s3).width(180.dp)) {
      SkeletonLine(0.62f, 11.dp)
      Spacer(Modifier.height(7.dp))
      SkeletonLine(0.26f, 8.dp)
    }
    Column(Modifier.padding(start = space.s3).weight(1f)) {
      SkeletonLine(0.46f, 10.dp)
      Spacer(Modifier.height(7.dp))
      SkeletonLine(0.2f, 8.dp)
    }
  }
}

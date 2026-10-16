package com.keeltv.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.keeltv.app.ui.theme.KeelTvTheme
import kotlinx.coroutines.flow.debounce

@Composable
fun HeroArt(url: () -> String?, modifier: Modifier = Modifier, settleMs: Long = 0L) {
  val wash = KeelTvTheme.colors.bgApp
  var shown by remember { mutableStateOf(if (settleMs == 0L) url() else null) }
  LaunchedEffect(url, settleMs) {
    snapshotFlow { url() }.debounce(settleMs).collect { shown = it }
  }
  Box(
    modifier
      .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
      .drawWithContent {
        drawContent()
        drawRect(
          brush = Brush.horizontalGradient(
            0f to wash.copy(alpha = 0.85f),
            0.45f to wash.copy(alpha = 0.45f),
            0.75f to Color.Transparent,
          ),
        )
        drawRect(
          brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.16f to Color.Black.copy(alpha = 0.12f),
            0.34f to Color.Black.copy(alpha = 0.55f),
            0.56f to Color.Black,
            1f to Color.Black,
          ),
          blendMode = BlendMode.DstIn,
        )
        drawRect(
          brush = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.55f),
            0.18f to Color.Black,
            0.5f to Color.Black,
            0.78f to Color.Black.copy(alpha = 0.35f),
            1f to Color.Transparent,
          ),
          blendMode = BlendMode.DstIn,
        )
      },
  ) {
    Crossfade(targetState = shown, animationSpec = tween(360), label = "hero") { picture ->
      if (picture != null) {
        AsyncImage(
          model = picture,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          alignment = Alignment.TopCenter,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
  }
}

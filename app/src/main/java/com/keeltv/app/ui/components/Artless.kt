package com.keeltv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.keeltv.app.ui.theme.KeelTvTheme

private val SHADES = listOf(
  Color(0xFF1B2A3E) to Color(0xFF0F1622),
  Color(0xFF2B2036) to Color(0xFF15111C),
  Color(0xFF122B2A) to Color(0xFF0C1817),
  Color(0xFF32241B) to Color(0xFF1A1310),
  Color(0xFF1F2438) to Color(0xFF12141F),
  Color(0xFF301E26) to Color(0xFF191014),
  Color(0xFF1A3030) to Color(0xFF0E1A1A),
  Color(0xFF26252F) to Color(0xFF14141A),
)

private fun shadeOf(seed: String): Pair<Color, Color> =
  SHADES[((seed.hashCode() % SHADES.size) + SHADES.size) % SHADES.size]

@Composable
fun artlessWash(seed: String): Brush = remember(seed) {
  val (top, bottom) = shadeOf(seed)
  Brush.linearGradient(
    colors = listOf(top, bottom),
    start = Offset.Zero,
    end = Offset(0f, Float.POSITIVE_INFINITY),
  )
}

@Composable
fun ArtlessTile(seed: String, label: String?, modifier: Modifier = Modifier, dense: Boolean = false) {
  Box(
    modifier = modifier.background(artlessWash(seed)),
    contentAlignment = Alignment.Center,
  ) {
    if (!label.isNullOrEmpty()) ArtlessLabel(label, dense = dense)
  }
}

@Composable
fun ArtlessLabel(label: String, modifier: Modifier = Modifier, dense: Boolean = true) {
  Text(
    text = label,
    style = if (dense) KeelTvTheme.type.micro else KeelTvTheme.type.small,
    color = KeelTvTheme.colors.textSecondary,
    textAlign = TextAlign.Center,
    maxLines = 3,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier.padding(horizontal = 8.dp, vertical = 10.dp),
  )
}

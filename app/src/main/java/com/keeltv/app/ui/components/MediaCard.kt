package com.keeltv.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.keeltv.app.core.pictureQuality
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.ui.theme.KeelTvTheme

fun metaParts(item: MediaItem): List<String> {
  val parts = mutableListOf<String>()
  item.year?.let { parts.add(it.toString()) }
  if (item.kind == MediaKind.SERIES) {
    item.seasonCount?.let { parts.add(seasonsLabel(it)) }
  }
  return parts
}

fun seasonsLabel(n: Int): String = "$n ${if (n == 1) "season" else "seasons"}"

fun formatRating(rating: Double?): String? {
  if (rating == null || rating.isNaN() || rating <= 0.0) return null
  return String.format(java.util.Locale.US, "%.1f", rating)
}

@Composable
fun MediaCard(
  item: MediaItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  progress: Float = 0f,
  favourite: Boolean = false,
  badge: Boolean = true,
  onLongClick: (() -> Unit)? = null,
) {
  val colors = KeelTvTheme.colors
  val interaction = remember { MutableInteractionSource() }
  val lit = interaction.collectIsFocusedAsState()
  val focused = lit.value
  val scale by animateFloatAsState(
    targetValue = if (focused) 1.08f else 1f,
    animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
    label = "cardScale",
  )
  val lift by animateFloatAsState(
    targetValue = if (focused) 1f else 0f,
    animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
    label = "cardLift",
  )
  val radius = KeelTvTheme.radii.row
  val cardShape = RoundedCornerShape(radius)
  val title = remember(item.id, item.title) { shownTitle(item) }
  val quality = remember(item.name, item.categoryName) { pictureQuality(item.name, item.categoryName) }
  val ring = colors.focus

  Column(
    modifier = modifier
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .drawWithContent {
        drawContent()
        if (!lit.value) return@drawWithContent
        val gap = RING_GAP.toPx()
        val stroke = RING_WIDTH.toPx()
        val out = gap + stroke / 2f
        val art = Size(size.width, size.width * 1.5f)
        drawRoundRect(
          color = ring,
          topLeft = Offset(-out, -out),
          size = Size(art.width + out * 2f, art.height + out * 2f),
          cornerRadius = CornerRadius(radius.toPx() + out),
          style = Stroke(stroke),
        )
      }
      .tvLongPress(onLongClick)
      .clickable(interactionSource = interaction, indication = null, onClick = onClick),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(2f / 3f)
        .graphicsLayer { shadowElevation = lift * 16f; shape = cardShape; clip = true }
        .background(artlessWash(item.id)),
    ) {
      if (item.logo.isNullOrEmpty()) {
        ArtlessLabel(title, Modifier.align(Alignment.Center))
      } else {
        AsyncImage(
          model = item.logo,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          alignment = Alignment.TopCenter,
          modifier = Modifier.fillMaxSize().graphicsLayer {
            alpha = 0.92f + 0.08f * lift
            compositingStrategy = CompositingStrategy.ModulateAlpha
          },
        )
      }
      if (quality != null) {
        Row(
          modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
          horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          if (badge) LanguageBadge(item)
          QualityBadge(quality)
        }
      } else if (badge) {
        LanguageBadge(
          item = item,
          modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
        )
      }
      if (favourite) {
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(4.dp)
            .size(18.dp)
            .background(Color(0xB80F0F11), CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Filled.Star, contentDescription = "Favourite", tint = colors.textPrimary, modifier = Modifier.size(11.dp))
        }
      }
      ProgressTrack(progress)
    }

    Column(
      Modifier
        .padding(top = 8.dp)
        .height(KeelTvTheme.type.small.lineHeight.value.dp * 2 + KeelTvTheme.type.micro.lineHeight.value.dp + 2.dp),
    ) {
      Text(
        text = title,
        style = KeelTvTheme.type.small,
        color = if (focused) colors.textPrimary else colors.textSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Kicker(
        parts = metaParts(item),
        rating = if (item.kind == MediaKind.SERIES) null else item.rating,
        modifier = Modifier.padding(top = 2.dp),
      )
    }
  }
}

@Composable
private fun QualityBadge(label: String) {
  val shape = RoundedCornerShape(KeelTvTheme.radii.chip)
  Text(
    text = label,
    style = KeelTvTheme.type.label,
    color = KeelTvTheme.colors.textPrimary,
    maxLines = 1,
    modifier = Modifier
      .background(Color(0x8A0F0F11), shape)
      .border(1.dp, Color(0xB3F5F5F7), shape)
      .padding(horizontal = 4.dp, vertical = 1.dp),
  )
}

private val RING_GAP = 2.dp
private val RING_WIDTH = 3.dp

@Composable
private fun BoxScope.ProgressTrack(progress: Float) {
  if (progress <= 0.02f) return
  val accent = KeelTvTheme.colors.accent
  Box(
    modifier = Modifier
      .align(Alignment.BottomCenter)
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
fun Kicker(parts: List<String>, rating: Double? = null, modifier: Modifier = Modifier) {
  val stars = formatRating(rating)
  val line = (parts + listOfNotNull(stars?.let { "★ $it" })).joinToString(TILE_GAP)
  if (line.isEmpty()) {
    Spacer(modifier.height(KeelTvTheme.type.micro.lineHeight.value.dp))
    return
  }
  Text(
    text = line,
    style = KeelTvTheme.type.micro,
    color = KeelTvTheme.colors.textTertiary,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier,
  )
}

const val FACT_GAP = "\u2003"

private const val TILE_GAP = "\u2002"

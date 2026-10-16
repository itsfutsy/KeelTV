package com.keeltv.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.keeltv.app.ui.theme.KeelTvTheme

@Composable
fun FocusRow(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(KeelTvTheme.radii.row),
  resting: Color = Color.Transparent,
  selected: Boolean = false,
  enabled: Boolean = true,
  onLongClick: (() -> Unit)? = null,
  content: @Composable (focused: Boolean) -> Unit,
) {
  val interaction = remember { MutableInteractionSource() }
  val lit = interaction.collectIsFocusedAsState()
  val focused = lit.value
  val colors = KeelTvTheme.colors
  val fill = if (enabled) colors.focus else colors.bgActive

  val scale = animateFloatAsState(
    targetValue = if (focused) 1.02f else 1f,
    animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
    label = "rowScale",
  )

  Box(
    modifier = modifier
      .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
      .clip(shape)
      .background(if (selected) colors.accentDim else resting)
      .drawBehind { if (lit.value) drawRect(fill) }
      .tvClickable(interaction, onLongClick?.let { long -> { if (enabled) long() } }) {
        if (enabled) onClick()
      },
    contentAlignment = Alignment.CenterStart,
  ) {
    content(focused)
  }
}

/**
 * A disabled control stays focusable. Dropping it out of the focus order would throw focus back to
 * the top of the screen the moment a button greys out, which is exactly when the remote is on it.
 *
 * Only a press that started on a control can click it. Compose takes a repeat of a held OK as a
 * fresh press, so a hold that opens a menu would land on the menu's first item the moment the
 * button came back up. Android's own views ignore a press they did not see start, and so does this.
 */
@Composable
private fun Modifier.tvClickable(
  interaction: MutableInteractionSource,
  onLongClick: (() -> Unit)? = null,
  onClick: () -> Unit,
): Modifier {
  val pressedHere = remember { BooleanArray(1) }
  return this
    .onPreviewKeyEvent { event ->
      if (!event.isCentre) return@onPreviewKeyEvent false
      when (event.type) {
        KeyEventType.KeyDown -> {
          if (event.nativeKeyEvent.repeatCount == 0) pressedHere[0] = true
          !pressedHere[0]
        }
        KeyEventType.KeyUp -> {
          val stray = !pressedHere[0]
          pressedHere[0] = false
          stray
        }
        else -> false
      }
    }
    .combinedClickable(
      interactionSource = interaction,
      indication = null,
      onLongClick = onLongClick,
      onClick = onClick,
    )
}

private val KeyEvent.isCentre: Boolean
  get() = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

enum class ButtonTone { Primary, Ghost, Plain, Danger }

@Composable
fun TvButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  tone: ButtonTone = ButtonTone.Ghost,
  enabled: Boolean = true,
  icon: ImageVector? = null,
  detail: String? = null,
  large: Boolean = false,
) {
  val colors = KeelTvTheme.colors
  val shape = RoundedCornerShape(if (large) 11.dp else KeelTvTheme.radii.control)
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val lit = focused && enabled
  val scale by animateFloatAsState(
    targetValue = if (lit) 1.05f else 1f,
    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
    label = "buttonScale",
  )

  val background = when {
    lit && tone == ButtonTone.Danger -> colors.danger
    lit -> colors.focus
    focused -> colors.bgActive
    !enabled -> colors.bgSurface
    tone == ButtonTone.Primary -> PRIMARY_REST
    tone == ButtonTone.Danger -> colors.dangerDim
    tone == ButtonTone.Ghost -> GHOST_REST
    else -> Color.Transparent
  }
  val foreground = when {
    lit -> colors.onFocus
    !enabled -> colors.textDisabled
    tone == ButtonTone.Danger -> colors.danger
    tone == ButtonTone.Primary -> colors.textPrimary
    else -> colors.textSecondary
  }
  val style = if (large) KeelTvTheme.type.body else KeelTvTheme.type.small

  Row(
    modifier = modifier
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .background(background, shape)
      .tvClickable(interaction) { if (enabled) onClick() }
      .defaultMinSize(minHeight = if (large) 44.dp else KeelTvTheme.dimens.controlHeight)
      .padding(
        start = if (icon != null) KeelTvTheme.space.s3 + 2.dp else if (large) 20.dp else KeelTvTheme.space.s4,
        end = if (large) 20.dp else KeelTvTheme.space.s4,
      ),
    horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2 + 2.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(if (large) 18.dp else 16.dp))
    }
    Text(
      text = text,
      style = style.copy(fontWeight = if (lit || tone == ButtonTone.Primary) FontWeight.SemiBold else FontWeight.Medium),
      color = foreground,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (detail != null) {
      Text(
        text = detail,
        style = KeelTvTheme.type.small,
        color = if (lit) colors.onFocusMuted else colors.textTertiary,
        maxLines = 1,
      )
    }
  }
}

private val GHOST_REST = Color(0x1FF2F2F4)
private val PRIMARY_REST = Color(0x29F2F2F4)

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
  androidx.compose.foundation.layout.Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s1),
  ) {
    Text(title, style = KeelTvTheme.type.title, color = KeelTvTheme.colors.textPrimary)
    if (subtitle != null) {
      Text(subtitle, style = KeelTvTheme.type.small, color = KeelTvTheme.colors.textTertiary)
    }
  }
}

private const val LONG_PRESS_MS = 500L

@Composable
fun Modifier.tvLongPress(onLongClick: (() -> Unit)?): Modifier {
  if (onLongClick == null) return this
  var downAt by remember { mutableStateOf(0L) }
  return this.onPreviewKeyEvent { event ->
    when {
      !event.isCentre -> false
      event.type == KeyEventType.KeyDown -> {
        // Android repeats a held key; only the first press starts the clock.
        if (downAt == 0L) downAt = System.currentTimeMillis()
        false
      }
      event.type == KeyEventType.KeyUp -> {
        val held = System.currentTimeMillis() - downAt
        val long = downAt != 0L && held >= LONG_PRESS_MS
        downAt = 0L
        if (long) onLongClick()
        long
      }
      else -> false
    }
  }
}

fun Modifier.fadeFoot(height: Dp = 56.dp, to: Color): Modifier = drawWithCache {
  val band = height.toPx().coerceAtMost(size.height)
  val brush = Brush.verticalGradient(
    0f to Color.Transparent,
    1f to to,
    startY = size.height - band,
    endY = size.height,
  )
  onDrawWithContent {
    drawContent()
    drawRect(brush, topLeft = Offset(0f, size.height - band), size = Size(size.width, band))
  }
}

fun Modifier.fadeEnd(width: Dp = 48.dp, to: Color): Modifier = drawWithCache {
  val band = width.toPx().coerceAtMost(size.width)
  val brush = Brush.horizontalGradient(
    0f to Color.Transparent,
    1f to to,
    startX = size.width - band,
    endX = size.width,
  )
  onDrawWithContent {
    drawContent()
    drawRect(brush, topLeft = Offset(size.width - band, 0f), size = Size(band, size.height))
  }
}

fun Modifier.dissolveEnd(width: Dp = 72.dp): Modifier = this
  .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
  .drawWithCache {
    val band = width.toPx().coerceAtMost(size.width)
    val brush = Brush.horizontalGradient(
      0f to Color.Transparent,
      1f to Color.Black,
      startX = size.width - band,
      endX = size.width,
    )
    onDrawWithContent {
      drawContent()
      drawRect(brush, topLeft = Offset(size.width - band, 0f), size = Size(band, size.height), blendMode = BlendMode.DstOut)
    }
  }

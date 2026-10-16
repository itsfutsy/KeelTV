package com.keeltv.app.ui.misc

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.keeltv.app.ui.components.FocusRow
import com.keeltv.app.ui.theme.KeelTvTheme

@Composable
fun TvChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  count: Int? = null,
  icon: ImageVector? = null,
  compact: Boolean = false,
) {
  val colors = KeelTvTheme.colors
  FocusRow(
    onClick = onClick,
    resting = colors.bgControl,
    selected = selected,
    shape = RoundedCornerShape(if (compact) KeelTvTheme.radii.card else KeelTvTheme.radii.control),
    modifier = modifier.defaultMinSize(minHeight = if (compact) 28.dp else 34.dp),
  ) { focused ->
    val tint = when {
      focused -> colors.onFocus
      selected -> colors.accent
      else -> colors.textSecondary
    }
    Row(
      modifier = Modifier.padding(
        start = if (icon != null) KeelTvTheme.space.s3 else if (compact) KeelTvTheme.space.s3 else 13.dp,
        end = if (compact) KeelTvTheme.space.s3 else 13.dp,
        top = if (compact) 5.dp else KeelTvTheme.space.s2,
        bottom = if (compact) 5.dp else KeelTvTheme.space.s2,
      ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
    ) {
      if (icon != null) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
      }
      Text(
        text = label,
        style = if (compact) KeelTvTheme.type.small else KeelTvTheme.type.control,
        color = tint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (count != null) {
        Text(
          text = count.toString(),
          style = KeelTvTheme.type.control,
          color = when {
            focused -> colors.onFocusMuted
            selected -> colors.accent.copy(alpha = 0.7f)
            else -> colors.textTertiary
          },
        )
      }
    }
  }
}

@Composable
private fun RowText(title: String, help: String?, modifier: Modifier = Modifier, focused: Boolean = false) {
  val colors = KeelTvTheme.colors
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      title,
      style = KeelTvTheme.type.control.copy(fontSize = KeelTvTheme.type.body.fontSize),
      color = if (focused) colors.onFocus else colors.textPrimary,
    )
    if (help != null) {
      Text(help, style = KeelTvTheme.type.micro, color = if (focused) colors.onFocusMuted else colors.textTertiary)
    }
  }
}

@Composable
fun ToggleRow(
  title: String,
  checked: Boolean,
  onToggle: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  help: String? = null,
) {
  val colors = KeelTvTheme.colors
  FocusRow(
    onClick = { onToggle(!checked) },
    resting = colors.bgSurface,
    shape = RoundedCornerShape(KeelTvTheme.radii.panel),
    modifier = modifier.fillMaxWidth(),
  ) { focused ->
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = KeelTvTheme.space.s4, vertical = KeelTvTheme.space.s3),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      RowText(title, help, Modifier.weight(1f), focused)
      Spacer(Modifier.width(KeelTvTheme.space.s3))
      Box(
        modifier = Modifier
          .width(38.dp)
          .height(22.dp)
          .background(
            when {
              checked -> colors.accent
              focused -> colors.onFocusMuted
              else -> colors.bgActive
            },
            RoundedCornerShape(11.dp),
          ),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
      ) {
        Box(
          Modifier
            .padding(horizontal = 3.dp)
            .size(16.dp)
            .background(if (checked || focused) colors.textPrimary else colors.textSecondary, RoundedCornerShape(8.dp)),
        )
      }
    }
  }
}

@Composable
fun ChoiceRow(
  title: String,
  value: String,
  options: List<Pair<String, String>>,
  onChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  help: String? = null,
  onFocusOption: ((String?) -> Unit)? = null,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .then(
        if (onFocusOption == null) Modifier else Modifier.onFocusChanged { if (!it.hasFocus) onFocusOption(null) },
      ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s4),
  ) {
    RowText(title, help, Modifier.width(176.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2)) {
      options.forEach { (key, label) ->
        TvChip(
          label = label,
          selected = key == value,
          onClick = { onChange(key) },
          modifier = if (onFocusOption == null) {
            Modifier
          } else {
            Modifier.onFocusChanged { if (it.isFocused) onFocusOption(key) }
          },
        )
      }
    }
  }
}

@Composable
fun SpecRow(label: String, value: String, modifier: Modifier = Modifier, bad: Boolean = false) {
  Row(
    modifier = modifier.fillMaxWidth().padding(vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = KeelTvTheme.type.small,
      color = KeelTvTheme.colors.textSecondary,
      maxLines = 1,
    )
    Spacer(Modifier.weight(1f).widthIn(min = KeelTvTheme.space.s4))
    Text(
      text = value,
      style = KeelTvTheme.type.small,
      color = if (bad) KeelTvTheme.colors.danger else KeelTvTheme.colors.textPrimary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun RowLabel(text: String, modifier: Modifier = Modifier, count: String? = null) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
  ) {
    Text(text, style = KeelTvTheme.type.heading, color = KeelTvTheme.colors.textPrimary)
    if (count != null) {
      Text(
        count,
        style = KeelTvTheme.type.small,
        color = KeelTvTheme.colors.textTertiary,
        modifier = Modifier.padding(bottom = 1.dp),
      )
    }
  }
}

@Composable
fun TvProgressBar(value: Float?, modifier: Modifier = Modifier, height: Dp = 4.dp) {
  val colors = KeelTvTheme.colors
  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .height(height)
      .clip(RoundedCornerShape(height / 2))
      .background(colors.lineBright),
  ) {
    if (value == null) {
      val transition = rememberInfiniteTransition(label = "indeterminate")
      val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "shift",
      )
      Box(
        Modifier
          .offset(x = (maxWidth - maxWidth * 0.3f) * shift)
          .width(maxWidth * 0.3f)
          .height(height)
          .clip(RoundedCornerShape(height / 2))
          .background(colors.accent),
      )
    } else {
      Box(
        Modifier
          .width(maxWidth * value.coerceIn(0f, 1f))
          .height(height)
          .clip(RoundedCornerShape(height / 2))
          .background(colors.accent),
      )
    }
  }
}

@Composable
fun EmptyState(
  title: String,
  body: String,
  modifier: Modifier = Modifier,
  action: (@Composable () -> Unit)? = null,
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(vertical = KeelTvTheme.space.s5),
    verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
  ) {
    Text(title, style = KeelTvTheme.type.sub, color = KeelTvTheme.colors.textPrimary)
    Text(
      text = body,
      style = KeelTvTheme.type.small,
      color = KeelTvTheme.colors.textSecondary,
      modifier = Modifier.widthIn(max = 560.dp),
    )
    if (action != null) {
      Spacer(Modifier.height(KeelTvTheme.space.s1))
      action()
    }
  }
}

@Composable
fun ShelfEmpty(
  body: String,
  modifier: Modifier = Modifier,
  action: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s4),
  ) {
    Text(
      text = body,
      style = KeelTvTheme.type.control.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
      color = KeelTvTheme.colors.textTertiary,
      modifier = Modifier.weight(1f, fill = false),
    )
    action?.invoke()
  }
}

@Composable
fun Note(text: String, modifier: Modifier = Modifier, tone: Color? = null) {
  Text(
    text = text,
    style = KeelTvTheme.type.small,
    color = tone ?: KeelTvTheme.colors.textTertiary,
    modifier = modifier.widthIn(max = 560.dp),
  )
}

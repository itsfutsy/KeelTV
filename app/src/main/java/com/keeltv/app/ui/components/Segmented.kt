package com.keeltv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.keeltv.app.ui.theme.KeelTvTheme

@Composable
fun Segmented(content: @Composable RowScope.() -> Unit) {
  Row(
    modifier = Modifier
      .height(KeelTvTheme.dimens.controlHeight)
      .background(KeelTvTheme.colors.bgControl, RoundedCornerShape(KeelTvTheme.radii.control))
      .padding(3.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    content = content,
  )
}

@Composable
fun SegButton(text: String, on: Boolean, count: Int = 0, onClick: () -> Unit) {
  val colors = KeelTvTheme.colors
  FocusRow(
    onClick = onClick,
    shape = RoundedCornerShape(KeelTvTheme.radii.card),
    selected = on,
    modifier = Modifier.height(30.dp),
  ) { focused ->
    Row(
      modifier = Modifier.padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = text,
        style = KeelTvTheme.type.control,
        color = when {
          focused -> colors.onFocus
          on -> colors.accent
          else -> colors.textSecondary
        },
        maxLines = 1,
        softWrap = false,
      )
      if (count > 0) {
        Text(
          text = tally(count),
          style = KeelTvTheme.type.control,
          color = when {
            focused -> colors.onFocusMuted
            on -> colors.accent.copy(alpha = 0.7f)
            else -> colors.textTertiary
          },
          maxLines = 1,
        )
      }
    }
  }
}

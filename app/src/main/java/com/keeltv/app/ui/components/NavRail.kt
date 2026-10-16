package com.keeltv.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.keeltv.app.ui.Section
import com.keeltv.app.ui.theme.KeelTvTheme

@Composable
fun NavRail(
  current: Section,
  onSelect: (Section) -> Unit,
  modifier: Modifier = Modifier,
  focusRequester: FocusRequester = remember { FocusRequester() },
) {
  var expanded by remember { mutableStateOf(false) }
  val dimens = KeelTvTheme.dimens
  val colors = KeelTvTheme.colors
  val width by animateDpAsState(
    targetValue = if (expanded) dimens.railExpanded else dimens.railCollapsed,
    animationSpec = tween(durationMillis = 220),
    label = "railWidth",
  )
  val wash by animateFloatAsState(
    targetValue = if (expanded) 1f else 0f,
    animationSpec = tween(durationMillis = 220),
    label = "railWash",
  )

  val rail = dimens.edge + width + KeelTvTheme.space.s2
  val plate = rail + FADE
  val solid = (rail / plate).coerceIn(0f, 1f)

  Box(
    modifier = modifier
      .fillMaxHeight()
      .width(plate)
      .background(
        Brush.horizontalGradient(
          0f to colors.bgApp,
          solid to colors.bgApp.copy(alpha = wash),
          1f to colors.bgApp.copy(alpha = 0f),
        ),
      ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxHeight()
        .width(rail)
        .onFocusChanged { expanded = it.hasFocus }
        .focusGroup()
        .padding(
          start = dimens.edge,
          end = KeelTvTheme.space.s2,
          top = 22.dp,
          bottom = 22.dp,
        ),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      KeelLogo(
        modifier = Modifier.padding(start = 6.dp).width(36.dp).height(36.dp),
        markOnly = true,
      )
      Spacer(Modifier.height(KeelTvTheme.space.s3))
      val groups = listOf(
        listOf(Section.LIVE, Section.MOVIES, Section.SHOWS),
        listOf(Section.LIBRARY, Section.SEARCH),
      )
      groups.forEachIndexed { index, group ->
        if (index > 0) Spacer(Modifier.height(KeelTvTheme.space.s4))
        group.forEach { section ->
          Marked(section == current) {
            RailItem(
              section = section,
              active = section == current,
              expanded = expanded,
              onSelect = onSelect,
              modifier = if (section == current) Modifier.focusRequester(focusRequester) else Modifier,
            )
          }
        }
      }
      Spacer(Modifier.weight(1f))
      Marked(current == Section.SETTINGS) {
        RailItem(
          section = Section.SETTINGS,
          active = current == Section.SETTINGS,
          expanded = expanded,
          onSelect = onSelect,
          modifier = if (current == Section.SETTINGS) Modifier.focusRequester(focusRequester) else Modifier,
        )
      }
    }
  }
}

@Composable
private fun Marked(active: Boolean, item: @Composable () -> Unit) {
  Box(contentAlignment = Alignment.CenterStart) {
    item()
    if (active) ActiveMark(Modifier.offset(x = -4.dp))
  }
}

private val FADE = 64.dp

@Composable
private fun RailItem(
  section: Section,
  active: Boolean,
  expanded: Boolean,
  onSelect: (Section) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = KeelTvTheme.colors
  FocusRow(
    onClick = { onSelect(section) },
    shape = RoundedCornerShape(KeelTvTheme.radii.card),
    modifier = modifier.fillMaxWidth().height(KeelTvTheme.dimens.railItemHeight),
  ) { focused ->
    val tint = when {
      focused -> colors.onFocus
      active -> colors.accent
      else -> colors.textTertiary
    }
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = KeelTvTheme.space.s2),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
      Icon(
        imageVector = section.icon,
        contentDescription = section.label,
        tint = tint,
        modifier = Modifier.size(20.dp),
      )
      if (expanded) {
        Spacer(Modifier.width(KeelTvTheme.space.s3))
        Text(
          text = section.label,
          style = KeelTvTheme.type.control,
          color = if (focused || active) tint else colors.textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.wrapContentWidth(Alignment.Start),
        )
      }
    }
  }
}

@Composable
private fun ActiveMark(modifier: Modifier = Modifier) {
  Box(
    modifier
      .width(3.dp)
      .height(16.dp)
      .background(KeelTvTheme.colors.accent, RoundedCornerShape(2.dp)),
  )
}

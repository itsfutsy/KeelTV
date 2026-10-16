package com.keeltv.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.keeltv.app.data.model.Category
import com.keeltv.app.ui.theme.KeelTvTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class CategoryRow(val key: String, val whole: Boolean, val chip: String?, val label: String)

private val ROW_HEIGHT = 30.dp

private val MARK_WIDTH = 2.dp

@Composable
fun rememberCategoryRows(
  allKey: String,
  allLabel: String,
  categories: List<Category>,
  categoryKey: (String) -> String,
): List<CategoryRow> {
  val all = CategoryRow(allKey, true, null, allLabel)
  val parsed by produceState(listOf(all), categories, allLabel) {
    value = withContext(Dispatchers.Default) {
      buildList(categories.size + 1) {
        add(all)
        categories.forEach { category ->
          val name = parseCategory(category.name)
          add(
            CategoryRow(
              key = categoryKey(category.id),
              whole = false,
              chip = name.chips.firstOrNull(),
              label = name.label,
            ),
          )
        }
      }
    }
  }
  return parsed
}

@Composable
fun CategoryColumn(
  rows: List<CategoryRow>,
  selected: String,
  total: Int,
  muted: Boolean,
  headingFocus: FocusRequester = remember { FocusRequester() },
  right: FocusRequester? = null,
  onFocus: (String) -> Unit,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val state = rememberLazyListState()
  val placed = remember { mutableStateOf(false) }
  LaunchedEffect(rows.size) {
    if (placed.value || rows.size <= 1) return@LaunchedEffect
    placed.value = true
    val at = rows.indexOfFirst { it.key == selected }
    if (at > 3) state.scrollToItem(at)
  }

  val fade = animateFloatAsState(
    targetValue = if (muted) 0.4f else 1f,
    animationSpec = tween(180),
    label = "categoryFade",
  )

  PivotScroll {
    LazyColumn(
      state = state,
      modifier = modifier
        .graphicsLayer {
          alpha = fade.value
          compositingStrategy = CompositingStrategy.ModulateAlpha
        }
        .fadeFoot(to = KeelTvTheme.colors.bgApp)
        .focusRestorer(headingFocus)
        .focusGroup(),
      contentPadding = PaddingValues(bottom = 22.dp),
      verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
      items(rows.size, key = { rows[it].key }, contentType = { HEADING }) { index ->
        val row = rows[index]
        CategoryHeading(
          row = row,
          selected = row.key == selected,
          count = total.takeIf { row.whole && it > 0 },
          onFocus = { state.pivotTo(index); onFocus(it) },
          onSelect = onSelect,
          modifier = Modifier
            .then(if (row.key == selected) Modifier.focusRequester(headingFocus) else Modifier)
            .then(if (right != null) Modifier.focusProperties { this.right = right } else Modifier),
        )
      }
    }
  }
}

private const val HEADING = "heading"

@Composable
private fun CategoryHeading(
  row: CategoryRow,
  selected: Boolean,
  count: Int?,
  onFocus: (String) -> Unit,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = KeelTvTheme.colors
  FocusRow(
    onClick = { onSelect(row.key) },
    modifier = modifier
      .fillMaxWidth()
      .height(ROW_HEIGHT)
      .onFocusChanged { if (it.isFocused) onFocus(row.key) },
  ) { focused ->
    val name = when {
      focused -> colors.onFocus
      selected -> colors.accent
      row.whole -> colors.textPrimary
      else -> colors.textSecondary
    }
    val quiet = when {
      focused -> colors.onFocusMuted
      selected -> colors.accent
      else -> colors.textTertiary
    }
    Row(
      modifier = Modifier.fillMaxSize().padding(start = 6.dp, end = KeelTvTheme.space.s2),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        Modifier
          .width(MARK_WIDTH)
          .height(14.dp)
          .background(
            if (selected && !focused) colors.accent else Color.Transparent,
            RoundedCornerShape(1.dp),
          ),
      )
      Spacer(Modifier.width(KeelTvTheme.space.s2))
      if (row.chip != null) {
        Text(
          text = row.chip,
          style = KeelTvTheme.type.label,
          color = quiet,
          maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
      }
      Text(
        text = row.label,
        style = KeelTvTheme.type.control.copy(
          fontWeight = if (row.whole || selected) FontWeight.Medium else FontWeight.Normal,
        ),
        color = name,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      if (count != null) {
        Spacer(Modifier.width(KeelTvTheme.space.s2))
        Text(
          text = tally(count),
          style = KeelTvTheme.type.micro,
          color = if (focused) colors.onFocusMuted else colors.textTertiary,
          maxLines = 1,
        )
      }
    }
  }
}

fun tally(value: Int): String = String.format(java.util.Locale.getDefault(), "%,d", value)

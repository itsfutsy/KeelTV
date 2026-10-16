package com.keeltv.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private const val PIVOT = 0.3f

/**
 * A bring-into-view that never scrolls.
 *
 * On a box that reports `FEATURE_LEANBACK` Compose's focus scroll is a pivot: every focus move
 * asks the scrollable to put the focused child 30% down the viewport, and `ContentInViewNode`
 * answers by springing there over a dozen frames, each of which measures the whole lazy list
 * again and each of which redraws the window. Returning zero here means the request is satisfied
 * the moment it is made, so no animation is ever launched; [pivotTo] then asks for the position
 * the spring would have ended at, once.
 */
@OptIn(ExperimentalFoundationApi::class)
private val StandStill = object : BringIntoViewSpec {
  override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PivotScroll(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalBringIntoViewSpec provides StandStill, content = content)
}

/**
 * Ask for the row at [index] to be on the pivot.
 *
 * A request rather than a scroll, and that is the whole point: it writes a position and marks the
 * list for measurement, so presses arriving inside one frame collapse into a single pass rather
 * than each forcing one of their own, and the list is never further behind the remote than the
 * frame being drawn.
 *
 * The position is expressed as the row that should be at the top rather than as the focused row
 * with a negative offset, although both land the same pixels. A lazy list keys its item map off
 * whatever index it is told is first, in blocks of thirty; naming a row nine below the real one
 * flips that block every third press and puts the whole visible list back through composition.
 * The row above is walked back to instead, which only ever names a row the list is already
 * showing.
 */
fun LazyListState.pivotTo(index: Int) {
  val info = layoutInfo
  val pivot = ((info.viewportEndOffset - info.viewportStartOffset) * PIVOT).toInt()
  if (pivot <= 0) return
  val rows = info.visibleItemsInfo
  val focused = rows.firstOrNull { it.index == index }
  if (focused == null) {
    requestScrollToItem(index, -pivot)
    return
  }
  val top = focused.offset - pivot
  val first = rows.lastOrNull { it.offset <= top }
  if (first == null) {
    requestScrollToItem(index, -pivot)
    return
  }
  requestScrollToItem(first.index, top - first.offset)
}

fun LazyGridState.pivotTo(index: Int) {
  val info = layoutInfo
  val pivot = ((info.viewportEndOffset - info.viewportStartOffset) * PIVOT).toInt()
  if (pivot <= 0) return
  val tiles = info.visibleItemsInfo
  val focused = tiles.firstOrNull { it.index == index }
  if (focused == null) {
    requestScrollToItem(index, -pivot)
    return
  }
  val top = focused.offset.y - pivot
  val first = tiles.lastOrNull { it.offset.y <= top }
  if (first == null) {
    requestScrollToItem(index, -pivot)
    return
  }
  requestScrollToItem(first.index, top - first.offset.y)
}

fun LazyGridState.rowTo(index: Int, columns: Int) {
  val first = index - index % columns.coerceAtLeast(1)
  if (firstVisibleItemIndex == first && firstVisibleItemScrollOffset == 0) return
  requestScrollToItem(first)
}

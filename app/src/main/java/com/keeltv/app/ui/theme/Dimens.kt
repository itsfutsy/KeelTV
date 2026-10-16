package com.keeltv.app.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Spacing(
  val s1: Dp = 4.dp,
  val s2: Dp = 8.dp,
  val s3: Dp = 12.dp,
  val s4: Dp = 16.dp,
  val s5: Dp = 20.dp,
  val s6: Dp = 24.dp,
  val s7: Dp = 32.dp,
  val s8: Dp = 48.dp,
)

@Immutable
data class Radii(
  val chip: Dp = 4.dp,
  val row: Dp = 6.dp,
  val card: Dp = 8.dp,
  val control: Dp = 10.dp,
  val panel: Dp = 14.dp,
  val modal: Dp = 16.dp,
)

@Immutable
data class Dimens(
  val edge: Dp = 40.dp,
  val screenPadding: PaddingValues = PaddingValues(horizontal = edge, vertical = 22.dp),
  val railCollapsed: Dp = 48.dp,
  val railExpanded: Dp = 196.dp,
  val railItemHeight: Dp = 42.dp,
  val sideColumn: Dp = 168.dp,
  val gridColumns: Int = 7,
  val gridColumnsWide: Int = 9,
  val cardWidth: Dp = 96.dp,
  val channelRowHeight: Dp = 42.dp,
  val controlHeight: Dp = 36.dp,
  val fieldHeight: Dp = 38.dp,
  val focusRing: Dp = 2.dp,
)

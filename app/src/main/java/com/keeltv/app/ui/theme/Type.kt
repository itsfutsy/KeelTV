package com.keeltv.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.keeltv.app.R

val Inter = FontFamily(
  Font(R.font.inter_regular, FontWeight.Normal),
  Font(R.font.inter_medium, FontWeight.Medium),
  Font(R.font.inter_semibold, FontWeight.SemiBold),
)

val Manrope = FontFamily(
  Font(R.font.manrope_bold, FontWeight.Bold),
  Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private const val FIGURES = "tnum"

@Immutable
data class KeelTvTypography(
  val display: TextStyle = TextStyle(
    fontFamily = Manrope, fontSize = 40.sp, lineHeight = 44.sp,
    letterSpacing = (-0.03).em, fontWeight = FontWeight.ExtraBold,
  ),
  val title: TextStyle = TextStyle(
    fontFamily = Manrope, fontSize = 24.sp, lineHeight = 30.sp,
    letterSpacing = (-0.015).em, fontWeight = FontWeight.ExtraBold,
  ),
  val section: TextStyle = TextStyle(
    fontFamily = Manrope, fontSize = 20.sp, lineHeight = 26.sp,
    letterSpacing = (-0.012).em, fontWeight = FontWeight.ExtraBold,
  ),
  val heading: TextStyle = TextStyle(
    fontFamily = Manrope, fontSize = 16.sp, lineHeight = 22.sp,
    letterSpacing = (-0.005).em, fontWeight = FontWeight.Bold,
  ),
  val sub: TextStyle = TextStyle(
    fontFamily = Inter, fontSize = 16.sp, lineHeight = 22.sp,
    letterSpacing = (-0.008).em, fontWeight = FontWeight.Medium, fontFeatureSettings = FIGURES,
  ),
  val body: TextStyle = TextStyle(
    fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp,
    letterSpacing = (-0.004).em, fontWeight = FontWeight.Normal, fontFeatureSettings = FIGURES,
  ),
  val control: TextStyle = TextStyle(
    fontFamily = Inter, fontSize = 14.sp, lineHeight = 19.sp,
    fontWeight = FontWeight.Medium, fontFeatureSettings = FIGURES,
  ),
  val small: TextStyle = TextStyle(
    fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp,
    fontWeight = FontWeight.Normal, fontFeatureSettings = FIGURES,
  ),
  val micro: TextStyle = TextStyle(
    fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp,
    letterSpacing = 0.002.em, fontWeight = FontWeight.Medium, fontFeatureSettings = FIGURES,
  ),
  val label: TextStyle = TextStyle(
    fontFamily = Inter, fontSize = 11.sp, lineHeight = 14.sp,
    fontWeight = FontWeight.SemiBold, fontFeatureSettings = FIGURES,
  ),
)

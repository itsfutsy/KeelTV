package com.keeltv.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class KeelTvColors(
  val bgApp: Color = Color(0xFF0F0F11),
  val bgLift: Color = Color(0xFF16161B),
  val bgSink: Color = Color(0xFF0A0A0C),
  val bgSurface: Color = Color(0xFF151518),
  val bgRaised: Color = Color(0xFF1B1C21),
  val bgControl: Color = Color(0x13F2F2F4),
  val bgHover: Color = Color(0xFF24252B),
  val bgActive: Color = Color(0xFF2A2A30),
  val bgOverlay: Color = Color(0xBD0F0F11),

  val textPrimary: Color = Color(0xFFF2F2F4),
  val textProse: Color = Color(0xFFD6D6DB),
  val textSecondary: Color = Color(0xFFB9BBC3),
  val textTertiary: Color = Color(0xFF83868F),
  val textDisabled: Color = Color(0xFF5C5C66),

  val focus: Color = Color(0xFFF2F2F4),
  val onFocus: Color = Color(0xFF0F0F11),
  val onFocusMuted: Color = Color(0x9E0F0F11),

  val line: Color = Color(0x12FFFFFF),
  val lineBright: Color = Color(0x24FFFFFF),

  val accent: Color = Color(0xFF26A5E4),
  val accentDim: Color = Color(0xFF1D3544),
  val onAccent: Color = Color(0xFF0F0F11),

  val danger: Color = Color(0xFFF0555F),
  val dangerDim: Color = Color(0x1FF0555F),
)

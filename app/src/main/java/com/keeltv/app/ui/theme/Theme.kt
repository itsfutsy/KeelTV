package com.keeltv.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val LocalKeelTvColors = staticCompositionLocalOf { KeelTvColors() }
private val LocalKeelTvTypography = staticCompositionLocalOf { KeelTvTypography() }
private val LocalSpacing = staticCompositionLocalOf { Spacing() }
private val LocalRadii = staticCompositionLocalOf { Radii() }
private val LocalDimens = staticCompositionLocalOf { Dimens() }

object KeelTvTheme {
  val colors: KeelTvColors
    @Composable @ReadOnlyComposable get() = LocalKeelTvColors.current
  val type: KeelTvTypography
    @Composable @ReadOnlyComposable get() = LocalKeelTvTypography.current
  val space: Spacing
    @Composable @ReadOnlyComposable get() = LocalSpacing.current
  val radii: Radii
    @Composable @ReadOnlyComposable get() = LocalRadii.current
  val dimens: Dimens
    @Composable @ReadOnlyComposable get() = LocalDimens.current
}

@Composable
fun KeelTvTheme(content: @Composable () -> Unit) {
  val colors = KeelTvColors()
  val scheme = darkColorScheme(
    primary = colors.accent,
    onPrimary = colors.onAccent,
    primaryContainer = colors.accentDim,
    onPrimaryContainer = colors.accent,
    secondary = colors.bgActive,
    onSecondary = colors.textPrimary,
    background = colors.bgApp,
    onBackground = colors.textPrimary,
    surface = colors.bgSurface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.bgRaised,
    onSurfaceVariant = colors.textSecondary,
    border = colors.line,
    borderVariant = colors.lineBright,
    error = colors.danger,
    onError = Color.White,
    scrim = colors.bgOverlay,
  )

  CompositionLocalProvider(
    LocalKeelTvColors provides colors,
    LocalKeelTvTypography provides KeelTvTypography(),
    LocalSpacing provides Spacing(),
    LocalRadii provides Radii(),
    LocalDimens provides Dimens(),
    LocalContentColor provides colors.textPrimary,
  ) {
    MaterialTheme(colorScheme = scheme, content = content)
  }
}

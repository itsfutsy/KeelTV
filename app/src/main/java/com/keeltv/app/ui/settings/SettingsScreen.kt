package com.keeltv.app.ui.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.keeltv.app.ui.LocalActiveSource
import com.keeltv.app.ui.components.FocusRow
import com.keeltv.app.ui.components.ScreenHeader
import com.keeltv.app.ui.theme.KeelTvTheme

enum class SettingsPane(val label: String, val icon: ImageVector) {
  SOURCES("Provider", Icons.Outlined.SettingsInputAntenna),
  GUIDE("Guide", Icons.Outlined.DateRange),
  PLAYBACK("Playback", Icons.Outlined.PlayCircleOutline),
  LANGUAGE("Language", Icons.Outlined.Language),
  ABOUT("About", Icons.Outlined.Info),
}

@Composable
fun SettingsScreen() {
  val source = LocalActiveSource.current
  var pane by remember { mutableStateOf(SettingsPane.SOURCES) }
  val colors = KeelTvTheme.colors

  Row(Modifier.fillMaxSize().padding(start = 40.dp)) {
    Column(
      modifier = Modifier
        .width(196.dp)
        .fillMaxHeight()
        .padding(top = 22.dp, bottom = 22.dp)
        .focusGroup(),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      ScreenHeader(title = "Settings", subtitle = source?.name ?: "No source yet")
      Spacer(Modifier.height(KeelTvTheme.space.s5))
      SettingsPane.entries.forEach { entry ->
        val on = entry == pane
        FocusRow(
          onClick = { pane = entry },
          selected = on,
          shape = RoundedCornerShape(9.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .onFocusChanged { if (it.isFocused) pane = entry },
        ) { focused ->
          val tint = when {
            focused -> colors.onFocus
            on -> colors.accent
            else -> colors.textSecondary
          }
          Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(entry.icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(
              text = entry.label,
              style = KeelTvTheme.type.control.copy(fontWeight = if (on || focused) FontWeight.SemiBold else FontWeight.Medium),
              color = tint,
            )
          }
        }
      }
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(start = 32.dp, end = 40.dp, top = 26.dp, bottom = 22.dp)
        .focusGroup(),
      verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
    ) {
      when (pane) {
        SettingsPane.SOURCES -> SourcesPane()
        SettingsPane.GUIDE -> GuidePane()
        SettingsPane.PLAYBACK -> PlaybackPane()
        SettingsPane.LANGUAGE -> LanguagePane()
        SettingsPane.ABOUT -> AboutPane()
      }
    }
  }
}

@Composable
fun PaneTitle(text: String) {
  Text(
    text,
    style = KeelTvTheme.type.section,
    color = KeelTvTheme.colors.textPrimary,
    modifier = Modifier.padding(bottom = KeelTvTheme.space.s1),
  )
}

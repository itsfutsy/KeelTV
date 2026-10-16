package com.keeltv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.keeltv.app.ui.LocalContainer
import com.keeltv.app.ui.misc.Note
import com.keeltv.app.ui.movies.settleFocus
import com.keeltv.app.ui.theme.KeelTvTheme
import com.keeltv.app.update.Release
import kotlinx.coroutines.launch

@Composable
fun UpdatePrompt(release: Release, onDismiss: () -> Unit) {
  val updater = LocalContainer.current.updater
  val scope = rememberCoroutineScope()
  var downloading by remember { mutableStateOf(false) }
  var failed by remember { mutableStateOf(false) }
  val first = remember { FocusRequester() }
  LaunchedEffect(Unit) { settleFocus(first) }

  Dialog(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .width(420.dp)
        .clip(RoundedCornerShape(KeelTvTheme.radii.modal))
        .background(KeelTvTheme.colors.bgSurface)
        .padding(KeelTvTheme.space.s5)
        .focusGroup(),
      verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
    ) {
      Text(
        text = "Keel TV ${release.versionName} is available",
        style = KeelTvTheme.type.heading,
        color = KeelTvTheme.colors.textPrimary,
      )
      Note(if (failed) "The update failed. Try again." else "Android will ask you to confirm the install.")
      Row(horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3)) {
        TvButton(
          text = if (downloading) "Downloading" else "Update",
          tone = ButtonTone.Primary,
          modifier = Modifier.focusRequester(first),
          onClick = {
            if (!downloading) {
              downloading = true
              failed = false
              scope.launch {
                runCatching { updater.install() }
                  .onSuccess { onDismiss() }
                  .onFailure { failed = true }
                downloading = false
              }
            }
          },
        )
        TvButton(text = "Later", onClick = onDismiss)
      }
    }
  }
}

package com.keeltv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.keeltv.app.data.model.SourceKind
import com.keeltv.app.data.model.SourceTest
import com.keeltv.app.data.repo.SourceDraft
import com.keeltv.app.ui.LocalContainer
import com.keeltv.app.ui.components.ButtonTone
import com.keeltv.app.ui.components.SourceFields
import com.keeltv.app.ui.components.SourceTestResult
import com.keeltv.app.ui.components.TvButton
import com.keeltv.app.ui.misc.TvChip
import com.keeltv.app.ui.theme.KeelTvTheme
import kotlinx.coroutines.launch

@Composable
fun SourceForm(
  initial: SourceDraft,
  submitLabel: String,
  onSubmit: suspend (SourceDraft) -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
  showName: Boolean = true,
) {
  val container = LocalContainer.current
  val scope = rememberCoroutineScope()
  var draft by remember { mutableStateOf(initial) }
  var test by remember { mutableStateOf<SourceTest?>(null) }
  var testing by remember { mutableStateOf<String?>(null) }
  var saving by remember { mutableStateOf(false) }
  val focus = remember { FocusRequester() }

  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

  val xtream = draft.kind == SourceKind.XTREAM

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(KeelTvTheme.radii.panel))
      .background(KeelTvTheme.colors.bgSurface)
      .padding(KeelTvTheme.space.s5)
      .focusRequester(focus)
      .focusGroup(),
    verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2)) {
      TvChip("Xtream Codes", xtream, {
        draft = draft.copy(kind = SourceKind.XTREAM)
        test = null
      })
      TvChip("M3U playlist", !xtream, {
        draft = draft.copy(kind = SourceKind.M3U)
        test = null
      })
    }

    SourceFields(draft = draft, onDraft = { draft = it; test = null }, showName = showName)

    SourceTestResult(test)

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
    ) {
      TvButton("Cancel", onCancel, tone = ButtonTone.Plain, modifier = Modifier.weight(1f))
      TvButton(
        text = testing ?: "Test connection",
        onClick = {
          testing = "Testing"
          scope.launch {
            test = container.sources.test(draft) { testing = it }
            testing = null
          }
        },
        enabled = draft.complete && testing == null,
        modifier = Modifier.weight(1f),
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
    ) {
      TvButton(
        text = if (saving) "Saving" else submitLabel,
        onClick = {
          saving = true
          scope.launch {
            runCatching { onSubmit(draft) }
            saving = false
          }
        },
        tone = ButtonTone.Primary,
        enabled = draft.complete && !saving,
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.weight(1f))
    }
  }
}

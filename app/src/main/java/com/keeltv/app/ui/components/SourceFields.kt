package com.keeltv.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.tv.material3.Text
import com.keeltv.app.data.model.SourceKind
import com.keeltv.app.data.model.SourceTest
import com.keeltv.app.data.repo.SourceDraft
import com.keeltv.app.ui.theme.KeelTvTheme

@Composable
fun SourceFields(
  draft: SourceDraft,
  onDraft: (SourceDraft) -> Unit,
  modifier: Modifier = Modifier,
  showName: Boolean = true,
) {
  val xtream = draft.kind == SourceKind.XTREAM
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s3),
  ) {
    if (showName) {
      TvTextField(
        label = "Name",
        value = draft.name,
        onValueChange = { onDraft(draft.copy(name = it)) },
        placeholder = if (xtream) "Named after your provider" else "Named after the playlist",
      )
    }
    TvTextField(
      label = if (xtream) "Server" else "Playlist URL",
      value = draft.url,
      onValueChange = { onDraft(draft.copy(url = it)) },
      placeholder = if (xtream) "http://host:8080" else "http://host/get.php?...",
      keyboardType = KeyboardType.Uri,
    )
    if (xtream) {
      TvTextField("Username", draft.username, { onDraft(draft.copy(username = it)) })
      TvTextField("Password", draft.password, { onDraft(draft.copy(password = it)) }, masked = true)
    }
    TvTextField(
      label = "Guide URL (optional)",
      value = draft.epgUrl,
      onValueChange = { onDraft(draft.copy(epgUrl = it)) },
      placeholder = if (xtream) "Defaults to xmltv.php on the same server" else "XMLTV URL",
      keyboardType = KeyboardType.Uri,
    )
  }
}

@Composable
fun SourceTestResult(test: SourceTest?, modifier: Modifier = Modifier) {
  if (test == null) return
  Column(modifier, verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s1)) {
    Text(
      text = test.message,
      style = KeelTvTheme.type.small,
      color = if (test.ok) KeelTvTheme.colors.accent else KeelTvTheme.colors.danger,
    )
    val probe = test.probe
    if (probe != null) {
      Text(
        "${probe.live} live, ${probe.movies} movies, ${probe.series} series (${probe.counts})",
        style = KeelTvTheme.type.micro,
        color = KeelTvTheme.colors.textTertiary,
      )
    }
  }
}

package com.keeltv.app.ui.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.keeltv.app.data.model.EpgChannel
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.ui.LocalContainer
import com.keeltv.app.ui.components.FocusRow
import com.keeltv.app.ui.theme.KeelTvTheme
import kotlinx.coroutines.launch

@Composable
fun ChannelMenu(
  sourceId: String,
  item: MediaItem,
  favourite: Boolean,
  onPlay: () -> Unit,
  onDismiss: () -> Unit,
) {
  val container = LocalContainer.current
  val scope = rememberCoroutineScope()
  val colors = KeelTvTheme.colors
  var picking by remember { mutableStateOf(false) }
  val first = remember { FocusRequester() }

  BackHandler(enabled = true) { if (picking) picking = false else onDismiss() }
  LaunchedEffect(picking) { runCatching { first.requestFocus() } }

  Box(
    modifier = Modifier.fillMaxSize().background(colors.bgOverlay),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .width(400.dp)
        .clip(RoundedCornerShape(KeelTvTheme.radii.modal))
        .background(colors.bgRaised)
        .padding(KeelTvTheme.space.s5)
        .focusGroup(),
      verticalArrangement = Arrangement.spacedBy(KeelTvTheme.space.s2),
    ) {
      Text(
        text = item.title.ifEmpty { item.name },
        style = KeelTvTheme.type.heading,
        color = colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = item.name,
        style = KeelTvTheme.type.micro,
        color = colors.textTertiary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(KeelTvTheme.space.s2))

      if (picking) {
        GuidePicker(
          sourceId = sourceId,
          item = item,
          firstFocus = first,
          onChoose = { channelId ->
            scope.launch {
              container.epg.setOverride(sourceId, item.id, channelId)
              onDismiss()
            }
          },
        )
      } else {
        MenuItem("Play", first) { onPlay() }
        MenuItem(if (favourite) "Remove from favourites" else "Add to favourites") {
          scope.launch { container.library.toggleFavourite(sourceId, item.id) }
          onDismiss()
        }
        MenuItem("Set guide channel") { picking = true }
      }
    }
  }
}

@Composable
private fun MenuItem(text: String, focus: FocusRequester? = null, onClick: () -> Unit) {
  FocusRow(
    onClick = onClick,
    modifier = Modifier
      .fillMaxWidth()
      .height(40.dp)
      .then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
  ) { focused ->
    Text(
      text = text,
      style = KeelTvTheme.type.body,
      color = if (focused) KeelTvTheme.colors.onFocus else KeelTvTheme.colors.textPrimary,
      modifier = Modifier.padding(horizontal = KeelTvTheme.space.s4),
    )
  }
}

@Composable
private fun GuidePicker(
  sourceId: String,
  item: MediaItem,
  firstFocus: FocusRequester,
  onChoose: (String?) -> Unit,
) {
  val container = LocalContainer.current
  var options by remember { mutableStateOf<List<EpgChannel>?>(null) }

  LaunchedEffect(item.id) {
    options = runCatching {
      container.epg.searchChannels(sourceId, item.title.ifEmpty { item.name })
    }.getOrDefault(emptyList())
  }

  Text(
    text = "Guide channel",
    style = KeelTvTheme.type.micro,
    color = KeelTvTheme.colors.textTertiary,
  )
  MenuItem("Use the automatic match", firstFocus) { onChoose(null) }

  val list = options
  when {
    list == null -> Text(
      text = "Searching the guide…",
      style = KeelTvTheme.type.small,
      color = KeelTvTheme.colors.textTertiary,
      modifier = Modifier.padding(KeelTvTheme.space.s4),
    )
    list.isEmpty() -> Text(
      text = "No guide channel matches that name.",
      style = KeelTvTheme.type.small,
      color = KeelTvTheme.colors.textTertiary,
      modifier = Modifier.padding(KeelTvTheme.space.s4),
    )
    else -> LazyColumn(
      modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      items(list, key = { it.channelId }) { channel ->
        FocusRow(
          onClick = { onChoose(channel.channelId) },
          selected = channel.channelId == item.guideChannelId,
          modifier = Modifier.fillMaxWidth().height(42.dp),
        ) { focused ->
          Column(Modifier.padding(horizontal = KeelTvTheme.space.s4)) {
            Text(
              text = channel.name,
              style = KeelTvTheme.type.small,
              color = when {
                focused -> KeelTvTheme.colors.onFocus
                channel.channelId == item.guideChannelId -> KeelTvTheme.colors.accent
                else -> KeelTvTheme.colors.textSecondary
              },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = channel.channelId,
              style = KeelTvTheme.type.micro,
              color = if (focused) KeelTvTheme.colors.onFocusMuted else KeelTvTheme.colors.textTertiary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}

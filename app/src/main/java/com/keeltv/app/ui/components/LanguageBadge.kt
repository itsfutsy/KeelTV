package com.keeltv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.keeltv.app.core.languageCode
import com.keeltv.app.core.mediaLanguage
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.ui.theme.KeelTvTheme

@Composable
fun LanguageBadge(item: MediaItem, modifier: Modifier = Modifier) {
  val hint = mediaLanguage(item) ?: return
  Text(
    text = languageCode(hint.label),
    style = KeelTvTheme.type.label,
    color = Color(0xD6F5F5F7),
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier
      .clip(RoundedCornerShape(KeelTvTheme.radii.chip))
      .background(Color(0x8A0F0F11))
      .padding(horizontal = 4.dp, vertical = 1.dp),
  )
}

fun shownTitle(item: MediaItem): String {
  val title = item.title.ifEmpty { item.name }
  val code = mediaLanguage(item)?.let { languageCode(it.label) } ?: return title
  val tail = " ($code)"
  return if (title.length > tail.length && title.endsWith(tail)) title.dropLast(tail.length) else title
}

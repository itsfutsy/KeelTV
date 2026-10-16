package com.keeltv.app.ui.shows

import androidx.compose.runtime.Composable
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.ui.movies.BrowseScreen

@Composable
fun ShowsScreen(onOpen: (itemId: String) -> Unit) {
  BrowseScreen(kind = MediaKind.SERIES, onOpen = onOpen)
}

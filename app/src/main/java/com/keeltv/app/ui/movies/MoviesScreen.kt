package com.keeltv.app.ui.movies

import androidx.compose.runtime.Composable
import com.keeltv.app.data.model.MediaKind

@Composable
fun MoviesScreen(onOpen: (itemId: String) -> Unit) {
  BrowseScreen(kind = MediaKind.MOVIE, onOpen = onOpen)
}

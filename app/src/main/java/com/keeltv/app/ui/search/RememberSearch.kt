package com.keeltv.app.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.data.repo.SearchAnswer
import com.keeltv.app.ui.LocalContainer

@Composable
fun rememberSearch(sourceId: String?, query: String, kind: MediaKind?, language: String?): SearchAnswer? {
  val catalog = LocalContainer.current.catalog
  val key = listOf(sourceId, query, kind, language)
  val searching = sourceId != null && query.length >= MIN_QUERY
  var read by remember { mutableStateOf<Pair<List<Any?>, SearchAnswer>?>(null) }
  val recalled = remember(key) { if (searching) catalog.recall(sourceId!!, query, kind, language) else null }
  val answer = if (searching) read?.takeIf { it.first == key }?.second ?: recalled else SearchAnswer.NONE
  LaunchedEffect(key) {
    if (answer != null || language == null) return@LaunchedEffect
    read = key to runCatching { catalog.search(sourceId!!, query, kind, language) }.getOrDefault(SearchAnswer.NONE)
  }
  return answer
}

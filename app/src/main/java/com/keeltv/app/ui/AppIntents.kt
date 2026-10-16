package com.keeltv.app.ui

import android.app.SearchManager
import android.content.Intent
import com.keeltv.app.tv.DeepLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AppIntent {
  data class Search(val query: String) : AppIntent

  data class Play(val itemId: String, val episodeId: String?, val startAtMs: Long) : AppIntent
}

object AppIntents {
  private val _pending = MutableStateFlow<AppIntent?>(null)
  val pending: StateFlow<AppIntent?> = _pending.asStateFlow()

  fun offer(intent: AppIntent) {
    _pending.value = intent
  }

  fun consume(intent: AppIntent) {
    _pending.compareAndSet(intent, null)
  }

  /**
   * What the box sent us. `ACTION_SEARCH` is the remote's microphone with the app in front and
   * `SEARCH_ACTION` is the assistant answering "search X in Keel TV"; both carry the words in
   * `SearchManager.QUERY`.
   */
  fun parse(intent: Intent?): AppIntent? {
    if (intent == null) return null
    val action = intent.action
    if (action == Intent.ACTION_SEARCH || action == GOOGLE_SEARCH_ACTION || action == Intent.ACTION_ASSIST) {
      val query = intent.getStringExtra(SearchManager.QUERY)?.trim().orEmpty()
      return if (query.isEmpty()) null else AppIntent.Search(query)
    }
    if (action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    if (uri.scheme != DeepLink.SCHEME) return null
    return when (uri.authority) {
      "play" -> uri.getQueryParameter("item")?.takeIf { it.isNotEmpty() }?.let { item ->
        AppIntent.Play(
          itemId = item,
          episodeId = uri.getQueryParameter("episode")?.takeIf { it.isNotEmpty() },
          startAtMs = uri.getQueryParameter("at")?.toLongOrNull() ?: 0L,
        )
      }
      "search" -> uri.getQueryParameter("q")?.trim()?.takeIf { it.isNotEmpty() }?.let(AppIntent::Search)
      else -> null
    }
  }

  /** What the assistant sends for "search X in <app>". */
  private const val GOOGLE_SEARCH_ACTION = "com.google.android.gms.actions.SEARCH_ACTION"
}

object SectionRequest {
  private val _wanted = MutableStateFlow<Section?>(null)
  val wanted: StateFlow<Section?> = _wanted.asStateFlow()

  fun open(section: Section) {
    _wanted.value = section
  }

  fun take(section: Section) {
    _wanted.compareAndSet(section, null)
  }
}

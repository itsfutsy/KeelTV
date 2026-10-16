package com.keeltv.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.keeltv.app.AppContainer
import com.keeltv.app.data.model.Source

val LocalContainer = staticCompositionLocalOf<AppContainer> {
  error("No AppContainer in this composition")
}

val LocalActiveSource = staticCompositionLocalOf<Source?> { null }

object Routes {
  const val ONBOARDING = "onboarding"
  const val MAIN = "main"
  const val DETAIL = "detail/{itemId}"
  const val PLAYER = "player/{itemId}?episodeId={episodeId}&startAt={startAt}&scope={scope}"

  fun detail(itemId: String) = "detail/${java.net.URLEncoder.encode(itemId, "UTF-8")}"

  fun player(
    itemId: String,
    episodeId: String? = null,
    startAtMs: Long = 0,
    scope: String = "",
  ): String {
    val id = java.net.URLEncoder.encode(itemId, "UTF-8")
    val ep = episodeId?.let { java.net.URLEncoder.encode(it, "UTF-8") }.orEmpty()
    val sc = java.net.URLEncoder.encode(scope, "UTF-8")
    return "player/$id?episodeId=$ep&startAt=$startAtMs&scope=$sc"
  }
}

enum class Section(val route: String, val label: String, val icon: ImageVector) {
  LIVE("live", "Live TV", Icons.Outlined.LiveTv),
  MOVIES("movies", "Movies", Icons.Outlined.Movie),
  SHOWS("shows", "TV Shows", Icons.Outlined.Tv),
  LIBRARY("library", "Library", Icons.Outlined.StarOutline),
  SEARCH("search", "Search", Icons.Outlined.Search),
  SETTINGS("settings", "Settings", Icons.Outlined.Tune);

  companion object {
    fun of(route: String?): Section = entries.firstOrNull { it.route == route } ?: LIVE
  }
}

@Composable
fun container(): AppContainer = LocalContainer.current

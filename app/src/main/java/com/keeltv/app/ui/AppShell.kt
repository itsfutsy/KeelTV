package com.keeltv.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.ui.components.NavRail
import com.keeltv.app.ui.components.UpdatePrompt
import com.keeltv.app.ui.components.appBackground
import com.keeltv.app.ui.detail.DetailScreen
import com.keeltv.app.ui.library.LibraryScreen
import com.keeltv.app.ui.live.LiveScreen
import com.keeltv.app.ui.movies.BrowseRequest
import com.keeltv.app.ui.movies.MoviesScreen
import com.keeltv.app.ui.onboarding.OnboardingScreen
import com.keeltv.app.ui.player.PlayerScreen
import com.keeltv.app.ui.search.SearchScreen
import com.keeltv.app.ui.settings.SettingsScreen
import com.keeltv.app.ui.shows.ShowsScreen
import com.keeltv.app.ui.theme.KeelTvTheme
import com.keeltv.app.update.Release

@Composable
fun KeelTvRoot() {
  val container = LocalContainer.current
  val sources by container.sources.sources.collectAsStateWithLifecycle(initialValue = null)
  val active by container.sources.activeSource.collectAsStateWithLifecycle(initialValue = null)
  val nav = rememberNavController()

  val known = sources ?: return
  // Decided once. Recomputing it would rebuild the graph the moment onboarding saves the source,
  // pulling the sync screen out from under the refresh it is reporting on.
  val start = remember { if (known.isEmpty()) Routes.ONBOARDING else Routes.MAIN }

  val pending by AppIntents.pending.collectAsStateWithLifecycle()
  LaunchedEffect(pending) {
    when (val request = pending) {
      is AppIntent.Play -> {
        AppIntents.consume(request)
        nav.navigate(Routes.player(request.itemId, request.episodeId, request.startAtMs)) {
          popUpTo(Routes.MAIN)
        }
      }
      is AppIntent.Search -> nav.popBackStack(Routes.MAIN, inclusive = false)
      null -> Unit
    }
  }

  var release by remember { mutableStateOf<Release?>(null) }
  LaunchedEffect(Unit) { release = runCatching { container.updater.check() }.getOrNull() }
  val route by nav.currentBackStackEntryAsState()
  release?.takeIf { route?.destination?.route == Routes.MAIN }?.let {
    UpdatePrompt(it, onDismiss = { release = null })
  }

  CompositionLocalProvider(LocalActiveSource provides active) {
    NavHost(
      navController = nav,
      startDestination = start,
      modifier = Modifier.fillMaxSize().appBackground(),
      enterTransition = { fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.96f) },
      exitTransition = { fadeOut(tween(160)) },
      popEnterTransition = { fadeIn(tween(200)) },
      popExitTransition = { fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.96f) },
    ) {
      composable(Routes.ONBOARDING) {
        OnboardingScreen(
          onDone = {
            nav.navigate(Routes.MAIN) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
          },
        )
      }
      composable(Routes.MAIN) { MainShell(nav) }
      composable(Routes.DETAIL) { entry ->
        DetailScreen(
          itemId = entry.arguments?.getString("itemId").orEmpty().decodeArg(),
          onPlay = { itemId, episodeId, startAt -> nav.navigate(Routes.player(itemId, episodeId, startAt)) },
          onGenre = { kind, genre ->
            BrowseRequest.open(kind, genre)
            SectionRequest.open(if (kind == MediaKind.SERIES) Section.SHOWS else Section.MOVIES)
            nav.popBackStack(Routes.MAIN, inclusive = false)
          },
          onBack = { nav.popBackStack() },
        )
      }
      composable(Routes.PLAYER) { entry ->
        PlayerScreen(
          itemId = entry.arguments?.getString("itemId").orEmpty().decodeArg(),
          episodeId = entry.arguments?.getString("episodeId").orEmpty().decodeArg().ifEmpty { null },
          startAtMs = entry.arguments?.getString("startAt")?.toLongOrNull() ?: 0L,
          channelScope = entry.arguments?.getString("scope").orEmpty().decodeArg(),
          onBack = { nav.popBackStack() },
        )
      }
    }
  }
}

private fun String.decodeArg(): String =
  runCatching { java.net.URLDecoder.decode(this, "UTF-8") }.getOrDefault(this)

private fun goTo(sectionNav: NavHostController, section: Section) {
  sectionNav.navigate(section.route) {
    popUpTo(sectionNav.graph.startDestinationId) { saveState = true }
    launchSingleTop = true
    restoreState = true
  }
}

@Composable
private fun MainShell(rootNav: NavHostController) {
  val sectionNav = rememberNavController()
  val entry by sectionNav.currentBackStackEntryAsState()
  // The entry is null for the first frame after a trip to a title page, and reading that as Live
  // TV put the rail's focus there while the grid reloaded, so the next OK left the section.
  var shown by rememberSaveable { mutableStateOf(Section.LIVE) }
  val current = entry?.let { Section.of(it.destination.route) } ?: shown
  LaunchedEffect(current) { shown = current }
  val railFocus = remember { FocusRequester() }

  LaunchedEffect(Unit) { runCatching { railFocus.requestFocus() } }

  val pending by AppIntents.pending.collectAsStateWithLifecycle()
  LaunchedEffect(pending) { if (pending is AppIntent.Search) goTo(sectionNav, Section.SEARCH) }
  val wanted by SectionRequest.wanted.collectAsStateWithLifecycle()
  LaunchedEffect(wanted) {
    val want = wanted ?: return@LaunchedEffect
    SectionRequest.take(want)
    if (want != current) goTo(sectionNav, want)
  }

  val dimens = KeelTvTheme.dimens
  Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().padding(start = dimens.edge + dimens.railCollapsed)) {
      NavHost(
        navController = sectionNav,
        startDestination = Section.LIVE.route,
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(140)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(140)) },
      ) {
        composable(Section.LIVE.route) {
          LiveScreen(
            onOpen = { id, scope, startAt ->
              rootNav.navigate(Routes.player(id, startAtMs = startAt, scope = scope))
            },
          )
        }
        composable(Section.MOVIES.route) {
          MoviesScreen(onOpen = { rootNav.navigate(Routes.detail(it)) })
        }
        composable(Section.SHOWS.route) {
          ShowsScreen(onOpen = { rootNav.navigate(Routes.detail(it)) })
        }
        composable(Section.LIBRARY.route) {
          LibraryScreen(
            onOpen = { rootNav.navigate(Routes.detail(it)) },
            onPlay = { itemId, episodeId, startAt ->
              rootNav.navigate(Routes.player(itemId, episodeId, startAt))
            },
            onBrowse = { section -> goTo(sectionNav, section) },
          )
        }
        composable(Section.SEARCH.route) {
          SearchScreen(
            onOpen = { rootNav.navigate(Routes.detail(it)) },
            onPlay = { rootNav.navigate(Routes.player(it)) },
          )
        }
        composable(Section.SETTINGS.route) { SettingsScreen() }
      }
    }

    NavRail(
      focusRequester = railFocus,
      current = current,
      onSelect = { section -> if (section != current) goTo(sectionNav, section) },
    )
  }
}

package com.keeltv.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.playback.CaptionAppearance
import com.keeltv.app.playback.CaptionMode
import com.keeltv.app.playback.PlaybackPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
  val activeSourceId: String? = null,
  val languageFilter: String = "",
  val epgFillAuto: Boolean = true,
  val epgFillEnabled: Set<String> = emptySet(),
  val epgFillDisabled: Set<String> = emptySet(),
  val epgAutoRefreshHours: Int = 12,
  val collapseDuplicates: Boolean = true,
  val audioLanguage: String = "",
  val captionLanguage: String = "",
  val captionMode: String = CaptionMode.OFF,
  val captionSize: Int = 100,
  val captionColor: String = CaptionAppearance.WHITE,
  val captionBackground: Float = 0.6f,
  val recentSearches: List<String> = emptyList(),
  val movieSort: String = "",
  val seriesSort: String = "",
) {
  fun sort(kind: MediaKind): String = if (kind == MediaKind.SERIES) seriesSort else movieSort

  val playback: PlaybackPreferences
    get() = PlaybackPreferences(
      audioLanguage = audioLanguage,
      captionLanguage = captionLanguage,
      captionMode = captionMode,
      appearance = CaptionAppearance(captionSize, captionColor, captionBackground),
    )
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {
  private val store = context.applicationContext.dataStore

  val settings: Flow<Settings> = store.data.map { p ->
    Settings(
      activeSourceId = p[ACTIVE_SOURCE],
      languageFilter = p[LANGUAGE_FILTER] ?: "",
      epgFillAuto = p[EPG_FILL_AUTO] ?: true,
      epgFillEnabled = p[EPG_FILL_ENABLED] ?: emptySet(),
      epgFillDisabled = p[EPG_FILL_DISABLED] ?: emptySet(),
      epgAutoRefreshHours = p[EPG_REFRESH_HOURS] ?: 12,
      collapseDuplicates = p[COLLAPSE_DUPLICATES] ?: true,
      audioLanguage = p[AUDIO_LANGUAGE] ?: "",
      captionLanguage = p[CAPTION_LANGUAGE] ?: "",
      captionMode = p[CAPTION_MODE]?.takeIf { it in CaptionMode.ALL } ?: CaptionMode.OFF,
      captionSize = (p[CAPTION_SIZE] ?: 100).coerceIn(75, 200),
      captionColor = p[CAPTION_COLOR] ?: CaptionAppearance.WHITE,
      captionBackground = (p[CAPTION_BACKGROUND] ?: 0.6f).coerceIn(0f, 1f),
      recentSearches = p[RECENT_SEARCHES]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
      movieSort = p[MOVIE_SORT] ?: "",
      seriesSort = p[SERIES_SORT] ?: "",
    )
  }

  suspend fun setActiveSource(id: String?) = store.edit { p ->
    if (id == null) p.remove(ACTIVE_SOURCE) else p[ACTIVE_SOURCE] = id
  }

  suspend fun setLanguageFilter(language: String) = store.edit { it[LANGUAGE_FILTER] = language }

  suspend fun setBrowseSort(kind: MediaKind, wire: String) =
    store.edit { it[if (kind == MediaKind.SERIES) SERIES_SORT else MOVIE_SORT] = wire }

  suspend fun setEpgFillAuto(auto: Boolean) = store.edit { it[EPG_FILL_AUTO] = auto }

  suspend fun setFeedEnabled(feedId: String, enabled: Boolean) = store.edit { p ->
    val on = (p[EPG_FILL_ENABLED] ?: emptySet()).toMutableSet()
    val off = (p[EPG_FILL_DISABLED] ?: emptySet()).toMutableSet()
    if (enabled) { on.add(feedId); off.remove(feedId) } else { on.remove(feedId); off.add(feedId) }
    p[EPG_FILL_ENABLED] = on
    p[EPG_FILL_DISABLED] = off
  }

  suspend fun setEpgAutoRefreshHours(hours: Int) = store.edit { it[EPG_REFRESH_HOURS] = hours }

  suspend fun setCollapseDuplicates(on: Boolean) = store.edit { it[COLLAPSE_DUPLICATES] = on }

  suspend fun setAudioLanguage(code: String) = store.edit { it[AUDIO_LANGUAGE] = code.trim() }

  suspend fun setCaptionLanguage(code: String) = store.edit { it[CAPTION_LANGUAGE] = code.trim() }

  suspend fun setCaptionMode(mode: String) = store.edit { it[CAPTION_MODE] = mode }

  suspend fun setCaptionSize(percent: Int) =
    store.edit { it[CAPTION_SIZE] = percent.coerceIn(75, 200) }

  suspend fun setCaptionColor(color: String) = store.edit { it[CAPTION_COLOR] = color }

  suspend fun setCaptionBackground(opacity: Float) =
    store.edit { it[CAPTION_BACKGROUND] = opacity.coerceIn(0f, 1f) }

  suspend fun setCaptionAppearance(appearance: CaptionAppearance) = store.edit { p ->
    p[CAPTION_SIZE] = appearance.size.coerceIn(75, 200)
    p[CAPTION_COLOR] = appearance.color
    p[CAPTION_BACKGROUND] = appearance.background.coerceIn(0f, 1f)
  }

  suspend fun setRecentSearches(queries: List<String>) =
    store.edit { it[RECENT_SEARCHES] = queries.joinToString("\n") }

  suspend fun catalogRulesVersion(): Int = store.data.first()[LANGUAGE_RULES] ?: 0

  suspend fun setCatalogRulesVersion(version: Int) = store.edit { it[LANGUAGE_RULES] = version }

  private companion object {
    val ACTIVE_SOURCE = stringPreferencesKey("activeSourceId")
    val LANGUAGE_FILTER = stringPreferencesKey("languageFilter")
    val EPG_FILL_AUTO = booleanPreferencesKey("epgFillAuto")
    val EPG_FILL_ENABLED = stringSetPreferencesKey("epgFillEnabled")
    val EPG_FILL_DISABLED = stringSetPreferencesKey("epgFillDisabled")
    val EPG_REFRESH_HOURS = intPreferencesKey("epgAutoRefreshHours")
    val COLLAPSE_DUPLICATES = booleanPreferencesKey("collapseDuplicates")
    val AUDIO_LANGUAGE = stringPreferencesKey("audioLanguage")
    val CAPTION_LANGUAGE = stringPreferencesKey("captionLanguage")
    val CAPTION_MODE = stringPreferencesKey("captionMode")
    val CAPTION_SIZE = intPreferencesKey("captionSize")
    val CAPTION_COLOR = stringPreferencesKey("captionColor")
    val CAPTION_BACKGROUND = floatPreferencesKey("captionBackground")
    val RECENT_SEARCHES = stringPreferencesKey("recentSearches")
    val LANGUAGE_RULES = intPreferencesKey("languageRulesVersion")
    val MOVIE_SORT = stringPreferencesKey("movieSort")
    val SERIES_SORT = stringPreferencesKey("seriesSort")
  }
}

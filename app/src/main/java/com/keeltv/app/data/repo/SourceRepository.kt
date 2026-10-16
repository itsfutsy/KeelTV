package com.keeltv.app.data.repo

import com.keeltv.app.data.db.KeelTvDatabase
import com.keeltv.app.data.m3u.parseM3u
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.data.model.Source
import com.keeltv.app.data.model.SourceKind
import com.keeltv.app.data.model.SourceProbe
import com.keeltv.app.data.model.SourceTest
import com.keeltv.app.data.net.openStream
import com.keeltv.app.data.net.redactText
import com.keeltv.app.data.prefs.Settings
import com.keeltv.app.data.prefs.SettingsStore
import com.keeltv.app.data.xtream.XtreamClient
import com.keeltv.app.data.xtream.XtreamCredentials
import com.keeltv.app.data.xtream.normalizeBaseUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.UUID

data class SourceDraft(
  val kind: SourceKind = SourceKind.XTREAM,
  val name: String = "",
  val url: String = "",
  val username: String = "",
  val password: String = "",
  val epgUrl: String = "",
) {
  val complete: Boolean
    get() = url.isNotBlank() &&
      (kind == SourceKind.M3U || (username.isNotBlank() && password.isNotBlank()))
}

class SourceRepository(
  private val db: KeelTvDatabase,
  private val settings: SettingsStore,
  private val http: OkHttpClient,
) {
  val sources: Flow<List<Source>> = db.sources().observeAll()

  val activeSource: Flow<Source?> =
    combine(sources, settings.settings) { list, s -> pickActive(list, s) }

  private fun pickActive(list: List<Source>, s: Settings): Source? =
    list.firstOrNull { it.id == s.activeSourceId } ?: list.firstOrNull()

  suspend fun byId(id: String): Source? = db.sources().byId(id)

  fun clientFor(source: Source): XtreamClient? =
    if (source.kind == SourceKind.XTREAM) {
      XtreamClient(XtreamCredentials(source.url, source.username, source.password), http)
    } else {
      null
    }

  suspend fun add(draft: SourceDraft): Source {
    val named = if (draft.name.isBlank()) draft.copy(name = discoverName(draft)) else draft
    val source = named.toSource(UUID.randomUUID().toString(), System.currentTimeMillis())
    db.sources().upsert(source)
    settings.setActiveSource(source.id)
    return source
  }

  suspend fun update(source: Source) = db.sources().upsert(source)

  private suspend fun discoverName(draft: SourceDraft): String = withContext(Dispatchers.IO) {
    val greeting = if (draft.kind == SourceKind.XTREAM) {
      runCatching {
        XtreamClient(XtreamCredentials(draft.url, draft.username, draft.password), http).auth()
      }.getOrNull()?.takeIf { it.ok }?.message
    } else {
      null
    }
    sourceName(draft, greeting)
  }

  suspend fun remove(id: String) = withContext(Dispatchers.IO) {
    db.catalog().clearCatalog(id)
    db.epg().clearGuide(id)
    db.sources().delete(id)
    val remaining = db.sources().all()
    settings.setActiveSource(remaining.firstOrNull()?.id)
  }

  suspend fun setActive(id: String) = settings.setActiveSource(id)

  suspend fun test(draft: SourceDraft, onStep: (String) -> Unit = {}): SourceTest =
    withContext(Dispatchers.IO) {
      try {
        if (draft.kind == SourceKind.XTREAM) testXtream(draft, onStep) else testM3u(draft, onStep)
      } catch (err: Exception) {
        SourceTest(false, redactText(err.message ?: "Could not reach the provider."))
      }
    }

  private fun testXtream(draft: SourceDraft, onStep: (String) -> Unit): SourceTest {
    val client = XtreamClient(XtreamCredentials(draft.url, draft.username, draft.password), http)
    onStep("Signing in")
    val auth = client.auth()
    if (!auth.ok) return SourceTest(false, auth.message)
    onStep("Reading channels")
    val live = client.categories("probe", MediaKind.LIVE).size
    onStep("Reading movies")
    val movies = client.categories("probe", MediaKind.MOVIE).size
    onStep("Reading series")
    val series = client.categories("probe", MediaKind.SERIES).size
    return SourceTest(true, auth.message, SourceProbe(live, movies, series, "categories"))
  }

  private fun testM3u(draft: SourceDraft, onStep: (String) -> Unit): SourceTest {
    onStep("Reading the playlist")
    http.openStream(draft.url).use { download ->
      var live = 0
      var movies = 0
      var series = 0
      val result = parseM3u(download.source, "probe") { item ->
        when (item.kind) {
          MediaKind.LIVE -> live++
          MediaKind.MOVIE -> movies++
          MediaKind.SERIES -> series++
        }
      }
      return SourceTest(
        ok = result.items > 0,
        message = if (result.items > 0) "${result.items} entries" else "That playlist has no entries.",
        probe = SourceProbe(live, movies, series, "items"),
      )
    }
  }
}

fun SourceDraft.toSource(id: String, createdAt: Long): Source = Source(
  id = id,
  kind = kind,
  name = sourceName(this),
  url = if (kind == SourceKind.XTREAM) normalizeBaseUrl(url) else url.trim(),
  username = username.trim(),
  password = password,
  epgUrl = epgUrl.trim().ifEmpty { null },
  createdAt = createdAt,
)

fun Source.toDraft(): SourceDraft = SourceDraft(
  kind = kind,
  name = name,
  url = url,
  username = username,
  password = password,
  epgUrl = epgUrl.orEmpty(),
)

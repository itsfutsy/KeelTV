package com.keeltv.app.data.xtream

import com.keeltv.app.core.cleanTitle
import com.keeltv.app.core.fold
import com.keeltv.app.core.genreTags
import com.keeltv.app.core.isSeparatorName
import com.keeltv.app.core.parseHms
import com.keeltv.app.core.parseYear
import com.keeltv.app.core.searchText
import com.keeltv.app.data.model.Category
import com.keeltv.app.data.model.Episode
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.data.model.SeriesDetail
import com.keeltv.app.data.net.Row
import com.keeltv.app.data.net.USER_AGENT
import com.keeltv.app.data.net.bool
import com.keeltv.app.data.net.int
import com.keeltv.app.data.net.list
import com.keeltv.app.data.net.long
import com.keeltv.app.data.net.num
import com.keeltv.app.data.net.readObject
import com.keeltv.app.data.net.readRows
import com.keeltv.app.data.net.redactText
import com.keeltv.app.data.net.row
import com.keeltv.app.data.net.str
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.io.IOException
import java.net.URLEncoder

/** Provider sentinel meaning "this channel has no EPG", alongside null and "". */
const val NO_EPG_CHANNEL = "#.No.Channel.#"

class XtreamException(message: String, val status: Int? = null) : IOException(message)

private val BASE_URL = Regex("^(https?://)?([^/?#]+)", RegexOption.IGNORE_CASE)

fun normalizeBaseUrl(url: String): String {
  val trimmed = url.trim()
  val m = BASE_URL.find(trimmed) ?: return trimmed.trimEnd('/')
  val scheme = m.groupValues[1].ifEmpty { "http://" }
  return scheme.lowercase() + m.groupValues[2]
}

private val SAFE_EXTENSION = Regex("^[a-z0-9]{1,5}$")

fun safeExtension(ext: String?, fallback: String): String {
  if (ext.isNullOrBlank()) return fallback
  val cleaned = ext.trim().trimStart('.').lowercase()
  return if (SAFE_EXTENSION.matches(cleaned)) cleaned else fallback
}

data class AuthResult(
  val ok: Boolean,
  val message: String,
  val maxConnections: Int = 0,
  val status: String = "Unknown",
  val expiresAtSeconds: Long? = null,
)

data class XtreamCredentials(val url: String, val username: String, val password: String)

class XtreamClient(
  credentials: XtreamCredentials,
  private val http: OkHttpClient,
) {
  val baseUrl: String = normalizeBaseUrl(credentials.url)
  private val username = credentials.username
  private val password = credentials.password

  private fun apiUrl(params: Map<String, String>): HttpUrl {
    val builder = "$baseUrl/player_api.php".toHttpUrlOrNull()?.newBuilder()
      ?: throw XtreamException("That server address is not a URL: $baseUrl")
    builder.addQueryParameter("username", username)
    builder.addQueryParameter("password", password)
    for ((k, v) in params) builder.addQueryParameter(k, v)
    return builder.build()
  }

  private fun <T> request(
    params: Map<String, String>,
    context: String,
    onBytes: ((Long) -> Unit)? = null,
    read: (okio.BufferedSource) -> T,
  ): T {
    val url = apiUrl(params)
    var last: Exception? = null
    for (attempt in 0..1) {
      try {
        return requestOnce(url, context, onBytes, read)
      } catch (err: Exception) {
        last = err
        val status = (err as? XtreamException)?.status
        val retryable = attempt == 0 && (status == null || status >= 500 || status == 429)
        if (!retryable) break
        Thread.sleep(500)
      }
    }
    throw last as? XtreamException ?: XtreamException(redactText(last?.message ?: "Request failed"))
  }

  private fun <T> requestOnce(
    url: HttpUrl,
    context: String,
    onBytes: ((Long) -> Unit)?,
    read: (okio.BufferedSource) -> T,
  ): T {
    val request = Request.Builder()
      .url(url)
      .header("User-Agent", USER_AGENT)
      .header("Accept", "application/json, text/plain, */*")
      .build()

    http.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        throw XtreamException(errorMessageFromBody(response.code, response.message, response.body?.string()), response.code)
      }
      val body = response.body ?: throw XtreamException("$context: provider sent no body")
      val source = if (onBytes == null) body.source() else CountingSource(body.source(), onBytes).buffer()
      return read(source)
    }
  }

  fun auth(): AuthResult {
    val raw = try {
      request(emptyMap(), "auth") { readObject(it) }
    } catch (err: Exception) {
      val status = (err as? XtreamException)?.status
      val rejected = status == 401 || status == 403
      return AuthResult(
        ok = false,
        message = redactText(err.message ?: "Could not reach the provider"),
        status = if (rejected) "Failed" else "Unreachable",
      )
    }

    if (raw == null) return AuthResult(false, "Unexpected response from provider")

    // Wrong credentials answer `{"error":"Authentication failed"}`, not an auth blob.
    raw.str("error")?.let { return AuthResult(false, it, status = "Failed") }

    val info = raw.row("user_info")
      ?: return AuthResult(false, "Provider did not return account info")

    val status = info.str("status") ?: "Unknown"
    val authed = info.bool("auth")
    val active = status.equals("active", ignoreCase = true)
    val maxConnections = info.int("max_connections") ?: 1
    val expires = info.long("exp_date")?.takeIf { it > 0 }

    val message = when {
      !authed -> info.str("message") ?: "Authentication failed"
      !active -> "Account status: $status"
      else -> info.str("message") ?: "Connected"
    }
    return AuthResult(authed && active, message, maxConnections, status, expires)
  }

  fun categories(sourceId: String, kind: MediaKind): List<Category> {
    val action = when (kind) {
      MediaKind.LIVE -> "get_live_categories"
      MediaKind.MOVIE -> "get_vod_categories"
      MediaKind.SERIES -> "get_series_categories"
    }
    val out = ArrayList<Category>()
    request(mapOf("action" to action), action) { source ->
      readRows(source, action) { row ->
        val id = row.str("category_id") ?: return@readRows
        out.add(Category(sourceId, id, kind, row.str("category_name") ?: id))
      }
    }
    return out
  }

  fun items(sourceId: String, kind: MediaKind, categoryId: String): List<MediaItem> {
    val action = listAction(kind)
    val out = ArrayList<MediaItem>()
    request(mapOf("action" to action, "category_id" to categoryId), "$action&category_id=$categoryId") { source ->
      readRows(source, action) { row -> mapItem(sourceId, row, kind, categoryId)?.let(out::add) }
    }
    return out
  }

  fun allItems(sourceId: String, kind: MediaKind, onBytes: (Long) -> Unit, onItem: (MediaItem) -> Unit) {
    val action = listAction(kind)
    request(mapOf("action" to action), action, onBytes) { source ->
      readRows(source, action) { row -> mapItem(sourceId, row, kind, "")?.let(onItem) }
    }
  }

  /**
   * `get_vod_info` splits what `get_vod_streams` returns as one row across two objects:
   * `movie_data` carries the playable fields and `info` the descriptive ones.
   */
  fun vodDetail(sourceId: String, vodId: String): MediaItem {
    val raw = request(mapOf("action" to "get_vod_info", "vod_id" to vodId), "get_vod_info") { readObject(it) }
      ?: throw XtreamException("No film info returned for $vodId")
    raw.str("error")?.let { throw XtreamException(it) }

    val info = raw.row("info") ?: emptyMap()
    val data = raw.row("movie_data") ?: emptyMap()
    // An unknown id comes back as a well-formed response with both halves empty, not as an error.
    if (info.isEmpty() && data.isEmpty()) throw XtreamException("No film found for $vodId")

    val row = HashMap<String, Any?>(info)
    row.putAll(data)
    row["stream_id"] = data["stream_id"] ?: vodId
    row["stream_icon"] = info["movie_image"] ?: info["cover_big"]
    row["cover"] = info["cover_big"] ?: info["movie_image"]
    return mapMovie(sourceId, row, data.str("category_id") ?: "")
      ?: throw XtreamException("Film $vodId could not be read")
  }

  fun seriesDetail(sourceId: String, seriesId: String): SeriesDetail {
    val raw = request(mapOf("action" to "get_series_info", "series_id" to seriesId), "get_series_info") { readObject(it) }
      ?: throw XtreamException("No series info returned for $seriesId")
    raw.str("error")?.let { throw XtreamException(it) }

    val info = raw.row("info") ?: emptyMap()
    val merged = HashMap<String, Any?>(info)
    merged["series_id"] = info["series_id"] ?: seriesId
    val item = mapSeries(sourceId, merged, info.str("category_id") ?: "")
      ?: fallbackSeriesItem(sourceId, seriesId, info)

    val episodes = HashMap<Int, MutableList<Episode>>()
    // `episodes` is normally `{ "1": [...], "2": [...] }` but some deployments send an array.
    val buckets: List<Pair<String, Any?>> = when (val raws = raw["episodes"]) {
      is List<*> -> raws.mapIndexed { i, v -> (i + 1).toString() to v }
      is Map<*, *> -> raws.entries.map { it.key.toString() to it.value }
      else -> emptyList()
    }

    for ((key, value) in buckets) {
      val entries = value as? List<*> ?: continue
      val seasonFromKey = key.toIntOrNull() ?: 1
      val list = ArrayList<Episode>()
      for (entry in entries) {
        val rowEntry = entry as? Row ?: continue
        mapEpisode(rowEntry, seriesId, seasonFromKey, list.size + 1)?.let(list::add)
      }
      if (list.isEmpty()) continue
      episodes.getOrPut(list[0].season) { ArrayList() }.addAll(list)
    }
    for (list in episodes.values) list.sortBy { it.episodeNum }

    // The provider's `seasons` array routinely declares seasons it has no files for.
    val withEpisodes = episodes.keys.sorted()
    val declared = (raw.list("seasons") ?: emptyList())
      .mapNotNull { (it as? Row)?.int("season_number") }
      .distinct()
      .sorted()
    val seasons = withEpisodes.ifEmpty { declared }
    for (season in seasons) episodes.getOrPut(season) { ArrayList() }

    return SeriesDetail(
      item = item.copy(seasonCount = seasons.size.takeIf { it > 0 }),
      seasons = seasons,
      episodes = episodes.mapValues { it.value.toList() },
    )
  }

  /**
   * Direct provider stream url. All three forms 302-redirect to a token-signed CDN host, so the
   * consumer must follow redirects.
   *
   * Defaults match what these servers actually serve: live is MPEG-TS, VOD is `mkv`, series
   * episodes are usually `mp4`.
   */
  fun streamUrl(kind: MediaKind, streamId: Long, containerExtension: String? = null): String {
    val u = encode(username)
    val p = encode(password)
    return when (kind) {
      MediaKind.LIVE -> "$baseUrl/live/$u/$p/$streamId.${safeExtension(containerExtension, "ts")}"
      MediaKind.MOVIE -> "$baseUrl/movie/$u/$p/$streamId.${safeExtension(containerExtension, "mkv")}"
      MediaKind.SERIES -> "$baseUrl/series/$u/$p/$streamId.${safeExtension(containerExtension, "mp4")}"
    }
  }

  /**
   * Catch-up. A finished programme comes back as a fixed length recording rather than a live pipe,
   * so it plays as seekable VOD.
   *
   * The start is stamped in the box's own clock, which is what every Xtream client does and what
   * the servers expect; a box set to the wrong timezone gets the wrong hour of the archive.
   */
  fun timeshiftUrl(streamId: Long, startSeconds: Long, durationMinutes: Int): String =
    "$baseUrl/timeshift/${encode(username)}/${encode(password)}/" +
      "${durationMinutes.coerceAtLeast(1)}/${timeshiftStamp(startSeconds)}/$streamId.ts"

  fun xmltvUrl(): String = "$baseUrl/xmltv.php?username=${encode(username)}&password=${encode(password)}"
}

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

/** `2026-09-20:14-30`, the one date shape the timeshift path accepts. */
fun timeshiftStamp(startSeconds: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
  java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")
    .format(java.time.Instant.ofEpochSecond(startSeconds).atZone(zone))

private fun listAction(kind: MediaKind): String = when (kind) {
  MediaKind.LIVE -> "get_live_streams"
  MediaKind.MOVIE -> "get_vod_streams"
  MediaKind.SERIES -> "get_series"
}

private fun errorMessageFromBody(code: Int, statusText: String, body: String?): String {
  val statusLine = "HTTP $code $statusText".trim()
  val text = body?.take(500)?.trim().orEmpty()
  if (text.isEmpty()) return statusLine
  if (text.startsWith("{")) {
    runCatching {
      val parsed = readObject(Buffer().writeUtf8(text))
      val message = parsed?.str("error") ?: parsed?.str("message")
      if (message != null) return message
    }
  }
  return statusLine
}

private class CountingSource(delegate: Source, private val onBytes: (Long) -> Unit) :
  ForwardingSource(delegate) {
  private var total = 0L
  private var reported = 0L

  override fun read(sink: Buffer, byteCount: Long): Long {
    val read = super.read(sink, byteCount)
    if (read > 0) {
      total += read
      if (total - reported >= 512 * 1024) {
        reported = total
        onBytes(total)
      }
    } else if (read == -1L) {
      onBytes(total)
    }
    return read
  }
}

private fun normalizeEpgChannelId(value: String?): String? =
  if (value == null || value == NO_EPG_CHANNEL) null else value

internal fun mapItem(sourceId: String, row: Row, kind: MediaKind, fallbackCategoryId: String): MediaItem? =
  when (kind) {
    MediaKind.LIVE -> mapLive(sourceId, row, fallbackCategoryId)
    MediaKind.MOVIE -> mapMovie(sourceId, row, fallbackCategoryId)
    MediaKind.SERIES -> mapSeries(sourceId, row, fallbackCategoryId)
  }

fun MediaItem.indexed(): MediaItem {
  val titleWords = searchText(title)
  val nameWords = searchText(name)
  return copy(
    search = if (nameWords == titleWords) titleWords else "$titleWords $nameWords",
    sortTitle = fold(title),
    genreTags = genreTags(genre),
    // Some rows carry a vote count in `rating` (7125 on a ten point scale). A number off the
    // scale is not a rating, and left alone it tops the rating sort.
    rating = rating?.takeIf { it > 0 && it <= 10 },
  )
}

private fun mapLive(sourceId: String, row: Row, fallbackCategoryId: String): MediaItem? {
  val streamId = row.long("stream_id") ?: return null
  val name = row.str("name") ?: "Channel $streamId"
  if (isSeparatorName(name)) return null
  return MediaItem(
    sourceId = sourceId,
    id = "live:$streamId",
    kind = MediaKind.LIVE,
    name = name,
    title = cleanTitle(name),
    categoryId = row.str("category_id") ?: fallbackCategoryId,
    logo = row.str("stream_icon"),
    streamId = streamId,
    epgChannelId = normalizeEpgChannelId(row.str("epg_channel_id")),
    hasArchive = row.bool("tv_archive"),
    archiveDays = (row.int("tv_archive_duration") ?: 0).coerceAtLeast(0),
    addedAt = row.long("added"),
  ).indexed()
}

private fun mapMovie(sourceId: String, row: Row, fallbackCategoryId: String): MediaItem? {
  val streamId = row.long("stream_id") ?: return null
  val name = row.str("name") ?: "Movie $streamId"
  return MediaItem(
    sourceId = sourceId,
    id = "movie:$streamId",
    kind = MediaKind.MOVIE,
    name = name,
    title = cleanTitle(name),
    categoryId = row.str("category_id") ?: fallbackCategoryId,
    logo = row.str("stream_icon") ?: row.str("cover"),
    streamId = streamId,
    language = row.str("audio_language") ?: row.str("language"),
    containerExtension = row.str("container_extension"),
    rating = row.num("rating"),
    year = parseYear(name),
    plot = row.str("plot") ?: row.str("description"),
    genre = row.str("genre"),
    cast = row.str("cast") ?: row.str("actors"),
    director = row.str("director"),
    backdrop = firstBackdrop(row["backdrop_path"]),
    durationSecs = row.int("duration_secs") ?: parseHms(row.str("duration")),
    addedAt = row.long("added"),
    trailer = row.str("trailer") ?: row.str("youtube_trailer"),
  ).indexed()
}

private fun mapSeries(sourceId: String, row: Row, fallbackCategoryId: String): MediaItem? {
  val seriesId = row.long("series_id") ?: return null
  val name = row.str("name") ?: "Series $seriesId"
  val releaseDate = row.str("releaseDate") ?: row.str("release_date")
  return MediaItem(
    sourceId = sourceId,
    id = "series:$seriesId",
    kind = MediaKind.SERIES,
    name = name,
    title = cleanTitle(name),
    categoryId = row.str("category_id") ?: fallbackCategoryId,
    logo = row.str("cover"),
    streamId = seriesId,
    language = row.str("audio_language") ?: row.str("language"),
    rating = row.num("rating"),
    year = (releaseDate?.let { parseYear(it) }) ?: parseYear(name),
    plot = row.str("plot"),
    genre = row.str("genre"),
    cast = row.str("cast"),
    director = row.str("director"),
    backdrop = firstBackdrop(row["backdrop_path"]),
    addedAt = row.long("last_modified") ?: row.long("added"),
    trailer = row.str("youtube_trailer") ?: row.str("trailer"),
  ).indexed()
}

/** `backdrop_path` is an array of urls, occasionally a bare string. Take the first usable one. */
private fun firstBackdrop(value: Any?): String? = when (value) {
  is List<*> -> value.firstNotNullOfOrNull { (it as? String)?.trim()?.ifEmpty { null } }
  is String -> value.trim().ifEmpty { null }
  else -> null
}

private fun fallbackSeriesItem(sourceId: String, seriesId: String, info: Row): MediaItem {
  val name = info.str("name") ?: "Series $seriesId"
  return MediaItem(
    sourceId = sourceId,
    id = "series:$seriesId",
    kind = MediaKind.SERIES,
    name = name,
    title = cleanTitle(name),
    categoryId = info.str("category_id") ?: "",
    logo = info.str("cover"),
    streamId = seriesId.toLongOrNull() ?: 0,
    plot = info.str("plot"),
    genre = info.str("genre"),
    cast = info.str("cast"),
    director = info.str("director"),
    backdrop = firstBackdrop(info["backdrop_path"]),
    rating = info.num("rating"),
    year = parseYear(info.str("releaseDate") ?: name),
  ).indexed()
}

private fun mapEpisode(row: Row, seriesId: String, seasonFromKey: Int, positionFallback: Int): Episode? {
  // The episode id is what goes into /series/U/P/<id>.<ext>, so it is mandatory.
  val streamId = row.long("id") ?: return null
  val info = row.row("info") ?: emptyMap()
  val rawTitle = row.str("title") ?: "Episode $positionFallback"
  return Episode(
    id = row.str("id") ?: streamId.toString(),
    seriesId = seriesId,
    season = row.int("season") ?: seasonFromKey,
    episodeNum = row.int("episode_num") ?: positionFallback,
    title = cleanTitle(rawTitle),
    plot = info.str("plot") ?: info.str("description"),
    image = info.str("movie_image") ?: info.str("cover_big") ?: row.str("cover"),
    durationSecs = info.int("duration_secs"),
    containerExtension = row.str("container_extension"),
    streamId = streamId,
  )
}

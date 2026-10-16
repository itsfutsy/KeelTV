package com.keeltv.app.data.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource
import okio.GzipSource
import okio.buffer
import java.io.IOException

class Download(val response: Response, val source: BufferedSource) : AutoCloseable {
  override fun close() {
    response.close()
  }
}

private const val GZIP_MAGIC_0 = 0x1f.toByte()
private const val GZIP_MAGIC_1 = 0x8b.toByte()

/**
 * Open a URL for streaming, transparently decompressing it.
 *
 * OkHttp only unwraps gzip it asked for itself, and plenty of providers serve a `.m3u.gz` or an
 * `.xml.gz` with no `Content-Encoding` at all, so the magic number is sniffed as well.
 */
fun OkHttpClient.openStream(url: String): Download {
  val request = Request.Builder()
    .url(url)
    .header("User-Agent", USER_AGENT)
    .header("Accept", "*/*")
    .build()

  val response = newCall(request).execute()
  if (!response.isSuccessful) {
    val code = response.code
    val message = response.message
    response.close()
    throw IOException(describeHttpFailure(code, message, url))
  }

  val body = response.body ?: run {
    response.close()
    throw IOException("The server answered but sent no body: ${redactUrl(url)}")
  }

  var source: BufferedSource = body.source()
  val encoding = response.header("Content-Encoding")?.lowercase()
  val sniff = source.peek().let { peek ->
    if (peek.request(2)) peek.readByteArray(2) else ByteArray(0)
  }
  val gzipped = encoding == "gzip" ||
    (encoding == null && sniff.size == 2 && sniff[0] == GZIP_MAGIC_0 && sniff[1] == GZIP_MAGIC_1)
  if (gzipped) source = GzipSource(source).buffer()

  return Download(response, source)
}

private fun describeHttpFailure(status: Int, statusText: String, url: String): String {
  val where = redactUrl(url)
  val head = "HTTP $status ${statusText}".trim() + " from $where."
  return when {
    status == 401 -> "$head Check the username and password."
    status == 429 -> "$head The provider is rate limiting; wait a minute and retry."
    status == 404 || status == 403 || status == 410 ->
      "$head Many Xtream providers disable the get.php playlist endpoint. If this is an Xtream " +
        "account, add it as an Xtream source instead of an M3U URL."
    status >= 500 -> "$head The provider's server is failing; try again later."
    else -> head
  }
}

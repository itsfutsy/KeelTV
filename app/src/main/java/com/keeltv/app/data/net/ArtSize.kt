package com.keeltv.app.data.net

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.size.Dimension
import kotlin.math.roundToInt

/** The widths TMDB's image server answers for. Posters and backdrops take any of them. */
private val TMDB_WIDTHS = intArrayOf(92, 154, 185, 300, 342, 500, 780, 1280)

private val TMDB_ART =
  Regex("^https?://(?:image\\.tmdb\\.org|(?:www\\.)?themoviedb\\.org)/t/p/([^/]+)/([^/?#]+)$")

private val POSTER_SIZE = Regex("^(?:w(?:92|154|185|342|500)|w\\d+_and_h\\d+.*)$")

fun sizedArtUrl(url: String, width: Int, height: Int): String {
  val match = TMDB_ART.find(url) ?: return url
  val (size, file) = match.destructured
  val aspect = if (POSTER_SIZE.matches(size)) 2f / 3f else 16f / 9f
  val needed = maxOf(width, (height * aspect).roundToInt())
  val fit = TMDB_WIDTHS.firstOrNull { it >= needed } ?: return url
  return "https://image.tmdb.org/t/p/w$fit/$file"
}

object ArtSizeInterceptor : Interceptor {
  override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
    val url = chain.request.data as? String
    val width = (chain.size.width as? Dimension.Pixels)?.px
    val height = (chain.size.height as? Dimension.Pixels)?.px
    if (url == null || width == null || height == null) return chain.proceed()
    val sized = sizedArtUrl(url, width, height)
    if (sized == url) return chain.proceed()
    return chain.withRequest(chain.request.newBuilder().data(sized).build()).proceed()
  }
}

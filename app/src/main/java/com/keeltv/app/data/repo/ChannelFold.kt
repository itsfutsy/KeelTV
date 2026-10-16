package com.keeltv.app.data.repo

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.keeltv.app.core.fold
import com.keeltv.app.data.model.MediaItem

private val VARIANT_SUFFIX = Regex(
  """\s*(?:\[[^\]]*]|\([^)]*\)|\b(?:4K|UHD|FHD|HD|SD|HEVC|H265|MULTI-?SUB|BACKUP\s*\d*|ALT\s*\d*)\b)\s*$""",
  RegexOption.IGNORE_CASE,
)

private val NON_ALNUM = Regex("[^a-z0-9]+")

private const val KEY_CACHE_CAP = 4096
private val keyCache = HashMap<String, String>(1024)

fun variantKey(title: String): String {
  synchronized(keyCache) { keyCache[title] }?.let { return it }
  val key = computeVariantKey(title)
  synchronized(keyCache) {
    if (keyCache.size > KEY_CACHE_CAP) keyCache.clear()
    keyCache[title] = key
  }
  return key
}

private fun computeVariantKey(title: String): String {
  var name = title
  for (i in 0 until 4) {
    val next = VARIANT_SUFFIX.replace(name, "")
    if (next == name) break
    name = next
  }
  return NON_ALNUM.replace(fold(name), " ").trim()
}

fun variantLabel(title: String, key: String): String {
  val stripped = NON_ALNUM.replace(fold(title.trim()), " ").trim()
  if (stripped == key) return "SD"
  val tail = VARIANT_SUFFIX.find(title)?.value.orEmpty()
  return tail.replace(Regex("[\\[\\]()]"), "").trim().uppercase().ifEmpty { "·" }
}

const val FOLD_BLOCK = 8

data class FoldedChannel(
  val feeds: List<MediaItem>,
  val number: Int,
  val labels: List<String>,
) {
  val item: MediaItem get() = feeds[0]
}

private fun titleOf(item: MediaItem): String = item.title.ifEmpty { item.name }

private fun keyOf(item: MediaItem): String = variantKey(titleOf(item))

fun foldChannels(rows: List<MediaItem>, from: Int, collapse: Boolean): List<FoldedChannel> {
  if (!collapse) {
    return rows.mapIndexed { i, item -> FoldedChannel(listOf(item), from + i + 1, emptyList()) }
  }
  val out = ArrayList<FoldedChannel>(rows.size)
  var i = 0
  while (i < rows.size) {
    val key = keyOf(rows[i])
    var j = i + 1
    if (key.length > 2) {
      while (j < rows.size && (from + j) % FOLD_BLOCK != 0 && keyOf(rows[j]) == key) j++
    }
    val run = rows.subList(i, j).toList()
    out.add(
      FoldedChannel(
        feeds = run,
        number = from + i + 1,
        labels = if (run.size > 1) run.map { variantLabel(titleOf(it), key) } else emptyList(),
      ),
    )
    i = j
  }
  return out
}

class ChannelPagingSource(
  private val collapse: Boolean,
  private val window: suspend (limit: Int, offset: Int) -> List<MediaItem>,
) : PagingSource<Int, FoldedChannel>() {

  override fun getRefreshKey(state: PagingState<Int, FoldedChannel>): Int? =
    state.pages.firstOrNull { it.data.isNotEmpty() }?.data?.first()?.number?.minus(1)

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FoldedChannel> {
    val raw = align(params.key ?: 0)
    val asked = align(params.loadSize)
    val offset = raw.coerceAtLeast(0)
    val limit = if (raw < 0) raw + asked else asked
    if (limit <= 0) {
      return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = offset)
    }
    val rows = try {
      window(limit, offset)
    } catch (error: Exception) {
      return LoadResult.Error(error)
    }
    return LoadResult.Page(
      data = foldChannels(rows, offset, collapse),
      prevKey = if (offset == 0) null else offset - asked,
      nextKey = if (rows.size < limit) null else offset + rows.size,
    )
  }

  private fun align(value: Int): Int = value - value % FOLD_BLOCK
}

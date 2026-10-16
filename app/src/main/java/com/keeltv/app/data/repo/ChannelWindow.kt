package com.keeltv.app.data.repo

import com.keeltv.app.data.model.MediaItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val PAGE = 200
private const val PAGES_HELD = 4

class ChannelWindow(
  val categoryName: String,
  val size: Int,
  val startIndex: Int,
  private val read: suspend (offset: Int, limit: Int) -> List<MediaItem>,
) {
  private val pages = LinkedHashMap<Int, List<MediaItem>>()
  private val lock = Mutex()

  suspend fun at(index: Int): MediaItem? {
    if (index < 0 || index >= size) return null
    val page = index / PAGE
    val rows = lock.withLock {
      pages[page] ?: read(page * PAGE, PAGE).also { loaded ->
        pages[page] = loaded
        while (pages.size > PAGES_HELD) pages.remove(pages.keys.first())
      }
    }
    return rows.getOrNull(index - page * PAGE)
  }

  fun step(from: Int, delta: Int): Int =
    if (size <= 0) 0 else ((from + delta) % size + size) % size
}

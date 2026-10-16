package com.keeltv.app.ui.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.keeltv.app.data.model.EpgProgramme
import com.keeltv.app.ui.LocalContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce

const val GUIDE_CHUNK_SECONDS = 6 * 3600L

private const val ROW_CAP = 800

/**
 * The guide rows for whatever is on screen.
 *
 * On a large lineup nothing can be read up front, so a row asks for its own window as it composes,
 * the asks are gathered for a moment and answered with one query per chunk, and a chunk already
 * read is never asked for again.
 *
 * A channel gets its own [State] rather than a share of one observable map. A snapshot state map
 * records the read of the whole map, so one answered channel would invalidate every row on screen
 * and the list would recompose from end to end every time a query landed. One slot per channel is
 * what keeps a D-pad walk down a long list at frame rate.
 */
@Stable
class GuideRows {
  private val slots = HashMap<String, MutableState<List<EpgProgramme>?>>()

  private val queue = ArrayList<Pair<String, Long>>()

  private val asked = HashSet<String>()

  internal val pending = MutableStateFlow(0)

  fun slot(channelId: String): State<List<EpgProgramme>?> = slotFor(channelId)

  private fun slotFor(channelId: String): MutableState<List<EpgProgramme>?> =
    slots.getOrPut(channelId) { mutableStateOf(null) }

  fun want(channelId: String?, from: Long, to: Long) {
    if (channelId.isNullOrEmpty()) return
    var chunk = floorTo(from, GUIDE_CHUNK_SECONDS)
    var added = false
    while (chunk < to) {
      if (asked.add("$channelId|$chunk")) {
        queue.add(channelId to chunk)
        added = true
      }
      chunk += GUIDE_CHUNK_SECONDS
    }
    if (added) pending.value += 1
  }

  internal fun drain(): List<Pair<String, Long>> {
    if (queue.isEmpty()) return emptyList()
    val batch = ArrayList(queue)
    queue.clear()
    return batch
  }

  internal fun merge(channelId: String, programmes: List<EpgProgramme>) {
    if (slots.size > ROW_CAP) {
      slots.clear()
      asked.clear()
    }
    val slot = slotFor(channelId)
    val current = slot.value
    slot.value = when {
      current.isNullOrEmpty() -> programmes
      programmes.isEmpty() -> current
      else -> (current + programmes).sortedBy { it.start }
    }
  }
}

@Composable
fun rememberGuideRows(sourceId: String): GuideRows {
  val container = LocalContainer.current
  val rows = remember(sourceId) { GuideRows() }

  LaunchedEffect(rows) {
    rows.pending.debounce(80).collect {
      val batch = rows.drain()
      if (batch.isEmpty()) return@collect
      for ((chunk, pairs) in batch.groupBy { it.second }) {
        val ids = pairs.map { it.first }.distinct()
        val grid = runCatching {
          container.epg.grid(sourceId, ids, chunk, chunk + GUIDE_CHUNK_SECONDS)
        }.getOrDefault(emptyMap())
        for (id in ids) rows.merge(id, grid[id].orEmpty())
      }
    }
  }
  return rows
}

@Composable
fun rememberNowSeconds(periodMs: Long = 30_000): Long {
  val now by produceState(System.currentTimeMillis() / 1000) {
    while (true) {
      delay(periodMs)
      value = System.currentTimeMillis() / 1000
    }
  }
  return now
}

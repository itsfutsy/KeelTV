package com.keeltv.app.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint

object TimeShift {
  const val REWIND_MS = 30_000L

  const val BACK_BUFFER_MS = 35_000

  const val PAUSE_MS = 120_000

  const val LIVE_EDGE_MS = 4_000L

  const val EDGE_MARGIN_MS = 1_500L

  fun stepTo(positionMs: Long, bufferedMs: Long, deepestMs: Long, deltaMs: Long): Long {
    val oldest = (deepestMs - REWIND_MS).coerceAtLeast(0)
    val newest = (bufferedMs - EDGE_MARGIN_MS).coerceAtLeast(oldest)
    val target = (positionMs + deltaMs).coerceIn(oldest, newest)
    return if (deltaMs > 0) maxOf(target, positionMs) else minOf(target, positionMs)
  }

  fun aheadMs(positionMs: Long, bufferedMs: Long): Long = (bufferedMs - positionMs).coerceAtLeast(0)

  fun behindMs(elapsedMs: Long, playedMs: Long): Long = (elapsedMs - playedMs).coerceAtLeast(0)
}

/**
 * Makes a live channel seekable inside the buffer the player is already keeping.
 *
 * MEASURED (Media3 1.11.1): a stream with no length and no duration is live as far as
 * `ProgressiveMediaPeriod` is concerned, and a live period sends every seek to zero without
 * looking at its own sample queues, so pausing a channel and stepping back restarted it at the
 * live edge. A seek map that carries a duration is enough to get the in-buffer path back: a step
 * that lands in the buffer is served from it, and one that does not opens the stream again, which
 * is where a live channel would have gone anyway.
 */
@OptIn(UnstableApi::class)
class LiveSeekableExtractors(private val delegate: ExtractorsFactory) : ExtractorsFactory by delegate {
  @Volatile
  var live: Boolean = false

  override fun createExtractors(): Array<Extractor> = wrap(delegate.createExtractors())

  override fun createExtractors(uri: Uri, headers: Map<String, List<String>>): Array<Extractor> =
    wrap(delegate.createExtractors(uri, headers))

  private fun wrap(found: Array<Extractor>): Array<Extractor> =
    Array(found.size) { Seekable(found[it]) }

  private inner class Seekable(private val extractor: Extractor) : Extractor by extractor {
    override fun init(output: ExtractorOutput) = extractor.init(Output(output))
  }

  private inner class Output(private val output: ExtractorOutput) : ExtractorOutput by output {
    override fun seekMap(seekMap: SeekMap) =
      output.seekMap(if (live && !seekMap.isSeekable) InBuffer else seekMap)
  }
}

private const val LIVE_DURATION_US = 30L * 24 * 60 * 60 * 1_000_000

@OptIn(UnstableApi::class)
private object InBuffer : SeekMap {
  override fun isSeekable(): Boolean = true

  override fun getDurationUs(): Long = LIVE_DURATION_US

  override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints =
    SeekMap.SeekPoints(SeekPoint(timeUs, 0))
}

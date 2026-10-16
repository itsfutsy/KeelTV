package com.keeltv.app.playback

object Progress {
  const val SAVE_EVERY_MS = 10_000L
  const val MIN_SAVE_MS = 5_000L
  const val MIN_RESUME_MS = 30_000L
  const val FINISHED_FRACTION = 0.95

  fun shouldSave(positionMs: Long, durationMs: Long): Boolean =
    durationMs > 0 && positionMs >= MIN_SAVE_MS

  fun isFinished(positionMs: Long, durationMs: Long): Boolean =
    durationMs > 0 && positionMs >= durationMs * FINISHED_FRACTION

  fun resumeFrom(positionMs: Long, durationMs: Long): Long = when {
    isFinished(positionMs, durationMs) -> 0
    positionMs > MIN_RESUME_MS -> positionMs
    else -> 0
  }
}

fun scrubStepMs(repeatCount: Int): Long = when {
  repeatCount <= 0 -> 10_000L
  repeatCount < 5 -> 15_000L
  repeatCount < 12 -> 30_000L
  repeatCount < 24 -> 60_000L
  else -> 300_000L
}

fun formatDuration(ms: Long): String {
  val total = (ms / 1000).coerceAtLeast(0)
  val h = total / 3600
  val m = (total % 3600) / 60
  val s = total % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

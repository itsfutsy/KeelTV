package com.keeltv.app.playback

/**
 * Providers often close a live connection every 30-60 s and expect the player to come back. A
 * failed request can also count as a failed login on their side, so the retry has to back off
 * rather than hammer, and credentials that were refused are never retried at all.
 */
object Reconnect {
  const val FIRST_DELAY_MS = 500L
  const val MAX_DELAY_MS = 5_000L

  const val MAX_CONSECUTIVE_FAILURES = 12

  const val MAX_INITIAL_RETRIES = 4

  /** Provider-side "your one connection is already open". */
  const val BUSY_SLOT = 555

  const val STALL_BEFORE_PLAY_MS = 20_000L
  const val STALL_LIVE_MS = 8_000L
  const val STALL_VOD_MS = 15_000L

  fun stallBudgetMs(played: Boolean, live: Boolean): Long = when {
    !played -> STALL_BEFORE_PLAY_MS
    live -> STALL_LIVE_MS
    else -> STALL_VOD_MS
  }

  /**
   * How long Media3 may wait before retrying a failed load by itself, or null to hand the failure to
   * the controller now.
   *
   * MEASURED: left to its defaults Media3 retries a live load up to six times on its own, one after
   * another with next to no delay, before the controller hears a word. Each attempt is another
   * request at a provider that counts them, and the whole time the screen says "Opening". A file is
   * different: Media3 resumes it at the byte it reached, which providers honour with a range
   * response, so a socket failure there is worth its retries. A status is never retried inside
   * Media3; whether it is worth asking again is a decision [retryBudget] makes.
   */
  fun loadRetryDelayMs(live: Boolean, status: Int?, errorCount: Int): Long? = when {
    live || status != null -> null
    else -> minOf((errorCount - 1) * 1_000L, MAX_DELAY_MS)
  }

  fun delayMs(failures: Int): Long {
    if (failures <= 0) return FIRST_DELAY_MS
    if (failures >= 4) return MAX_DELAY_MS
    return minOf(FIRST_DELAY_MS shl failures, MAX_DELAY_MS)
  }

  fun retryBudget(played: Boolean, status: Int?): Int = when {
    // Refused credentials and a refused network will not get better by asking again, and every
    // request that comes back like this counts as a failed login at the provider.
    status == 401 || status == 403 || status == 511 || status == 513 -> 0
    played -> MAX_CONSECUTIVE_FAILURES
    status != null && status != BUSY_SLOT -> 0
    else -> MAX_INITIAL_RETRIES
  }

  fun shouldRetry(failures: Int, played: Boolean, status: Int?): Boolean =
    failures <= retryBudget(played, status)
}

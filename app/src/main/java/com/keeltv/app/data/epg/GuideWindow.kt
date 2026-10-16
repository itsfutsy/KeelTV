package com.keeltv.app.data.epg

const val KEEP_PAST_SECONDS = 3 * 3600L

const val KEEP_FUTURE_SECONDS = 2 * 24 * 3600L

const val ARCHIVE_MAX_DAYS = 7

const val DEFAULT_ARCHIVE_DAYS = 3

fun archiveSeconds(days: Int): Long = days.coerceIn(0, ARCHIVE_MAX_DAYS) * 24 * 3600L

data class GuideWindow(val from: Long, val to: Long) {
  fun holds(start: Long, stop: Long, archiveDays: Int = 0): Boolean =
    stop > from - archiveSeconds(archiveDays) && start < to

  fun oldest(archiveDays: Int): Long = from - archiveSeconds(archiveDays)
}

fun guideWindow(now: Long): GuideWindow =
  GuideWindow(now - KEEP_PAST_SECONDS, now + KEEP_FUTURE_SECONDS)

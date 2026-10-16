package com.keeltv.app.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val DAY = DateTimeFormatter.ofPattern("EEE d MMM")
private val MARK = DateTimeFormatter.ofPattern("EEE d")

private fun local(epochSeconds: Long) =
  Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault())

fun clock(epochSeconds: Long): String = local(epochSeconds).format(TIME)

fun dayLabel(epochSeconds: Long): String {
  val day = local(epochSeconds).toLocalDate()
  return when (day) {
    LocalDate.now() -> "Today"
    LocalDate.now().plusDays(1) -> "Tomorrow"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> day.format(DAY)
  }
}

fun dayHeading(epochSeconds: Long): String {
  val relative = dayLabel(epochSeconds)
  val date = local(epochSeconds).format(DAY)
  return if (relative == date) date else "$relative, $date"
}

fun dayMark(epochSeconds: Long): String = local(epochSeconds).format(MARK)

fun fractionThrough(start: Long, stop: Long, at: Long): Float {
  if (stop <= start) return 0f
  return ((at - start).toFloat() / (stop - start).toFloat()).coerceIn(0f, 1f)
}

fun timeLeft(seconds: Long): String {
  val minutes = ((seconds.coerceAtLeast(0) + 59) / 60).toInt()
  return if (minutes < 60) "$minutes min left" else "${minutes / 60}h ${minutes % 60}m left"
}

package com.keeltv.app.ui.live

import com.keeltv.app.data.model.EpgProgramme

const val GUIDE_STEP_SECONDS = 1800L

const val GUIDE_SPAN_SECONDS = 2 * 3600L

data class GuideSlot(val start: Long, val stop: Long, val programme: EpgProgramme?)

fun floorTo(time: Long, step: Long): Long = time - ((time % step) + step) % step

fun layoutRow(programmes: List<EpgProgramme>, from: Long, to: Long): List<GuideSlot> {
  if (to <= from) return emptyList()
  val clean = programmes
    .filter { it.stop > it.start && it.stop > from && it.start < to }
    .sortedBy { it.start }

  val slots = ArrayList<GuideSlot>(clean.size + 2)
  var cursor = from
  for (i in clean.indices) {
    val p = clean[i]
    val next = clean.getOrNull(i + 1)
    val stop = if (next != null && next.start < p.stop) next.start else p.stop
    val start = maxOf(cursor, p.start)
    val end = minOf(to, stop)
    if (end <= start) continue
    if (start > cursor) slots.add(GuideSlot(cursor, start, null))
    slots.add(GuideSlot(start, end, p))
    cursor = end
  }
  if (cursor < to) slots.add(GuideSlot(cursor, to, null))
  return slots
}

fun ticks(from: Long, to: Long, step: Long = GUIDE_STEP_SECONDS): List<Long> {
  if (to <= from) return emptyList()
  val out = ArrayList<Long>(((to - from) / step).toInt() + 2)
  var t = floorTo(from, step)
  if (t < from) t += step
  while (t <= to) {
    out.add(t)
    t += step
  }
  return out
}

fun slotIndexAt(slots: List<GuideSlot>, at: Long): Int =
  slots.indexOfFirst { at >= it.start && at < it.stop }

fun scrolledWindow(
  current: Long,
  at: Long,
  min: Long,
  span: Long = GUIDE_SPAN_SECONDS,
  step: Long = GUIDE_STEP_SECONDS,
): Long = when {
  at < current -> minOf(current, maxOf(min, floorTo(at, step) - step))
  at >= current + span - step -> maxOf(current, floorTo(at, step) - (span - 2 * step))
  else -> current
}

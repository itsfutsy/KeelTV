package com.keeltv.app.data.epg

import com.keeltv.app.data.model.EpgProgramme
import com.keeltv.app.data.xtream.NO_EPG_CHANNEL
import java.io.Reader

private const val MAX_ELEMENT_CHARS = 1 shl 20
private const val TAG_LOOKBEHIND = 24
private const val DEFAULT_PROGRAMME_SECONDS = 3600L
private const val CHUNK = 64 * 1024

data class XmltvChannel(val id: String, val displayName: String, val icon: String?)

private val NAMED_ENTITIES = mapOf(
  "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
)

private val ENTITY = Regex("&(?:#x([0-9a-fA-F]{1,6})|#([0-9]{1,7})|([a-zA-Z][a-zA-Z0-9]{1,31}));")

private fun fromCodePoint(cp: Int, original: String): String {
  if (cp <= 0 || cp > 0x10ffff) return original
  // Surrogate halves are not legal standalone characters in XML.
  if (cp in 0xd800..0xdfff) return original
  return String(Character.toChars(cp))
}

private fun decodeEntities(raw: String): String {
  if (raw.isEmpty()) return ""
  if (!raw.contains('&')) return raw
  return ENTITY.replace(raw) { m ->
    val hex = m.groupValues[1]
    val dec = m.groupValues[2]
    val name = m.groupValues[3]
    when {
      hex.isNotEmpty() -> fromCodePoint(hex.toIntOrNull(16) ?: -1, m.value)
      dec.isNotEmpty() -> fromCodePoint(dec.toIntOrNull() ?: -1, m.value)
      name.isNotEmpty() -> NAMED_ENTITIES[name.lowercase()] ?: m.value
      else -> m.value
    }
  }
}

private val attrPatterns = HashMap<String, Regex>()

private fun attrValue(attrs: String, name: String): String? {
  // `\b` is not enough: `-` is a non-word character, so `\bstart` also matches inside `pdc-start`
  // and `\bid` inside `tvg-id`. Anchor on a real attribute separator.
  val re = attrPatterns.getOrPut(name) { Regex("(?:^|[\\s\"'/])$name\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')") }
  val m = re.find(attrs) ?: return null
  return decodeEntities(m.groupValues[1].ifEmpty { m.groupValues[2] })
}

private val CDATA = Regex("^<!\\[CDATA\\[([\\s\\S]*)\\]\\]>$")

private fun decodeText(raw: String): String {
  val trimmed = raw.trim()
  CDATA.find(trimmed)?.let { return it.groupValues[1] }
  return decodeEntities(trimmed)
}

private val DISPLAY_NAME = Regex("<display-name\\b[^>]*>([\\s\\S]*?)</display-name>")
private val ICON_SRC = Regex("<icon\\b[^>]*\\bsrc\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
private val TITLE = Regex("<title\\b[^>]*>([\\s\\S]*?)</title>")
private val DESC = Regex("<desc\\b[^>]*>([\\s\\S]*?)</desc>")
private val ALPHANUM = Regex("[a-z0-9]", RegexOption.IGNORE_CASE)

internal fun isUsableChannelId(id: String): Boolean {
  if (id.isEmpty()) return false
  if (id == NO_EPG_CHANNEL || id == "null" || id == "undefined") return false
  return ALPHANUM.containsMatchIn(id)
}

private fun digits(s: String, at: Int, len: Int): Int {
  if (at + len > s.length) return -1
  var v = 0
  for (i in at until at + len) {
    val d = s[i].code - 48
    if (d < 0 || d > 9) return -1
    v = v * 10 + d
  }
  return v
}

/** XMLTV timestamps are `YYYYMMDDHHMMSS +HHMM`, e.g. `20260828120000 +0200`. */
fun parseXmltvTime(raw: String): Long? {
  val s = raw.trim()
  val year = digits(s, 0, 4)
  val month = digits(s, 4, 2)
  val day = digits(s, 6, 2)
  if (year < 1970 || month < 1 || month > 12 || day < 1 || day > 31) return null
  val hour = if (s.length >= 10) digits(s, 8, 2) else 0
  val minute = if (s.length >= 12) digits(s, 10, 2) else 0
  val second = if (s.length >= 14) digits(s, 12, 2) else 0
  if (hour < 0 || hour > 23 || minute < 0 || minute > 59 || second < 0 || second > 60) return null

  var t = utcSeconds(year, month, day, hour, minute, second)

  val p = s.length - 5
  if (p >= 8) {
    val sign = s[p]
    if (sign == '+' || sign == '-') {
      val oh = digits(s, p + 1, 2)
      val om = digits(s, p + 3, 2)
      if (oh >= 0 && om >= 0) t -= (if (sign == '+') 1 else -1) * (oh * 3600L + om * 60L)
    }
  }
  return t
}

private fun utcSeconds(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
  val y = if (month <= 2) year - 1 else year
  val era = (if (y >= 0) y else y - 399) / 400
  val yoe = y - era * 400
  val mp = (month + 9) % 12
  val doy = (153 * mp + 2) / 5 + day - 1
  val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
  val days = era * 146097L + doe - 719468L
  return days * 86400L + hour * 3600L + minute * 60L + second
}

private val ELEMENT_START = Regex("<(channel|programme)(?=[\\s>/])")

private fun findTagEnd(buf: StringBuilder, from: Int): Int {
  var quote = '\u0000'
  for (i in from until buf.length) {
    val c = buf[i]
    if (quote != '\u0000') {
      if (c == quote) quote = '\u0000'
    } else if (c == '"' || c == '\'') {
      quote = c
    } else if (c == '>') {
      return i
    }
  }
  return -1
}

private fun emitChannel(attrs: String, inner: String, onChannel: (XmltvChannel) -> Unit) {
  val id = attrValue(attrs, "id") ?: return
  if (!isUsableChannelId(id)) return
  val displayName = DISPLAY_NAME.find(inner)?.let { decodeText(it.groupValues[1]) } ?: ""
  val iconMatch = ICON_SRC.find(inner)
  val icon = iconMatch?.let { decodeEntities(it.groupValues[1].ifEmpty { it.groupValues[2] }) } ?: ""
  onChannel(XmltvChannel(id, displayName.ifEmpty { id }, icon.ifEmpty { null }))
}

private fun emitProgramme(attrs: String, inner: String, onProgramme: (EpgProgramme) -> Unit) {
  val channelId = attrValue(attrs, "channel") ?: return
  if (!isUsableChannelId(channelId)) return
  val start = attrValue(attrs, "start")?.let { parseXmltvTime(it) } ?: return
  val stopParsed = attrValue(attrs, "stop")?.let { parseXmltvTime(it) }
  val stop = if (stopParsed != null && stopParsed > start) stopParsed else start + DEFAULT_PROGRAMME_SECONDS

  val description = DESC.find(inner)?.let { decodeText(it.groupValues[1]) }.orEmpty()
  onProgramme(
    EpgProgramme(
      channelId = channelId,
      title = TITLE.find(inner)?.let { decodeText(it.groupValues[1]) } ?: "",
      description = description.ifEmpty { null },
      start = start,
      stop = stop,
    ),
  )
}

private fun scanBuffer(
  buf: StringBuilder,
  onChannel: (XmltvChannel) -> Unit,
  onProgramme: (EpgProgramme) -> Unit,
  final: Boolean,
) {
  var pos = 0
  var pending = -1
  val text = buf
  while (true) {
    val m = ELEMENT_START.find(text, pos) ?: break
    val start = m.range.first
    val isChannel = m.groupValues[1] == "channel"
    val afterTagName = m.range.last + 1
    val tagEnd = findTagEnd(text, afterTagName)
    if (tagEnd < 0) {
      if (final || text.length - start > MAX_ELEMENT_CHARS) { pos = start + m.value.length; continue }
      pending = start
      break
    }

    val selfClosing = text[tagEnd - 1] == '/'
    val attrs = text.substring(afterTagName, if (selfClosing) tagEnd - 1 else tagEnd)
    var inner = ""
    val elementEnd: Int
    if (selfClosing) {
      elementEnd = tagEnd + 1
    } else {
      val closeTag = if (isChannel) "</channel>" else "</programme>"
      val close = text.indexOf(closeTag, tagEnd + 1)
      if (close < 0) {
        if (!final && text.length - start <= MAX_ELEMENT_CHARS) { pending = start; break }
        pos = start + m.value.length
        continue
      }
      inner = text.substring(tagEnd + 1, close)
      elementEnd = close + closeTag.length
    }

    if (isChannel) emitChannel(attrs, inner, onChannel) else emitProgramme(attrs, inner, onProgramme)
    pos = elementEnd
  }

  if (final) { buf.setLength(0); return }
  val keep = if (pending >= 0) pending else maxOf(pos, buf.length - TAG_LOOKBEHIND)
  if (keep >= buf.length) buf.setLength(0) else buf.delete(0, keep)
}

fun parseXmltv(
  reader: Reader,
  onChannel: (XmltvChannel) -> Unit,
  onProgramme: (EpgProgramme) -> Unit,
  onProgress: ((Long) -> Unit)? = null,
) {
  val buf = StringBuilder()
  val chunk = CharArray(CHUNK)
  var read = 0L
  var bomChecked = false

  while (true) {
    val n = reader.read(chunk)
    if (n < 0) break
    if (n == 0) continue
    read += n
    var from = 0
    if (!bomChecked) {
      bomChecked = true
      if (chunk[0] == '\uFEFF') from = 1
    }
    buf.appendRange(chunk, from, n)
    scanBuffer(buf, onChannel, onProgramme, false)
    onProgress?.invoke(read)
  }

  scanBuffer(buf, onChannel, onProgramme, true)
  onProgress?.invoke(read)
}

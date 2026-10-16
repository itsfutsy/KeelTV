package com.keeltv.app.data.repo

import com.keeltv.app.data.model.SourceKind

private val GREETING = Regex("^(welcome to|welcome|hello to|hello)\\b", RegexOption.IGNORE_CASE)
private val GENERIC = setOf("connected", "welcome", "active", "ok", "success", "authentication failed")
private val NOT_A_NAME = listOf("expire", "account", "trial", "disabl", "ban", "error", "fail", "http")
private val HOST = Regex("^(?:[a-z][a-z0-9+.-]*://)?(?:[^/?#@]*@)?([^/?#:]+)", RegexOption.IGNORE_CASE)
private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
private val PLAYLIST_FILE = Regex("([^/]+)\\.(m3u8?|m3u_plus)$", RegexOption.IGNORE_CASE)
private val SECOND_LEVEL = setOf("co", "com", "net", "org", "ac", "gov")

fun providerName(greeting: String?): String? {
  val message = greeting?.trim().orEmpty()
  if (message.isEmpty() || message.lowercase() in GENERIC) return null
  val name = GREETING.replace(message, "").trim().trim('!', '.', ',', ':', '-').trim()
  if (name.isEmpty() || name.length > 40 || name.split(' ').size > 5) return null
  val folded = name.lowercase()
  if (folded in GENERIC || NOT_A_NAME.any { it in folded }) return null
  return name
}

fun nameFromUrl(url: String, kind: SourceKind): String? {
  val trimmed = url.trim()
  if (trimmed.isEmpty()) return null
  if (kind == SourceKind.M3U) {
    PLAYLIST_FILE.find(trimmed.substringBefore('?'))?.let { return tidy(it.groupValues[1]) }
  }
  val host = HOST.find(trimmed)?.groupValues?.get(1)?.removePrefix("www.").orEmpty()
  if (host.isEmpty()) return null
  if (IPV4.matches(host)) return host
  val labels = host.split('.').filter { it.isNotBlank() }
  if (labels.size < 2) return tidy(labels.firstOrNull().orEmpty())
  var index = labels.size - 2
  if (index > 0 && labels[index] in SECOND_LEVEL) index--
  return tidy(labels[index])
}

fun sourceName(draft: SourceDraft, greeting: String? = null): String {
  val typed = draft.name.trim()
  if (typed.isNotEmpty()) return typed
  return providerName(greeting)
    ?: nameFromUrl(draft.url, draft.kind)
    ?: if (draft.kind == SourceKind.XTREAM) "Provider" else "Playlist"
}

private fun tidy(raw: String): String? {
  val words = raw.split('-', '_', '+', ' ', '.').filter { it.isNotBlank() }
  if (words.isEmpty()) return null
  return words.joinToString(" ") { word ->
    if (word.none { it.isLetter() }) word else word.lowercase().replaceFirstChar { it.uppercase() }
  }
}

package com.keeltv.app.data.epg

private val QUALITY_TOKENS = setOf(
  "hd", "fhd", "uhd", "sd", "hq", "lq", "4k", "8k", "hevc", "h264", "h265",
  "1080", "1080p", "1080i", "720", "720p", "576", "576p", "480", "480p",
  "2160", "2160p", "50fps", "60fps", "fps", "plus", "tvg", "dummy", "backup",
)

private val DECORATIVE_PREFIX = Regex("^\\s*(?:[a-z0-9]{2,4}\\s*)?(?:[^\\p{L}\\p{N}\\s\\x00-\\x7f]|\\|){1,3}\\s+")
private val PIPE_PREFIX = Regex("^\\s*[a-z0-9]{2,4}\\s*\\|\\s*")
private val NON_ALPHANUM = Regex("[^\\p{L}\\p{N}]+")

fun normaliseName(raw: String): String {
  var s = raw.lowercase()
  s = PIPE_PREFIX.replace(s, " ")
  s = DECORATIVE_PREFIX.replace(s, " ")
  s = NON_ALPHANUM.replace(s, " ")
  return s.split(' ').filter { it.isNotEmpty() && it !in QUALITY_TOKENS }.joinToString("")
}

private val COUNTRY_SUFFIX = Regex("\\.([a-z]{2,3}(?:-[a-z]{2,3})?)$", RegexOption.IGNORE_CASE)

fun normaliseId(id: String): String = normaliseName(COUNTRY_SUFFIX.replace(id, ""))

fun idCountry(id: String): String? = COUNTRY_SUFFIX.find(id)?.groupValues?.get(1)?.lowercase()

private fun countryMatches(id: String, hint: String): Boolean {
  val c = idCountry(id) ?: return false
  return c == hint || hint in c.split('-')
}

private val COUNTRY_HINT = Regex("^\\s*([a-z]{2})\\s*[^\\p{L}\\p{N}\\s]", RegexOption.IGNORE_CASE)

fun countryHint(displayName: String): String? =
  COUNTRY_HINT.find(displayName)?.groupValues?.get(1)?.lowercase()

private val COUNTRY_TAG = Regex("^\\s*[a-z]{2}\\s*(?:[^\\p{L}\\p{N}\\s]|\\|)+\\s*", RegexOption.IGNORE_CASE)

private const val PLACEHOLDER_NAMES = 30

fun placeholderGuideIds(links: List<Pair<String, String>>): Set<String> {
  val names = HashMap<String, MutableSet<String>>()
  for ((name, id) in links) {
    names.getOrPut(id) { HashSet() }.add(normaliseName(COUNTRY_TAG.replace(name, "")))
  }
  return names.filterValues { it.size > PLACEHOLDER_NAMES }.keys
}

fun nameKeys(displayName: String): List<String> {
  val keys = ArrayList<String>(2)
  fun push(k: String) { if (k.length >= 2 && k !in keys) keys.add(k) }

  push(normaliseName(displayName))
  val untagged = COUNTRY_TAG.replace(displayName, "")
  if (untagged != displayName) push(normaliseName(untagged))
  return keys
}

data class MatcherChannel(
  val channelId: String,
  val name: String,
  val feedRank: Int,
  val programmes: Int,
)

class ChannelMatcher(channels: List<MatcherChannel>) {
  private val byId = HashMap<String, MatcherChannel>(channels.size * 2)
  private val nameIndex = HashMap<String, MutableList<String>>()
  private val idIndex = HashMap<String, MutableList<String>>()
  private val countries = HashSet<String>()

  init {
    for (c in channels) {
      byId[c.channelId] = c
      add(idIndex, normaliseId(c.channelId), c.channelId)
      if (c.name.isNotEmpty()) add(nameIndex, normaliseName(c.name), c.channelId)
      idCountry(c.channelId)?.let { country ->
        countries.add(country)
        for (part in country.split('-')) countries.add(part)
      }
    }
  }

  private fun add(index: HashMap<String, MutableList<String>>, key: String, id: String) {
    if (key.length < 2) return
    val list = index.getOrPut(key) { ArrayList(1) }
    if (id !in list) list.add(id)
  }

  fun has(channelId: String): Boolean = byId.containsKey(channelId)

  fun resolve(displayName: String): String? {
    if (displayName.isEmpty()) return null
    if (byId.containsKey(displayName)) return displayName

    val hint = countryHint(displayName)
    for (key in nameKeys(displayName)) {
      resolveKey(key, hint)?.let { return it }
    }
    return null
  }

  private fun resolveKey(key: String, hint: String?): String? {
    if (key.length < 2) return null

    var candidates = LinkedHashSet<String>()
    nameIndex[key]?.let(candidates::addAll)
    idIndex[key]?.let(candidates::addAll)
    if (candidates.isEmpty()) return null

    if (candidates.size > 1) {
      val best = candidates.minOf { byId[it]?.feedRank ?: 0 }
      candidates = LinkedHashSet(candidates.filter { (byId[it]?.feedRank ?: 0) == best })
    }
    if (candidates.size == 1) return candidates.first()

    var pool = candidates.toList()
    if (hint != null && hint in countries) {
      val inCountry = pool.filter { countryMatches(it, hint) }
      if (inCountry.isNotEmpty()) pool = inCountry
    }
    if (pool.size == 1) return pool[0]

    if (pool.map { idCountry(it) }.distinct().size > 1) return null
    return fullestId(pool)
  }

  private fun fullestId(ids: List<String>): String = ids.sortedWith(
    compareByDescending<String> { byId[it]?.programmes ?: 0 }
      .thenBy { it.count { c -> c == '.' } }
      .thenBy { it.length }
      .thenBy { it },
  ).first()
}

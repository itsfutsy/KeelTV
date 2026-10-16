package com.keeltv.app.data.epg

import com.keeltv.app.data.model.EpgFeed

private const val BASE = "https://epgshare01.online/epgshare01/"

private fun feed(id: String, label: String, file: String, countries: List<String> = listOf(id)) =
  EpgFeed(id, label, "${BASE}epg_ripper_$file.xml.gz", countries)

val EPG_FEEDS: List<EpgFeed> = listOf(
  feed("ae", "United Arab Emirates", "AE1"),
  feed("al", "Albania", "AL1"),
  feed("ar", "Argentina", "AR1"),
  feed("at", "Austria", "AT1"),
  feed("au", "Australia", "AU1"),
  feed("ba", "Bosnia and Herzegovina", "BA1"),
  feed("bb", "Barbados", "BB1"),
  feed("be", "Belgium", "BE2"),
  feed("bg", "Bulgaria", "BG1"),
  feed("br", "Brazil", "BR1"),
  feed("ca", "Canada", "CA2"),
  feed("ch", "Switzerland", "CH1"),
  feed("cl", "Chile", "CL1"),
  feed("co", "Colombia", "CO1"),
  feed("cr", "Costa Rica", "CR1"),
  feed("cy", "Cyprus", "CY1"),
  feed("cz", "Czechia", "CZ1"),
  feed("de", "Germany", "DE1"),
  feed("dk", "Denmark", "DK1"),
  feed("do", "Dominican Republic", "DO1"),
  feed("ec", "Ecuador", "EC1"),
  feed("eg", "Egypt", "EG1"),
  feed("es", "Spain", "ES1"),
  feed("fi", "Finland", "FI1"),
  feed("fr", "France", "FR1"),
  feed("gr", "Greece", "GR1"),
  feed("hk", "Hong Kong", "HK1"),
  feed("hr", "Croatia", "HR1"),
  feed("hu", "Hungary", "HU1"),
  feed("id", "Indonesia", "ID1"),
  feed("ie", "Ireland", "IE1"),
  feed("il", "Israel", "IL1"),
  feed("in", "India", "IN1"),
  feed("it", "Italy", "IT1"),
  feed("jm", "Jamaica", "JM1"),
  feed("jp", "Japan", "JP1"),
  feed("ke", "Kenya", "KE1"),
  feed("kr", "South Korea", "KR1"),
  feed("lt", "Lithuania", "LT1"),
  feed("lu", "Luxembourg", "LU1"),
  feed("lv", "Latvia", "LV1"),
  feed("mt", "Malta", "MT1"),
  feed("mx", "Mexico", "MX1"),
  feed("my", "Malaysia", "MY1"),
  feed("ng", "Nigeria", "NG1"),
  feed("nl", "Netherlands", "NL1"),
  feed("no", "Norway", "NO1"),
  feed("nz", "New Zealand", "NZ1"),
  feed("pa", "Panama", "PA1"),
  feed("pe", "Peru", "PE1"),
  feed("ph", "Philippines", "PH2"),
  feed("pl", "Poland", "PL1"),
  feed("pt", "Portugal", "PT1"),
  feed("ro", "Romania", "RO1"),
  feed("rs", "Serbia", "RS1"),
  feed("sa", "Saudi Arabia", "SA2"),
  feed("se", "Sweden", "SE1"),
  feed("sg", "Singapore", "SG1"),
  feed("sk", "Slovakia", "SK1"),
  feed("sv", "El Salvador", "SV1"),
  feed("th", "Thailand", "TH1"),
  feed("tr", "Turkey", "TR3"),
  feed("uk", "United Kingdom", "UK1", listOf("uk", "gb")),
  feed("us", "United States", "US2"),
  feed("us-sports", "United States sports", "US_SPORTS1", listOf("us")),
  feed("uy", "Uruguay", "UY1"),
  feed("vn", "Vietnam", "VN1"),
  feed("za", "South Africa", "ZA1"),
  feed("plex", "Plex", "PLEX1", emptyList()),
  feed("rakuten", "Rakuten TV", "RAKUTEN1", emptyList()),
  feed("distrotv", "DistroTV", "DISTROTV1", emptyList()),
)

private val BY_ID = EPG_FEEDS.associateBy { it.id }

private val TAG = Regex("^\\s*([a-z]{2})\\s*(?:[^\\p{L}\\p{N}\\s]|\\|)", RegexOption.IGNORE_CASE)

fun countryTag(name: String): String? = TAG.find(name)?.groupValues?.get(1)?.lowercase()

/**
 * Tags that name a language rather than the country with the same code. An `AR:` section is the
 * Arabic one, as the language filter reads it, not Argentina's, so it reads the UAE guide.
 */
private val LANGUAGE_TAGS = mapOf("ar" to "ae")

private const val AUTO_COUNTRY_CAP = 4

fun autoFeeds(names: Iterable<String>, minChannels: Int = 5, cap: Int = AUTO_COUNTRY_CAP): List<EpgFeed> {
  val counts = HashMap<String, Int>()
  for (name in names) {
    val tag = countryTag(name)?.let { LANGUAGE_TAGS[it] ?: it } ?: continue
    counts[tag] = (counts[tag] ?: 0) + 1
  }
  val top = counts.entries
    .filter { it.value >= minChannels }
    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    .take(cap)
    .mapTo(HashSet()) { it.key }
  return EPG_FEEDS.filter { f -> f.countries.any { it in top } }
}

fun resolveFeed(ref: String): EpgFeed? {
  BY_ID[ref]?.let { return it }
  val url = runCatching { java.net.URI(ref) }.getOrNull() ?: return null
  val scheme = url.scheme?.lowercase()
  if (scheme != "http" && scheme != "https") return null
  return EpgFeed(ref, url.host ?: ref, ref, emptyList())
}

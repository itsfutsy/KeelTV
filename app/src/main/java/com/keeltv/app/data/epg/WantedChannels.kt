package com.keeltv.app.data.epg

data class ArchiveChannel(val providerId: String?, val name: String, val days: Int)

class WantedChannels(
  providerIds: Collection<String>,
  liveNames: Collection<String>,
  archives: Collection<ArchiveChannel> = emptyList(),
) {
  private val ids = providerIds.filter { it.isNotEmpty() }.toHashSet()
  private val keys = HashSet<String>(liveNames.size * 2)
  private val archiveIds = HashMap<String, Int>()
  private val archiveKeys = HashMap<String, Int>()

  init {
    for (name in liveNames) keys.addAll(nameKeys(name))
    for (channel in archives) {
      val days = channel.days
      channel.providerId?.takeIf { it.isNotEmpty() }?.let {
        archiveIds[it] = maxOf(archiveIds[it] ?: 0, days)
      }
      for (key in nameKeys(channel.name)) archiveKeys[key] = maxOf(archiveKeys[key] ?: 0, days)
    }
  }

  fun wants(channelId: String, displayName: String): Boolean {
    if (channelId in ids) return true
    if (normaliseId(channelId) in keys) return true
    return displayName.isNotEmpty() && normaliseName(displayName) in keys
  }

  fun archiveDays(channelId: String, displayName: String): Int = maxOf(
    archiveIds[channelId] ?: 0,
    archiveKeys[normaliseId(channelId)] ?: 0,
    if (displayName.isEmpty()) 0 else archiveKeys[normaliseName(displayName)] ?: 0,
  )
}

package com.keeltv.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "source")
data class Source(
  @PrimaryKey val id: String,
  val kind: SourceKind,
  val name: String,
  val url: String,
  val username: String = "",
  val password: String = "",
  val epgUrl: String? = null,
  val createdAt: Long = 0,
)

@Entity(
  tableName = "category",
  primaryKeys = ["sourceId", "kind", "id"],
  indices = [Index("sourceId", "kind")],
)
data class Category(
  val sourceId: String,
  val id: String,
  val kind: MediaKind,
  val name: String,
  val count: Int = 0,
)

@Entity(
  tableName = "media_item",
  primaryKeys = ["sourceId", "id"],
  indices = [
    Index("sourceId", "kind", "sortTitle"),
    Index("sourceId", "kind", "categoryId"),
    Index("sourceId", "guideChannelId"),
    Index("sourceId", "kind", "search"),
    Index("sourceId", "kind", "ordinal"),
    Index("sourceId", "kind", "rating"),
    Index("sourceId", "kind", "year"),
    Index("sourceId", "kind", "addedAt"),
  ],
)
data class MediaItem(
  val sourceId: String,
  val id: String,
  val kind: MediaKind,
  val name: String,
  val title: String,
  val categoryId: String,
  val categoryName: String? = null,
  val language: String? = null,
  val logo: String? = null,
  val streamId: Long = 0,
  val containerExtension: String? = null,
  val rating: Double? = null,
  val year: Int? = null,
  val plot: String? = null,
  val genre: String? = null,
  val cast: String? = null,
  val director: String? = null,
  val backdrop: String? = null,
  val epgChannelId: String? = null,
  val guideChannelId: String? = null,
  val hasArchive: Boolean = false,
  val archiveDays: Int = 0,
  val seasonCount: Int? = null,
  val durationSecs: Int? = null,
  val addedAt: Long? = null,
  val url: String? = null,
  val search: String = "",
  val sortTitle: String = "",
  val languageTags: String? = null,
  val genreTags: String? = null,
  val trailer: String? = null,
  val ordinal: Int = 0,
)

@Entity(
  tableName = "media_category",
  primaryKeys = ["sourceId", "itemId", "categoryId"],
  indices = [Index("sourceId", "kind", "categoryId")],
)
data class MediaCategory(
  val sourceId: String,
  val itemId: String,
  val kind: MediaKind,
  val categoryId: String,
  val sortTitle: String = "",
  val ordinal: Int = 0,
)

@Entity(
  tableName = "epg_channel",
  primaryKeys = ["sourceId", "channelId"],
  indices = [Index("sourceId", "normalisedName"), Index("sourceId", "normalisedId")],
)
data class EpgChannel(
  val sourceId: String,
  val channelId: String,
  val name: String,
  val icon: String? = null,
  val feedRank: Int = 0,
  val normalisedName: String = "",
  val normalisedId: String = "",
  val country: String? = null,
  val programmeCount: Int = 0,
)

@Entity(
  tableName = "epg_programme",
  indices = [Index("sourceId", "channelId", "start"), Index("sourceId", "start")],
)
data class EpgProgramme(
  @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
  val sourceId: String = "",
  val channelId: String,
  val title: String,
  val description: String? = null,
  val start: Long,
  val stop: Long,
)

@Entity(tableName = "epg_feed_status", primaryKeys = ["sourceId", "feedId"])
data class EpgFeedStatus(
  val sourceId: String,
  val feedId: String,
  val label: String,
  val url: String,
  val feedRank: Int = 0,
  val channels: Int = 0,
  val programmes: Int = 0,
  val lastSync: Long? = null,
  val etag: String? = null,
  val lastModified: String? = null,
  val error: String? = null,
)

@Entity(tableName = "favourite", primaryKeys = ["sourceId", "itemId"])
data class Favourite(
  val sourceId: String,
  val itemId: String,
  val addedAt: Long,
)

@Entity(tableName = "watch_progress", primaryKeys = ["sourceId", "itemId", "episodeId"])
data class WatchProgress(
  val sourceId: String,
  val itemId: String,
  val episodeId: String = "",
  val kind: MediaKind,
  val title: String,
  val image: String? = null,
  val position: Long,
  val duration: Long,
  val updatedAt: Long,
)

@Entity(tableName = "recent_channel", primaryKeys = ["sourceId", "itemId"])
data class RecentChannel(
  val sourceId: String,
  val itemId: String,
  val watchedAt: Long,
)

@Entity(tableName = "guide_override", primaryKeys = ["sourceId", "itemId"])
data class GuideOverride(
  val sourceId: String,
  val itemId: String,
  val channelId: String,
)

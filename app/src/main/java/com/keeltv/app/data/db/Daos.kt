package com.keeltv.app.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.keeltv.app.data.model.Category
import com.keeltv.app.data.model.EpgChannel
import com.keeltv.app.data.model.EpgFeedStatus
import com.keeltv.app.data.model.EpgProgramme
import com.keeltv.app.data.model.Favourite
import com.keeltv.app.data.model.GuideOverride
import com.keeltv.app.data.model.MediaCategory
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.data.model.RecentChannel
import com.keeltv.app.data.model.Source
import com.keeltv.app.data.model.WatchProgress
import com.keeltv.app.data.repo.FacetRow
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
  @Query("SELECT * FROM source ORDER BY createdAt")
  fun observeAll(): Flow<List<Source>>

  @Query("SELECT * FROM source ORDER BY createdAt")
  suspend fun all(): List<Source>

  @Query("SELECT * FROM source WHERE id = :id")
  suspend fun byId(id: String): Source?

  @Upsert
  suspend fun upsert(source: Source)

  @Query("DELETE FROM source WHERE id = :id")
  suspend fun delete(id: String)
}

@Dao
interface CatalogDao {
  @Query(
    """
    SELECT c.* FROM category c
    WHERE c.sourceId = :sourceId AND c.kind = :kind
      AND (:language = '' OR EXISTS (
        SELECT 1 FROM media_category m
        JOIN media_item i ON i.sourceId = m.sourceId AND i.id = m.itemId
        WHERE m.sourceId = c.sourceId AND m.kind = c.kind AND m.categoryId = c.id
          AND ((:language = 'unknown' AND i.languageTags IS NULL)
            OR i.languageTags LIKE '%|' || :language || '|%')
      ))
    ORDER BY c.name COLLATE NOCASE
    """,
  )
  fun observeCategories(sourceId: String, kind: MediaKind, language: String): Flow<List<Category>>

  @Query("SELECT COUNT(*) FROM category WHERE sourceId = :sourceId AND kind = :kind")
  suspend fun countCategories(sourceId: String, kind: MediaKind): Int

  @Query("SELECT COUNT(*) FROM media_item WHERE sourceId = :sourceId AND kind = :kind")
  suspend fun countItems(sourceId: String, kind: MediaKind): Int

  @Query(
    """
    SELECT COUNT(*) FROM media_item
    WHERE sourceId = :sourceId AND kind = :kind
      AND (:language = '' OR (:language = 'unknown' AND languageTags IS NULL)
        OR languageTags LIKE '%|' || :language || '|%')
    """,
  )
  fun observeItemCount(sourceId: String, kind: MediaKind, language: String): Flow<Int>

  @Query("SELECT * FROM media_item WHERE sourceId = :sourceId AND id = :itemId")
  suspend fun item(sourceId: String, itemId: String): MediaItem?

  @Query("SELECT * FROM media_item WHERE sourceId = :sourceId AND id = :itemId")
  fun observeItem(sourceId: String, itemId: String): Flow<MediaItem?>

  @Query(
    """
    SELECT * FROM media_item
    WHERE sourceId = :sourceId AND kind = :kind
      AND (:language = '' OR (:language = 'unknown' AND languageTags IS NULL)
        OR languageTags LIKE '%|' || :language || '|%')
    ORDER BY sortTitle
    """,
  )
  fun pageKind(sourceId: String, kind: MediaKind, language: String): PagingSource<Int, MediaItem>

  @RawQuery
  suspend fun search(query: SupportSQLiteQuery): List<MediaItem>

  @RawQuery
  suspend fun searchCount(query: SupportSQLiteQuery): Int

  @Query("SELECT * FROM category WHERE sourceId = :sourceId AND kind = :kind AND id = :categoryId")
  suspend fun category(sourceId: String, kind: MediaKind, categoryId: String): Category?

  /**
   * Browse with a sort and facets chosen at runtime. A `@Query` cannot take an `ORDER BY` as a
   * parameter and a `CASE` in one throws the index away, which on a large catalogue is the
   * difference between walking an index and sorting the section. `BrowseQuery` builds the
   * statement.
   */
  @RawQuery(observedEntities = [MediaItem::class])
  fun pageBrowse(query: SupportSQLiteQuery): PagingSource<Int, MediaItem>

  @RawQuery
  suspend fun browseCount(query: SupportSQLiteQuery): Int

  @RawQuery
  suspend fun facetSample(query: SupportSQLiteQuery): List<FacetRow>

  @Query(
    """
    SELECT i.* FROM media_item i
    JOIN media_category m ON m.sourceId = i.sourceId AND m.itemId = i.id
    WHERE i.sourceId = :sourceId AND m.kind = 'live' AND m.categoryId = :categoryId
      AND (:language = '' OR (:language = 'unknown' AND i.languageTags IS NULL)
        OR i.languageTags LIKE '%|' || :language || '|%')
    ORDER BY m.sortTitle LIMIT :limit OFFSET :offset
    """,
  )
  suspend fun liveInCategory(sourceId: String, categoryId: String, language: String, limit: Int, offset: Int): List<MediaItem>

  @Query(
    """
    SELECT COUNT(*) FROM media_item i
    JOIN media_category m ON m.sourceId = i.sourceId AND m.itemId = i.id
    WHERE i.sourceId = :sourceId AND m.kind = 'live' AND m.categoryId = :categoryId
      AND (:language = '' OR (:language = 'unknown' AND i.languageTags IS NULL)
        OR i.languageTags LIKE '%|' || :language || '|%')
    """,
  )
  suspend fun countLiveInCategory(sourceId: String, categoryId: String, language: String): Int

  @Query(
    """
    SELECT COUNT(*) FROM media_item i
    JOIN media_category m ON m.sourceId = i.sourceId AND m.itemId = i.id
    WHERE i.sourceId = :sourceId AND m.kind = 'live' AND m.categoryId = :categoryId
      AND m.sortTitle < :sortTitle
      AND (:language = '' OR (:language = 'unknown' AND i.languageTags IS NULL)
        OR i.languageTags LIKE '%|' || :language || '|%')
    """,
  )
  suspend fun liveBeforeInCategory(sourceId: String, categoryId: String, language: String, sortTitle: String): Int

  @Query(
    """
    SELECT i.id FROM media_item i
    JOIN media_category m ON m.sourceId = i.sourceId AND m.itemId = i.id
    WHERE i.sourceId = :sourceId AND m.kind = 'live' AND m.categoryId = :categoryId
      AND m.sortTitle = :sortTitle
      AND (:language = '' OR (:language = 'unknown' AND i.languageTags IS NULL)
        OR i.languageTags LIKE '%|' || :language || '|%')
    ORDER BY m.sortTitle
    """,
  )
  suspend fun liveTieInCategory(sourceId: String, categoryId: String, language: String, sortTitle: String): List<String>

  @Query(
    """
    SELECT * FROM media_item
    WHERE sourceId = :sourceId AND kind = 'live'
      AND (:language = '' OR (:language = 'unknown' AND languageTags IS NULL)
        OR languageTags LIKE '%|' || :language || '|%')
    ORDER BY sortTitle LIMIT :limit OFFSET :offset
    """,
  )
  suspend fun liveChannels(sourceId: String, language: String, limit: Int, offset: Int): List<MediaItem>

  @Query(
    """
    SELECT COUNT(*) FROM media_item
    WHERE sourceId = :sourceId AND kind = 'live'
      AND (:language = '' OR (:language = 'unknown' AND languageTags IS NULL)
        OR languageTags LIKE '%|' || :language || '|%')
    """,
  )
  suspend fun countLive(sourceId: String, language: String): Int

  @Query(
    """
    SELECT COUNT(*) FROM media_item
    WHERE sourceId = :sourceId AND kind = 'live' AND sortTitle < :sortTitle
      AND (:language = '' OR (:language = 'unknown' AND languageTags IS NULL)
        OR languageTags LIKE '%|' || :language || '|%')
    """,
  )
  suspend fun liveBefore(sourceId: String, language: String, sortTitle: String): Int

  @Query(
    """
    SELECT id FROM media_item
    WHERE sourceId = :sourceId AND kind = 'live' AND sortTitle = :sortTitle
      AND (:language = '' OR (:language = 'unknown' AND languageTags IS NULL)
        OR languageTags LIKE '%|' || :language || '|%')
    ORDER BY sortTitle
    """,
  )
  suspend fun liveTie(sourceId: String, language: String, sortTitle: String): List<String>

  @Query("SELECT id, name FROM media_item WHERE sourceId = :sourceId AND kind = 'live'")
  fun liveNames(sourceId: String): List<ItemName>

  @Query("SELECT id, epgChannelId AS name FROM media_item WHERE sourceId = :sourceId AND kind = 'live' AND epgChannelId IS NOT NULL")
  fun liveProviderChannels(sourceId: String): List<ItemName>

  @Query("SELECT id, name, guideChannelId FROM media_item WHERE sourceId = :sourceId AND kind = 'live'")
  fun liveGuideLinks(sourceId: String): List<GuideLink>

  @Query(
    """
    SELECT id, name, epgChannelId, archiveDays FROM media_item
    WHERE sourceId = :sourceId AND kind = 'live' AND hasArchive != 0
    """,
  )
  fun liveArchives(sourceId: String): List<ArchiveItem>

  @Query(
    """
    SELECT COALESCE(MAX(MAX(archiveDays, :fallback)), 0) FROM media_item
    WHERE sourceId = :sourceId AND kind = 'live' AND hasArchive != 0
    """,
  )
  suspend fun maxArchiveDays(sourceId: String, fallback: Int): Int

  @Query("UPDATE media_item SET guideChannelId = :channelId WHERE sourceId = :sourceId AND id = :itemId")
  fun setGuideChannel(sourceId: String, itemId: String, channelId: String?)

  @Query(
    """
    UPDATE media_item SET
      guideChannelId = CASE WHEN guideChannelId = epgChannelId THEN NULL ELSE guideChannelId END,
      epgChannelId = NULL
    WHERE sourceId = :sourceId AND kind = 'live' AND epgChannelId IN (:ids)
    """,
  )
  fun dropProviderGuideIds(sourceId: String, ids: List<String>)

  @Query(
    """
    SELECT * FROM media_item
    WHERE sourceId > :sourceId OR (sourceId = :sourceId AND id > :id)
    ORDER BY sourceId, id LIMIT :limit
    """,
  )
  fun pageAfter(sourceId: String, id: String, limit: Int): List<MediaItem>

  @Query(
    """
    UPDATE media_item SET title = :title, sortTitle = :sortTitle, search = :search,
      languageTags = :tags
    WHERE sourceId = :sourceId AND id = :itemId
    """,
  )
  fun setDerived(
    sourceId: String,
    itemId: String,
    title: String,
    sortTitle: String,
    search: String,
    tags: String?,
  )

  @Query("UPDATE media_category SET sortTitle = :sortTitle WHERE sourceId = :sourceId AND itemId = :itemId")
  fun setMembershipSort(sourceId: String, itemId: String, sortTitle: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insertItems(items: List<MediaItem>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insertMemberships(rows: List<MediaCategory>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insertCategories(categories: List<Category>)

  @Query("DELETE FROM media_item WHERE sourceId = :sourceId")
  fun clearItems(sourceId: String)

  @Query("DELETE FROM media_category WHERE sourceId = :sourceId")
  fun clearMemberships(sourceId: String)

  @Query("DELETE FROM category WHERE sourceId = :sourceId")
  fun clearCategories(sourceId: String)

  @Transaction
  fun clearCatalog(sourceId: String) {
    clearItems(sourceId)
    clearMemberships(sourceId)
    clearCategories(sourceId)
  }

  @Transaction
  fun writeBatch(items: List<MediaItem>, memberships: List<MediaCategory>) {
    insertItems(items)
    insertMemberships(memberships)
  }
}

data class ItemName(val id: String, val name: String)

data class GuideLink(val id: String, val name: String, val guideChannelId: String?)

private const val GUIDE_CLEAR_BATCH = 5000

data class ArchiveItem(
  val id: String,
  val name: String,
  val epgChannelId: String?,
  val archiveDays: Int,
)

@Dao
interface EpgDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insertChannels(channels: List<EpgChannel>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insertProgrammes(programmes: List<EpgProgramme>)

  @Upsert
  suspend fun upsertFeedStatus(status: EpgFeedStatus)

  @Query("SELECT * FROM epg_feed_status WHERE sourceId = :sourceId ORDER BY feedRank")
  fun observeFeedStatus(sourceId: String): Flow<List<EpgFeedStatus>>

  @Query("SELECT * FROM epg_feed_status WHERE sourceId = :sourceId ORDER BY feedRank")
  suspend fun feedStatus(sourceId: String): List<EpgFeedStatus>

  @Query("DELETE FROM epg_feed_status WHERE sourceId = :sourceId AND feedId NOT IN (:keep)")
  suspend fun pruneFeedStatus(sourceId: String, keep: List<String>)

  @Query("SELECT channelId, name, feedRank, programmeCount AS programmes FROM epg_channel WHERE sourceId = :sourceId")
  fun matcherChannels(sourceId: String): List<com.keeltv.app.data.epg.MatcherChannel>

  @Query("SELECT * FROM epg_channel WHERE sourceId = :sourceId AND (name LIKE '%' || :query || '%' ESCAPE '\' OR channelId LIKE '%' || :query || '%' ESCAPE '\') ORDER BY feedRank, name LIMIT :limit")
  suspend fun searchChannels(sourceId: String, query: String, limit: Int): List<EpgChannel>

  @Query("SELECT COUNT(*) FROM epg_channel WHERE sourceId = :sourceId")
  suspend fun countChannels(sourceId: String): Int

  @Query("SELECT COUNT(*) FROM epg_programme WHERE sourceId = :sourceId")
  suspend fun countProgrammes(sourceId: String): Int

  @Query(
    """
    SELECT * FROM epg_programme
    WHERE sourceId = :sourceId AND channelId = :channelId AND stop > :at
    ORDER BY start LIMIT 2
    """,
  )
  suspend fun nowNext(sourceId: String, channelId: String, at: Long): List<EpgProgramme>

  @Query(
    """
    SELECT * FROM epg_programme
    WHERE sourceId = :sourceId AND channelId = :channelId AND stop > :at
    ORDER BY start LIMIT 2
    """,
  )
  fun observeNowNext(sourceId: String, channelId: String, at: Long): Flow<List<EpgProgramme>>

  /**
   * The guide grid: every programme overlapping a window, for the channels on screen.
   *
   * `start >= :floor` is what turns this into a range scan. Without it the index on
   * (sourceId, channelId, start) is entered at the channel's first listing and walked forward to
   * `to`, so a channel keeping a week of catch-up is read from a week ago to answer about an hour.
   */
  @Query(
    """
    SELECT * FROM epg_programme
    WHERE sourceId = :sourceId AND channelId IN (:channelIds)
      AND start >= :floor AND start < :to AND stop > :from
    ORDER BY channelId, start
    """,
  )
  suspend fun window(
    sourceId: String,
    channelIds: List<String>,
    floor: Long,
    from: Long,
    to: Long,
  ): List<EpgProgramme>

  @Query(
    """
    SELECT * FROM epg_programme
    WHERE sourceId = :sourceId AND channelId = :channelId AND start < :to AND stop > :from
    ORDER BY start
    """,
  )
  suspend fun channelWindow(sourceId: String, channelId: String, from: Long, to: Long): List<EpgProgramme>

  @Query("DELETE FROM epg_programme WHERE rowId IN (SELECT rowId FROM epg_programme WHERE sourceId = :sourceId LIMIT :limit)")
  fun deleteProgrammes(sourceId: String, limit: Int): Int

  @Query("DELETE FROM epg_channel WHERE sourceId = :sourceId")
  fun clearChannels(sourceId: String)

  @Query("DELETE FROM epg_programme WHERE sourceId = :sourceId AND stop < :before")
  suspend fun pruneOld(sourceId: String, before: Long)

  fun clearGuide(sourceId: String) {
    do {
      val dropped = deleteProgrammes(sourceId, GUIDE_CLEAR_BATCH)
    } while (dropped >= GUIDE_CLEAR_BATCH)
    clearChannels(sourceId)
  }
}

@Dao
interface LibraryDao {
  /**
   * The favourites and recents joins start from their own small table and look each title up by
   * its key. Left to itself SQLite starts from `media_item` and walks every row of the source to
   * find the few that match, and these queries run again on every write to the catalogue.
   * `CROSS JOIN` is how SQLite is told which side goes first.
   */
  @Query(
    """
    SELECT i.* FROM favourite f
    CROSS JOIN media_item i ON i.sourceId = f.sourceId AND i.id = f.itemId
    WHERE f.sourceId = :sourceId ORDER BY f.addedAt DESC
    """,
  )
  fun observeFavourites(sourceId: String): Flow<List<MediaItem>>

  @Query("SELECT EXISTS(SELECT 1 FROM favourite WHERE sourceId = :sourceId AND itemId = :itemId)")
  fun observeIsFavourite(sourceId: String, itemId: String): Flow<Boolean>

  @Query("SELECT EXISTS(SELECT 1 FROM favourite WHERE sourceId = :sourceId AND itemId = :itemId)")
  suspend fun isFavourite(sourceId: String, itemId: String): Boolean

  @Upsert
  suspend fun addFavourite(favourite: Favourite)

  @Query("DELETE FROM favourite WHERE sourceId = :sourceId AND itemId = :itemId")
  suspend fun removeFavourite(sourceId: String, itemId: String)

  @Query("SELECT * FROM watch_progress WHERE sourceId = :sourceId ORDER BY updatedAt DESC LIMIT :limit")
  fun observeProgress(sourceId: String, limit: Int): Flow<List<WatchProgress>>

  @Query("SELECT * FROM watch_progress WHERE sourceId = :sourceId AND itemId = :itemId AND episodeId = :episodeId")
  suspend fun progressFor(sourceId: String, itemId: String, episodeId: String): WatchProgress?

  @Upsert
  suspend fun saveProgress(progress: WatchProgress)

  @Query("DELETE FROM watch_progress WHERE sourceId = :sourceId AND itemId = :itemId AND (:episodeId IS NULL OR episodeId = :episodeId)")
  suspend fun clearProgress(sourceId: String, itemId: String, episodeId: String?)

  @Query(
    """
    SELECT i.* FROM recent_channel r
    CROSS JOIN media_item i ON i.sourceId = r.sourceId AND i.id = r.itemId
    WHERE r.sourceId = :sourceId ORDER BY r.watchedAt DESC LIMIT :limit
    """,
  )
  fun observeRecentChannels(sourceId: String, limit: Int): Flow<List<MediaItem>>

  @Query(
    """
    SELECT i.* FROM favourite f
    CROSS JOIN media_item i ON i.sourceId = f.sourceId AND i.id = f.itemId
    WHERE f.sourceId = :sourceId AND i.kind = 'live' ORDER BY f.addedAt DESC
    """,
  )
  suspend fun favouriteChannels(sourceId: String): List<MediaItem>

  @Query(
    """
    SELECT i.* FROM recent_channel r
    CROSS JOIN media_item i ON i.sourceId = r.sourceId AND i.id = r.itemId
    WHERE r.sourceId = :sourceId ORDER BY r.watchedAt DESC LIMIT :limit
    """,
  )
  suspend fun recentChannelList(sourceId: String, limit: Int): List<MediaItem>

  @Upsert
  suspend fun touchRecentChannel(recent: RecentChannel)

  @Query("SELECT * FROM guide_override WHERE sourceId = :sourceId")
  fun overrides(sourceId: String): List<GuideOverride>

  @Upsert
  suspend fun setOverride(override: GuideOverride)

  @Query("DELETE FROM guide_override WHERE sourceId = :sourceId AND itemId = :itemId")
  suspend fun clearOverride(sourceId: String, itemId: String)
}

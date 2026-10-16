package com.keeltv.app.data.repo

import com.keeltv.app.data.db.KeelTvDatabase
import com.keeltv.app.data.model.Favourite
import com.keeltv.app.data.model.MediaItem
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.data.model.RecentChannel
import com.keeltv.app.data.model.WatchProgress
import com.keeltv.app.tv.WatchNext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

private const val PROGRESS_LIMIT = 60
private const val RECENT_CHANNEL_LIMIT = 20

class LibraryRepository(
  private val db: KeelTvDatabase,
  private val watchNext: WatchNext? = null,
) {
  fun favourites(sourceId: String): Flow<List<MediaItem>> = db.library().observeFavourites(sourceId)

  fun isFavourite(sourceId: String, itemId: String): Flow<Boolean> =
    db.library().observeIsFavourite(sourceId, itemId)

  suspend fun toggleFavourite(sourceId: String, itemId: String): Boolean = withContext(Dispatchers.IO) {
    if (db.library().isFavourite(sourceId, itemId)) {
      db.library().removeFavourite(sourceId, itemId)
      false
    } else {
      db.library().addFavourite(Favourite(sourceId, itemId, System.currentTimeMillis()))
      true
    }
  }

  fun continueWatching(sourceId: String): Flow<List<WatchProgress>> =
    db.library().observeProgress(sourceId, PROGRESS_LIMIT)

  suspend fun progressFor(sourceId: String, itemId: String, episodeId: String = ""): WatchProgress? =
    withContext(Dispatchers.IO) { db.library().progressFor(sourceId, itemId, episodeId) }

  suspend fun saveProgress(
    sourceId: String,
    item: MediaItem,
    positionMs: Long,
    durationMs: Long,
    episodeId: String = "",
  ) = withContext(Dispatchers.IO) {
    val row = WatchProgress(
      sourceId = sourceId,
      itemId = item.id,
      episodeId = episodeId,
      kind = item.kind,
      title = item.title,
      image = item.logo ?: item.backdrop,
      position = positionMs,
      duration = durationMs,
      updatedAt = System.currentTimeMillis(),
    )
    db.library().saveProgress(row)
    if (item.kind != MediaKind.LIVE) watchNext?.save(row)
  }

  suspend fun clearProgress(sourceId: String, itemId: String, episodeId: String? = null) =
    withContext(Dispatchers.IO) {
      db.library().clearProgress(sourceId, itemId, episodeId)
      watchNext?.remove(sourceId, itemId, episodeId)
    }

  fun recentChannels(sourceId: String): Flow<List<MediaItem>> =
    db.library().observeRecentChannels(sourceId, RECENT_CHANNEL_LIMIT)

  suspend fun touchChannel(sourceId: String, item: MediaItem) = withContext(Dispatchers.IO) {
    if (item.kind != MediaKind.LIVE) return@withContext
    db.library().touchRecentChannel(RecentChannel(sourceId, item.id, System.currentTimeMillis()))
  }
}

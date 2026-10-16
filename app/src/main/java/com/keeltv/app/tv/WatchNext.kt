package com.keeltv.app.tv

import android.content.Context
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.keeltv.app.data.model.MediaKind
import com.keeltv.app.data.model.WatchProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WatchNext(private val context: Context) {

  suspend fun save(progress: WatchProgress) = withContext(Dispatchers.IO) {
    if (progress.duration <= 0) return@withContext
    val key = key(progress.sourceId, progress.itemId, progress.episodeId)
    val program = WatchNextProgram.Builder()
      .setType(
        if (progress.kind == MediaKind.SERIES) {
          TvContractCompat.PreviewProgramColumns.TYPE_TV_EPISODE
        } else {
          TvContractCompat.PreviewProgramColumns.TYPE_MOVIE
        },
      )
      .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
      .setTitle(progress.title)
      .setInternalProviderId(key)
      .setLastPlaybackPositionMillis(progress.position.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
      .setDurationMillis(progress.duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
      .setLastEngagementTimeUtcMillis(progress.updatedAt)
      .setIntentUri(Uri.parse(DeepLink.play(progress.itemId, progress.episodeId, progress.position)))
      .apply {
        progress.image?.takeIf { it.isNotBlank() }?.let {
          setPosterArtUri(Uri.parse(it))
          setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3)
        }
      }
      .build()

    runCatching {
      val existing = idOf(key)
      if (existing == null) {
        context.contentResolver.insert(
          TvContractCompat.WatchNextPrograms.CONTENT_URI,
          program.toContentValues(),
        )
      } else {
        context.contentResolver.update(
          TvContractCompat.buildWatchNextProgramUri(existing),
          program.toContentValues(),
          null,
          null,
        )
      }
    }
    Unit
  }

  suspend fun remove(sourceId: String, itemId: String, episodeId: String?) = withContext(Dispatchers.IO) {
    val prefix = key(sourceId, itemId, episodeId ?: "")
    runCatching {
      for ((id, providerId) in rows()) {
        val hit = if (episodeId == null) providerId.startsWith(key(sourceId, itemId, "")) else providerId == prefix
        if (hit) context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null)
      }
    }
    Unit
  }

  private fun idOf(key: String): Long? = rows().firstOrNull { it.second == key }?.first

  private fun rows(): List<Pair<Long, String>> {
    val cursor = context.contentResolver.query(
      TvContractCompat.WatchNextPrograms.CONTENT_URI,
      WatchNextProgram.PROJECTION,
      null,
      null,
      null,
    ) ?: return emptyList()
    return cursor.use {
      buildList {
        while (it.moveToNext()) {
          val program = WatchNextProgram.fromCursor(it)
          val providerId = program.internalProviderId ?: continue
          if (providerId.startsWith(PREFIX)) add(program.id to providerId)
        }
      }
    }
  }

  private fun key(sourceId: String, itemId: String, episodeId: String) = "$PREFIX$sourceId|$itemId|$episodeId"

  private companion object {
    const val PREFIX = "keeltv:"
  }
}

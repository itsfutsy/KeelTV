package com.keeltv.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.keeltv.app.data.model.SourceKind
import com.keeltv.app.data.model.WatchProgress

class Converters {
  @TypeConverter fun mediaKindToWire(kind: MediaKind?): String? = kind?.wire
  @TypeConverter fun wireToMediaKind(wire: String?): MediaKind? = wire?.let { MediaKind.of(it) }
  @TypeConverter fun sourceKindToWire(kind: SourceKind?): String? = kind?.wire
  @TypeConverter fun wireToSourceKind(wire: String?): SourceKind? =
    wire?.let { w -> SourceKind.entries.firstOrNull { it.wire == w } }
}

@Database(
  entities = [
    Source::class,
    Category::class,
    MediaItem::class,
    MediaCategory::class,
    EpgChannel::class,
    EpgProgramme::class,
    EpgFeedStatus::class,
    Favourite::class,
    WatchProgress::class,
    RecentChannel::class,
    GuideOverride::class,
  ],
  version = 5,
  exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KeelTvDatabase : RoomDatabase() {
  abstract fun sources(): SourceDao
  abstract fun catalog(): CatalogDao
  abstract fun epg(): EpgDao
  abstract fun library(): LibraryDao

  companion object {
    fun open(context: Context): KeelTvDatabase =
      Room.databaseBuilder(context, KeelTvDatabase::class.java, "keeltv.db")
        .addMigrations(SEARCH_BY_KIND)
        .fallbackToDestructiveMigration(dropAllTables = true)
        // Room's default drops write-ahead logging on a box Android flags as low on memory, which
        // is a good share of TV boxes. Without it a refresh writing the catalogue holds every
        // screen's read off until each batch commits, and the list under the remote stalls.
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .build()
  }
}

private val SEARCH_BY_KIND = object : Migration(4, 5) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("DROP INDEX IF EXISTS index_media_item_sourceId_search")
    db.execSQL(
      "CREATE INDEX IF NOT EXISTS index_media_item_sourceId_kind_search ON media_item (sourceId, kind, search)",
    )
  }
}

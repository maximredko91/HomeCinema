package com.homecinema.library.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType = MediaType.valueOf(value)

    @TypeConverter
    fun fromDownloadState(value: DownloadState): String = value.name

    @TypeConverter
    fun toDownloadState(value: String): DownloadState = DownloadState.valueOf(value)
}

/** Adds media_items.tags (from .nfo <tag>, for #hashtag search) - a real migration rather than
 * relying on the destructive fallback below, now that watch history and download progress are
 * real data worth keeping across an upgrade rather than starting from an empty library. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media_items ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
    }
}

/** Adds favorites (a plain column on media_items) and user-created custom lists (two new
 * tables) - same real-migration approach as MIGRATION_5_6 above. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media_items ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS custom_lists (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, createdAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS list_items (listId TEXT NOT NULL, itemId TEXT NOT NULL, " +
                "PRIMARY KEY(listId, itemId), " +
                "FOREIGN KEY(listId) REFERENCES custom_lists(id) ON DELETE CASCADE, " +
                "FOREIGN KEY(itemId) REFERENCES media_items(id) ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_list_items_itemId ON list_items(itemId)")
    }
}

/** Adds media_items.originalTitle (from .nfo <originaltitle>) - same real-migration approach
 * as the ones above. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media_items ADD COLUMN originalTitle TEXT")
    }
}

/** Adds media_items.mpaa/studio/tagline (from .nfo <mpaa>/<studio>/<tagline>) - same
 * real-migration approach as the ones above. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media_items ADD COLUMN mpaa TEXT")
        db.execSQL("ALTER TABLE media_items ADD COLUMN studio TEXT")
        db.execSQL("ALTER TABLE media_items ADD COLUMN tagline TEXT")
    }
}

@Database(
    entities = [MediaItemEntity::class, SmbSourceEntity::class, CustomListEntity::class, ListItemCrossRef::class],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun smbSourceDao(): SmbSourceDao
    abstract fun listDao(): ListDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "home_cinema.db")
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                // Safety net only for gaps not covered by an explicit migration above -
                // every version bump from here on should get a real Migration instead.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

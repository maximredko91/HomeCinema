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

@Database(entities = [MediaItemEntity::class, SmbSourceEntity::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun smbSourceDao(): SmbSourceDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "home_cinema.db")
                .addMigrations(MIGRATION_5_6)
                // Safety net only for gaps not covered by an explicit migration above -
                // every version bump from here on should get a real Migration instead.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

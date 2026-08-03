package com.homecinema.library.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

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

@Database(entities = [MediaItemEntity::class, SmbSourceEntity::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun smbSourceDao(): SmbSourceDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "home_cinema.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}

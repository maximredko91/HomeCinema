package com.homecinema.library.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmbSourceDao {

    @Query("SELECT * FROM smb_sources ORDER BY name ASC")
    fun observeAll(): Flow<List<SmbSourceEntity>>

    @Query("SELECT * FROM smb_sources WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SmbSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: SmbSourceEntity)

    @Query("DELETE FROM smb_sources WHERE id = :id")
    suspend fun delete(id: String)
}

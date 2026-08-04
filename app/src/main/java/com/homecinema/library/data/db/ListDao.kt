package com.homecinema.library.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ListCountRow(val listId: String, val cnt: Int)

@Dao
interface ListDao {

    @Query("SELECT * FROM custom_lists ORDER BY createdAt ASC")
    fun observeLists(): Flow<List<CustomListEntity>>

    @Insert
    suspend fun insertList(list: CustomListEntity)

    @Query("UPDATE custom_lists SET name = :name WHERE id = :id")
    suspend fun renameList(id: String, name: String)

    @Query("DELETE FROM custom_lists WHERE id = :id")
    suspend fun deleteList(id: String)

    // Already-in-list is a no-op, not an error - the UI toggles membership freely.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItemToList(crossRef: ListItemCrossRef)

    @Query("DELETE FROM list_items WHERE listId = :listId AND itemId = :itemId")
    suspend fun removeItemFromList(listId: String, itemId: String)

    @Query("SELECT m.* FROM media_items m INNER JOIN list_items li ON m.id = li.itemId WHERE li.listId = :listId ORDER BY m.title ASC")
    fun observeItemsInList(listId: String): Flow<List<MediaItemEntity>>

    @Query("SELECT listId FROM list_items WHERE itemId = :itemId")
    fun observeListIdsForItem(itemId: String): Flow<List<String>>

    @Query("SELECT listId, COUNT(*) as cnt FROM list_items GROUP BY listId")
    fun observeListCounts(): Flow<List<ListCountRow>>
}

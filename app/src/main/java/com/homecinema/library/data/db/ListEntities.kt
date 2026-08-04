package com.homecinema.library.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A user-created named list (e.g. "Пересмотреть", "С детьми") - separate from the
 * NFO-derived "Коллекции" (Kodi movie sets/franchises), which are automatic and read-only. */
@Entity(tableName = "custom_lists")
data class CustomListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long
)

/** Membership of a title in a custom list - many-to-many, cascades on either side being
 * deleted so removing a list or a title never leaves orphaned rows behind. */
@Entity(
    tableName = "list_items",
    primaryKeys = ["listId", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = CustomListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("itemId")]
)
data class ListItemCrossRef(
    val listId: String,
    val itemId: String
)

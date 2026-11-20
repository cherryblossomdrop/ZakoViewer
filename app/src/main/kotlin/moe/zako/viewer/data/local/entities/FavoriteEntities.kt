package moe.zako.viewer.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorite_folders",
    indices = [Index(value = ["parentId"])]
)
data class FavoriteFolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val parentId: Long? = null, // null = root folder, non-null = subfolder (infinite nesting)
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "favorite_items",
    foreignKeys = [
        ForeignKey(
            entity = FavoriteFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["videoId"]),
        Index(value = ["folderId", "videoId"], unique = true)
    ]
)
data class FavoriteItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val folderId: Long,
    val videoId: String,
    val title: String,
    val coverUrl: String,
    val duration: String = "",
    val isVip: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

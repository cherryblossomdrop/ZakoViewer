package moe.zako.viewer.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.local.entities.FavoriteItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    // --- Folder Operations ---
    @Query("SELECT * FROM favorite_folders WHERE parentId IS NULL ORDER BY name ASC")
    fun getRootFoldersFlow(): Flow<List<FavoriteFolderEntity>>

    @Query("SELECT * FROM favorite_folders WHERE parentId IS NULL ORDER BY name ASC")
    suspend fun getRootFoldersList(): List<FavoriteFolderEntity>

    @Query("SELECT * FROM favorite_folders WHERE parentId = :parentId ORDER BY name ASC")
    fun getSubFoldersFlow(parentId: Long): Flow<List<FavoriteFolderEntity>>

    @Query("SELECT * FROM favorite_folders WHERE parentId = :parentId ORDER BY name ASC")
    suspend fun getSubFoldersList(parentId: Long): List<FavoriteFolderEntity>

    @Query("SELECT * FROM favorite_folders WHERE id = :folderId LIMIT 1")
    suspend fun getFolderById(folderId: Long): FavoriteFolderEntity?

    @Query("SELECT * FROM favorite_folders ORDER BY parentId ASC, name ASC")
    suspend fun getAllFoldersList(): List<FavoriteFolderEntity>

    @Query("SELECT * FROM favorite_folders ORDER BY parentId ASC, name ASC")
    fun getAllFoldersFlow(): Flow<List<FavoriteFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FavoriteFolderEntity): Long

    @Update
    suspend fun updateFolder(folder: FavoriteFolderEntity)

    @Delete
    suspend fun deleteFolder(folder: FavoriteFolderEntity)

    @Query("DELETE FROM favorite_folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: Long)

    @Query("UPDATE favorite_folders SET parentId = :targetParentId WHERE id IN (:folderIds)")
    suspend fun batchMoveFolders(folderIds: List<Long>, targetParentId: Long)

    @Query("DELETE FROM favorite_folders WHERE id IN (:folderIds) AND parentId IS NOT NULL")
    suspend fun batchDeleteFolders(folderIds: List<Long>)

    // --- Item (Metadata Index) Operations ---
    @Query("SELECT * FROM favorite_items WHERE folderId = :folderId ORDER BY addedAt DESC")
    fun getItemsInFolderFlow(folderId: Long): Flow<List<FavoriteItemEntity>>

    @Query("SELECT * FROM favorite_items WHERE folderId = :folderId AND title LIKE '%' || :query || '%' ORDER BY addedAt DESC")
    fun searchItemsInFolderFlow(folderId: Long, query: String): Flow<List<FavoriteItemEntity>>

    @Query("""
        WITH RECURSIVE subfolders(id) AS (
            SELECT id FROM favorite_folders WHERE id = :folderId
            UNION ALL
            SELECT f.id FROM favorite_folders f
            JOIN subfolders s ON f.parentId = s.id
        )
        SELECT * FROM favorite_items 
        WHERE folderId IN (SELECT id FROM subfolders) 
          AND title LIKE '%' || :query || '%' 
        ORDER BY addedAt DESC
    """)
    fun searchItemsInFolderRecursiveFlow(folderId: Long, query: String): Flow<List<FavoriteItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: FavoriteItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<FavoriteItemEntity>)

    @Query("DELETE FROM favorite_items WHERE id IN (:itemIds)")
    suspend fun deleteItemsByIds(itemIds: List<Long>)

    @Query("DELETE FROM favorite_items WHERE folderId = :folderId AND videoId = :videoId")
    suspend fun deleteItem(folderId: Long, videoId: String)

    @Query("SELECT COUNT(*) FROM favorite_items WHERE folderId = :folderId")
    suspend fun getItemCountInFolder(folderId: Long): Int

    @Query("SELECT COUNT(*) > 0 FROM favorite_items WHERE videoId = :videoId")
    suspend fun isFavorite(videoId: String): Boolean

    @Query("SELECT COUNT(*) > 0 FROM favorite_items WHERE videoId = :videoId")
    fun isFavoriteFlow(videoId: String): Flow<Boolean>

    @Query("SELECT * FROM favorite_items WHERE videoId = :videoId LIMIT 1")
    suspend fun getItemByVideoId(videoId: String): FavoriteItemEntity?

    @Query("UPDATE favorite_items SET coverUrl = :coverUrl WHERE videoId = :videoId")
    suspend fun updateCoverUrl(videoId: String, coverUrl: String)

    @Query("UPDATE favorite_items SET duration = :duration WHERE videoId = :videoId")
    suspend fun updateDuration(videoId: String, duration: String)

    @Query("DELETE FROM favorite_items WHERE videoId = :videoId")
    suspend fun deleteItemByVideoId(videoId: String)

    @Query("UPDATE favorite_items SET title = :newTitle WHERE id = :itemId")
    suspend fun renameItem(itemId: Long, newTitle: String)

    @Query("SELECT * FROM favorite_items")
    suspend fun getAllItemsList(): List<FavoriteItemEntity>

    // --- Batch Metadata Index Move & Copy ---
    @Query("UPDATE favorite_items SET folderId = :targetFolderId WHERE id IN (:itemIds)")
    suspend fun batchMoveItems(itemIds: List<Long>, targetFolderId: Long)

    @Transaction
    suspend fun batchCopyItems(itemsToCopy: List<FavoriteItemEntity>, targetFolderId: Long) {
        val newItems = itemsToCopy.map {
            it.copy(id = 0, folderId = targetFolderId, addedAt = System.currentTimeMillis())
        }
        insertItems(newItems)
    }
}

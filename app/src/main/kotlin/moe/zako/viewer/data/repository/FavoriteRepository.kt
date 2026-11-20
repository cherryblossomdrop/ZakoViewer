package moe.zako.viewer.data.repository

import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.data.local.dao.FavoriteDao
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.local.entities.FavoriteItemEntity
import kotlinx.coroutines.flow.Flow

class FavoriteRepository(private val favoriteDao: FavoriteDao) {
    companion object {
        private const val TAG = "FavoriteRepository"
    }

    // --- Infinite Folder Tree System ---
    suspend fun ensureDefaultRootFolderExists(): FavoriteFolderEntity {
        val rootFolders = favoriteDao.getRootFoldersList()
        if (rootFolders.isEmpty()) {
            ZakoLog.i(TAG, "Creating pre-built unique root favorite folder: '我的收藏'")
            val defaultFolder = FavoriteFolderEntity(
                parentId = null,
                name = "我的收藏",
                createdAt = System.currentTimeMillis()
            )
            val id = favoriteDao.insertFolder(defaultFolder)
            return defaultFolder.copy(id = id)
        }
        val primaryRoot = rootFolders.first()
        // 根收藏夹必须有且仅有一个：如果存在多余历史根收藏夹，自动归并挂靠为子收藏夹
        if (rootFolders.size > 1) {
            for (extra in rootFolders.drop(1)) {
                favoriteDao.updateFolder(extra.copy(parentId = primaryRoot.id, updatedAt = System.currentTimeMillis()))
            }
        }
        return primaryRoot
    }

    fun getRootFolders(): Flow<List<FavoriteFolderEntity>> = favoriteDao.getRootFoldersFlow()

    fun getSubFolders(parentId: Long): Flow<List<FavoriteFolderEntity>> = favoriteDao.getSubFoldersFlow(parentId)

    suspend fun getFolderById(id: Long): FavoriteFolderEntity? = favoriteDao.getFolderById(id)

    suspend fun getAllFolders(): List<FavoriteFolderEntity> {
        val folders = favoriteDao.getAllFoldersList()
        if (folders.isEmpty() || folders.none { it.parentId == null }) {
            ensureDefaultRootFolderExists()
            return favoriteDao.getAllFoldersList()
        }
        return folders
    }

    suspend fun createFolder(name: String, parentId: Long?): Long {
        // 用户不能创建根收藏夹，根收藏夹必须有且仅有一个，若 parentId 为空则默认挂靠至唯一根收藏夹
        val targetParentId = parentId ?: ensureDefaultRootFolderExists().id
        ZakoLog.i(TAG, "Creating favorite folder: '$name' under parent: $targetParentId")
        return favoriteDao.insertFolder(
            FavoriteFolderEntity(
                parentId = targetParentId,
                name = name,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun renameFolder(folderId: Long, newName: String) {
        val folder = favoriteDao.getFolderById(folderId) ?: return
        favoriteDao.updateFolder(folder.copy(name = newName, updatedAt = System.currentTimeMillis()))
    }

    suspend fun renameItem(itemId: Long, newTitle: String) {
        favoriteDao.renameItem(itemId, newTitle)
    }

    suspend fun deleteFolder(folderId: Long): Boolean {
        val folder = favoriteDao.getFolderById(folderId) ?: return false
        if (folder.parentId == null) {
            // 根收藏夹不能被删除，必须始终存在
            ZakoLog.w(TAG, "Cannot delete root folder: $folderId ('${folder.name}')")
            return false
        }
        ZakoLog.i(TAG, "Deleting favorite folder: $folderId ('${folder.name}')")
        deleteFolderRecursive(folderId)
        return true
    }

    private suspend fun deleteFolderRecursive(folderId: Long) {
        val subFolders = favoriteDao.getSubFoldersList(folderId)
        for (sub in subFolders) {
            deleteFolderRecursive(sub.id)
        }
        favoriteDao.deleteFolderById(folderId)
    }

    suspend fun batchMoveFolders(folderIds: List<Long>, targetFolderId: Long) {
        val validIds = folderIds.filter { it != targetFolderId }
        for (id in validIds) {
            val f = favoriteDao.getFolderById(id)
            if (f != null && f.parentId != null) {
                favoriteDao.updateFolder(f.copy(parentId = targetFolderId, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    suspend fun batchDeleteFolders(folderIds: List<Long>) {
        for (id in folderIds) {
            deleteFolder(id)
        }
    }

    // --- Metadata Index Operations ---
    fun getItemsInFolder(folderId: Long): Flow<List<FavoriteItemEntity>> =
        favoriteDao.getItemsInFolderFlow(folderId)

    fun searchItemsInFolder(folderId: Long, query: String): Flow<List<FavoriteItemEntity>> =
        favoriteDao.searchItemsInFolderRecursiveFlow(folderId, query)

    suspend fun addToFavorites(
        folderId: Long,
        videoId: String,
        title: String,
        coverUrl: String,
        duration: String = "",
        isVip: Boolean = false
    ) {
        favoriteDao.insertItem(
            FavoriteItemEntity(
                folderId = folderId,
                videoId = videoId,
                title = title,
                coverUrl = coverUrl,
                duration = duration,
                isVip = isVip
            )
        )
    }

    suspend fun deleteItems(itemIds: List<Long>) {
        favoriteDao.deleteItemsByIds(itemIds)
    }

    suspend fun isFavorite(videoId: String): Boolean = favoriteDao.isFavorite(videoId)

    fun isFavoriteFlow(videoId: String): Flow<Boolean> = favoriteDao.isFavoriteFlow(videoId)

    suspend fun removeFromFavorites(videoId: String) = favoriteDao.deleteItemByVideoId(videoId)

    // --- Batch Metadata Move & Copy ---
    suspend fun batchMove(itemIds: List<Long>, targetFolderId: Long) {
        ZakoLog.i(TAG, "Batch moving ${itemIds.size} items to folder $targetFolderId")
        favoriteDao.batchMoveItems(itemIds, targetFolderId)
    }

    suspend fun batchCopy(items: List<FavoriteItemEntity>, targetFolderId: Long) {
        ZakoLog.i(TAG, "Batch copying ${items.size} items to folder $targetFolderId")
        favoriteDao.batchCopyItems(items, targetFolderId)
    }

    suspend fun moveItem(itemId: Long, targetFolderId: Long) {
        favoriteDao.batchMoveItems(listOf(itemId), targetFolderId)
    }

    suspend fun copyItem(item: FavoriteItemEntity, targetFolderId: Long) {
        favoriteDao.batchCopyItems(listOf(item), targetFolderId)
    }

    suspend fun deleteItem(itemId: Long) {
        favoriteDao.deleteItemsByIds(listOf(itemId))
    }
}

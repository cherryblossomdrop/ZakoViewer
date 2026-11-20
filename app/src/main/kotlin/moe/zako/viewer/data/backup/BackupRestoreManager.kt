package moe.zako.viewer.data.backup

import android.content.Context
import com.google.gson.Gson
import moe.zako.viewer.core.logger.NeoLog
import moe.zako.viewer.data.local.NeoDatabase
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.local.entities.FavoriteItemEntity
import moe.zako.viewer.data.local.entities.WatchHistoryEntity
import moe.zako.viewer.data.local.entities.WatchLaterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupBundle(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val folders: List<FavoriteFolderEntity> = emptyList(),
    val favoriteItems: List<FavoriteItemEntity> = emptyList(),
    val watchHistory: List<WatchHistoryEntity> = emptyList(),
    val watchLater: List<WatchLaterEntity> = emptyList()
)

class BackupRestoreManager(
    private val context: Context,
    private val db: NeoDatabase = NeoDatabase.getInstance(context),
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "BackupRestoreManager"
    }

    /**
     * Exports full backup. If includeDownloadedVideos is true, packages JSON + video files into a .zip.
     */
    suspend fun exportBackup(includeDownloadedVideos: Boolean): File = withContext(Dispatchers.IO) {
        val backupDir = File(context.cacheDir, "backups")
        if (!backupDir.exists()) backupDir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val folders = db.favoriteDao().getAllFoldersList()
        val items = db.favoriteDao().getAllItemsList()
        val history = db.watchHistoryDao().getAllHistoryList()
        val watchLater = db.watchLaterDao().getAllList()

        val bundle = BackupBundle(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            folders = folders,
            favoriteItems = items,
            watchHistory = history,
            watchLater = watchLater
        )
        val jsonContent = gson.toJson(bundle)

        if (!includeDownloadedVideos) {
            val jsonFile = File(backupDir, "NeoViewer_Backup_$timeStamp.json")
            jsonFile.writeText(jsonContent)
            NeoLog.i(TAG, "Exported JSON metadata backup to: ${jsonFile.absolutePath}")
            return@withContext jsonFile
        }

        // Bundle with downloaded videos into a ZIP archive
        val zipFile = File(backupDir, "NeoViewer_FullBackup_$timeStamp.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            // Entry 1: metadata
            zos.putNextEntry(ZipEntry("backup_metadata.json"))
            zos.write(jsonContent.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Entry 2+: downloaded video files
            val downloadsDir = File(context.getExternalFilesDir(null), "downloads")
            if (downloadsDir.exists()) {
                val videoFiles = downloadsDir.listFiles() ?: emptyArray()
                for (file in videoFiles) {
                    if (file.isFile) {
                        zos.putNextEntry(ZipEntry("videos/${file.name}"))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }
        NeoLog.i(TAG, "Exported full ZIP backup with video files to: ${zipFile.absolutePath}")
        zipFile
    }

    /**
     * Restores data from a backup JSON or ZIP file
     */
    suspend fun restoreBackup(backupFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            NeoLog.i(TAG, "Restoring backup from: ${backupFile.absolutePath}")
            var jsonString: String? = null

            if (backupFile.name.endsWith(".zip", ignoreCase = true)) {
                // Extract zip
                val downloadsDir = File(context.getExternalFilesDir(null), "downloads")
                if (!downloadsDir.exists()) downloadsDir.mkdirs()

                ZipInputStream(BufferedInputStream(FileInputStream(backupFile))).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "backup_metadata.json") {
                            jsonString = zis.bufferedReader().readText()
                        } else if (entry.name.startsWith("videos/")) {
                            val fileName = entry.name.removePrefix("videos/")
                            if (fileName.isNotEmpty()) {
                                val outFile = File(downloadsDir, fileName)
                                outFile.outputStream().use { zis.copyTo(it) }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } else {
                jsonString = backupFile.readText()
            }

            if (jsonString.isNullOrEmpty()) {
                NeoLog.e(TAG, "Backup restore failed: no valid metadata found")
                return@withContext false
            }

            val bundle = gson.fromJson(jsonString, BackupBundle::class.java)

            // Map old folder IDs to new folder IDs to preserve infinite tree relationships
            val oldToNewFolderIdMap = mutableMapOf<Long, Long>()
            val sortedFolders = bundle.folders.sortedBy { it.parentId ?: 0L }

            for (folder in sortedFolders) {
                val newParentId = folder.parentId?.let { oldToNewFolderIdMap[it] }
                val newId = db.favoriteDao().insertFolder(
                    folder.copy(id = 0, parentId = newParentId, updatedAt = System.currentTimeMillis())
                )
                oldToNewFolderIdMap[folder.id] = newId
            }

            // Restore favorite items metadata
            val newItems = bundle.favoriteItems.mapNotNull { item ->
                val newFolderId = oldToNewFolderIdMap[item.folderId] ?: return@mapNotNull null
                item.copy(id = 0, folderId = newFolderId)
            }
            db.favoriteDao().insertItems(newItems)

            // Restore watch history
            for (h in bundle.watchHistory) {
                db.watchHistoryDao().insertOrUpdate(h)
            }

            // Restore watch later
            for (wl in bundle.watchLater) {
                db.watchLaterDao().insert(wl)
            }

            NeoLog.i(TAG, "Successfully restored ${bundle.folders.size} folders, ${bundle.favoriteItems.size} favorites, ${bundle.watchHistory.size} history items")
            true
        } catch (e: Exception) {
            NeoLog.e(TAG, "Restore error", e)
            false
        }
    }
}

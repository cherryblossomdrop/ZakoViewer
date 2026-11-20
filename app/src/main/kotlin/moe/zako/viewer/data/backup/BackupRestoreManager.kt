package moe.zako.viewer.data.backup

import android.content.Context
import com.google.gson.Gson
import moe.zako.viewer.core.download.DownloadManager
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.data.local.ZakoDatabase
import moe.zako.viewer.data.local.entities.DownloadEntity
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
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupBundle(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val folders: List<FavoriteFolderEntity> = emptyList(),
    val favoriteItems: List<FavoriteItemEntity> = emptyList(),
    val watchHistory: List<WatchHistoryEntity> = emptyList(),
    val watchLater: List<WatchLaterEntity> = emptyList(),
    val downloads: List<DownloadEntity> = emptyList()
)

class BackupRestoreManager(
    private val context: Context,
    private val db: ZakoDatabase = ZakoDatabase.getInstance(context),
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "BackupRestoreManager"
        private const val BUFFER_SIZE = 32768 // 32KB
    }

    /**
     * Check if a file is a ZIP archive by magic number (0x504B0304) or extension
     */
    private fun isZipArchive(file: File): Boolean {
        if (file.name.endsWith(".zip", ignoreCase = true)) return true
        if (file.length() < 4) return false
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(4)
                if (fis.read(header) == 4) {
                    header[0] == 0x50.toByte() && // 'P'
                    header[1] == 0x4B.toByte() && // 'K'
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()
                } else {
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Exports full backup. If includeDownloadedVideos is true, packages JSON + video files into a .zip.
     * Otherwise exports lightweight .json backup.
     */
    suspend fun exportBackup(includeDownloadedVideos: Boolean): File = withContext(Dispatchers.IO) {
        val backupDir = File(context.cacheDir, "backups")
        if (!backupDir.exists()) backupDir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val folders = db.favoriteDao().getAllFoldersList()
        val items = db.favoriteDao().getAllItemsList()
        val history = db.watchHistoryDao().getAllHistoryList()
        val watchLater = db.watchLaterDao().getAllList()
        val downloads = db.downloadDao().getAllDownloadsList()

        val bundle = BackupBundle(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            folders = folders,
            favoriteItems = items,
            watchHistory = history,
            watchLater = watchLater,
            downloads = downloads
        )
        val jsonContent = gson.toJson(bundle)

        if (!includeDownloadedVideos) {
            val jsonFile = File(backupDir, "ZakoViewer_Backup_$timeStamp.json")
            jsonFile.writeText(jsonContent)
            ZakoLog.i(TAG, "Exported JSON metadata backup to: ${jsonFile.absolutePath}")
            return@withContext jsonFile
        }

        // Bundle with downloaded videos into a ZIP archive with NO_COMPRESSION for high speed
        val zipFile = File(backupDir, "ZakoViewer_FullBackup_$timeStamp.zip")
        val zipOut = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile)))
        zipOut.use { zos ->
            zos.setLevel(Deflater.NO_COMPRESSION)

            // Entry 1: metadata
            zos.putNextEntry(ZipEntry("backup_metadata.json"))
            val jsonBytes = jsonContent.toByteArray(Charsets.UTF_8)
            zos.write(jsonBytes)
            zos.closeEntry()

            // Entry 2+: downloaded video files
            val videoFiles = mutableSetOf<File>()
            val downloadManager = DownloadManager.getInstance(context)
            val primaryDownloadsDir = downloadManager.getDownloadDir()
            if (primaryDownloadsDir.exists() && primaryDownloadsDir.isDirectory) {
                primaryDownloadsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp4", ignoreCase = true) }?.let {
                    videoFiles.addAll(it)
                }
            }
            val appDownloadsDir = File(context.getExternalFilesDir(null), "downloads")
            if (appDownloadsDir.exists() && appDownloadsDir.isDirectory) {
                appDownloadsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp4", ignoreCase = true) }?.let {
                    videoFiles.addAll(it)
                }
            }
            for (dl in downloads) {
                val f = File(dl.savePath)
                if (f.exists() && f.isFile && f.name.endsWith(".mp4", ignoreCase = true)) {
                    videoFiles.add(f)
                }
            }

            val buffer = ByteArray(BUFFER_SIZE)
            for (file in videoFiles) {
                if (file.isFile) {
                    zos.putNextEntry(ZipEntry("videos/${file.name}"))
                    file.inputStream().use { fis ->
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            zos.write(buffer, 0, bytesRead)
                        }
                    }
                    zos.closeEntry()
                }
            }
        }
        ZakoLog.i(TAG, "Exported full ZIP backup with video files to: ${zipFile.absolutePath}")
        zipFile
    }

    /**
     * Restores data from a backup JSON or ZIP file
     */
    suspend fun restoreBackup(backupFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            ZakoLog.i(TAG, "Restoring backup from: ${backupFile.absolutePath}")
            var jsonString: String? = null
            val restoredVideos = mutableListOf<File>()

            if (isZipArchive(backupFile)) {
                val downloadManager = DownloadManager.getInstance(context)
                val downloadsDir = downloadManager.getDownloadDir()
                if (!downloadsDir.exists()) downloadsDir.mkdirs()

                val buffer = ByteArray(BUFFER_SIZE)
                ZipInputStream(BufferedInputStream(FileInputStream(backupFile))).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        if (entry.isDirectory) {
                            zis.closeEntry()
                            entry = zis.nextEntry
                            continue
                        }

                        val entryName = entry.name
                        if (entryName.endsWith(".json", ignoreCase = true)) {
                            val baos = ByteArrayOutputStream()
                            var bytesRead: Int
                            while (zis.read(buffer).also { bytesRead = it } != -1) {
                                baos.write(buffer, 0, bytesRead)
                            }
                            jsonString = baos.toString(Charsets.UTF_8.name())
                            zis.closeEntry()
                        } else if (entryName.endsWith(".mp4", ignoreCase = true)) {
                            val fileName = File(entryName).name
                            if (fileName.isNotEmpty()) {
                                val outFile = File(downloadsDir, fileName)
                                FileOutputStream(outFile).use { fos ->
                                    var bytesRead: Int
                                    while (zis.read(buffer).also { bytesRead = it } != -1) {
                                        fos.write(buffer, 0, bytesRead)
                                    }
                                    fos.flush()
                                }
                                restoredVideos.add(outFile)
                            }
                            zis.closeEntry()
                        } else {
                            zis.closeEntry()
                        }

                        entry = zis.nextEntry
                    }
                }
            } else {
                // Pure JSON backup
                jsonString = backupFile.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }

            if (jsonString.isNullOrEmpty()) {
                ZakoLog.e(TAG, "Backup restore failed: no valid metadata found")
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

            // Restore downloads records
            for (dl in bundle.downloads) {
                db.downloadDao().insertOrUpdate(dl)
            }

            val allFavorites = db.favoriteDao().getAllItemsList()
            val allHistory = db.watchHistoryDao().getAllHistoryList()
            val allWatchLater = db.watchLaterDao().getAllList()

            // Synchronize Room database local video file paths for restored video files
            for (outFile in restoredVideos) {
                val fileName = outFile.name
                val idCandidate = if (fileName.contains("-")) {
                    fileName.substringBeforeLast(".mp4").substringAfterLast("-").trim()
                } else {
                    fileName.substringBeforeLast(".mp4").trim()
                }
                val allDownloads = db.downloadDao().getAllDownloadsList()
                val matched = allDownloads.find { File(it.savePath).name == fileName || (idCandidate.isNotEmpty() && it.videoId == idCandidate) }

                if (matched != null) {
                    db.downloadDao().insertOrUpdate(
                        matched.copy(
                            savePath = outFile.absolutePath,
                            status = DownloadEntity.STATUS_COMPLETED,
                            totalBytes = outFile.length(),
                            downloadedBytes = outFile.length(),
                            completedAt = matched.completedAt ?: System.currentTimeMillis()
                        )
                    )
                } else {
                    val matchedBundleDl = bundle.downloads.find { it.videoId == idCandidate || File(it.savePath).name == fileName }
                    val matchedFav = allFavorites.find { it.videoId == idCandidate }
                    val matchedHist = allHistory.find { it.videoId == idCandidate }
                    val matchedLater = allWatchLater.find { it.videoId == idCandidate }

                    val title = matchedBundleDl?.title
                        ?: matchedFav?.title
                        ?: matchedHist?.title
                        ?: matchedLater?.title
                        ?: if (fileName.contains("-")) fileName.substringBeforeLast(".mp4").substringBeforeLast("-").trim() else fileName.substringBeforeLast(".mp4").trim()

                    val coverUrl = matchedBundleDl?.coverUrl
                        ?: matchedFav?.coverUrl
                        ?: matchedHist?.coverUrl
                        ?: matchedLater?.coverUrl
                        ?: ""

                    val newEntity = DownloadEntity(
                        videoId = if (idCandidate.isNotEmpty()) idCandidate else fileName.substringBeforeLast(".mp4"),
                        title = title.ifEmpty { "视频 $idCandidate" },
                        coverUrl = coverUrl,
                        savePath = outFile.absolutePath,
                        totalBytes = outFile.length(),
                        downloadedBytes = outFile.length(),
                        status = DownloadEntity.STATUS_COMPLETED,
                        createdAt = System.currentTimeMillis(),
                        completedAt = System.currentTimeMillis()
                    )
                    db.downloadDao().insertOrUpdate(newEntity)
                    ZakoLog.i(TAG, "Restored and registered download entity: ${newEntity.title} (${newEntity.videoId})")
                }
            }

            // Also reconcile any other video files in target & app download directories
            DownloadManager.getInstance(context).reconcileLocalDownloadFiles()

            ZakoLog.i(TAG, "Successfully restored ${bundle.folders.size} folders, ${bundle.favoriteItems.size} favorites, ${bundle.watchHistory.size} history items, ${restoredVideos.size} videos")
            true
        } catch (e: Exception) {
            ZakoLog.e(TAG, "Restore error", e)
            false
        }
    }
}

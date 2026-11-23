package moe.zako.viewer.data.backup

import android.content.Context
import com.google.gson.Gson
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.core.download.DownloadManager
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.core.storage.ExportResult
import moe.zako.viewer.core.storage.PublicStorageHelper
import moe.zako.viewer.core.storage.StorageConstants
import moe.zako.viewer.data.local.ZakoDatabase
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.local.entities.FavoriteItemEntity
import moe.zako.viewer.data.local.entities.WatchHistoryEntity
import moe.zako.viewer.data.local.entities.WatchLaterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class BackupProgress(
    val stage: String,
    val detail: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val percent: Float? = null
)

data class RestoreResult(
    val success: Boolean,
    val folderCount: Int = 0,
    val favoriteItemCount: Int = 0,
    val historyCount: Int = 0,
    val watchLaterCount: Int = 0,
    val videoCount: Int = 0,
    val coverCount: Int = 0,
    val errorMessage: String? = null
)

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
        private const val BUFFER_SIZE = StorageConstants.STREAM_IO_BUFFER_SIZE
    }

    /**
     * Check if a file is a ZIP archive by magic number (0x504B0304) or extension
     */
    private fun isZipArchive(file: File): Boolean {
        if (file.name.endsWith(".zip", ignoreCase = true)) return true
        if (file.length() < 4) return false
        return try {
            file.inputStream().use { fis ->
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
     * Exports full backup. 导出的备份文件统一采用 .zip 格式，且强制打包 covers/{videoId}.jpg 与 backup.json；
     * 若 includeDownloadedVideos 为 true，一并打包已下载的 .mp4 文件。
     * 直接导出至公共 Download/ZakoViewer/Backups/ 目录，不残留私有缓存。
     */
    suspend fun exportBackup(
        includeDownloadedVideos: Boolean,
        onProgress: (BackupProgress) -> Unit = {}
    ): ExportResult = withContext(Dispatchers.IO) {
        onProgress(BackupProgress(stage = "正在准备备份数据…", detail = "读取数据库索引与列表", percent = 0.05f))

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

        // 收集所有 4 个来源的唯一 videoId
        val allVideoIds = mutableSetOf<String>()
        items.forEach { if (it.videoId.isNotBlank()) allVideoIds.add(it.videoId) }
        history.forEach { if (it.videoId.isNotBlank()) allVideoIds.add(it.videoId) }
        watchLater.forEach { if (it.videoId.isNotBlank()) allVideoIds.add(it.videoId) }
        downloads.forEach { if (it.videoId.isNotBlank()) allVideoIds.add(it.videoId) }

        // 打包前若本地未缓存封面，先尝试按补全链拉取保存
        if (allVideoIds.isNotEmpty()) {
            for ((idx, videoId) in allVideoIds.withIndex()) {
                if (!CoverCacheManager.hasValidCover(context, videoId)) {
                    val candidateUrl = items.find { it.videoId == videoId }?.coverUrl
                        ?: downloads.find { it.videoId == videoId }?.coverUrl
                        ?: watchLater.find { it.videoId == videoId }?.coverUrl
                        ?: history.find { it.videoId == videoId }?.coverUrl
                    val localPath = downloads.find { it.videoId == videoId }?.savePath
                    CoverCacheManager.ensureCoverPersisted(context, videoId, candidateUrl, localPath)
                }
                if (idx % 5 == 0 || idx == allVideoIds.size - 1) {
                    val p = 0.05f + 0.15f * ((idx + 1).toFloat() / allVideoIds.size)
                    onProgress(
                        BackupProgress(
                            stage = "正在准备封面缓存…",
                            detail = "检查并补全封面 (${idx + 1}/${allVideoIds.size})",
                            current = idx + 1,
                            total = allVideoIds.size,
                            percent = p
                        )
                    )
                }
            }
        }

        // 统一采用 ZIP 归档格式导出备份至公共 Download/ZakoViewer/Backups/
        val zipFileName = "ZakoViewer_Backup_$timeStamp.zip"

        val exportResult = PublicStorageHelper.createPublicDownloadOutputStream(
            context = context,
            subDir = PublicStorageHelper.BACKUPS_SUB_DIR,
            fileName = zipFileName,
            mimeType = "application/zip"
        ) { outputStream ->
            val zipOut = ZipOutputStream(BufferedOutputStream(outputStream))
            zipOut.use { zos ->
                zos.setLevel(Deflater.NO_COMPRESSION)

                // Entry 1: metadata json
                onProgress(BackupProgress(stage = "正在写入备份归档…", detail = "写入 backup.json 元数据", percent = 0.25f))
                zos.putNextEntry(ZipEntry("backup.json"))
                val jsonBytes = jsonContent.toByteArray(Charsets.UTF_8)
                zos.write(jsonBytes)
                zos.closeEntry()

                // Entry 2+: covers/{videoId}.jpg 流式写入 ZIP
                for ((idx, videoId) in allVideoIds.withIndex()) {
                    val coverFile = CoverCacheManager.getCoverFile(context, videoId)
                    if (coverFile.exists() && coverFile.length() > 0L) {
                        zos.putNextEntry(ZipEntry("covers/$videoId.jpg"))
                        coverFile.inputStream().use { fis ->
                            fis.copyTo(zos, BUFFER_SIZE)
                        }
                        zos.closeEntry()
                    }
                    if (idx % 10 == 0 || idx == allVideoIds.size - 1) {
                        val p = 0.25f + 0.15f * ((idx + 1).toFloat() / allVideoIds.size)
                        onProgress(
                            BackupProgress(
                                stage = "正在打包封面资源…",
                                detail = "已打包封面 (${idx + 1}/${allVideoIds.size})",
                                current = idx + 1,
                                total = allVideoIds.size,
                                percent = p
                            )
                        )
                    }
                }

                // Entry 3+: downloaded video files (仅在勾选时打入)
                if (includeDownloadedVideos) {
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

                    val totalVideos = videoFiles.size
                    for ((idx, file) in videoFiles.withIndex()) {
                        val p = 0.40f + 0.55f * (idx.toFloat() / maxOf(1, totalVideos))
                        onProgress(
                            BackupProgress(
                                stage = "正在打包已下载视频…",
                                detail = "正在打包 (${idx + 1}/$totalVideos): ${file.name}",
                                current = idx + 1,
                                total = totalVideos,
                                percent = p
                            )
                        )
                        if (file.isFile) {
                            zos.putNextEntry(ZipEntry("videos/${file.name}"))
                            file.inputStream().use { fis ->
                                fis.copyTo(zos, BUFFER_SIZE)
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }
        }

        onProgress(BackupProgress(stage = "正在完成备份导出…", detail = "完成写入", percent = 1.0f))

        // 清理可能遗留的历史 cache/backups 临时文件
        try {
            val legacyBackupDir = File(context.cacheDir, "backups")
            if (legacyBackupDir.exists()) {
                legacyBackupDir.deleteRecursively()
            }
        } catch (_: Exception) {}

        ZakoLog.i(TAG, "Exported ZIP backup to public downloads: ${exportResult.displayPath}")
        exportResult
    }

    /**
     * Restores data from a backup JSON or ZIP file with fine-grained real-time progress callbacks
     */
    suspend fun restoreBackup(
        backupFile: File,
        onProgress: (BackupProgress) -> Unit = {}
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            ZakoLog.i(TAG, "Restoring backup from: ${backupFile.absolutePath}")
            var jsonString: String? = null
            val restoredVideos = mutableListOf<File>()
            var restoredCoverCount = 0

            if (isZipArchive(backupFile)) {
                onProgress(BackupProgress(stage = "正在解析备份文件…", detail = "读取归档目录索引", percent = null))

                val zipFile = try {
                    ZipFile(backupFile)
                } catch (e: Exception) {
                    ZakoLog.e(TAG, "Failed opening zip archive", e)
                    return@withContext RestoreResult(success = false, errorMessage = "无法打开归档文件: ${e.message}")
                }

                try {
                    val entries = zipFile.entries().toList()
                    val jsonEntry = entries.find { !it.isDirectory && it.name.endsWith(".json", ignoreCase = true) }
                    if (jsonEntry == null) {
                        return@withContext RestoreResult(success = false, errorMessage = "归档内未找到备份元数据索引 (backup.json)")
                    }

                    jsonString = zipFile.getInputStream(jsonEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }

                    val coverEntries = entries.filter {
                        !it.isDirectory && (it.name.startsWith("covers/") || it.name.contains("/covers/")) &&
                                (it.name.endsWith(".jpg", ignoreCase = true) || it.name.endsWith(".png", ignoreCase = true))
                    }
                    restoredCoverCount = coverEntries.size
                    val videoEntries = entries.filter { !it.isDirectory && it.name.endsWith(".mp4", ignoreCase = true) }

                    val downloadManager = DownloadManager.getInstance(context)
                    val downloadsDir = downloadManager.getDownloadDir()
                    if (!downloadsDir.exists()) {
                        try { downloadsDir.mkdirs() } catch (_: Exception) {}
                    }
                    val fallbackDir = File(context.getExternalFilesDir(null), "downloads")
                    if (!fallbackDir.exists()) {
                        try { fallbackDir.mkdirs() } catch (_: Exception) {}
                    }

                    // 阶段 2.1: 解压封面
                    if (coverEntries.isNotEmpty()) {
                        val totalCoverCount = coverEntries.size
                        for ((idx, entry) in coverEntries.withIndex()) {
                            val fileName = File(entry.name).name
                            val targetCover = File(CoverCacheManager.getCoversDir(context), fileName)
                            try {
                                zipFile.getInputStream(entry).use { input ->
                                    targetCover.outputStream().use { fos ->
                                        input.copyTo(fos, BUFFER_SIZE)
                                        fos.flush()
                                    }
                                }
                            } catch (e: Exception) {
                                ZakoLog.w(TAG, "Failed extracting cover $fileName from backup: ${e.message}")
                            }

                            if (idx % 5 == 0 || idx == totalCoverCount - 1) {
                                val weight = if (videoEntries.isNotEmpty()) 0.3f else 0.8f
                                val p = weight * ((idx + 1).toFloat() / totalCoverCount)
                                onProgress(
                                    BackupProgress(
                                        stage = "正在解压封面缓存…",
                                        detail = "已解压封面 (${idx + 1}/$totalCoverCount)",
                                        current = idx + 1,
                                        total = totalCoverCount,
                                        percent = p
                                    )
                                )
                            }
                        }
                    }

                    // 阶段 2.2: 解压视频文件
                    if (videoEntries.isNotEmpty()) {
                        val totalVideoCount = videoEntries.size
                        for ((idx, entry) in videoEntries.withIndex()) {
                            val fileName = File(entry.name).name
                            if (fileName.isEmpty()) continue

                            val p = 0.3f + 0.55f * (idx.toFloat() / totalVideoCount)
                            onProgress(
                                BackupProgress(
                                    stage = "正在恢复视频文件…",
                                    detail = "正在恢复视频 (${idx + 1}/$totalVideoCount): $fileName",
                                    current = idx + 1,
                                    total = totalVideoCount,
                                    percent = p
                                )
                            )

                            var targetFile = File(downloadsDir, fileName)
                            var writeSucceeded = false

                            // 1. 优先尝试写入选定的下载目录
                            try {
                                if (targetFile.exists()) {
                                    try { targetFile.delete() } catch (_: Exception) {}
                                }
                                zipFile.getInputStream(entry).use { input ->
                                    targetFile.outputStream().use { fos ->
                                        input.copyTo(fos, BUFFER_SIZE)
                                        fos.flush()
                                    }
                                }
                                writeSucceeded = true
                                ZakoLog.i(TAG, "Restored video to primary download dir: ${targetFile.absolutePath}")
                            } catch (e: Exception) {
                                ZakoLog.w(TAG, "Writing to primary download dir ($targetFile) failed: ${e.message}, falling back to app external files sandbox")
                                val sandboxFile = File(fallbackDir, fileName)
                                try {
                                    if (sandboxFile.exists()) {
                                        try { sandboxFile.delete() } catch (_: Exception) {}
                                    }
                                    zipFile.getInputStream(entry).use { input ->
                                        sandboxFile.outputStream().use { fos ->
                                            input.copyTo(fos, BUFFER_SIZE)
                                            fos.flush()
                                        }
                                    }
                                    targetFile = sandboxFile
                                    writeSucceeded = true
                                    ZakoLog.i(TAG, "Restored video to sandbox download dir: ${sandboxFile.absolutePath}")
                                } catch (e2: Exception) {
                                    ZakoLog.e(TAG, "Writing to sandbox fallback ($sandboxFile) failed: ${e2.message}", e2)
                                }
                            }

                            if (writeSucceeded) {
                                restoredVideos.add(targetFile)
                            }
                        }
                    }
                } finally {
                    try { zipFile.close() } catch (_: Exception) {}
                }
            } else {
                // Pure JSON backup
                onProgress(BackupProgress(stage = "正在读取备份文件…", detail = "读取 JSON 元数据", percent = null))
                jsonString = backupFile.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }

            if (jsonString.isNullOrEmpty()) {
                ZakoLog.e(TAG, "Backup restore failed: no valid metadata found")
                return@withContext RestoreResult(success = false, errorMessage = "未找到有效的备份元数据")
            }

            onProgress(BackupProgress(stage = "正在写入数据…", detail = "正在写入收藏夹、历史记录与下载数据", percent = 0.88f))

            val bundle = gson.fromJson(jsonString, BackupBundle::class.java)

            // Map old folder IDs to new folder IDs to preserve infinite tree relationships
            val oldToNewFolderIdMap = mutableMapOf<Long, Long>()
            val existingFolders = db.favoriteDao().getAllFoldersList().toMutableList()
            val existingRootFolder = existingFolders.firstOrNull { it.parentId == null }
            val sortedFolders = bundle.folders.sortedBy { it.parentId ?: 0L }

            for (folder in sortedFolders) {
                if (folder.parentId == null) {
                    if (existingRootFolder != null) {
                        oldToNewFolderIdMap[folder.id] = existingRootFolder.id
                        ZakoLog.i(TAG, "Merged backup root folder '${folder.name}' with existing root folder id=${existingRootFolder.id}")
                    } else {
                        val newId = db.favoriteDao().insertFolder(
                            folder.copy(id = 0, parentId = null, updatedAt = System.currentTimeMillis())
                        )
                        val inserted = folder.copy(id = newId, parentId = null)
                        existingFolders.add(inserted)
                        oldToNewFolderIdMap[folder.id] = newId
                    }
                } else {
                    val newParentId = folder.parentId?.let { oldToNewFolderIdMap[it] }
                    val matchedExisting = existingFolders.find { it.parentId == newParentId && it.name == folder.name }
                    if (matchedExisting != null) {
                        oldToNewFolderIdMap[folder.id] = matchedExisting.id
                        ZakoLog.i(TAG, "Merged backup subfolder '${folder.name}' with existing folder id=${matchedExisting.id}")
                    } else {
                        val newId = db.favoriteDao().insertFolder(
                            folder.copy(id = 0, parentId = newParentId, updatedAt = System.currentTimeMillis())
                        )
                        val inserted = folder.copy(id = newId, parentId = newParentId)
                        existingFolders.add(inserted)
                        oldToNewFolderIdMap[folder.id] = newId
                    }
                }
            }

            fun cleanseCoverUrl(url: String): String {
                val trimmed = url.trim()
                return if (trimmed.startsWith("/data/user/") || trimmed.startsWith("/data/data/") ||
                    (trimmed.startsWith("/") && !File(trimmed).exists())
                ) {
                    ""
                } else {
                    trimmed
                }
            }

            val existingItems = db.favoriteDao().getAllItemsList()
            val existingItemKeys = existingItems.map { "${it.videoId}_${it.folderId}" }.toMutableSet()
            val newItems = bundle.favoriteItems.mapNotNull { item ->
                val newFolderId = oldToNewFolderIdMap[item.folderId] ?: return@mapNotNull null
                val key = "${item.videoId}_${newFolderId}"
                if (existingItemKeys.contains(key)) {
                    null
                } else {
                    existingItemKeys.add(key)
                    item.copy(id = 0, folderId = newFolderId, coverUrl = cleanseCoverUrl(item.coverUrl))
                }
            }
            if (newItems.isNotEmpty()) {
                db.favoriteDao().insertItems(newItems)
            }

            for (h in bundle.watchHistory) {
                db.watchHistoryDao().insertOrUpdate(h.copy(coverUrl = cleanseCoverUrl(h.coverUrl)))
            }

            for (wl in bundle.watchLater) {
                db.watchLaterDao().insert(wl.copy(coverUrl = cleanseCoverUrl(wl.coverUrl)))
            }

            for (dl in bundle.downloads) {
                db.downloadDao().insertOrUpdate(dl.copy(coverUrl = cleanseCoverUrl(dl.coverUrl)))
            }

            val allFavorites = db.favoriteDao().getAllItemsList()
            val allHistory = db.watchHistoryDao().getAllHistoryList()
            val allWatchLater = db.watchLaterDao().getAllList()

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

                    val duration = matchedBundleDl?.duration
                        ?: matchedFav?.duration
                        ?: matchedHist?.duration
                        ?: matchedLater?.duration
                        ?: ""

                    val newEntity = DownloadEntity(
                        videoId = if (idCandidate.isNotEmpty()) idCandidate else fileName.substringBeforeLast(".mp4"),
                        title = title.ifEmpty { "视频 $idCandidate" },
                        coverUrl = cleanseCoverUrl(coverUrl),
                        savePath = outFile.absolutePath,
                        totalBytes = outFile.length(),
                        downloadedBytes = outFile.length(),
                        status = DownloadEntity.STATUS_COMPLETED,
                        createdAt = System.currentTimeMillis(),
                        completedAt = System.currentTimeMillis(),
                        duration = duration
                    )
                    db.downloadDao().insertOrUpdate(newEntity)
                    ZakoLog.i(TAG, "Restored and registered download entity: ${newEntity.title} (${newEntity.videoId})")
                }
            }

            onProgress(BackupProgress(stage = "正在完成恢复…", detail = "核验本地文件与封面补全", percent = 0.96f))

            DownloadManager.getInstance(context).reconcileLocalDownloadFiles()
            CoverCacheManager.syncMissingCovers(context)

            onProgress(BackupProgress(stage = "恢复完成", detail = "所有数据已成功恢复", percent = 1.0f))

            ZakoLog.i(TAG, "Successfully restored ${bundle.folders.size} folders, ${bundle.favoriteItems.size} favorites, ${bundle.watchHistory.size} history items, ${restoredVideos.size} videos")
            RestoreResult(
                success = true,
                folderCount = bundle.folders.size,
                favoriteItemCount = bundle.favoriteItems.size,
                historyCount = bundle.watchHistory.size,
                watchLaterCount = bundle.watchLater.size,
                videoCount = restoredVideos.size,
                coverCount = restoredCoverCount
            )
        } catch (e: Exception) {
            ZakoLog.e(TAG, "Restore error", e)
            RestoreResult(success = false, errorMessage = e.message ?: "未知恢复错误")
        }
    }
}

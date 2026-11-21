package moe.zako.viewer.core.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import moe.zako.viewer.core.logger.ZakoLog
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

data class ExportResult(
    val uri: Uri,
    val displayPath: String,
    val fileName: String
)

object PublicStorageHelper {
    private const val TAG = "PublicStorageHelper"

    /** 备份保存子目录名 (位于系统 Download 下) */
    const val BACKUPS_SUB_DIR = "ZakoViewer/Backups"
    /** 日志保存子目录名 (位于系统 Download 下) */
    const val LOGS_SUB_DIR = "ZakoViewer/Logs"

    /** UI 常驻展示与说明的备份完整保存目录 */
    const val BACKUPS_DISPLAY_DIR = "Download/$BACKUPS_SUB_DIR/"
    /** UI 常驻展示与说明的日志完整保存目录 */
    const val LOGS_DISPLAY_DIR = "Download/$LOGS_SUB_DIR/"

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0.00 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val i = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(java.util.Locale.getDefault(), "%.2f %s", bytes / Math.pow(1024.0, i.toDouble()), units[i])
    }

    /**
     * 在公共 Download 目录下创建输出流并完成文件导出。
     * Android 10 (API 29) 及以上优先使用 MediaStore.Downloads 写入 Download/<subDir>/<fileName>，
     * 无需额外权限且文件管理器立即可见；Android 9 及以下（或异常时）回退使用公共存储目录。
     */
    fun createPublicDownloadOutputStream(
        context: Context,
        subDir: String, // 例如 "ZakoViewer/Backups" 或 "ZakoViewer/Logs"
        fileName: String,
        mimeType: String,
        block: (outputStream: OutputStream) -> Unit
    ): ExportResult {
        val cleanSubDir = subDir.trim().trim('/')
        val displayPath = "Download/$cleanSubDir/$fileName"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$cleanSubDir"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            var insertedUri: Uri? = null
            try {
                insertedUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (insertedUri != null) {
                    resolver.openOutputStream(insertedUri, "wt")?.use { os ->
                        block(os)
                        os.flush()
                    } ?: throw IllegalStateException("无法打开 MediaStore 输出流: $insertedUri")

                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(insertedUri, values, null, null)

                    ZakoLog.i(TAG, "Successfully exported to MediaStore: $displayPath, uri: $insertedUri")
                    return ExportResult(insertedUri, displayPath, fileName)
                } else {
                    ZakoLog.w(TAG, "MediaStore insert returned null, falling back to legacy public file export")
                }
            } catch (e: Exception) {
                if (insertedUri != null) {
                    try { resolver.delete(insertedUri, null, null) } catch (_: Exception) {}
                }
                ZakoLog.w(TAG, "MediaStore export failed: ${e.message}, attempting legacy public directory fallback", e)
            }
        }

        // Android 9 及以下，或 MediaStore 不可用时的回退路径
        val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(baseDir, cleanSubDir)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val targetFile = File(targetDir, fileName)
        FileOutputStream(targetFile).use { fos ->
            block(fos)
            fos.flush()
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            targetFile
        )
        ZakoLog.i(TAG, "Successfully exported to public file: ${targetFile.absolutePath}, uri: $uri")
        return ExportResult(uri, displayPath, fileName)
    }
}

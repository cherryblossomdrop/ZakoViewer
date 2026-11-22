package moe.zako.viewer.core.storage

/**
 * 全局存储路径与 I/O 缓冲区核心常量底座
 * 集中管理应用公共存储目录、系统相册/下载导出子路径以及流式处理缓冲区规格
 */
object StorageConstants {
    /** 根公共应用名目录 */
    const val APP_DIR_NAME: String = "ZakoViewer"

    /** 默认主外部存储绝对根路径 */
    const val DEFAULT_PRIMARY_STORAGE_ROOT: String = "/storage/emulated/0"

    /** 默认公共下载绝对根路径 */
    const val DEFAULT_DOWNLOAD_DIR: String = "$DEFAULT_PRIMARY_STORAGE_ROOT/Download/$APP_DIR_NAME"

    /** 备份保存子目录名 (位于系统 Download 下) */
    const val BACKUPS_SUB_DIR: String = "$APP_DIR_NAME/Backups"

    /** 日志保存子目录名 (位于系统 Download 下) */
    const val LOGS_SUB_DIR: String = "$APP_DIR_NAME/Logs"

    /** UI 常驻展示与说明的备份完整保存目录 */
    const val BACKUPS_DISPLAY_DIR: String = "Download/$BACKUPS_SUB_DIR/"

    /** UI 常驻展示与说明的日志完整保存目录 */
    const val LOGS_DISPLAY_DIR: String = "Download/$LOGS_SUB_DIR/"

    /** 大文件物理 I/O 与 ZIP 备份全量导出标准缓冲区大小 (64KB) */
    const val DEFAULT_IO_BUFFER_SIZE: Int = 65536

    /** 中型网络/流媒体分片流式传输缓冲区大小 (32KB) */
    const val STREAM_IO_BUFFER_SIZE: Int = 32768

    /**
     * 全局统一文件大小/字节数格式化算法
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0.00 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val i = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(java.util.Locale.getDefault(), "%.2f %s", bytes / Math.pow(1024.0, i.toDouble()), units[i])
    }
}

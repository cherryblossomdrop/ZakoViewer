package moe.zako.viewer.core.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import moe.zako.viewer.core.logger.ZakoLog

object StoragePermissionHelper {
    private const val TAG = "StoragePermissionHelper"

    /**
     * Check whether the app has full external storage management access (Environment.isExternalStorageManager())
     */
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Environment.isExternalStorageManager()
            } catch (e: Exception) {
                ZakoLog.e(TAG, "Error checking isExternalStorageManager", e)
                false
            }
        } else {
            true
        }
    }

    /**
     * Launch system settings page for All Files Access permission
     */
    fun requestAllFilesAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                ZakoLog.w(TAG, "Failed to launch app specific manage all files permission: ${e.message}, trying generic")
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    ZakoLog.e(TAG, "Failed to launch manage all files permission settings", e2)
                }
            }
        }
    }
}

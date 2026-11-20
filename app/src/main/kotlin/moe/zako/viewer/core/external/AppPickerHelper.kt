package moe.zako.viewer.core.external

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build

data class AppItem(
    val appName: String,
    val packageName: String,
    val icon: Drawable? = null,
    val isSystemApp: Boolean = false
)

object AppPickerHelper {
    /**
     * Get installed apps capable of playing video
     */
    fun getVideoPlayers(context: Context): List<AppItem> {
        val pm = context.packageManager
        val myPkg = context.packageName
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("https://example.com/video.mp4"), "video/*")
        }
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        val map = linkedMapOf<String, AppItem>()
        for (ri in resolveList) {
            val pkg = ri.activityInfo.packageName
            if (pkg != myPkg && !map.containsKey(pkg)) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    map[pkg] = AppItem(label, pkg, icon, isSys)
                } catch (_: Exception) {}
            }
        }
        return map.values.sortedBy { it.appName.lowercase() }
    }

    /**
     * Get installed apps capable of handling downloads / video streams (ADM, 1DM, Browsers, etc.)
     */
    fun getDownloaders(context: Context): List<AppItem> {
        val pm = context.packageManager
        val myPkg = context.packageName
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://example.com/video.mp4")
        }
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        val map = linkedMapOf<String, AppItem>()
        for (ri in resolveList) {
            val pkg = ri.activityInfo.packageName
            if (pkg != myPkg && !map.containsKey(pkg)) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    map[pkg] = AppItem(label, pkg, icon, isSys)
                } catch (_: Exception) {}
            }
        }
        return map.values.sortedBy { it.appName.lowercase() }
    }

    /**
     * Query all installed launchable applications
     */
    fun getAllInstalledApps(context: Context): List<AppItem> {
        val pm = context.packageManager
        val myPkg = context.packageName
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        val map = linkedMapOf<String, AppItem>()
        for (ri in resolveList) {
            val pkg = ri.activityInfo.packageName
            if (pkg != myPkg && !map.containsKey(pkg)) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    map[pkg] = AppItem(label, pkg, icon, isSys)
                } catch (_: Exception) {}
            }
        }
        return map.values.sortedWith(compareBy({ it.isSystemApp }, { it.appName.lowercase() }))
    }

    /**
     * Look up app details by package name
     */
    fun getAppByPackage(context: Context, packageName: String): AppItem? {
        if (packageName.isBlank()) return null
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            val icon = pm.getApplicationIcon(appInfo)
            val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            AppItem(label, packageName, icon, isSys)
        } catch (_: Exception) {
            AppItem(packageName, packageName, null, false)
        }
    }
}

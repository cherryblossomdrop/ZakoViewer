package moe.zako.viewer.core.external

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import moe.zako.viewer.ui.components.ZakoToast
import moe.zako.viewer.core.logger.NeoLog

object ExternalLauncher {
    private const val TAG = "ExternalLauncher"

    /**
     * Launch external video player with stream URL and HTTP headers (User-Agent, Referer, etc.)
     */
    fun openExternalPlayer(
        context: Context,
        videoUrl: String,
        title: String,
        customPackage: String? = null
    ) {
        try {
            NeoLog.i(TAG, "Opening external player for URL: $videoUrl (Package: $customPackage)")
            val uri = Uri.parse(videoUrl)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("title", title)

                // Standard headers extra used by MX Player, VLC, MPV, etc.
                val headers = arrayOf(
                    "User-Agent", "Mozilla/5.0 (Linux; Android 14)",
                    "Referer", "https://api.cos-dkop.net/"
                )
                putExtra("headers", headers)

                val headersBundle = Bundle().apply {
                    putString("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                    putString("Referer", "https://api.cos-dkop.net/")
                }
                putExtra("android.media.intent.extra.HTTP_HEADERS", headersBundle)

                if (!customPackage.isNullOrEmpty()) {
                    setPackage(customPackage)
                }
            }

            context.startActivity(Intent.createChooser(intent, "选择外部播放器").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            NeoLog.e(TAG, "Failed to launch external player", e)
            ZakoToast.show("无法启动外部播放器: ${e.message}")
        }
    }

    /**
     * Launch external downloader (such as 1DM, ADM, etc.) with URL and HTTP headers
     */
    fun openExternalDownloader(
        context: Context,
        downloadUrl: String,
        title: String,
        customPackage: String? = null
    ) {
        try {
            NeoLog.i(TAG, "Opening external downloader for URL: $downloadUrl (Package: $customPackage)")
            val uri = Uri.parse(downloadUrl)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("title", title)
                putExtra("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                putExtra("Referer", "https://api.cos-dkop.net/")

                if (!customPackage.isNullOrEmpty()) {
                    setPackage(customPackage)
                }
            }

            context.startActivity(Intent.createChooser(intent, "选择外部下载器").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            NeoLog.e(TAG, "Failed to launch external downloader", e)
            ZakoToast.show("无法启动外部下载器: ${e.message}")
        }
    }
}

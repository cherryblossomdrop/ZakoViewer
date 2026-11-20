package moe.zako.viewer.core.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import moe.zako.viewer.R

class DownloadService : Service() {
    companion object {
        const val CHANNEL_ID = "zakoviewer_downloads"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_DOWNLOAD = "ACTION_START_DOWNLOAD"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra("title") ?: "正在多节点分片拉取视频..."
        val notification = buildNotification(title, "下载进行中")
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "视频下载任务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "展示当前视频多节点多线程下载进度与速率"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

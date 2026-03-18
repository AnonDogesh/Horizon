package com.dean.browservault

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()

        createChannelIfNeeded()
        startForeground(NOTIFICATION_ID, createNotification("Downloading...", 0, indeterminate = true))

        scope.launch {
            DownloadQueue.update(taskId, status = "downloading", progress = 0)
            val outputPath = getExternalFilesDir(null)?.path ?: filesDir.path

            val success = DownloadManager.start(url, outputPath) { progress ->
                DownloadQueue.update(taskId, status = "downloading", progress = progress)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, createNotification("Downloading...", progress, indeterminate = false))
            }

            DownloadQueue.update(taskId, status = if (success) "completed" else "failed", progress = if (success) 100 else 0)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(
                NOTIFICATION_ID,
                createNotification(if (success) "Download complete" else "Download failed", if (success) 100 else 0, indeterminate = false)
            )
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf(startId)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(title: String, progress: Int, indeterminate: Boolean): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("Horizon download service")
            .setOnlyAlertOnce(true)
            .setOngoing(indeterminate)
            .setProgress(100, progress, indeterminate)
            .build()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "Horizon Downloads", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TASK_ID = "task_id"
        private const val CHANNEL_ID = "horizon_downloads"
        private const val NOTIFICATION_ID = 4101
    }
}

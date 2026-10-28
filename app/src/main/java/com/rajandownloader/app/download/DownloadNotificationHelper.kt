package com.rajandownloader.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rajandownloader.app.MainActivity
import com.rajandownloader.app.R
import com.rajandownloader.app.model.DownloadProgress
import com.rajandownloader.app.model.DownloadStage

class DownloadNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "rajan_downloader_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL = "com.rajandownloader.app.CANCEL_DOWNLOAD"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_description)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(title: String, progress: DownloadProgress): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(context, DownloadService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            context,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appIcon = runCatching {
            android.graphics.BitmapFactory.decodeResource(context.resources, com.rajandownloader.app.R.mipmap.ic_launcher)
        }.getOrNull()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setLargeIcon(appIcon)
            .setContentTitle(title.ifEmpty { "YtMax Downloader" })
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        when (progress.stage) {
            DownloadStage.FETCHING_METADATA -> {
                builder.setContentText("Fetching metadata from YouTube...")
                builder.setProgress(0, 0, true)
            }
            DownloadStage.DOWNLOADING, DownloadStage.DOWNLOADING_VIDEO, DownloadStage.DOWNLOADING_AUDIO -> {
                val detail = if (progress.speedStr.isNotEmpty()) "${progress.speedStr} • ETA: ${progress.etaStr}" else "Downloading stream..."
                builder.setContentText("${progress.percentage}% • $detail")
                builder.setProgress(100, progress.percentage, false)
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            }
            DownloadStage.MERGING -> {
                builder.setContentText("Stitching video & audio streams with FFmpeg...")
                builder.setProgress(0, 0, true)
            }
            DownloadStage.SAVING -> {
                builder.setContentText("Saving file to destination...")
                builder.setProgress(0, 0, true)
            }
            DownloadStage.COMPLETED -> {
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                builder.setContentText("Download and packaging completed!")
                builder.setProgress(0, 0, false)
                builder.setOngoing(false)
            }
            DownloadStage.FAILED -> {
                builder.setSmallIcon(android.R.drawable.stat_notify_error)
                builder.setContentText("Download failed: ${progress.stageMessage}")
                builder.setProgress(0, 0, false)
                builder.setOngoing(false)
            }
            DownloadStage.CANCELLED -> {
                builder.setContentText("Download cancelled")
                builder.setProgress(0, 0, false)
                builder.setOngoing(false)
            }
            DownloadStage.IDLE -> {
                builder.setContentText("Ready")
            }
        }

        return builder.build()
    }

    fun update(title: String, progress: DownloadProgress) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(title, progress))
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}


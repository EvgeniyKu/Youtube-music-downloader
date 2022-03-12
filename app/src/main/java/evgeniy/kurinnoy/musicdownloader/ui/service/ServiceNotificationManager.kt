package evgeniy.kurinnoy.musicdownloader.ui.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import evgeniy.kurinnoy.musicdownloader.R
import evgeniy.kurinnoy.musicdownloader.domain.models.MusicDownloadingState
import evgeniy.kurinnoy.musicdownloader.ui.MainActivity
import javax.inject.Inject

class ServiceNotificationManager(
    private val service: DownloadService
) {
    private val context: Context
        get() = service

    private val notificationManager by lazy { NotificationManagerCompat.from(context) }

    private val showedNotificationIds = mutableSetOf<Int>()

    fun updateNotifications(list: List<MusicDownloadingState>) {
        val hasInProgress = list.any {
            it is MusicDownloadingState.Pending
                    || it is MusicDownloadingState.InProgress
                    || it is MusicDownloadingState.Failure
        }

        if (hasInProgress) {
            val notification = createSummaryNotification()
            service.startForeground(R.id.notification_id, notification)
        } else {
            service.stopForeground(false)
        }

        val notificationIds = mutableSetOf<Int>()
        list.forEach {
            val notification = createDownloadNotification(it)
            val notificationId = R.id.notification_id + it.id.hashCode()
            notificationIds.add(notificationId)
            notificationManager.notify(notificationId, notification)
        }

        // remove missing notifications
        showedNotificationIds.subtract(notificationIds).forEach {
            notificationManager.cancel(it)
        }

        showedNotificationIds.clear()
        showedNotificationIds.addAll(notificationIds)
    }

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Download service",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        notificationManager.createNotificationChannel(channel)
    }

    private fun createSummaryNotification(): Notification {
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading songs...")
            .setProgress(100, 0, true)
            .setGroup(NOTIFICATION_GROUP_KEY)
            .setGroupSummary(true)
            .setContentIntent(createActivityPendingIntent())
            .build()
    }

    private fun createDownloadNotification(
        state: MusicDownloadingState
    ): Notification {
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setGroup(NOTIFICATION_GROUP_KEY)
            .setContentIntent(createActivityPendingIntent())

        when (state) {
            is MusicDownloadingState.Pending -> {
                builder
                    .setContentText("Load info from ${state.url}")
                    .setProgress(100, 0, true)
                    .setOngoing(true)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
            }
            is MusicDownloadingState.Completed -> {
                builder
                    .setContentTitle("Successfully downloaded")
                    .setContentText("${state.info.musicInfo.artist}: ${state.info.musicInfo.title}")
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setDeleteIntent(createDeletePendingIntent(state.url))
            }
            is MusicDownloadingState.Failure -> {
                builder
                    .setContentTitle("Failed to load")
                    .setContentText("${state.info.musicInfo.artist}: ${state.info.musicInfo.title}")
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setDeleteIntent(createDeletePendingIntent(state.url))
            }
            is MusicDownloadingState.InProgress -> {
                builder
                    .setContentText("${state.info.musicInfo.artist}: ${state.info.musicInfo.title}")
                    .setProgress(100, state.progress.toInt(), false)
                    .setOngoing(true)
                    .setSmallIcon(android.R.drawable.stat_sys_download)

            }
        }

        return builder.build()
    }

    private fun createActivityPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            111,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createDeletePendingIntent(url :String): PendingIntent {
        val intent = DownloadService.removeMusicIntent(context, url)
        return PendingIntent.getService(
            context,
            112,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "music_download_service"
        private const val NOTIFICATION_GROUP_KEY = "downloading_group"
    }
}
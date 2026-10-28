package nl.jellycast.mobile.core.downloads

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import nl.jellycast.mobile.R
import nl.jellycast.mobile.ui.downloads.DownloadText
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * Keeps the app running while a download runs, with a progress notification (the iOS Live
 * Activity). The store does the work; this only lives as long as something downloads.
 */
class DownloadService : Service() {
    private val store: DownloadStore by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val text by lazy { DownloadText(this) }
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private var watch: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.downloads_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val download = store.current.value
        if (download == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            NOTIFICATION_ID,
            notification(download, null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        watch?.cancel()
        watch = scope.launch {
            combine(store.current, store.rate) { current, rate -> current to rate }
                .collect { (current, rate) ->
                    if (current == null) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        notifications.notify(NOTIFICATION_ID, notification(current, rate))
                    }
                }
        }
        return START_NOT_STICKY
    }

    // Android allows a data sync service six hours a day; the download goes on while the app lives.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(download: Download, rate: DownloadRate?): Notification {
        val open =
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
            }
        val fraction = download.fraction
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(text.title(download.media))
            .setContentText(text.statusLine(download, store.secondsLeft(download, rate)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setProgress(PROGRESS_MAX, ((fraction ?: 0f) * PROGRESS_MAX).toInt(), fraction == null)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 7301
        const val PROGRESS_MAX = 1000
    }
}

/** Starts the service when a download begins; it stops itself when none runs. */
class ServiceDownloadForeground(private val context: Context) : DownloadForeground {
    override fun onRunningChanged(running: Boolean) {
        if (!running) return
        try {
            context.startForegroundService(Intent(context, DownloadService::class.java))
        } catch (e: ForegroundServiceStartNotAllowedException) {
            // Started from the background (the network came back): it runs without notification.
            Timber.w(e, "No download notification")
        }
    }
}

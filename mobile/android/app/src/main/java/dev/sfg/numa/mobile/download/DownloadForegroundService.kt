package dev.sfg.numa.mobile.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.connect.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DownloadForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val downloads get() = NumaGraph.from(this).downloads
    private val notifications get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Descargas de música", NotificationManager.IMPORTANCE_LOW))
        ServiceCompat.startForeground(this, ID, notification(DownloadBatchProgress(pending = 1)), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:downloads").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!observing) {
            observing = true
            scope.launch {
                downloads.batchProgress.collect { progress ->
                    val canNotify = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                        this@DownloadForegroundService, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    if (progress.total > 0 && canNotify) notifications.notify(ID, notification(progress))
                    if (!progress.active) {
                        delay(750)
                        if (!downloads.batchProgress.value.active) {
                            stopForeground(if (downloads.batchProgress.value.total == 0) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
                            stopSelf()
                        }
                    } else delay(250)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(progress: DownloadBatchProgress): android.app.Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val title = "Música sin conexión"
        val summary = if (progress.total == 0) "Preparando descargas…"
            else "${progress.completed} / ${progress.total} canciones"
        val detail = when {
            progress.failed > 0 -> "${progress.failed} fallidas · ${progress.pending} pendientes"
            progress.active -> "Descargando en segundo plano"
            else -> "Descargas completadas"
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$summary\n$detail"))
            .setProgress(progress.total, progress.completed + progress.failed, progress.total == 0)
            .setOnlyAlertOnce(true)
            .setOngoing(progress.active)
            .setAutoCancel(!progress.active)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply { if (launch != null) setContentIntent(PendingIntent.getActivity(this@DownloadForegroundService, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)) }
            .build()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        downloads.pauseDownloads()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "music-downloads"
        private const val ID = 2401
    }
}

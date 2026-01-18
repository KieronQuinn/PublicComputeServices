package com.kieronquinn.app.pcs.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.kieronquinn.app.pcs.BuildConfig
import com.kieronquinn.app.pcs.R
import com.kieronquinn.app.pcs.model.Release
import com.kieronquinn.app.pcs.repositories.ManifestRepository
import com.kieronquinn.app.pcs.repositories.SyncRepository
import com.kieronquinn.app.pcs.repositories.UpdateRepository
import com.kieronquinn.app.pcs.utils.extensions.hasPermission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class RefreshWorker(appContext: Context, workerParams: WorkerParameters):
    CoroutineWorker(appContext, workerParams), KoinComponent {

    companion object {
        private const val TAG = "refresh_worker"
        private const val HOUR = 12L
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_NOTIFICATION_CHANNEL_ID = "updates"

        fun setEnabled(workManager: WorkManager, enabled: Boolean) {
            workManager.cancelAllWorkByTag(TAG)
            if (enabled) {
                workManager.queueRefreshWorker()
            }
        }

        private fun WorkManager.queueRefreshWorker(){
            val refreshWorker = Builder().build()
            enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                refreshWorker
            )
        }
    }

    private val syncRepository by inject<SyncRepository>()
    private val updateRepository by inject<UpdateRepository>()
    private val manifestRepository by inject<ManifestRepository>()

    private val notificationManager = NotificationManagerCompat.from(applicationContext)

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val syncVersions = syncRepository.getSyncRequired()
        return if (syncVersions != null) {
            if (syncVersions.isNotEmpty()) {
                syncRepository.performSync(syncVersions)
            }
            if (applicationContext.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                updateRepository.getUpdate()?.let {
                    showUpdateNotification(it)
                }
            }
            Result.success()
        } else {
            Result.retry()
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showUpdateNotification(update: Release) {
        val channel = NotificationChannelCompat.Builder(
            UPDATE_NOTIFICATION_CHANNEL_ID,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setName(applicationContext.getString(R.string.notification_channel_updates_title))
            setDescription(
                applicationContext.getString(R.string.notification_channel_updates_content)
            )
        }.build()
        notificationManager.createNotificationChannel(channel)
        val content = applicationContext.getString(
            R.string.update_notification_content,
            BuildConfig.TAG_NAME,
            update.versionName
        )
        val notification = NotificationCompat.Builder(
            applicationContext,
            UPDATE_NOTIFICATION_CHANNEL_ID
        ).apply {
            setSmallIcon(R.drawable.ic_logo)
            setContentTitle(applicationContext.getString(R.string.update_notification_title))
            setContentText(content)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = update.gitHubUrl.toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    NOTIFICATION_ID,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE
                ))
            setAutoCancel(true)
            priority = NotificationCompat.PRIORITY_HIGH
        }.build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    class Builder {
        fun build(): PeriodicWorkRequest {
            val delay = if (LocalDateTime.now().hour < HOUR) {
                Duration.between(ZonedDateTime.now(), ZonedDateTime.now().toLocalDate()
                    .atStartOfDay(ZoneId.systemDefault()).plusHours(HOUR)).toMinutes()
            } else {
                Duration.between(ZonedDateTime.now(), ZonedDateTime.now().toLocalDate()
                    .atStartOfDay(ZoneId.systemDefault()).plusDays(1)
                    .plusHours(HOUR)).toMinutes()
            }
            val scheduleTime = LocalDateTime.now().plusMinutes(delay)
            Log.d("RefreshWorker", "Scheduling refresh worker at $scheduleTime")
            val constraints: Constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            return PeriodicWorkRequest.Builder(
                RefreshWorker::class.java,
                24,
                TimeUnit.HOURS
            ).addTag(TAG)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                ).build()
        }
    }

}
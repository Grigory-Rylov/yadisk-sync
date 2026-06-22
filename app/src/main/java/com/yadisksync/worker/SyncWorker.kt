package com.yadisksync.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.yadisksync.R
import com.yadisksync.domain.usecase.CleanupOldPhotosUseCase
import com.yadisksync.domain.usecase.SyncPhotosUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncPhotosUseCase: SyncPhotosUseCase,
    private val cleanupOldPhotosUseCase: CleanupOldPhotosUseCase
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        const val CHANNEL_ID = "sync_channel"
        const val NOTIFICATION_ID = 1001
        const val WORK_NAME = "photo_sync_work"

        fun buildRequest(): PeriodicWorkRequest {
            return PeriodicWorkRequestBuilder<SyncWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES,
                5, java.util.concurrent.TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
        }
    }

    override suspend fun doWork(): Result {
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted, skipping notification-based sync")
            }
        }

        setForeground(createForegroundInfo())

        return try {
            val result = syncPhotosUseCase()
            if (result.isSuccess) {
                val downloaded = result.getOrDefault(0)

                val cleaned = cleanupOldPhotosUseCase()
                Log.d(TAG, "Cleanup: $cleaned files cleaned locally")

                Log.d(TAG, "Sync success: $downloaded downloaded, $cleaned cleaned")
                showCompletionNotification(downloaded, cleaned)
                updateForegroundNotification(downloaded, true, cleaned)
                Result.success()
            } else {
                Log.w(TAG, "Sync returned failure: ${result.exceptionOrNull()}")
                updateForegroundNotification(0, false, 0)
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync worker exception (attempt $runAttemptCount)", e)
            updateForegroundNotification(0, false, 0)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sync Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows sync progress" }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Syncing photos")
            .setSmallIcon(R.drawable.ic_sync)
            .setProgress(0, 0, true)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun updateForegroundNotification(downloaded: Int, success: Boolean, cleaned: Int = 0) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(if (success) "Sync complete" else "Sync failed")
            .setContentText(
                if (success) {
                    if (cleaned > 0) "Downloaded $downloaded, cleaned $cleaned" else "Downloaded $downloaded new photos"
                } else "Retry attempt $runAttemptCount"
            )
            .setSmallIcon(R.drawable.ic_sync)
            .setProgress(0, 0, false)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(downloaded: Int, cleaned: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Sync complete")
            .setContentText(
                when {
                    downloaded > 0 && cleaned > 0 -> "Downloaded $downloaded, cleaned $cleaned"
                    downloaded > 0 -> "Downloaded $downloaded new photos"
                    cleaned > 0 -> "Cleaned $cleaned old photos"
                    else -> "No new photos"
                }
            )
            .setSmallIcon(R.drawable.ic_sync)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }
}
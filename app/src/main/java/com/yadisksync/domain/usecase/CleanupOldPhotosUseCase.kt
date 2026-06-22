package com.yadisksync.domain.usecase

import android.util.Log
import com.yadisksync.data.local.SyncStatus
import com.yadisksync.domain.repository.SettingsRepository
import com.yadisksync.domain.repository.SyncRepository
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

class CleanupOldPhotosUseCase @Inject constructor(
    private val syncRepository: SyncRepository,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "CleanupOldPhotosUseCase"
    }

    suspend operator fun invoke(): Int {
        val enabled = settingsRepository.deleteOldPhotos.first()
        if (!enabled) {
            Log.d(TAG, "Local cleanup not enabled")
            return 0
        }

        val retentionDays = settingsRepository.deleteAfterDays.first()
        val cutoffMillis = System.currentTimeMillis() - (retentionDays * 24L * 3600 * 1000)
        Log.d(TAG, "Retention: $retentionDays days, cutoff=$cutoffMillis")

        val oldFiles = syncRepository.getFilesOlderThan(cutoffMillis)
        Log.d(TAG, "Found ${oldFiles.size} files older than $retentionDays days")

        var cleaned = 0
        for (file in oldFiles) {
            try {
                file.localPath?.let { path ->
                    if (!path.startsWith("[")) {
                        val localFile = File(path)
                        if (localFile.exists()) {
                            val deleted = localFile.delete()
                            Log.d(TAG, "Local file ${if (deleted) "deleted" else "NOT deleted"}: $path")
                        } else {
                            Log.w(TAG, "Local file not found: $path")
                        }
                    }
                }
                syncRepository.markAsLocallyDeleted(file.id, SyncStatus.DELETED)
                cleaned++
                Log.d(TAG, "Marked as DELETED: ${file.fileName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clean ${file.fileName}", e)
            }
        }

        Log.d(TAG, "Locally cleaned $cleaned files")
        return cleaned
    }
}

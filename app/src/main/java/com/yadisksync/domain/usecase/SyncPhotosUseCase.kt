package com.yadisksync.domain.usecase

import android.content.Context
import android.util.Log
import com.yadisksync.BuildConfig
import com.yadisksync.data.local.SyncedFileEntity
import com.yadisksync.data.local.SyncStatus
import com.yadisksync.data.remote.DiskFile
import com.yadisksync.data.remote.YandexDiskApi
import com.yadisksync.domain.repository.SettingsRepository
import com.yadisksync.domain.repository.SyncRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject

class SyncPhotosUseCase @Inject constructor(
    private val api: YandexDiskApi,
    private val syncRepository: SyncRepository,
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SyncPhotosUseCase"
    }

    suspend operator fun invoke(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val token = settingsRepository.oauthToken.first()
            if (token.isBlank()) {
                Log.e(TAG, "OAuth token not configured")
                return@withContext Result.failure(Exception("OAuth token not configured"))
            }

            val oldestDateMillis = settingsRepository.oldestDateMillis.first()
            val storagePath = settingsRepository.storagePath.first()

            Log.d(TAG, "Sync started. storagePath=$storagePath, oldestDateMillis=$oldestDateMillis, token=${token.take(10)}...(${token.length} chars)")

            val authHeader = "OAuth $token"
            var offset = 0
            val limit = 100
            var totalDownloaded = 0
            var totalSkipped = 0
            var totalFailed = 0

            while (true) {
                val response = api.getFiles(
                    authorization = authHeader,
                    path = "disk:/Фотокамера",
                    limit = limit,
                    offset = offset
                )

                if (response.items.isEmpty() && response._embedded?.items.isNullOrEmpty()) break

                val files = response.items.ifEmpty { response._embedded?.items ?: emptyList() }
                if (files.isEmpty()) break

                Log.d(TAG, "Fetched ${files.size} remote items (offset=$offset)")

                for (file in files) {
                    try {
                        val result = processFile(file, oldestDateMillis, storagePath, authHeader)
                        when (result) {
                            1 -> totalDownloaded++
                            -1 -> totalFailed++
                            0 -> totalSkipped++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing file ${file.path}", e)
                        totalFailed++
                    }
                }

                offset += limit
            }

            Log.d(TAG, "Sync finished. downloaded=$totalDownloaded, skipped=$totalSkipped, failed=$totalFailed")
            settingsRepository.setLastSyncTime(System.currentTimeMillis())
            Result.success(totalDownloaded)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.failure(e)
        }
    }

    private suspend fun processFile(
        file: DiskFile,
        oldestDateMillis: Long,
        storagePath: String,
        authHeader: String
    ): Int {
        if (file.type == "dir") {
            Log.d(TAG, "Skipping directory: ${file.path}")
            return 0
        }

        val remoteId = md5(file.path)
        val existing = syncRepository.getFileByRemoteId(remoteId)
        if (existing != null && existing.syncStatus == SyncStatus.COMPLETED) {
            Log.d(TAG, "Already completed, skipping: ${file.name}")
            return 0
        }

        val fileDate = parseIsoDate(file.modified ?: file.created)
        if (fileDate > 0 && fileDate < oldestDateMillis) {
            Log.d(TAG, "Too old, skipping: ${file.name} (date=$fileDate < ${oldestDateMillis})")
            return 0
        }

        val entityId = if (existing != null) {
            syncRepository.updateStatus(existing.id, SyncStatus.DOWNLOADING)
            existing.id
        } else {
            val newEntity = SyncedFileEntity(
                remoteId = remoteId,
                fileName = file.name,
                remotePath = file.path,
                localPath = null,
                fileSize = file.size,
                mimeType = file.mimeType,
                downloadedAt = null,
                syncStatus = SyncStatus.PENDING,
                createdAt = System.currentTimeMillis()
            )
            syncRepository.insert(newEntity)
            syncRepository.getFileByRemoteId(remoteId)?.id ?: run {
                Log.e(TAG, "Cannot find entity after insert: ${file.path}")
                return -1
            }
        }

        return downloadFile(file, storagePath, entityId, authHeader)
    }

    private suspend fun downloadFile(
        file: DiskFile,
        storagePath: String,
        entityId: Long,
        authHeader: String
    ): Int {
        return try {
            val linkResponse = api.getDownloadLink(authHeader, file.path)
            Log.d(TAG, "Download link obtained for ${file.name}: ${linkResponse.href}")

            val request = Request.Builder()
                .url(linkResponse.href)
                .addHeader("Authorization", authHeader)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed HTTP ${response.code} for ${file.name}")
                    syncRepository.updateStatus(entityId, SyncStatus.FAILED)
                    return -1
                }

                val contentLength = response.body?.contentLength() ?: file.size
                Log.d(TAG, "Found downloadable file: ${file.name} (${contentLength}B)")
                Log.d(TAG, "  Remote path: ${file.path}")
                Log.d(TAG, "  mimeType: ${file.mimeType}")
                Log.d(TAG, "  Download URL: ${linkResponse.href}")

                if (BuildConfig.SAVE_FILES_TO_DISK) {
                    val dir = File(storagePath)
                    if (!dir.exists()) dir.mkdirs()

                    val uniqueName = generateUniqueName(file, storagePath)
                    val localFile = File(dir, uniqueName)
                    var downloadedBytes = 0L
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(localFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                            }
                        }
                    }

                    Log.d(TAG, "Saved ${file.name} -> ${localFile.absolutePath} ($downloadedBytes bytes)")
                    syncRepository.markAsDownloaded(
                        id = entityId,
                        localPath = localFile.absolutePath,
                        downloadedAt = System.currentTimeMillis(),
                        status = SyncStatus.COMPLETED
                    )
                } else {
                    Log.d(TAG, "[LOG-ONLY] SAVE_FILES_TO_DISK=false, skipping write to disk")
                    response.body?.byteStream()?.close()

                    syncRepository.markAsDownloaded(
                        id = entityId,
                        localPath = "[LOG-ONLY] ${file.path}",
                        downloadedAt = System.currentTimeMillis(),
                        status = SyncStatus.COMPLETED
                    )
                }
            }
            1
        } catch (e: Exception) {
            Log.e(TAG, "Download exception for ${file.name}", e)
            try {
                syncRepository.updateStatus(entityId, SyncStatus.FAILED)
            } catch (_: Exception) {
                // ignore
            }
            -1
        }
    }

    private fun generateUniqueName(file: DiskFile, storagePath: String): String {
        val relativePath = file.path.removePrefix("disk:/Фотокамера/").removePrefix("disk:/Фотокамера")
        val subDirs = File(storagePath, relativePath).parentFile
        if (subDirs != null && !subDirs.isAbsolute.equals(true).not()) {
            if (!subDirs.exists()) subDirs.mkdirs()
            return relativePath
        }
        val baseName = file.name.substringBeforeLast(".")
        val extension = file.name.substringAfterLast(".")
        val candidate = if (extension.isNotEmpty()) "$baseName.$extension" else baseName
        var uniqueName = candidate
        var counter = 1
        while (File(storagePath, uniqueName).exists()) {
            uniqueName = if (extension.isNotEmpty()) "$baseName($counter).$extension" else "$baseName($counter)"
            counter++
        }
        return uniqueName
    }

    private fun md5(input: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun parseIsoDate(dateStr: String?): Long {
        if (dateStr == null) return 0L
        return try {
            // Replace +00:00 with Z for reliable Instant parsing
            val normalized = dateStr.replace(Regex("[+-]00:00$"), "Z")
            java.time.Instant.parse(normalized).toEpochMilli()
        } catch (e: Exception) {
            // Fallback: manually parse "yyyy-MM-dd'T'HH:mm:ss[+HH:MM]"
            try {
                val m = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})([+-]\d{2}:\d{2})?""").matchEntire(dateStr)
                if (m != null) {
                    val (year, month, day, hour, minute, second) = m.destructured
                    val tzStr = m.groupValues[7]
                    val base = java.sql.Timestamp.valueOf(
                        "${year}-${month}-${day} ${hour}:${minute}:${second}"
                    ).time
                    val offsetMillis = if (tzStr == null || tzStr == "+00:00" || tzStr == "-00:00") {
                        0L
                    } else {
                        val tzMatch = Regex("([+-])(\\d{2}):(\\d{2})").matchEntire(tzStr)
                        if (tzMatch != null) {
                            val (sign, h, m) = tzMatch.destructured
                            val signInt = if (sign == "+") 1 else -1
                            signInt * (h.toLong() * 3600 + m.toLong() * 60) * 1000
                        } else {
                            0L
                        }
                    }
                    base + offsetMillis
                } else {
                    Log.w(TAG, "Cannot parse date: $dateStr")
                    0L
                }
            } catch (e2: Exception) {
                Log.w(TAG, "Cannot parse date: $dateStr", e2)
                0L
            }
        }
    }
}
package com.yadisksync.domain.repository

import com.yadisksync.data.local.SyncedFileEntity
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    fun getAllFiles(): Flow<List<SyncedFileEntity>>
    fun getRecentFiles(limit: Int): Flow<List<SyncedFileEntity>>
    suspend fun getFileByRemoteId(remoteId: String): SyncedFileEntity?
    suspend fun insert(file: SyncedFileEntity)
    suspend fun updateStatus(id: Long, status: com.yadisksync.data.local.SyncStatus)
    suspend fun markAsDownloaded(id: Long, localPath: String, downloadedAt: Long, status: com.yadisksync.data.local.SyncStatus)
}
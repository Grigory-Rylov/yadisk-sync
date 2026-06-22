package com.yadisksync.data.repository

import com.yadisksync.data.local.SyncedFileDao
import com.yadisksync.data.local.SyncStatus
import com.yadisksync.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val dao: SyncedFileDao
) : SyncRepository {

    override fun getAllFiles(): Flow<List<com.yadisksync.data.local.SyncedFileEntity>> = dao.getAllFiles()

    override fun getRecentFiles(limit: Int): Flow<List<com.yadisksync.data.local.SyncedFileEntity>> =
        dao.getRecentFiles(limit)

    override suspend fun getFileByRemoteId(remoteId: String) = dao.getFileByRemoteId(remoteId)

    override suspend fun insert(file: com.yadisksync.data.local.SyncedFileEntity): Unit {
        return dao.insert(file)
    }

    override suspend fun updateStatus(id: Long, status: SyncStatus) = dao.updateStatus(id, status)

    override suspend fun markAsDownloaded(
        id: Long,
        localPath: String,
        downloadedAt: Long,
        status: SyncStatus
    ) = dao.markAsDownloaded(id, localPath, downloadedAt, status)

    override suspend fun getFilesOlderThan(cutoffMillis: Long) = dao.getFilesOlderThan(cutoffMillis)
    override suspend fun markAsLocallyDeleted(id: Long, status: SyncStatus) = dao.markAsLocallyDeleted(id, status)
}
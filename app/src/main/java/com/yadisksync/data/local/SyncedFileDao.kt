package com.yadisksync.data.local

import androidx.room.*
import com.yadisksync.data.local.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncedFileDao {

    @Query("SELECT * FROM synced_files ORDER BY createdAt DESC")
    fun getAllFiles(): Flow<List<SyncedFileEntity>>

    @Query("SELECT * FROM synced_files WHERE syncStatus = :status")
    suspend fun getFilesByStatus(status: SyncStatus): List<SyncedFileEntity>

    @Query("SELECT * FROM synced_files WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getFileByRemoteId(remoteId: String): SyncedFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: SyncedFileEntity)

    @Update
    suspend fun update(file: SyncedFileEntity)

    @Delete
    suspend fun delete(file: SyncedFileEntity)

    @Query("DELETE FROM synced_files WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM synced_files ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentFiles(limit: Int): Flow<List<SyncedFileEntity>>

    @Query("UPDATE synced_files SET syncStatus = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: SyncStatus)

    @Query("UPDATE synced_files SET localPath = :localPath, downloadedAt = :downloadedAt, syncStatus = :status WHERE id = :id")
    suspend fun markAsDownloaded(id: Long, localPath: String, downloadedAt: Long, status: SyncStatus)

    @Query("SELECT * FROM synced_files WHERE syncStatus = 'COMPLETED' AND downloadedAt IS NOT NULL AND downloadedAt <= :cutoffMillis")
    suspend fun getFilesOlderThan(cutoffMillis: Long): List<SyncedFileEntity>

    @Query("UPDATE synced_files SET localPath = null, syncStatus = :status WHERE id = :id")
    suspend fun markAsLocallyDeleted(id: Long, status: SyncStatus)
}
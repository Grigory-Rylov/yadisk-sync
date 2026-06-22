package com.yadisksync.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "synced_files")
data class SyncedFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val remoteId: String,
    val fileName: String,
    val remotePath: String,
    val localPath: String?,
    val fileSize: Long,
    val mimeType: String,
    val downloadedAt: Long?,
    val syncStatus: SyncStatus,
    val createdAt: Long
)
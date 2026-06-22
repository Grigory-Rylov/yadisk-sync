package com.yadisksync.domain.model

import com.yadisksync.data.local.SyncStatus

data class SyncedFile(
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
package com.yadisksync.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val oauthToken: Flow<String>
    val oldestDateMillis: Flow<Long>
    val storagePath: Flow<String>
    val syncIntervalMinutes: Flow<Int>
    val lastSyncTime: Flow<Long>

    suspend fun setOauthToken(token: String)
    suspend fun setOldestDateMillis(millis: Long)
    suspend fun setStoragePath(path: String)
    suspend fun setSyncIntervalMinutes(minutes: Int)
    suspend fun setLastSyncTime(time: Long)
}
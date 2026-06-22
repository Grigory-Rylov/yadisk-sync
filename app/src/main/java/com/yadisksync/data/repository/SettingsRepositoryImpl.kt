package com.yadisksync.data.repository

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yadisksync.data.local.SettingsDataStore
import com.yadisksync.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore,
    @ApplicationContext context: Context
) : SettingsRepository {

    override val oauthToken: Flow<String> = dataStore.oauthToken
    override val oldestDateMillis: Flow<Long> = dataStore.oldestDateMillis
    override val storagePath: Flow<String> = dataStore.storagePath
    override val syncIntervalMinutes: Flow<Int> = dataStore.syncIntervalMinutes
    override val lastSyncTime: Flow<Long> = dataStore.lastSyncTime

    override suspend fun setOauthToken(token: String) = dataStore.setOauthToken(token)
    override suspend fun setOldestDateMillis(millis: Long) = dataStore.setOldestDateMillis(millis)
    override suspend fun setStoragePath(path: String) = dataStore.setStoragePath(path)
    override suspend fun setSyncIntervalMinutes(minutes: Int) = dataStore.setSyncIntervalMinutes(minutes)
    override suspend fun setLastSyncTime(time: Long) = dataStore.setLastSyncTime(time)
}
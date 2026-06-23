package com.yadisksync.data.local

import android.content.Context
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    private val context: Context
) {
    companion object {
        private val OAUTH_TOKEN = stringPreferencesKey("oauth_token")
        private val OLDEST_DATE_MILLIS = longPreferencesKey("oldest_date_millis")
        private val STORAGE_PATH = stringPreferencesKey("storage_path")
        private val SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")
        private val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        private val DELETE_OLD_PHOTOS = booleanPreferencesKey("delete_old_photos")
        private val DELETE_AFTER_DAYS = intPreferencesKey("delete_after_days")
    }

    val oauthToken: Flow<String> = context.dataStore.data.map { prefs ->
        (prefs[OAUTH_TOKEN] ?: com.yadisksync.BuildConfig.YA_DISK_TOKEN).takeIf { it.isNotBlank() }
            ?: com.yadisksync.BuildConfig.YA_DISK_TOKEN
    }
    val oldestDateMillis: Flow<Long> = context.dataStore.data.map { it[OLDEST_DATE_MILLIS] ?: 1451606400000L }
    val storagePath: Flow<String> = context.dataStore.data.map {
        it[STORAGE_PATH] ?: getPublicDownloadsPath()
    }
    val syncIntervalMinutes: Flow<Int> = context.dataStore.data.map { it[SYNC_INTERVAL_MINUTES] ?: 15 }
    val lastSyncTime: Flow<Long> = context.dataStore.data.map { it[LAST_SYNC_TIME] ?: 0L }
    val deleteOldPhotos: Flow<Boolean> = context.dataStore.data.map { it[DELETE_OLD_PHOTOS] ?: false }
    val deleteAfterDays: Flow<Int> = context.dataStore.data.map { it[DELETE_AFTER_DAYS] ?: 7 }

    private fun getPublicDownloadsPath(): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: File("/storage/emulated/0/Download")
        return File(dir, "YaDiskSync").absolutePath
    }

    suspend fun setOauthToken(token: String) {
        context.dataStore.edit { it[OAUTH_TOKEN] = token }
    }

    suspend fun setOldestDateMillis(millis: Long) {
        context.dataStore.edit { it[OLDEST_DATE_MILLIS] = millis }
    }

    suspend fun setStoragePath(path: String) {
        context.dataStore.edit { it[STORAGE_PATH] = path }
    }

    suspend fun setSyncIntervalMinutes(minutes: Int) {
        context.dataStore.edit { it[SYNC_INTERVAL_MINUTES] = minutes }
    }

    suspend fun setLastSyncTime(time: Long) {
        context.dataStore.edit { it[LAST_SYNC_TIME] = time }
    }

    suspend fun setDeleteOldPhotos(enabled: Boolean) {
        context.dataStore.edit { it[DELETE_OLD_PHOTOS] = enabled }
    }

    suspend fun setDeleteAfterDays(days: Int) {
        context.dataStore.edit { it[DELETE_AFTER_DAYS] = days }
    }
}

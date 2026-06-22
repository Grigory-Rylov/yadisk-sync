package com.yadisksync.di

import android.content.Context
import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.yadisksync.data.local.AppDatabase
import com.yadisksync.data.local.SyncedFileDao
import com.yadisksync.data.local.SettingsDataStore
import com.yadisksync.data.remote.YandexDiskApi
import com.yadisksync.data.repository.SettingsRepositoryImpl
import com.yadisksync.data.repository.SyncRepositoryImpl
import com.yadisksync.domain.repository.SettingsRepository
import com.yadisksync.domain.repository.SyncRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl(YandexDiskApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideYandexDiskApi(retrofit: Retrofit): YandexDiskApi {
        return retrofit.create(YandexDiskApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "yadisk_sync_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideSyncedFileDao(database: AppDatabase): SyncedFileDao {
        return database.syncedFileDao()
    }

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {
        return SettingsDataStore(context)
    }

    @Provides
    @Singleton
    fun provideSyncRepository(dao: SyncedFileDao): SyncRepository {
        return SyncRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        dataStore: SettingsDataStore,
        @ApplicationContext context: Context
    ): SettingsRepository {
        return SettingsRepositoryImpl(dataStore, context)
    }
}
package com.yadisksync.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface YandexDiskApi {

    @GET("v1/disk/resources/")
    suspend fun getFiles(
        @Header("Authorization") authorization: String,
        @Query("path") path: String = "disk:/",
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("media_type") mediaType: String? = null,
        @Query("sort") sort: String? = null
    ): DiskResource

    @GET("v1/disk/resources/download")
    suspend fun getDownloadLink(
        @Header("Authorization") authorization: String,
        @Query("path") path: String
    ): DownloadLinkResponse

    companion object {
        const val BASE_URL = "https://cloud-api.yandex.net/"
    }
}
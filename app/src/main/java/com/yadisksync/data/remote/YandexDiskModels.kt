package com.yadisksync.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiskResource(
    val items: List<DiskFile> = emptyList(),
    val _embedded: EmbeddedResources? = null,
    val limit: Int = 0,
    val path: String = "",
    val total: Long = 0
)

@Serializable
data class EmbeddedResources(
    val items: List<DiskFile> = emptyList()
)

@Serializable
data class DiskFile(
    val name: String,
    val path: String,
    val size: Long,
    @SerialName("created") val created: String? = null,
    @SerialName("modified") val modified: String? = null,
    @SerialName("mime_type") val mimeType: String = "",
    val type: String = ""
)

@Serializable
data class DownloadLinkResponse(
    val href: String,
    val method: String,
    val templated: Boolean = false
)
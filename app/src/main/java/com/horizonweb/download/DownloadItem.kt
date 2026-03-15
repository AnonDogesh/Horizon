package com.horizonweb.download

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    MOVED_TO_VAULT,
    FAILED,
    CANCELED
}

data class DownloadItem(
    val id: Long,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val status: DownloadStatus,
    val progress: Int,
    val localPath: String? = null,
    val requiresPostDownloadAction: Boolean = false
)

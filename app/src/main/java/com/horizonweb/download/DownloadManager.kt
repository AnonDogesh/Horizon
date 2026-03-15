package com.horizonweb.download

import android.app.DownloadManager as AndroidDownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val systemDownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as AndroidDownloadManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trackingJobs = mutableMapOf<Long, Job>()

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    fun startDownload(url: String, fileName: String, mimeType: String): Long {
        val request = AndroidDownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription("Downloading $fileName")
            setMimeType(mimeType)
            setNotificationVisibility(AndroidDownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
        }

        val id = systemDownloadManager.enqueue(request)
        addOrUpdate(
            DownloadItem(
                id = id,
                url = url,
                fileName = fileName,
                mimeType = mimeType,
                status = DownloadStatus.QUEUED,
                progress = 0
            )
        )
        trackProgress(id)
        return id
    }

    fun cancelDownload(id: Long) {
        systemDownloadManager.remove(id)
        trackingJobs.remove(id)?.cancel()
        updateDownload(id) { item ->
            item.copy(
                status = DownloadStatus.CANCELED,
                progress = 0,
                requiresPostDownloadAction = false
            )
        }
    }

    fun markDownloadHandled(id: Long) {
        updateDownload(id) { item -> item.copy(requiresPostDownloadAction = false) }
    }

    fun markMovedToVault(id: Long) {
        updateDownload(id) { item ->
            item.copy(
                status = DownloadStatus.MOVED_TO_VAULT,
                progress = 100,
                localPath = null,
                requiresPostDownloadAction = false
            )
        }
    }

    fun findDownload(id: Long): DownloadItem? = _downloads.value.firstOrNull { it.id == id }

    private fun trackProgress(id: Long) {
        trackingJobs[id]?.cancel()
        trackingJobs[id] = scope.launch {
            val query = AndroidDownloadManager.Query().setFilterById(id)
            while (true) {
                val cursor = systemDownloadManager.query(query)
                if (!cursor.moveToFirst()) {
                    cursor.close()
                    updateDownload(id) { item ->
                        item.copy(status = DownloadStatus.FAILED, progress = 0, requiresPostDownloadAction = false)
                    }
                    break
                }

                val bytesDownloaded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(AndroidDownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                )
                val bytesTotal = cursor.getLong(
                    cursor.getColumnIndexOrThrow(AndroidDownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                )
                val status = cursor.getInt(
                    cursor.getColumnIndexOrThrow(AndroidDownloadManager.COLUMN_STATUS)
                )

                val progress = if (bytesTotal > 0) {
                    ((bytesDownloaded.toDouble() / bytesTotal.toDouble()) * 100.0).roundToInt()
                        .coerceIn(0, 100)
                } else {
                    0
                }

                when (status) {
                    AndroidDownloadManager.STATUS_PENDING -> updateDownload(id) { item ->
                        item.copy(status = DownloadStatus.QUEUED, progress = progress)
                    }

                    AndroidDownloadManager.STATUS_RUNNING -> updateDownload(id) { item ->
                        item.copy(status = DownloadStatus.DOWNLOADING, progress = progress)
                    }

                    AndroidDownloadManager.STATUS_SUCCESSFUL -> {
                        val localUri = cursor.getString(
                            cursor.getColumnIndexOrThrow(AndroidDownloadManager.COLUMN_LOCAL_URI)
                        )
                        val localPath = localUri?.let { Uri.parse(it).path }
                        updateDownload(id) { item ->
                            item.copy(
                                status = DownloadStatus.COMPLETED,
                                progress = 100,
                                localPath = localPath,
                                requiresPostDownloadAction = true
                            )
                        }
                        cursor.close()
                        break
                    }

                    AndroidDownloadManager.STATUS_FAILED -> {
                        updateDownload(id) { item ->
                            item.copy(status = DownloadStatus.FAILED, progress = progress, requiresPostDownloadAction = false)
                        }
                        cursor.close()
                        break
                    }
                }

                cursor.close()
                delay(500)
            }
            trackingJobs.remove(id)
        }
    }

    private fun updateDownload(id: Long, updater: (DownloadItem) -> DownloadItem) {
        _downloads.value = _downloads.value.map { item ->
            if (item.id == id) updater(item) else item
        }
    }

    private fun addOrUpdate(item: DownloadItem) {
        val existing = _downloads.value.indexOfFirst { it.id == item.id }
        _downloads.value = if (existing == -1) {
            listOf(item) + _downloads.value
        } else {
            _downloads.value.toMutableList().also { it[existing] = item }
        }
    }
}

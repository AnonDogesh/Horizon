package com.dean.browservault

import java.io.File

object DownloadManager {

    fun start(
        url: String,
        path: String,
        onProgress: ((Int) -> Unit)? = null
    ): Boolean {
        val directory = File(path).apply { mkdirs() }
        return when {
            url.contains(".m3u8", ignoreCase = true) -> {
                HlsDownloader.download(url, directory.absolutePath, onProgress)
            }
            else -> {
                val fileName = FileUtils.filenameFromUrl(url, "video_${System.currentTimeMillis()}")
                val destination = FileUtils.createUniqueFile(directory, fileName)
                Mp4Downloader.download(url, destination, onProgress)
            }
        }
    }
}

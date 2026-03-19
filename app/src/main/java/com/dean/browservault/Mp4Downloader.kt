package com.dean.browservault

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream

object Mp4Downloader {

    private val client = OkHttpClient()

    fun start(task: DownloadTask, onDone: (Boolean, String?, String?) -> Unit) {
        Thread {
            val result = runCatching {
                val downloadsDir = FileUtils.ensureDownloadsDirectory(task.context)
                val fileName = FileUtils.filenameFromUrl(task.url, "media")
                val destination = FileUtils.createUniqueFile(downloadsDir, fileName)

                val request = Request.Builder()
                    .url(task.url)
                    .addHeader("User-Agent", task.userAgent)
                    .addHeader("Cookie", task.cookies)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                    val input = response.body?.byteStream() ?: throw IllegalStateException("Empty response body")
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }
                destination.absolutePath
            }

            result.onSuccess { onDone(true, it, null) }
                .onFailure { onDone(false, null, it.message ?: "Unknown error") }
        }.start()
    }
}

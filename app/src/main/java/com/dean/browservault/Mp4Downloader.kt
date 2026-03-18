package com.dean.browservault

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object Mp4Downloader {

    private val client = OkHttpClient()

    fun download(url: String, file: File, onProgress: ((Int) -> Unit)? = null): Boolean {
        return runCatching {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) return false

            val body = response.body ?: return false
            val total = body.contentLength().takeIf { it > 0L }

            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total != null && onProgress != null) {
                            val percent = ((downloaded * 100) / total).toInt()
                            onProgress(percent)
                        }
                    }
                    output.flush()
                }
            }
            true
        }.getOrDefault(false)
    }
}

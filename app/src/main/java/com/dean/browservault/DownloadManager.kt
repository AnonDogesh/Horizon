package com.dean.browservault

object DownloadManager {

    private val queue = mutableListOf<DownloadTask>()
    private var running = false

    @Synchronized
    fun enqueue(task: DownloadTask) {
        queue.add(task)
        processNext()
    }

    @Synchronized
    private fun processNext() {
        if (running) return
        val task = queue.firstOrNull { it.status == "PENDING" } ?: return

        running = true
        task.status = "DOWNLOADING"

        val callback: (Boolean, String?, String?) -> Unit = { success, output, error ->
            synchronized(this) {
                task.status = if (success) "COMPLETED" else "FAILED"
                task.outputPath = output
                task.error = error
                queue.remove(task)
                running = false
            }
            task.onComplete?.invoke(task)
            processNext()
        }

        when {
            task.url.contains(".m3u8", ignoreCase = true) -> HlsDownloader.start(task, callback)
            else -> Mp4Downloader.start(task, callback)
        }
    }
}

package com.dean.browservault

data class DownloadTask(
    val id: String,
    val url: String,
    var status: String,
    var progress: Int = 0
)

object DownloadQueue {
    val tasks = mutableListOf<DownloadTask>()

    @Synchronized
    fun enqueue(url: String): DownloadTask {
        val task = DownloadTask(
            id = "task_${System.currentTimeMillis()}",
            url = url,
            status = "queued",
            progress = 0
        )
        tasks += task
        return task
    }

    @Synchronized
    fun update(taskId: String, status: String, progress: Int? = null) {
        val task = tasks.firstOrNull { it.id == taskId } ?: return
        task.status = status
        if (progress != null) {
            task.progress = progress.coerceIn(0, 100)
        }
    }
}

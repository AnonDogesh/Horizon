package com.dean.browservault

object HlsDownloader {

    fun start(task: DownloadTask, onDone: (Boolean, String?, String?) -> Unit) {
        val output = FileUtils.ensureDownloadsDirectory(task.context)
            .absolutePath + "/video_${System.currentTimeMillis()}.mp4"
        val command = "-y -i \"${task.url}\" -c copy -bsf:a aac_adtstoasc \"$output\""

        FFmpegEngine.run(command) { success ->
            if (success) onDone(true, output, null)
            else onDone(false, null, "HLS Failed")
        }
    }
}

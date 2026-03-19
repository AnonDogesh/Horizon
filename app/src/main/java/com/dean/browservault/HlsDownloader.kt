package com.dean.browservault

object HlsDownloader {

    fun start(task: DownloadTask, onDone: (Boolean, String?, String?) -> Unit) {
        val output = FileUtils.ensureDownloadsDirectory(task.context)
            .absolutePath + "/video_${System.currentTimeMillis()}.mp4"
        val ua = task.userAgent.ifBlank { "Mozilla/5.0" }.replace("\"", "\\\"")
        val cookies = task.cookies.replace("\"", "\\\"")
        val command = """
            -y
            -headers "User-Agent: $ua\r\nCookie: $cookies\r\n"
            -i "${task.url}"
            -c copy
            -bsf:a aac_adtstoasc
            "$output"
        """.trimIndent().replace("\n", " ")

        runAttempt(command, output, 0, onDone)
    }

    private fun runAttempt(
        command: String,
        output: String,
        attempt: Int,
        onDone: (Boolean, String?, String?) -> Unit
    ) {
        FFmpegEngine.run(command) { success ->
            if (success) {
                onDone(true, output, null)
            } else if (attempt + 1 < MAX_RETRIES) {
                runAttempt(command, output, attempt + 1, onDone)
            } else {
                onDone(false, null, "HLS Failed")
            }
        }
    }

    private const val MAX_RETRIES = 2
}

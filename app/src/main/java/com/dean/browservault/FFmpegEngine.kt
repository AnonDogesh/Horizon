package com.dean.browservault

object FFmpegEngine {

    fun run(command: String, onResult: (Boolean) -> Unit) {
        Thread {
            onResult(runBlocking(command))
        }.start()
    }

    fun runBlocking(command: String): Boolean {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "ffmpeg $command"))
            process.waitFor()
            process.exitValue() == 0
        }.getOrDefault(false)
    }
}

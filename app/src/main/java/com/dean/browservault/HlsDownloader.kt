package com.dean.browservault

object HlsDownloader {

    fun download(url: String, outputPath: String, onProgress: ((Int) -> Unit)? = null): Boolean {
        val output = "$outputPath/video_${System.currentTimeMillis()}.mp4"
        val command = "-y -i \"$url\" -c copy -bsf:a aac_adtstoasc \"$output\""
        onProgress?.invoke(5)
        return FFmpegEngine.runBlocking(command).also { success ->
            onProgress?.invoke(if (success) 100 else 0)
        }
    }
}

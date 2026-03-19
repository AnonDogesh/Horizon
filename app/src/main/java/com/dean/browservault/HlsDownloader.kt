package com.dean.browservault

object HlsDownloader {

    fun download(url: String, outputPath: String, onProgress: ((Int) -> Unit)? = null): String? {
        val output = "$outputPath/video_${System.currentTimeMillis()}.mp4"
        val command = "-y -i \"$url\" -c copy -bsf:a aac_adtstoasc \"$output\""
        onProgress?.invoke(5)
        val success = FFmpegEngine.runBlocking(command)
        onProgress?.invoke(if (success) 100 else 0)
        return if (success) output else null
    }
}

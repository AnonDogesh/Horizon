package com.dean.browservault

import android.content.Context

data class DownloadTask(
    val context: Context,
    val url: String,
    val userAgent: String,
    val cookies: String,
    var status: String = "PENDING",
    var outputPath: String? = null,
    var error: String? = null,
    val onComplete: ((DownloadTask) -> Unit)? = null
)

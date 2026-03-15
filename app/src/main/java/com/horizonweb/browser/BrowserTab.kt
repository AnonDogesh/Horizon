package com.horizonweb.browser

data class BrowserTab(
    val tabId: Long,
    val title: String,
    val url: String,
    val favicon: String?,
    val isPrivate: Boolean
)

package com.dean.browservault

import java.util.Locale

class AdBlocker {

    private val adDomainPatterns = listOf(
        "doubleclick.net",
        "googlesyndication",
        "ads.",
        "adservice."
    )

    fun isAdUrl(url: String): Boolean {
        val normalized = url.lowercase(Locale.US)
        return adDomainPatterns.any { pattern -> normalized.contains(pattern) }
    }
}

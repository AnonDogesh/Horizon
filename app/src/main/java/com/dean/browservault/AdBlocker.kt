package com.dean.browservault

import java.util.Locale

class AdBlocker {

    private val adDomainPatterns = listOf(
        "doubleclick.net",
        "googlesyndication",
        "adservice.",
        "googleadservices.com",
        "googletagmanager.com",
        "googletagservices.com",
        "adnxs.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "rubiconproject.com",
        "pubmatic.com",
        "smartadserver.com",
        "adsystem.com",
        "scorecardresearch.com",
        "facebook.com/tr",
        "/ads?",
        "/ad?",
        "/advert",
        "vast",
        "vmap",
        "preroll",
        "midroll",
        "instream"
    )

    private val adQueryHints = listOf(
        "ad_unit",
        "adunit",
        "ad_type",
        "adformat",
        "gampad",
        "iu=/",
        "sz=",
        "cust_params"
    )

    fun isAdUrl(url: String): Boolean {
        val normalized = url.lowercase(Locale.US)
        if (adDomainPatterns.any { pattern -> normalized.contains(pattern) }) return true
        return adQueryHints.any { hint -> normalized.contains(hint) }
    }
}

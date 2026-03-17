package com.dean.browservault

import android.content.Context
import android.net.Uri
import android.webkit.URLUtil

object SearchEngineManager {
    private const val PREFS_NAME = "search_engine"
    private const val KEY_ENGINE = "selected_engine"

    const val GOOGLE = "google"
    const val DUCKDUCKGO = "duckduckgo"
    const val BING = "bing"
    const val YAHOO = "yahoo"

    val engines: LinkedHashMap<String, String> = linkedMapOf(
        GOOGLE to "Google",
        DUCKDUCKGO to "DuckDuckGo",
        BING to "Bing",
        YAHOO to "Yahoo"
    )

    private val searchUrlMap: Map<String, String> = mapOf(
        GOOGLE to "https://www.google.com/search?q=%s",
        DUCKDUCKGO to "https://duckduckgo.com/?q=%s",
        BING to "https://www.bing.com/search?q=%s",
        YAHOO to "https://search.yahoo.com/search?p=%s"
    )

    fun selectedEngine(context: Context): String {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENGINE, GOOGLE)
            ?: GOOGLE
        return if (engines.containsKey(value)) value else GOOGLE
    }

    fun saveSelectedEngine(context: Context, engine: String) {
        if (!engines.containsKey(engine)) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENGINE, engine)
            .apply()
    }

    fun buildSearchUrl(engine: String, query: String, tbm: String? = null): String {
        val encoded = Uri.encode(query)
        val normalizedEngine = if (engines.containsKey(engine)) engine else GOOGLE
        val base = (searchUrlMap[normalizedEngine] ?: searchUrlMap.getValue(GOOGLE)).format(encoded)
        return if (normalizedEngine == GOOGLE && !tbm.isNullOrBlank()) "$base&tbm=$tbm" else base
    }

    fun resolveInputToUrl(context: Context, rawInput: String): String {
        val value = rawInput.trim()
        if (looksLikeUrl(value)) {
            return normalizeUrl(value)
        }
        val selected = selectedEngine(context)
        return buildSearchUrl(selected, value)
    }

    private fun looksLikeUrl(value: String): Boolean {
        return URLUtil.isValidUrl(value) || (value.contains(".") && !value.contains(" "))
    }

    private fun normalizeUrl(value: String): String {
        return if (value.startsWith("http://") || value.startsWith("https://")) value else "https://$value"
    }
}

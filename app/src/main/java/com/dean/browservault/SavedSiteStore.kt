package com.dean.browservault

import android.content.Context
import org.json.JSONArray

object SavedSiteStore {
    const val TYPE_BOOKMARKS = "bookmarks"
    const val TYPE_HISTORY = "history"

    private const val MAX_BOOKMARKS = 50
    private const val MAX_HISTORY = 50

    fun list(context: Context, type: String): List<String> {
        val prefs = prefs(context, type)
        val raw = prefs.getString(key(type), null)
        if (raw.isNullOrBlank()) {
            val migrated = prefs.getStringSet(legacyKey(type), emptySet()).orEmpty().toList().sortedDescending()
            if (migrated.isNotEmpty()) {
                save(context, type, migrated)
            }
            return migrated
        }

        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                add(array.optString(index))
            }
        }.filter { it.isNotBlank() }
    }

    fun add(context: Context, type: String, url: String) {
        val items = list(context, type).toMutableList()
        items.remove(url)
        items.add(0, url)
        save(context, type, items.take(maxItems(type)))
    }

    fun remove(context: Context, type: String, url: String) {
        removeAll(context, type, listOf(url))
    }

    fun removeAll(context: Context, type: String, urls: Collection<String>) {
        if (urls.isEmpty()) return
        val updated = list(context, type).filterNot(urls.toSet()::contains)
        save(context, type, updated)
    }

    private fun save(context: Context, type: String, items: List<String>) {
        val array = JSONArray()
        items.forEach(array::put)
        prefs(context, type)
            .edit()
            .putString(key(type), array.toString())
            .remove(legacyKey(type))
            .apply()
    }

    private fun prefs(context: Context, type: String) =
        context.getSharedPreferences(prefName(type), Context.MODE_PRIVATE)

    private fun prefName(type: String) = when (type) {
        TYPE_HISTORY -> "home_history"
        else -> "bookmarks"
    }

    private fun key(type: String) = when (type) {
        TYPE_HISTORY -> "history_list_json"
        else -> "bookmark_list_json"
    }

    private fun legacyKey(type: String) = when (type) {
        TYPE_HISTORY -> "history_list"
        else -> "bookmark_list"
    }

    private fun maxItems(type: String) = when (type) {
        TYPE_HISTORY -> MAX_HISTORY
        else -> MAX_BOOKMARKS
    }
}

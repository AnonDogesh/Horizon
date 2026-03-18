package com.dean.browservault

import android.content.Context
import org.json.JSONArray

object TabSessionStore {
    private const val PREFS_NAME = "tab_sessions"
    private const val KEY_TABS = "open_tabs"
    private const val MAX_TABS = 50

    fun list(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TABS, "[]")
            ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                add(array.optString(i))
            }
        }.filter { it.isNotBlank() }
    }

    fun add(context: Context, url: String) {
        val current = list(context).toMutableList()
        current.remove(url)
        current.add(0, url)
        save(context, current.take(MAX_TABS))
    }

    fun replace(context: Context, previousUrl: String, newUrl: String) {
        val current = list(context).toMutableList()
        current.remove(previousUrl)
        current.remove(newUrl)
        current.add(0, newUrl)
        save(context, current.take(MAX_TABS))
    }

    fun remove(context: Context, url: String) {
        val current = list(context).toMutableList()
        current.remove(url)
        save(context, current)
    }

    private fun save(context: Context, tabs: List<String>) {
        val array = JSONArray()
        tabs.forEach { array.put(it) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TABS, array.toString())
            .apply()
    }
}

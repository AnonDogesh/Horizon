package com.dean.browservault

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TabSession(
    val url: String,
    val desktopMode: Boolean = false
)

object TabSessionStore {
    private const val PREFS_NAME = "tab_sessions"
    private const val KEY_TABS = "open_tabs"
    private const val MAX_TABS = 50

    fun list(context: Context): List<TabSession> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TABS, "[]")
            ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                when (val entry = array.opt(i)) {
                    is JSONObject -> {
                        val url = entry.optString("url")
                        if (url.isNotBlank()) {
                            add(TabSession(url, entry.optBoolean("desktopMode", false)))
                        }
                    }

                    is String -> {
                        if (entry.isNotBlank()) {
                            add(TabSession(entry, false))
                        }
                    }
                }
            }
        }
    }

    fun add(context: Context, url: String, desktopMode: Boolean = false) {
        upsert(context, previousUrl = null, url = url, desktopMode = desktopMode)
    }

    fun upsert(context: Context, previousUrl: String?, url: String, desktopMode: Boolean) {
        val current = list(context).toMutableList()
        if (!previousUrl.isNullOrBlank()) {
            current.removeAll { it.url == previousUrl }
        }
        current.removeAll { it.url == url }
        current.add(0, TabSession(url, desktopMode))
        save(context, current.take(MAX_TABS))
    }

    fun remove(context: Context, url: String) {
        val current = list(context).filterNot { it.url == url }
        save(context, current)
    }

    private fun save(context: Context, tabs: List<TabSession>) {
        val array = JSONArray()
        tabs.forEach { session ->
            array.put(
                JSONObject()
                    .put("url", session.url)
                    .put("desktopMode", session.desktopMode)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TABS, array.toString())
            .apply()
    }
}

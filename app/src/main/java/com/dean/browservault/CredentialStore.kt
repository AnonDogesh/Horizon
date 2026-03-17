package com.dean.browservault

import android.content.Context

data class SavedCredential(val username: String, val password: String)

object CredentialStore {
    private const val PREFS_NAME = "saved_credentials"

    fun save(context: Context, host: String, username: String, password: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("${host}_u", username)
            .putString("${host}_p", password)
            .apply()
    }

    fun get(context: Context, host: String): SavedCredential? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val user = prefs.getString("${host}_u", null)
        val pass = prefs.getString("${host}_p", null)
        return if (!user.isNullOrBlank() && !pass.isNullOrBlank()) SavedCredential(user, pass) else null
    }
}

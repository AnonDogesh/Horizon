package com.dean.browservault

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var adBlockerSwitch: SwitchMaterial
    private lateinit var javascriptSwitch: SwitchMaterial
    private lateinit var desktopModeSwitch: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        adBlockerSwitch = findViewById(R.id.switchAdBlocker)
        javascriptSwitch = findViewById(R.id.switchJavascript)
        desktopModeSwitch = findViewById(R.id.switchDesktopMode)

        val prefs = getSharedPreferences(BrowserPreferences.PREFS_NAME, Context.MODE_PRIVATE)

        adBlockerSwitch.isChecked = prefs.getBoolean(BrowserPreferences.KEY_AD_BLOCKER, true)
        javascriptSwitch.isChecked = prefs.getBoolean(BrowserPreferences.KEY_JAVASCRIPT, true)
        desktopModeSwitch.isChecked = prefs.getBoolean(BrowserPreferences.KEY_DESKTOP_MODE, false)

        adBlockerSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(BrowserPreferences.KEY_AD_BLOCKER, checked).apply()
        }

        javascriptSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(BrowserPreferences.KEY_JAVASCRIPT, checked).apply()
        }

        desktopModeSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(BrowserPreferences.KEY_DESKTOP_MODE, checked).apply()
        }
    }
}

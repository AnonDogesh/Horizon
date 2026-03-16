package com.dean.browservault

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.buttonBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.buttonSearch).setOnClickListener {
            startActivity(Intent(this, BrowserTabActivity::class.java).putExtra(BrowserTabActivity.EXTRA_QUERY, "horizon settings"))
        }

        findViewById<MaterialButton>(R.id.buttonManageAccount).setOnClickListener {
            toast(getString(R.string.msg_manage_account))
        }

        findViewById<android.view.View>(R.id.rowAppearance).setOnClickListener {
            toast(getString(R.string.msg_appearance_opened))
        }

        findViewById<android.view.View>(R.id.rowPrivacy).setOnClickListener {
            showPrivacyDialog()
        }

        findViewById<android.view.View>(R.id.rowDownloads).setOnClickListener {
            runCatching {
                startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
            }.onFailure {
                toast(getString(R.string.msg_downloads_not_available))
            }
        }

        findViewById<android.view.View>(R.id.rowAdvanced).setOnClickListener {
            toast(getString(R.string.msg_advanced_opened))
        }

        findViewById<android.view.View>(R.id.rowAbout).setOnClickListener {
            showAboutDialog()
        }

        findViewById<android.view.View>(R.id.rowHelp).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:support@horizon.com")
                putExtra(Intent.EXTRA_SUBJECT, "Horizon Feedback")
            }
            runCatching { startActivity(intent) }
                .onFailure { toast(getString(R.string.msg_help_not_available)) }
        }
    }

    private fun showPrivacyDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_privacy_settings, null)
        val prefs = getSharedPreferences(BrowserPreferences.PREFS_NAME, Context.MODE_PRIVATE)

        val adSwitch = view.findViewById<SwitchMaterial>(R.id.switchAdBlocker)
        val jsSwitch = view.findViewById<SwitchMaterial>(R.id.switchJavascript)
        val desktopSwitch = view.findViewById<SwitchMaterial>(R.id.switchDesktopMode)

        adSwitch.isChecked = prefs.getBoolean(BrowserPreferences.KEY_AD_BLOCKER, true)
        jsSwitch.isChecked = prefs.getBoolean(BrowserPreferences.KEY_JAVASCRIPT, true)
        desktopSwitch.isChecked = prefs.getBoolean(BrowserPreferences.KEY_DESKTOP_MODE, false)

        adSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(BrowserPreferences.KEY_AD_BLOCKER, checked).apply()
        }
        jsSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(BrowserPreferences.KEY_JAVASCRIPT, checked).apply()
        }
        desktopSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(BrowserPreferences.KEY_DESKTOP_MODE, checked).apply()
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_privacy)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_about)
            .setMessage(getString(R.string.settings_about_desc))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

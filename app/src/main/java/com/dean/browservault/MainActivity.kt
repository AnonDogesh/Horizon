package com.dean.browservault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var inputSearch: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputSearch = findViewById(R.id.inputSearch)
        setupTopActions()
        setupShortcutActions()
        setupFeedActions()
        setupBottomActions()
    }

    private fun setupTopActions() {
        findViewById<MaterialButton>(R.id.buttonSearch).setOnClickListener {
            searchFromInput()
        }

        findViewById<ImageButton>(R.id.buttonProfile).setOnClickListener { view ->
            showQuickMenu(view)
        }

        findViewById<TextView>(R.id.textCustomize).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupShortcutActions() {
        findViewById<MaterialButton>(R.id.buttonMail).setOnClickListener {
            openUrl("https://mail.google.com")
        }
        findViewById<MaterialButton>(R.id.buttonDaily).setOnClickListener {
            openUrl("https://news.google.com")
        }
        findViewById<MaterialButton>(R.id.buttonMarket).setOnClickListener {
            openUrl("https://www.tradingview.com")
        }
        findViewById<MaterialButton>(R.id.buttonCloud).setOnClickListener {
            openUrl("https://drive.google.com")
        }
        findViewById<MaterialButton>(R.id.buttonAdd).setOnClickListener {
            startActivity(Intent(this, VaultActivity::class.java))
        }
    }

    private fun setupFeedActions() {
        findViewById<MaterialCardView>(R.id.cardPrimary).setOnClickListener {
            openUrl("https://en.wikipedia.org/wiki/Post-quantum_cryptography")
        }
        findViewById<MaterialCardView>(R.id.cardSecondary).setOnClickListener {
            openUrl("https://www.bloomberg.com/markets")
        }
        findViewById<MaterialCardView>(R.id.cardTertiary).setOnClickListener {
            openUrl("https://www.weforum.org/agenda/archive/geopolitics/")
        }
    }

    private fun setupBottomActions() {
        findViewById<MaterialButton>(R.id.navHome).setOnClickListener {
            toast(getString(R.string.msg_home_active))
        }
        findViewById<MaterialButton>(R.id.navHistory).setOnClickListener {
            showHistoryDialog()
        }
        findViewById<MaterialButton>(R.id.navSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.navMenu).setOnClickListener { view ->
            showQuickMenu(view)
        }
    }

    private fun searchFromInput() {
        val raw = inputSearch.text.toString().trim()
        if (raw.isEmpty()) {
            toast(getString(R.string.msg_enter_search))
            return
        }

        val resolved = if (raw.startsWith("http://") || raw.startsWith("https://")) {
            raw
        } else if (raw.contains(".") && !raw.contains(" ")) {
            "https://$raw"
        } else {
            "https://www.google.com/search?q=${Uri.encode(raw)}"
        }

        if (resolved.startsWith("https://www.google.com/search")) {
            startActivity(
                Intent(this, BrowserTabActivity::class.java)
                    .putExtra(BrowserTabActivity.EXTRA_QUERY, raw)
            )
        } else {
            openUrl(resolved)
        }
    }

    private fun openUrl(url: String) {
        rememberHistory(url)

        val lower = url.lowercase(Locale.US)
        if (lower.endsWith(".mp4") || lower.endsWith(".m3u8") || lower.endsWith(".webm")) {
            startActivity(
                Intent(this, VideoPlayerActivity::class.java)
                    .putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, url)
            )
            return
        }

        startActivity(
            Intent(this, BrowserTabActivity::class.java)
                .putExtra(BrowserTabActivity.EXTRA_URL, url)
        )
    }

    private fun showQuickMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, MENU_VAULT, 0, getString(R.string.action_vault))
        popup.menu.add(0, MENU_SETTINGS, 1, getString(R.string.action_settings))
        popup.menu.add(0, MENU_CLEAR_HISTORY, 2, getString(R.string.action_clear_history))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_VAULT -> {
                    startActivity(Intent(this, VaultActivity::class.java))
                    true
                }

                MENU_SETTINGS -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }

                MENU_CLEAR_HISTORY -> {
                    clearHistory()
                    toast(getString(R.string.msg_history_cleared))
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun rememberHistory(url: String) {
        val prefs = getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_HISTORY, emptySet()).orEmpty().toMutableList()
        existing.remove(url)
        existing.add(0, url)
        val trimmed = existing.take(MAX_HISTORY).toSet()
        prefs.edit().putStringSet(KEY_HISTORY, trimmed).apply()
    }

    private fun showHistoryDialog() {
        val prefs = getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE)
        val entries = prefs.getStringSet(KEY_HISTORY, emptySet()).orEmpty().toList().sortedDescending()

        if (entries.isEmpty()) {
            toast(getString(R.string.msg_no_history))
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.nav_history)
            .setItems(entries.toTypedArray()) { _, which ->
                openUrl(entries[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun clearHistory() {
        getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE).edit().remove(KEY_HISTORY).apply()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val MENU_VAULT = 1
        private const val MENU_SETTINGS = 2
        private const val MENU_CLEAR_HISTORY = 3
        private const val HISTORY_PREFS = "home_history"
        private const val KEY_HISTORY = "history_list"
        private const val MAX_HISTORY = 20
    }
}

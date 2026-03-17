package com.dean.browservault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
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

        findViewById<ImageButton>(R.id.buttonSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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
        findViewById<MaterialButton>(R.id.navTabs).setOnClickListener {
            startActivity(Intent(this, TabManagerActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.navMenu).setOnClickListener {
            showBottomMenuSheet()
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
        TabSessionStore.add(this, url)

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

    private fun showBottomMenuSheet() {
        val prefs = getSharedPreferences(BrowserPreferences.PREFS_NAME, MODE_PRIVATE)

        val dialog = BottomSheetDialog(this)
        val content = layoutInflater.inflate(R.layout.bottom_sheet_tab_menu, null)
        dialog.setContentView(content)

        content.findViewById<MaterialButton>(R.id.menuPrivateVault).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, VaultActivity::class.java))
        }
        content.findViewById<android.view.View>(R.id.rowNewTab).setOnClickListener {
            dialog.dismiss()
        }
        content.findViewById<android.view.View>(R.id.rowBookmarks).setOnClickListener {
            dialog.dismiss()
            showBookmarksDialog()
        }
        content.findViewById<android.view.View>(R.id.rowHistory).setOnClickListener {
            dialog.dismiss()
            showHistoryDialog()
        }
        content.findViewById<android.view.View>(R.id.rowDownloads).setOnClickListener {
            dialog.dismiss()
            runCatching {
                startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
            }.onFailure {
                toast(getString(R.string.msg_downloads_not_available))
            }
        }

        val desktopSwitch = content.findViewById<SwitchMaterial>(R.id.switchDesktopSite)
        desktopSwitch.isChecked = prefs.getBoolean(BrowserPreferences.KEY_DESKTOP_MODE, false)
        desktopSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(BrowserPreferences.KEY_DESKTOP_MODE, checked).apply()
        }

        dialog.show()
    }

    private fun showBookmarksDialog() {
        val prefs = getSharedPreferences(BOOKMARK_PREFS, MODE_PRIVATE)
        val entries = prefs.getStringSet(KEY_BOOKMARKS, emptySet()).orEmpty().toList().sortedDescending()
        if (entries.isEmpty()) {
            toast(getString(R.string.msg_no_bookmarks))
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.action_bookmarks)
            .setItems(entries.toTypedArray()) { _, which ->
                openUrl(entries[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val HISTORY_PREFS = "home_history"
        private const val KEY_HISTORY = "history_list"
        private const val MAX_HISTORY = 20

        private const val BOOKMARK_PREFS = "bookmarks"
        private const val KEY_BOOKMARKS = "bookmark_list"
    }
}

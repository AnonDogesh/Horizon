package com.dean.browservault

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.ByteArrayInputStream
import java.util.Locale

class BrowserTabActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var webView: WebView
    private lateinit var searchInput: EditText
    private lateinit var currentHost: TextView
    private lateinit var prefs: SharedPreferences

    private val adBlocker = AdBlocker()
    private var isAdBlockEnabled = true
    private var defaultUserAgent: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser_tab)

        prefs = getSharedPreferences(BrowserPreferences.PREFS_NAME, Context.MODE_PRIVATE)

        webView = findViewById(R.id.webView)
        searchInput = findViewById(R.id.searchInput)
        currentHost = findViewById(R.id.currentHost)

        configureWebView()
        applyBrowserSettings(reloadPage = false)
        setupTopBar()
        setupCategoryBar()
        setupBottomBar()

        val initialQuery = intent.getStringExtra(EXTRA_QUERY)
        val initialUrl = intent.getStringExtra(EXTRA_URL)

        when {
            !initialUrl.isNullOrBlank() -> loadUrlOrVideo(initialUrl)
            !initialQuery.isNullOrBlank() -> performSearch(initialQuery)
            else -> performSearch("horizon browser")
        }
    }

    override fun onStart() {
        super.onStart()
        prefs.registerOnSharedPreferenceChangeListener(this)
        applyBrowserSettings(reloadPage = false)
    }

    override fun onStop() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key in setOf(
                BrowserPreferences.KEY_AD_BLOCKER,
                BrowserPreferences.KEY_JAVASCRIPT,
                BrowserPreferences.KEY_DESKTOP_MODE
            )
        ) {
            applyBrowserSettings(reloadPage = true)
        }
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.setBackgroundColor(Color.parseColor("#0E110C"))
        webView.settings.apply {
            defaultUserAgent = userAgentString
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val requestUrl = request.url.toString()
                return if (isAdBlockEnabled && adBlocker.isAdUrl(requestUrl)) {
                    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                } else {
                    super.shouldInterceptRequest(view, request)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                val target = request.url.toString()
                if (request.isForMainFrame && isVideoUrl(target)) {
                    startActivity(
                        Intent(this@BrowserTabActivity, VideoPlayerActivity::class.java)
                            .putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, target)
                    )
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!url.isNullOrBlank()) {
                    searchInput.setText(url)
                    currentHost.text = Uri.parse(url).host ?: getString(R.string.app_name)
                    rememberHistory(url)
                }
            }
        }
    }

    private fun setupTopBar() {
        findViewById<ImageButton>(R.id.buttonHome).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        findViewById<ImageButton>(R.id.buttonReload).setOnClickListener {
            webView.reload()
        }

        findViewById<MaterialButton>(R.id.buttonSearch).setOnClickListener {
            val value = searchInput.text.toString().trim()
            if (value.isBlank()) {
                toast(getString(R.string.msg_enter_search))
            } else {
                if (looksLikeUrl(value)) {
                    loadUrlOrVideo(normalizeUrl(value))
                } else {
                    performSearch(value)
                }
            }
        }

        findViewById<ImageButton>(R.id.buttonVoice).setOnClickListener {
            toast(getString(R.string.msg_voice_not_ready))
        }

        findViewById<ImageButton>(R.id.buttonClearSearch).setOnClickListener {
            searchInput.setText("")
        }
    }

    private fun setupCategoryBar() {
        findViewById<MaterialButton>(R.id.filterAll).setOnClickListener {
            performSearch(currentQuery())
        }
        findViewById<MaterialButton>(R.id.filterImages).setOnClickListener {
            performSearch(currentQuery(), "isch")
        }
        findViewById<MaterialButton>(R.id.filterVideos).setOnClickListener {
            performSearch(currentQuery(), "vid")
        }
        findViewById<MaterialButton>(R.id.filterNews).setOnClickListener {
            performSearch(currentQuery(), "nws")
        }
        findViewById<MaterialButton>(R.id.filterShopping).setOnClickListener {
            performSearch(currentQuery(), "shop")
        }
    }

    private fun setupBottomBar() {
        findViewById<ImageButton>(R.id.bottomBack).setOnClickListener {
            if (webView.canGoBack()) webView.goBack() else toast(getString(R.string.msg_no_back_history))
        }
        findViewById<ImageButton>(R.id.bottomForward).setOnClickListener {
            if (webView.canGoForward()) webView.goForward() else toast(getString(R.string.msg_no_forward_history))
        }
        findViewById<ImageButton>(R.id.bottomNewTab).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<ImageButton>(R.id.bottomTabs).setOnClickListener {
            toast(getString(R.string.msg_tabs_placeholder))
        }
        findViewById<ImageButton>(R.id.bottomMenu).setOnClickListener {
            showBottomMenuSheet()
        }
    }

    private fun showBottomMenuSheet() {
        val dialog = BottomSheetDialog(this)
        val content = layoutInflater.inflate(R.layout.bottom_sheet_tab_menu, null)
        dialog.setContentView(content)

        content.findViewById<MaterialButton>(R.id.menuPrivateVault).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, VaultActivity::class.java))
        }
        content.findViewById<View>(R.id.rowNewTab).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, MainActivity::class.java))
        }
        content.findViewById<View>(R.id.rowBookmarks).setOnClickListener {
            dialog.dismiss()
            addCurrentPageToBookmarks()
            showBookmarksDialog()
        }
        content.findViewById<View>(R.id.rowHistory).setOnClickListener {
            dialog.dismiss()
            showHistoryDialog()
        }
        content.findViewById<View>(R.id.rowDownloads).setOnClickListener {
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

    private fun addCurrentPageToBookmarks() {
        val url = webView.url ?: return
        val prefs = getSharedPreferences(BOOKMARK_PREFS, MODE_PRIVATE)
        val list = prefs.getStringSet(KEY_BOOKMARKS, emptySet()).orEmpty().toMutableList()
        list.remove(url)
        list.add(0, url)
        prefs.edit().putStringSet(KEY_BOOKMARKS, list.take(MAX_BOOKMARKS).toSet()).apply()
        toast(getString(R.string.msg_bookmark_saved))
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
                loadUrlOrVideo(entries[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun rememberHistory(url: String) {
        val prefs = getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_HISTORY, emptySet()).orEmpty().toMutableList()
        existing.remove(url)
        existing.add(0, url)
        prefs.edit().putStringSet(KEY_HISTORY, existing.take(MAX_HISTORY).toSet()).apply()
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
                loadUrlOrVideo(entries[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun currentQuery(): String {
        val current = searchInput.text.toString().trim()
        return if (current.isBlank()) "horizon browser" else current
    }

    private fun performSearch(query: String, tbm: String? = null) {
        val encoded = Uri.encode(query)
        val url = if (tbm == null) {
            "https://www.google.com/search?q=$encoded"
        } else {
            "https://www.google.com/search?q=$encoded&tbm=$tbm"
        }
        webView.loadUrl(url)
    }

    private fun loadUrlOrVideo(url: String) {
        if (isVideoUrl(url)) {
            startActivity(
                Intent(this, VideoPlayerActivity::class.java)
                    .putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, url)
            )
        } else {
            webView.loadUrl(url)
        }
    }

    private fun looksLikeUrl(value: String): Boolean {
        return URLUtil.isValidUrl(value) || (value.contains(".") && !value.contains(" "))
    }

    private fun normalizeUrl(value: String): String {
        return if (value.startsWith("http://") || value.startsWith("https://")) value else "https://$value"
    }

    private fun isVideoUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return lower.endsWith(".mp4") || lower.endsWith(".m3u8") || lower.endsWith(".webm")
    }

    @Suppress("SetJavaScriptEnabled")
    private fun applyBrowserSettings(reloadPage: Boolean) {
        val jsEnabled = prefs.getBoolean(BrowserPreferences.KEY_JAVASCRIPT, true)
        val desktopMode = prefs.getBoolean(BrowserPreferences.KEY_DESKTOP_MODE, false)
        isAdBlockEnabled = prefs.getBoolean(BrowserPreferences.KEY_AD_BLOCKER, true)

        webView.settings.javaScriptEnabled = jsEnabled
        webView.settings.useWideViewPort = desktopMode
        webView.settings.loadWithOverviewMode = desktopMode
        webView.settings.userAgentString = if (desktopMode) {
            DESKTOP_USER_AGENT
        } else {
            defaultUserAgent ?: webView.settings.userAgentString
        }

        if (reloadPage && webView.url != null) {
            webView.reload()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_QUERY = "extra_query"
        const val EXTRA_URL = "extra_url"

        private const val HISTORY_PREFS = "home_history"
        private const val KEY_HISTORY = "history_list"
        private const val MAX_HISTORY = 50

        private const val BOOKMARK_PREFS = "bookmarks"
        private const val KEY_BOOKMARKS = "bookmark_list"
        private const val MAX_BOOKMARKS = 50

        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}

package com.dean.browservault

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.io.ByteArrayInputStream
import java.util.Locale

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var webView: WebView
    private lateinit var urlInput: TextInputEditText
    private lateinit var prefs: SharedPreferences

    private val adBlocker = AdBlocker()
    private var isAdBlockEnabled = true
    private var defaultUserAgent: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(BrowserPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        initializeDefaultSettingsIfMissing()

        webView = findViewById(R.id.webView)
        urlInput = findViewById(R.id.urlInput)

        configureWebView(webView)
        applyBrowserSettings(reloadPage = false)
        setupUiActions()

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }

        if (savedInstanceState == null) {
            loadFromInputOrDefault("https://example.com")
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
    private fun configureWebView(target: WebView) {
        target.setBackgroundColor(Color.BLACK)
        target.settings.apply {
            defaultUserAgent = userAgentString
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        target.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val requestUrl = request.url.toString()
                return if (isAdBlockEnabled && adBlocker.isAdUrl(requestUrl)) {
                    WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                } else {
                    super.shouldInterceptRequest(view, request)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest
            ): Boolean {
                val targetUrl = request.url.toString()
                if (request.isForMainFrame && isVideoUrl(targetUrl)) {
                    openVideoPlayer(targetUrl)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!url.isNullOrBlank()) {
                    urlInput.setText(url)
                }
            }
        }
    }

    private fun initializeDefaultSettingsIfMissing() {
        if (!prefs.contains(BrowserPreferences.KEY_AD_BLOCKER)) {
            prefs.edit().putBoolean(BrowserPreferences.KEY_AD_BLOCKER, true).apply()
        }
        if (!prefs.contains(BrowserPreferences.KEY_JAVASCRIPT)) {
            prefs.edit().putBoolean(BrowserPreferences.KEY_JAVASCRIPT, true).apply()
        }
        if (!prefs.contains(BrowserPreferences.KEY_DESKTOP_MODE)) {
            prefs.edit().putBoolean(BrowserPreferences.KEY_DESKTOP_MODE, false).apply()
        }
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

    private fun setupUiActions() {
        findViewById<MaterialButton>(R.id.buttonGo).setOnClickListener {
            loadFromInputOrDefault()
        }

        findViewById<MaterialButton>(R.id.buttonMenu).setOnClickListener { view ->
            showMenuActions(view)
        }

        findViewById<MaterialButton>(R.id.buttonBack).setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                toast(getString(R.string.msg_no_back_history))
            }
        }

        findViewById<MaterialButton>(R.id.buttonForward).setOnClickListener {
            if (webView.canGoForward()) {
                webView.goForward()
            } else {
                toast(getString(R.string.msg_no_forward_history))
            }
        }

        findViewById<MaterialButton>(R.id.buttonRefresh).setOnClickListener {
            webView.reload()
        }

        findViewById<MaterialButton>(R.id.buttonTabs).setOnClickListener {
            toast(getString(R.string.msg_tabs_placeholder))
        }

        findViewById<MaterialButton>(R.id.buttonVault).setOnClickListener {
            startActivity(Intent(this, VaultActivity::class.java))
        }
    }

    private fun showMenuActions(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, MENU_HOME, 0, getString(R.string.action_go_home))
        popup.menu.add(0, MENU_CLEAR_CACHE, 1, getString(R.string.action_clear_cache))
        popup.menu.add(0, MENU_SETTINGS, 2, getString(R.string.action_settings))

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_HOME -> {
                    loadFromInputOrDefault("https://example.com")
                    true
                }

                MENU_CLEAR_CACHE -> {
                    webView.clearCache(true)
                    toast(getString(R.string.msg_cache_cleared))
                    true
                }

                MENU_SETTINGS -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun loadFromInputOrDefault(fallbackUrl: String = "https://example.com") {
        val rawInput = urlInput.text?.toString()?.trim().orEmpty()
        val resolvedUrl = when {
            rawInput.isEmpty() -> fallbackUrl
            URLUtil.isValidUrl(rawInput) -> rawInput
            else -> "https://$rawInput"
        }

        if (isVideoUrl(resolvedUrl)) {
            openVideoPlayer(resolvedUrl)
        } else {
            webView.loadUrl(resolvedUrl)
        }
        urlInput.clearFocus()
    }

    private fun isVideoUrl(url: String): Boolean {
        val lower = Uri.parse(url).toString().lowercase(Locale.US)
        return VIDEO_EXTENSIONS.any { extension -> lower.contains(extension) }
    }

    private fun openVideoPlayer(videoUrl: String) {
        startActivity(
            Intent(this, VideoPlayerActivity::class.java)
                .putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, videoUrl)
        )
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val MENU_HOME = 1
        private const val MENU_CLEAR_CACHE = 2
        private const val MENU_SETTINGS = 3
        private val VIDEO_EXTENSIONS = listOf(".mp4", ".m3u8", ".webm")
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}

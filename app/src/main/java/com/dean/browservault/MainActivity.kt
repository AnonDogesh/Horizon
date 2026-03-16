package com.dean.browservault

import android.content.Intent
import android.graphics.Color
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

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlInput: TextInputEditText
    private val adBlocker = AdBlocker()
    private var isAdBlockEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        urlInput = findViewById(R.id.urlInput)

        configureWebView(webView)
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

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView(target: WebView) {
        target.setBackgroundColor(Color.BLACK)
        target.settings.apply {
            javaScriptEnabled = true
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

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!url.isNullOrBlank()) {
                    urlInput.setText(url)
                }
            }
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
        popup.menu.add(0, MENU_AD_BLOCK, 2, adBlockMenuTitle())

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

                MENU_AD_BLOCK -> {
                    isAdBlockEnabled = !isAdBlockEnabled
                    toast(if (isAdBlockEnabled) getString(R.string.msg_ad_block_enabled) else getString(R.string.msg_ad_block_disabled))
                    webView.reload()
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun adBlockMenuTitle(): String {
        return if (isAdBlockEnabled) {
            getString(R.string.action_ad_block_on)
        } else {
            getString(R.string.action_ad_block_off)
        }
    }

    private fun loadFromInputOrDefault(fallbackUrl: String = "https://example.com") {
        val rawInput = urlInput.text?.toString()?.trim().orEmpty()
        val resolvedUrl = when {
            rawInput.isEmpty() -> fallbackUrl
            URLUtil.isValidUrl(rawInput) -> rawInput
            else -> "https://$rawInput"
        }
        webView.loadUrl(resolvedUrl)
        urlInput.clearFocus()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val MENU_HOME = 1
        private const val MENU_CLEAR_CACHE = 2
        private const val MENU_AD_BLOCK = 3
    }
}

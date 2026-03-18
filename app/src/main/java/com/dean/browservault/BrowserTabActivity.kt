package com.dean.browservault

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.ByteArrayInputStream
import java.util.Locale

class BrowserTabActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var webView: WebView
    private lateinit var searchInput: EditText
    private lateinit var buttonReload: ImageButton
    private lateinit var engineSpinner: Spinner
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingProgress: LinearProgressIndicator
    private lateinit var prefs: SharedPreferences

    private val adBlocker = AdBlocker()
    private var isAdBlockEnabled = true
    private var defaultUserAgent: String? = null
    private var isPageLoading = false
    private var currentTabUrl: String? = null
    private val promptedHosts = mutableSetOf<String>()

    private val savedSitesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val url = result.data?.getStringExtra(SavedSitesActivity.EXTRA_SELECTED_URL) ?: return@registerForActivityResult
        loadUrlOrVideo(url)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser_tab)

        prefs = getSharedPreferences(BrowserPreferences.PREFS_NAME, Context.MODE_PRIVATE)

        webView = findViewById(R.id.webView)
        searchInput = findViewById(R.id.searchInput)
        buttonReload = findViewById(R.id.buttonReload)
        engineSpinner = findViewById(R.id.spinnerSearchEngineBrowser)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        loadingProgress = findViewById(R.id.loadingProgress)

        setupSearchEngineSpinner()
        configureWebView()
        applyBrowserSettings(reloadPage = false)
        setupTopBar()
        setupBottomBar()
        setupBackNavigation()
        setupSwipeRefresh()

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

    private fun setupSearchEngineSpinner() {
        val engineNames = SearchEngineManager.engines.values.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, engineNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        engineSpinner.adapter = adapter

        val selectedEngine = SearchEngineManager.selectedEngine(this)
        val selectedIndex = SearchEngineManager.engines.keys.indexOf(selectedEngine).coerceAtLeast(0)
        engineSpinner.setSelection(selectedIndex, false)

        engineSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val key = SearchEngineManager.engines.keys.elementAt(position)
                SearchEngineManager.saveSelectedEngine(this@BrowserTabActivity, key)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.setBackgroundColor(Color.parseColor("#0E110C"))
        webView.settings.apply {
            defaultUserAgent = userAgentString
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                loadingProgress.progress = newProgress
                loadingProgress.visibility = if (newProgress in 0..99) View.VISIBLE else View.GONE
            }
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

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isPageLoading = true
                loadingProgress.progress = 0
                loadingProgress.visibility = View.VISIBLE
                updateReloadButton()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isPageLoading = false
                swipeRefresh.isRefreshing = false
                loadingProgress.visibility = View.GONE
                updateReloadButton()

                if (!url.isNullOrBlank()) {
                    searchInput.setText(url)
                    SavedSiteStore.add(this@BrowserTabActivity, SavedSiteStore.TYPE_HISTORY, url)
                    syncTabSession(url)
                    maybeHandleCredentialPrompt(url)
                }
            }
        }
    }

    private fun setupTopBar() {
        buttonReload.setOnClickListener {
            if (isPageLoading) {
                webView.stopLoading()
                isPageLoading = false
                swipeRefresh.isRefreshing = false
                loadingProgress.visibility = View.GONE
                updateReloadButton()
            } else {
                reloadCurrentPage()
            }
        }

        findViewById<ImageButton>(R.id.buttonSearch).setOnClickListener {
            val value = searchInput.text.toString().trim()
            if (value.isBlank()) {
                toast(getString(R.string.msg_enter_search))
            } else {
                loadUrlOrVideo(SearchEngineManager.resolveInputToUrl(this, value))
            }
        }

        findViewById<ImageButton>(R.id.buttonClearSearch).setOnClickListener {
            searchInput.setText("")
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(Color.parseColor("#B8C58A"))
        swipeRefresh.setOnRefreshListener {
            reloadCurrentPage()
        }
    }

    private fun maybeHandleCredentialPrompt(url: String) {
        val host = Uri.parse(url).host ?: return
        if (!looksLikeAuthPage(url)) return

        if (!promptedHosts.add(host)) return

        val existing = CredentialStore.get(this, host)
        if (existing != null) {
            AlertDialog.Builder(this)
                .setTitle(R.string.title_use_saved_credentials)
                .setMessage(getString(R.string.msg_use_saved_credentials, host))
                .setPositiveButton(R.string.action_use) { _, _ -> autofillCredentials(existing) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.title_save_credentials)
            .setMessage(getString(R.string.msg_save_credentials_for, host))
            .setPositiveButton(R.string.action_save) { _, _ -> showSaveCredentialDialog(host) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSaveCredentialDialog(host: String) {
        val usernameInput = EditText(this).apply { hint = getString(R.string.hint_username) }
        val passwordInput = EditText(this).apply {
            hint = getString(R.string.hint_password)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 0)
            addView(usernameInput)
            addView(passwordInput)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.title_save_credentials)
            .setView(container)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val user = usernameInput.text.toString().trim()
                val pass = passwordInput.text.toString().trim()
                if (user.isNotEmpty() && pass.isNotEmpty()) {
                    CredentialStore.save(this, host, user, pass)
                    toast(getString(R.string.msg_credentials_saved))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun autofillCredentials(credential: SavedCredential) {
        val safeUser = credential.username.replace("'", "\\'")
        val safePass = credential.password.replace("'", "\\'")
        webView.evaluateJavascript(
            """
            (function(){
              var user = document.querySelector('input[type=email], input[name*=user], input[name*=email], input[type=text]');
              var pass = document.querySelector('input[type=password]');
              if(user){user.value='$safeUser';}
              if(pass){pass.value='$safePass';}
            })();
            """.trimIndent(),
            null
        )
    }

    private fun looksLikeAuthPage(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return lower.contains("login") || lower.contains("signin") || lower.contains("signup") || lower.contains("register")
    }

    private fun updateReloadButton() {
        if (isPageLoading) {
            buttonReload.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            buttonReload.contentDescription = getString(R.string.action_stop_loading)
        } else {
            buttonReload.setImageResource(android.R.drawable.ic_popup_sync)
            buttonReload.contentDescription = getString(R.string.action_refresh)
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
            startActivity(Intent(this, TabManagerActivity::class.java))
        }
        findViewById<ImageButton>(R.id.bottomMenu).setOnClickListener {
            showBottomMenuSheet()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
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
            openSavedSites(SavedSiteStore.TYPE_BOOKMARKS)
        }
        content.findViewById<View>(R.id.rowAddBookmark).apply {
            visibility = View.VISIBLE
            setOnClickListener {
                dialog.dismiss()
                addCurrentPageToBookmarks()
            }
        }
        content.findViewById<View>(R.id.rowHistory).setOnClickListener {
            dialog.dismiss()
            openSavedSites(SavedSiteStore.TYPE_HISTORY)
        }
        content.findViewById<View>(R.id.rowDownloads).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, DownloadsActivity::class.java))
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
        SavedSiteStore.add(this, SavedSiteStore.TYPE_BOOKMARKS, url)
        toast(getString(R.string.msg_bookmark_saved))
    }

    private fun openSavedSites(type: String) {
        val entries = SavedSiteStore.list(this, type)
        if (entries.isEmpty()) {
            toast(getString(if (type == SavedSiteStore.TYPE_HISTORY) R.string.msg_no_history else R.string.msg_no_bookmarks))
            return
        }
        savedSitesLauncher.launch(SavedSitesActivity.createIntent(this, type))
    }

    private fun performSearch(query: String) {
        val selectedEngine = SearchEngineManager.selectedEngine(this)
        val url = SearchEngineManager.buildSearchUrl(selectedEngine, query)
        loadWebPage(url)
    }

    private fun loadUrlOrVideo(url: String) {
        if (isVideoUrl(url)) {
            startActivity(
                Intent(this, VideoPlayerActivity::class.java)
                    .putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, url)
            )
        } else {
            loadWebPage(url)
        }
    }

    private fun loadWebPage(url: String) {
        val headers = buildRequestHeaders()
        if (headers.isEmpty()) {
            webView.loadUrl(url)
        } else {
            webView.loadUrl(url, headers)
        }
    }

    private fun reloadCurrentPage() {
        val currentUrl = webView.url
        if (currentUrl.isNullOrBlank()) {
            webView.reload()
            return
        }
        webView.stopLoading()
        webView.clearCache(false)
        loadWebPage(currentUrl)
    }

    private fun buildRequestHeaders(): Map<String, String> {
        val userAgent = webView.settings.userAgentString ?: return emptyMap()
        return mapOf("User-Agent" to userAgent)
    }

    private fun syncTabSession(url: String) {
        val previousUrl = currentTabUrl
        if (previousUrl == null) {
            TabSessionStore.add(this, url)
        } else if (previousUrl != url) {
            TabSessionStore.replace(this, previousUrl, url)
        }
        currentTabUrl = url
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
        webView.settings.domStorageEnabled = true
        webView.settings.useWideViewPort = desktopMode
        webView.settings.loadWithOverviewMode = desktopMode
        webView.settings.userAgentString = if (desktopMode) {
            DESKTOP_USER_AGENT
        } else {
            defaultUserAgent ?: webView.settings.userAgentString
        }

        if (reloadPage && webView.url != null) {
            reloadCurrentPage()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_QUERY = "extra_query"
        const val EXTRA_URL = "extra_url"

        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}

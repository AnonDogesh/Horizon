package com.dean.browservault

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.net.URL
import java.util.Locale

class BrowserTabActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var webView: WebView
    private lateinit var searchInput: EditText
    private lateinit var buttonReload: ImageButton
    private lateinit var engineSpinner: Spinner
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingProgress: LinearProgressIndicator
    private lateinit var videoActionButton: FloatingActionButton
    private lateinit var prefs: SharedPreferences

    private val adBlocker = AdBlocker()
    private var isAdBlockEnabled = true
    private var defaultUserAgent: String? = null
    private var isPageLoading = false
    private var isDesktopMode = false
    private var currentTabUrl: String? = null
    private var currentPlayingVideoUrl: String? = null
    @Volatile
    private var lastDetectedMediaUrl: String? = null
    private var lastTouchRawX = 0f
    private var lastTouchRawY = 0f
    private var currentFloatingMenu: PopupWindow? = null
    private val promptedHosts = mutableSetOf<String>()

    private val savedSitesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val url = result.data?.getStringExtra(SavedSitesActivity.EXTRA_SELECTED_URL) ?: return@registerForActivityResult
        loadUrlOrVideo(url)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser_tab)

        prefs = getSharedPreferences(BrowserPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        isDesktopMode = intent.getBooleanExtra(EXTRA_DESKTOP_MODE, false)

        webView = findViewById(R.id.webView)
        searchInput = findViewById(R.id.searchInput)
        buttonReload = findViewById(R.id.buttonReload)
        engineSpinner = findViewById(R.id.spinnerSearchEngineBrowser)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        loadingProgress = findViewById(R.id.loadingProgress)
        videoActionButton = findViewById(R.id.buttonVideoActions)

        setupSearchEngineSpinner()
        configureWebView()
        applyBrowserSettings(reloadPage = false)
        setupTopBar()
        setupBottomBar()
        setupBackNavigation()
        setupSwipeRefresh()
        setupLongPressActions()
        setupVideoActionButton()
        updateVideoActionButton()

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
        currentFloatingMenu?.dismiss()
        super.onStop()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key in setOf(BrowserPreferences.KEY_AD_BLOCKER, BrowserPreferences.KEY_JAVASCRIPT)) {
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
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        webView.addJavascriptInterface(BrowserJsBridge(), JS_BRIDGE_NAME)
        webView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                lastTouchRawX = event.rawX
                lastTouchRawY = event.rawY
            }
            false
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
                if (isLikelyMediaRequest(request)) {
                    recordDetectedMediaUrl(requestUrl)
                }
                return if (isAdBlockEnabled && adBlocker.isAdUrl(requestUrl)) {
                    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                } else {
                    super.shouldInterceptRequest(view, request)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false

                val target = request.url.toString()
                if (isVideoUrl(target)) {
                    openNativeVideoPlayer(target)
                    return true
                }

                val scheme = request.url.scheme.orEmpty().lowercase(Locale.US)
                if (scheme in setOf("http", "https")) {
                    loadWebPage(target)
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isPageLoading = true
                currentPlayingVideoUrl = null
                lastDetectedMediaUrl = null
                updateVideoActionButton()
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
                    injectVideoObserver()
                    refreshPlayingVideoStateFromPage()
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
        swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            webView.scrollY > 0 || webView.canScrollVertically(-1)
        }
        swipeRefresh.setOnRefreshListener {
            if (webView.scrollY == 0 && !webView.canScrollVertically(-1)) {
                reloadCurrentPage()
            } else {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun setupLongPressActions() {
        webView.setOnLongClickListener {
            val hitResult = webView.hitTestResult ?: return@setOnLongClickListener false
            val mediaUrl = when (hitResult.type) {
                WebView.HitTestResult.IMAGE_TYPE,
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> hitResult.extra
                else -> null
            }

            if (mediaUrl.isNullOrBlank() || !looksLikeImageAsset(mediaUrl)) {
                return@setOnLongClickListener false
            }

            showMediaMenu(mediaUrl, lastTouchRawX.toInt(), lastTouchRawY.toInt())
            true
        }
    }

    private fun setupVideoActionButton() {
        videoActionButton.setOnClickListener {
            if (resolveActionableVideoUrl().isNullOrBlank()) {
                toast(getString(R.string.msg_no_active_video))
            } else {
                showVideoMenu(videoActionButton)
            }
        }
    }

    private fun updateVideoActionButton() {
        val hasVideo = !resolveActionableVideoUrl().isNullOrBlank()
        videoActionButton.alpha = if (hasVideo) 1f else 0.45f
        videoActionButton.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(if (hasVideo) "#C7D0A5" else "#2A3126")
        )
        videoActionButton.imageTintList = ColorStateList.valueOf(
            Color.parseColor(if (hasVideo) "#11140F" else "#AAB58A")
        )
    }

    private fun showMediaMenu(url: String, rawX: Int, rawY: Int) {
        showFloatingGridMenu(
            actions = listOf(
                MenuAction(R.string.action_view_in_new_tab) {
                    startActivity(Intent(this, BrowserTabActivity::class.java).putExtra(EXTRA_URL, url))
                },
                MenuAction(R.string.action_share) { shareMedia(url) },
                MenuAction(R.string.action_download) { downloadMedia(url) },
                MenuAction(R.string.action_close) {}
            ),
            rawX = rawX,
            rawY = rawY,
            anchorView = null
        )
    }

    private fun showVideoMenu(anchor: View) {
        showFloatingGridMenu(
            actions = listOf(
                MenuAction(R.string.action_watch) { watchCurrentVideo() },
                MenuAction(R.string.action_download) {
                    val url = resolveActionableVideoUrl()
                    if (url.isNullOrBlank()) {
                        toast(getString(R.string.msg_no_active_video))
                    } else {
                        downloadMedia(url)
                    }
                }
            ),
            rawX = null,
            rawY = null,
            anchorView = anchor
        )
    }

    private fun showFloatingGridMenu(
        actions: List<MenuAction>,
        rawX: Int?,
        rawY: Int?,
        anchorView: View?
    ) {
        currentFloatingMenu?.dismiss()

        val menuView = createFloatingMenuView(actions)
        val popupWindow = PopupWindow(menuView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popupWindow.isOutsideTouchable = true
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        menuView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)

        val popupWidth = menuView.measuredWidth
        val popupHeight = menuView.measuredHeight
        val displayMetrics = resources.displayMetrics

        val (x, y) = if (anchorView != null) {
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val anchorX = location[0] + (anchorView.width / 2) - (popupWidth / 2)
            val anchorY = location[1] - popupHeight - dp(12)
            clampPopupPosition(anchorX, anchorY, popupWidth, popupHeight, displayMetrics.widthPixels, displayMetrics.heightPixels)
        } else {
            val touchX = rawX ?: displayMetrics.widthPixels / 2
            val touchY = rawY ?: displayMetrics.heightPixels / 2
            clampPopupPosition(
                touchX - (popupWidth / 2),
                touchY - (popupHeight / 2),
                popupWidth,
                popupHeight,
                displayMetrics.widthPixels,
                displayMetrics.heightPixels
            )
        }

        popupWindow.showAtLocation(window.decorView, Gravity.NO_GRAVITY, x, y)
        currentFloatingMenu = popupWindow
    }

    private fun clampPopupPosition(
        x: Int,
        y: Int,
        popupWidth: Int,
        popupHeight: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Pair<Int, Int> {
        val clampedX = x.coerceIn(dp(8), (screenWidth - popupWidth - dp(8)).coerceAtLeast(dp(8)))
        val clampedY = y.coerceIn(dp(8), (screenHeight - popupHeight - dp(8)).coerceAtLeast(dp(8)))
        return clampedX to clampedY
    }

    private fun createFloatingMenuView(actions: List<MenuAction>): View {
        val menuBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(Color.parseColor("#1B2218"))
            setStroke(dp(1), Color.parseColor("#2F3A2A"))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = menuBackground
            elevation = dp(12).toFloat()
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val grid = GridLayout(this).apply {
            columnCount = 2
        }

        actions.forEach { action ->
            grid.addView(
                TextView(this).apply {
                    text = getString(action.labelRes)
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#E6ECD7"))
                    setBackgroundResource(android.R.color.transparent)
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    minWidth = dp(88)
                    minHeight = dp(72)
                    setOnClickListener {
                        currentFloatingMenu?.dismiss()
                        action.onClick()
                    }
                }
            )
        }

        container.addView(grid)
        return container
    }

    private fun watchCurrentVideo() {
        val url = resolveActionableVideoUrl()
        if (url.isNullOrBlank() || !isDirectPlayableMediaUrl(url)) {
            toast(getString(R.string.msg_video_action_unavailable))
            return
        }
        pauseWebVideos()
        openNativeVideoPlayer(url)
    }

    private fun openNativeVideoPlayer(url: String) {
        startActivity(
            Intent(this, VideoPlayerActivity::class.java)
                .putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, url)
        )
    }

    private fun shareMedia(url: String) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                },
                getString(R.string.action_share)
            )
        )
    }

    private fun downloadMedia(url: String) {
        if (!isDownloadableMediaUrl(url)) {
            toast(getString(R.string.msg_media_download_failed))
            return
        }
        Thread {
            val result = runCatching {
                val downloadsDir = FileUtils.ensureDownloadsDirectory(this)
                val fileName = FileUtils.filenameFromUrl(url, "media")
                val destination = FileUtils.createUniqueFile(downloadsDir, fileName)
                URL(url).openStream().use { input ->
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }
                destination
            }
            runOnUiThread {
                result
                    .onSuccess { file -> toast(getString(R.string.msg_media_downloaded, file.name)) }
                    .onFailure { toast(getString(R.string.msg_media_download_failed)) }
            }
        }.start()
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
            setPadding(dp(20), dp(12), dp(20), 0)
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

    private fun injectVideoObserver() {
        webView.evaluateJavascript(
            """
            (function() {
              var bridge = window.$JS_BRIDGE_NAME;
              if (!bridge) return;
              function resolveSrc(video) {
                if (!video) return '';
                var src = video.currentSrc || video.src || '';
                if (src) return src;
                var source = video.querySelector('source[src]');
                return source ? (source.src || source.getAttribute('src') || '') : '';
              }
              function notify(video, state) {
                var src = resolveSrc(video);
                bridge.onVideoState(src, state);
              }
              function attach(video) {
                if (!video || video.__horizonAttached) return;
                video.__horizonAttached = true;
                video.addEventListener('play', function() { notify(video, 'play'); });
                video.addEventListener('playing', function() { notify(video, 'play'); });
                video.addEventListener('pause', function() { notify(video, 'pause'); });
                video.addEventListener('ended', function() { notify(video, 'pause'); });
                if (!video.paused && !video.ended) {
                  notify(video, 'play');
                }
              }
              document.querySelectorAll('video').forEach(attach);
              if (!window.__horizonVideoObserver) {
                window.__horizonVideoObserver = new MutationObserver(function() {
                  document.querySelectorAll('video').forEach(attach);
                });
                window.__horizonVideoObserver.observe(document.documentElement, { childList: true, subtree: true });
              }
            })();
            """.trimIndent(),
            null
        )
    }

    private fun refreshPlayingVideoStateFromPage() {
        webView.evaluateJavascript(
            """
            (function() {
              var video = Array.from(document.querySelectorAll('video')).find(function(item) {
                return !item.paused && !item.ended;
              });
              if (!video) return '';
              var src = video.currentSrc || video.src || '';
              if (src) return src;
              var source = video.querySelector('source[src]');
              return source ? (source.src || source.getAttribute('src') || '') : '';
            })();
            """.trimIndent()
        ) { rawValue ->
            currentPlayingVideoUrl = parseJavascriptString(rawValue)
            updateVideoActionButton()
        }
    }

    private fun pauseWebVideos() {
        webView.evaluateJavascript(
            """
            (function() {
              document.querySelectorAll('video').forEach(function(video) {
                try { video.pause(); } catch (e) {}
              });
            })();
            """.trimIndent(),
            null
        )
        currentPlayingVideoUrl = null
        updateVideoActionButton()
    }

    private fun parseJavascriptString(rawValue: String?): String? {
        val value = rawValue.orEmpty().trim()
        if (value.isBlank() || value == "null" || value == "\"\"") return null
        return value.removeSurrounding("\"")
            .replace("\\/", "/")
            .replace("\\u003C", "<")
            .replace("\\n", "")
            .takeIf { it.isNotBlank() }
    }

    private fun looksLikeAuthPage(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return lower.contains("login") || lower.contains("signin") || lower.contains("signup") || lower.contains("register")
    }

    private fun looksLikeImageAsset(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp").any(lower::contains)
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
        desktopSwitch.isChecked = isDesktopMode
        desktopSwitch.setOnCheckedChangeListener { _, checked ->
            setDesktopModeEnabled(checked)
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
            openNativeVideoPlayer(url)
        } else {
            loadWebPage(url)
        }
    }

    private fun loadWebPage(url: String) {
        val normalizedUrl = normalizeUrlForMode(url)
        val headers = buildRequestHeaders()
        if (headers.isEmpty()) {
            webView.loadUrl(normalizedUrl)
        } else {
            webView.loadUrl(normalizedUrl, headers)
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
        return mapOf(
            "User-Agent" to userAgent,
            "X-Requested-With" to ""
        )
    }

    private fun normalizeUrlForMode(url: String): String {
        if (!isDesktopMode) return url
        val uri = Uri.parse(url)
        val host = uri.host.orEmpty().lowercase(Locale.US)
        if (host == "m.youtube.com" || host == "youtube.com") {
            return uri.buildUpon().authority("www.youtube.com").build().toString()
        }
        return url
    }

    private fun syncTabSession(url: String) {
        TabSessionStore.upsert(this, currentTabUrl, url, isDesktopMode)
        currentTabUrl = url
    }

    private fun setDesktopModeEnabled(enabled: Boolean) {
        if (isDesktopMode == enabled) return
        isDesktopMode = enabled
        applyBrowserSettings(reloadPage = true)
        val currentUrl = webView.url ?: currentTabUrl
        if (!currentUrl.isNullOrBlank()) {
            syncTabSession(currentUrl)
        }
    }

    private fun isVideoUrl(url: String): Boolean {
        return looksLikeMediaAssetUrl(url)
    }

    private fun isDirectPlayableMediaUrl(url: String): Boolean {
        if (url.startsWith("blob:")) return false
        val scheme = Uri.parse(url).scheme.orEmpty().lowercase(Locale.US)
        if (scheme !in setOf("http", "https", "content", "file")) return false
        return looksLikeMediaAssetUrl(url)
    }

    private fun isDownloadableMediaUrl(url: String): Boolean {
        if (url.startsWith("blob:")) return false
        val scheme = Uri.parse(url).scheme.orEmpty().lowercase(Locale.US)
        if (scheme !in setOf("http", "https")) return false
        return looksLikeMediaAssetUrl(url)
    }

    private fun resolveActionableVideoUrl(): String? {
        val primary = currentPlayingVideoUrl?.takeIf { isDirectPlayableMediaUrl(it) }
        if (!primary.isNullOrBlank()) return primary
        return lastDetectedMediaUrl?.takeIf { isDirectPlayableMediaUrl(it) }
    }

    private fun isLikelyMediaRequest(request: WebResourceRequest): Boolean {
        val requestUrl = request.url.toString()
        if (looksLikeMediaAssetUrl(requestUrl)) return true
        val accept = request.requestHeaders["Accept"].orEmpty().lowercase(Locale.US)
        return accept.contains("video/") || accept.contains("application/vnd.apple.mpegurl")
    }

    private fun recordDetectedMediaUrl(url: String) {
        if (!isDirectPlayableMediaUrl(url)) return
        if (url == lastDetectedMediaUrl) return
        lastDetectedMediaUrl = url
        runOnUiThread { updateVideoActionButton() }
    }

    private fun looksLikeMediaAssetUrl(url: String): Boolean {
        val sanitized = url.substringBefore('#').substringBefore('?').lowercase(Locale.US)
        return sanitized.endsWith(".mp4") ||
            sanitized.endsWith(".m3u8") ||
            sanitized.endsWith(".webm") ||
            sanitized.endsWith(".mkv") ||
            sanitized.endsWith(".m4v") ||
            sanitized.endsWith(".mov")
    }

    @Suppress("SetJavaScriptEnabled")
    private fun applyBrowserSettings(reloadPage: Boolean) {
        val jsEnabled = prefs.getBoolean(BrowserPreferences.KEY_JAVASCRIPT, true)
        isAdBlockEnabled = prefs.getBoolean(BrowserPreferences.KEY_AD_BLOCKER, true)

        webView.settings.javaScriptEnabled = jsEnabled
        webView.settings.domStorageEnabled = true
        webView.settings.useWideViewPort = isDesktopMode
        webView.settings.loadWithOverviewMode = isDesktopMode
        webView.settings.userAgentString = if (isDesktopMode) {
            DESKTOP_USER_AGENT
        } else {
            defaultUserAgent ?: webView.settings.userAgentString
        }

        if (reloadPage && webView.url != null) {
            reloadCurrentPage()
        }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics
    ).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private data class MenuAction(
        val labelRes: Int,
        val onClick: () -> Unit
    )

    private inner class BrowserJsBridge {
        @JavascriptInterface
        fun onVideoState(url: String?, state: String?) {
            runOnUiThread {
                if (state == "play") {
                    currentPlayingVideoUrl = url?.takeIf { it.isNotBlank() }
                    updateVideoActionButton()
                } else {
                    refreshPlayingVideoStateFromPage()
                }
            }
        }
    }

    companion object {
        const val EXTRA_QUERY = "extra_query"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_DESKTOP_MODE = "extra_desktop_mode"

        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val JS_BRIDGE_NAME = "HorizonBridge"
    }
}

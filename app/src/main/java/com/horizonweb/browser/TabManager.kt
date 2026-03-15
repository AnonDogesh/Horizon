package com.horizonweb.browser

import android.net.Uri
import com.horizonweb.download.DownloadManager
import javax.inject.Inject
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.GeckoSession

@ViewModelScoped
class TabManager @Inject constructor(
    private val downloadManager: DownloadManager,
    runtimeProvider: GeckoRuntimeProvider
) {
    private val runtime = runtimeProvider.runtime

    private val sessions = LinkedHashMap<Long, GeckoSession>()
    private val tabStates = LinkedHashMap<Long, BrowserTab>()
    private val tabBackState = LinkedHashMap<Long, Boolean>()
    private val tabForwardState = LinkedHashMap<Long, Boolean>()

    private var nextId = 1L

    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<Long?>(null)
    val activeTabId: StateFlow<Long?> = _activeTabId.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    private val _activeUrl = MutableStateFlow(DEFAULT_URL)
    val activeUrl: StateFlow<String> = _activeUrl.asStateFlow()

    private val _privateBrowsingEnabled = MutableStateFlow(false)
    val privateBrowsingEnabled: StateFlow<Boolean> = _privateBrowsingEnabled.asStateFlow()

    private val _trackerBlockingEnabled = MutableStateFlow(true)
    val trackerBlockingEnabled: StateFlow<Boolean> = _trackerBlockingEnabled.asStateFlow()

    fun ensureInitialTab(restoredActiveTabId: Long?) {
        if (sessions.isNotEmpty()) {
            if (restoredActiveTabId != null && sessions.containsKey(restoredActiveTabId)) {
                switchTab(restoredActiveTabId)
            }
            return
        }

        openNewTab(initialUrl = DEFAULT_URL, switchToNew = true)
    }

    fun openNewTab(initialUrl: String = DEFAULT_URL, switchToNew: Boolean = true): Long {
        val tabId = nextId++
        val session = GeckoSession()
        configureSession(tabId, session)
        session.open(runtime)

        val normalized = normalizeUrl(initialUrl)
        tabStates[tabId] = BrowserTab(
            tabId = tabId,
            title = normalized,
            url = normalized,
            favicon = null,
            isPrivate = _privateBrowsingEnabled.value
        )
        sessions[tabId] = session
        tabBackState[tabId] = false
        tabForwardState[tabId] = false
        publishTabs()

        if (switchToNew) {
            switchTab(tabId)
        }

        session.loadUri(normalized)
        val current = tabStates[tabId]
        if (current != null) {
            tabStates[tabId] = current.copy(url = normalized)
            _activeUrl.value = normalized
            publishTabs()
        }
        return tabId
    }

    fun closeTab(tabId: Long) {
        val removedSession = sessions.remove(tabId) ?: return
        destroySession(removedSession)

        tabStates.remove(tabId)
        tabBackState.remove(tabId)
        tabForwardState.remove(tabId)
        publishTabs()

        if (_activeTabId.value == tabId) {
            val replacement = sessions.keys.lastOrNull()
            if (replacement == null) {
                openNewTab(switchToNew = true)
            } else {
                switchTab(replacement)
            }
        }
    }

    fun switchTab(tabId: Long) {
        if (!sessions.containsKey(tabId)) return

        _activeTabId.value = tabId
        _activeUrl.value = tabStates[tabId]?.url ?: DEFAULT_URL
        _canGoBack.value = tabBackState[tabId] ?: false
        _canGoForward.value = tabForwardState[tabId] ?: false
    }

    fun activeSession(): GeckoSession? = _activeTabId.value?.let { sessions[it] }

    fun setActive(active: Boolean) {
        activeSession()?.setActive(active)
    }

    fun loadUrl(urlInput: String) {
        val tabId = _activeTabId.value ?: return
        val session = sessions[tabId] ?: return
        val normalized = normalizeUrl(urlInput)
        if (normalized.startsWith("file://") && normalized.contains("/files/vault/")) {
            return
        }
        if (_trackerBlockingEnabled.value && isLikelyTracker(normalized)) {
            return
        }

        tabStates[tabId] = tabStates[tabId]?.copy(url = normalized, title = normalized) ?: BrowserTab(
            tabId = tabId,
            title = normalized,
            url = normalized,
            favicon = null,
            isPrivate = _privateBrowsingEnabled.value
        )
        _activeUrl.value = normalized
        publishTabs()
        session.loadUri(normalized)
        val current = tabStates[tabId]
        if (current != null) {
            tabStates[tabId] = current.copy(url = normalized)
            _activeUrl.value = normalized
            publishTabs()
        }
    }

    fun reload() {
        activeSession()?.reload()
    }

    fun goBack() {
        val tabId = _activeTabId.value ?: return
        if (tabBackState[tabId] == true) {
            activeSession()?.goBack()
        }
    }

    fun goForward() {
        val tabId = _activeTabId.value ?: return
        if (tabForwardState[tabId] == true) {
            activeSession()?.goForward()
        }
    }

    fun setPrivateBrowsingEnabled(enabled: Boolean) {
        _privateBrowsingEnabled.value = enabled
        if (enabled) {
            clearCookies()
            clearBrowsingHistory()
        } else {
            closePrivateTabs()
            clearCookies()
        }
    }

    fun setTrackerBlockingEnabled(enabled: Boolean) {
        _trackerBlockingEnabled.value = enabled
    }

    fun clearCookies() {
        // Best-effort clear via GeckoRuntime reflection to stay compatible across GeckoView versions.
        runCatching {
            val controller = runtime.javaClass.methods
                .firstOrNull { it.name == "getStorageController" }
                ?.invoke(runtime)
            controller?.javaClass?.methods
                ?.firstOrNull { it.name == "clearData" && it.parameterCount == 0 }
                ?.invoke(controller)
        }
    }

    fun clearCache() {
        runCatching {
            val storage = runtime.javaClass.methods
                .firstOrNull { it.name == "getStorageController" }
                ?.invoke(runtime)
            storage?.javaClass?.methods
                ?.firstOrNull { it.name == "clearData" && it.parameterCount == 0 }
                ?.invoke(storage)
        }
    }

    fun clearBrowsingHistory() {
        sessions.values.forEach { it.loadUri("about:blank") }
        tabStates.keys.toList().forEach { id ->
            val current = tabStates[id] ?: return@forEach
            tabStates[id] = current.copy(title = "about:blank", url = "about:blank")
            tabBackState[id] = false
            tabForwardState[id] = false
        }
        _canGoBack.value = false
        _canGoForward.value = false
        _activeUrl.value = "about:blank"
        publishTabs()
    }

    fun closeAll() {
        sessions.values.forEach(::destroySession)
        sessions.clear()
        tabStates.clear()
        tabBackState.clear()
        tabForwardState.clear()
        _tabs.value = emptyList()
        _activeTabId.value = null
        _canGoBack.value = false
        _canGoForward.value = false
    }

    private fun closePrivateTabs() {
        val privateTabIds = tabStates.values.filter { it.isPrivate }.map { it.tabId }
        privateTabIds.forEach { closeTab(it) }
        if (_activeTabId.value == null) {
            openNewTab(switchToNew = true)
        }
    }

    private fun configureSession(tabId: Long, session: GeckoSession) {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tabBackState[tabId] = canGoBack
                if (_activeTabId.value == tabId) {
                    _canGoBack.value = canGoBack
                }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tabForwardState[tabId] = canGoForward
                if (_activeTabId.value == tabId) {
                    _canGoForward.value = canGoForward
                }
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {}
        session.contentDelegate = object : GeckoSession.ContentDelegate {}
    }

    private fun destroySession(session: GeckoSession) {
        session.navigationDelegate = null
        session.progressDelegate = null
        session.contentDelegate = null
        session.close()
    }

    private fun publishTabs() {
        _tabs.value = tabStates.values.toList()
    }

    private fun isLikelyTracker(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
        val trackerDomains = listOf("doubleclick", "google-analytics", "ads", "tracker", "metrics")
        return trackerDomains.any { host.contains(it, ignoreCase = true) }
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("about:")) {
            return trimmed
        }
        return "https://$trimmed"
    }

    companion object {
        const val DEFAULT_URL = "https://www.mozilla.org"
    }
}

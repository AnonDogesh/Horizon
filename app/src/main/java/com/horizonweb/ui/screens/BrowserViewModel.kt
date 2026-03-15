package com.horizonweb.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.horizonweb.browser.BrowserTab
import com.horizonweb.browser.TabManager
import com.horizonweb.data.VaultFileEntity
import com.horizonweb.data.VaultRepository
import com.horizonweb.download.DownloadItem
import com.horizonweb.extensions.ExtensionItem
import com.horizonweb.extensions.ExtensionManager
import com.horizonweb.download.DownloadManager
import com.horizonweb.security.SecurityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val tabManager: TabManager,
    private val downloadManager: DownloadManager,
    private val vaultRepository: VaultRepository,
    private val extensionManager: ExtensionManager,
    private val vaultSecurityManager: SecurityManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    val tabs: StateFlow<List<BrowserTab>> = tabManager.tabs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val activeTabId: StateFlow<Long?> = tabManager.activeTabId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = savedStateHandle[KEY_ACTIVE_TAB_ID]
    )

    val currentUrl: StateFlow<String> = tabManager.activeUrl.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TabManager.DEFAULT_URL
    )

    val canGoBack: StateFlow<Boolean> = tabManager.canGoBack.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    val privateBrowsingEnabled: StateFlow<Boolean> = tabManager.privateBrowsingEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    val trackerBlockingEnabled: StateFlow<Boolean> = tabManager.trackerBlockingEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true
    )

    val canGoForward: StateFlow<Boolean> = tabManager.canGoForward.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    val downloads: StateFlow<List<DownloadItem>> = downloadManager.downloads.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val vaultFiles: StateFlow<List<VaultFileEntity>> = vaultRepository.observeVaultFiles().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val extensions: StateFlow<List<ExtensionItem>> = extensionManager.extensions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val extensionMessage: StateFlow<String?> = extensionManager.message.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val isVaultLocked: StateFlow<Boolean> = vaultSecurityManager.isVaultLocked.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true
    )

    private val _viewerTempPath = MutableStateFlow<String?>(null)
    val viewerTempPath: StateFlow<String?> = _viewerTempPath.asStateFlow()

    private val _viewerMimeType = MutableStateFlow<String?>(null)
    val viewerMimeType: StateFlow<String?> = _viewerMimeType.asStateFlow()

    private val _thumbnailCache = MutableStateFlow<Map<Long, Bitmap>>(emptyMap())
    val thumbnailCache: StateFlow<Map<Long, Bitmap>> = _thumbnailCache.asStateFlow()

    init {
        tabManager.ensureInitialTab(savedStateHandle[KEY_ACTIVE_TAB_ID])
    }

    fun session() = tabManager.activeSession()

    fun setActive(active: Boolean) = tabManager.setActive(active)

    fun loadUrl(url: String) = tabManager.loadUrl(url)

    fun openNewTab(url: String = TabManager.DEFAULT_URL) {
        val id = tabManager.openNewTab(initialUrl = url, switchToNew = true)
        savedStateHandle[KEY_ACTIVE_TAB_ID] = id
    }

    fun closeTab(tabId: Long) {
        tabManager.closeTab(tabId)
        savedStateHandle[KEY_ACTIVE_TAB_ID] = tabManager.activeTabId.value
    }

    fun switchTab(tabId: Long) {
        tabManager.switchTab(tabId)
        savedStateHandle[KEY_ACTIVE_TAB_ID] = tabManager.activeTabId.value
    }

    fun goBack() = tabManager.goBack()

    fun goForward() = tabManager.goForward()

    fun reload() = tabManager.reload()

    fun setPrivateBrowsingEnabled(enabled: Boolean) = tabManager.setPrivateBrowsingEnabled(enabled)

    fun setTrackerBlockingEnabled(enabled: Boolean) = tabManager.setTrackerBlockingEnabled(enabled)

    fun clearCookies() = tabManager.clearCookies()

    fun clearBrowsingHistory() = tabManager.clearBrowsingHistory()

    fun clearCache() = tabManager.clearCache()

    fun cancelDownload(downloadId: Long) = downloadManager.cancelDownload(downloadId)

    fun markDownloadHandled(downloadId: Long) = downloadManager.markDownloadHandled(downloadId)

    fun moveDownloadToVault(downloadId: Long) {
        viewModelScope.launch {
            val item = downloadManager.findDownload(downloadId) ?: return@launch
            val localPath = item.localPath ?: run {
                downloadManager.markDownloadHandled(downloadId)
                return@launch
            }
            vaultRepository.importDownloadedFile(
                path = localPath,
                originalName = item.fileName,
                mimeType = item.mimeType
            )
            downloadManager.markMovedToVault(downloadId)
        }
    }

    fun importVaultFile(uri: Uri) {
        viewModelScope.launch {
            vaultRepository.importFile(uri)
        }
    }

    fun deleteVaultFile(file: VaultFileEntity) {
        viewModelScope.launch {
            vaultRepository.deleteVaultFile(file)
            _thumbnailCache.value = _thumbnailCache.value - file.id
        }
    }

    fun requestThumbnail(file: VaultFileEntity) {
        if (_thumbnailCache.value.containsKey(file.id)) return
        viewModelScope.launch {
            val bitmap = vaultRepository.loadThumbnail(file, THUMBNAIL_SIZE_PX) ?: return@launch
            _thumbnailCache.value = _thumbnailCache.value + (file.id to bitmap)
        }
    }

    fun openVaultFile(file: VaultFileEntity) {
        viewModelScope.launch {
            clearViewerTempFile()
            val temp = vaultRepository.decryptToTempFile(file)
            _viewerTempPath.value = temp.absolutePath
            _viewerMimeType.value = file.fileType
        }
    }

    fun clearViewerTempFile() {
        vaultRepository.deleteTempFile(_viewerTempPath.value)
        _viewerTempPath.value = null
        _viewerMimeType.value = null
    }

    fun installExtensionFromUrl(url: String) = extensionManager.installFromUrl(url)

    fun refreshExtensions() = extensionManager.refreshInstalledExtensions()

    fun toggleExtension(id: String, enabled: Boolean) = extensionManager.setEnabled(id, enabled)

    fun uninstallExtension(id: String) = extensionManager.uninstall(id)

    fun hasVaultPin(): Boolean = vaultSecurityManager.hasPin()

    fun setVaultPin(pin: String) = vaultSecurityManager.setPin(pin)

    fun unlockVaultWithPin(pin: String): Boolean = vaultSecurityManager.verifyPin(pin)

    fun unlockVaultWithBiometric() = vaultSecurityManager.unlockWithBiometric()

    fun lockVaultNow() = vaultSecurityManager.lockNow()

    fun onAppBackgrounded() = vaultSecurityManager.onAppBackgrounded()

    fun onAppForegrounded() = vaultSecurityManager.onAppForegrounded()

    override fun onCleared() {
        clearViewerTempFile()
        tabManager.closeAll()
        super.onCleared()
    }

    companion object {
        private const val KEY_ACTIVE_TAB_ID = "active_tab_id"
        private const val THUMBNAIL_SIZE_PX = 256
    }
}

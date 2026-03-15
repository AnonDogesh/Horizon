package com.horizonweb.extensions

import com.horizonweb.browser.GeckoRuntimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class ExtensionManager @Inject constructor(
    runtimeProvider: GeckoRuntimeProvider
) {
    private val controller = runtimeProvider.runtime.webExtensionController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val extensionRefs = mutableMapOf<String, WebExtension>()

    private val _extensions = MutableStateFlow<List<ExtensionItem>>(emptyList())
    val extensions: StateFlow<List<ExtensionItem>> = _extensions.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refreshInstalledExtensions()
    }

    fun installFromUrl(url: String) {
        scope.launch {
            try {
                val normalized = url.trim()
                require(normalized.startsWith("https://") && normalized.endsWith(".xpi")) {
                    "Extension URL must be HTTPS and end with .xpi"
                }
                await(controller.install(normalized))
                _message.value = "Extension installed"
                refreshInstalledExtensions()
            } catch (t: Throwable) {
                _message.value = t.message ?: "Failed to install extension"
            }
        }
    }

    fun refreshInstalledExtensions() {
        scope.launch {
            try {
                val list = await(controller.list())
                extensionRefs.clear()
                _extensions.value = list.map { ext ->
                    extensionRefs[ext.id] = ext
                    ExtensionItem(
                        id = ext.id,
                        name = ext.metaData.name ?: ext.id,
                        version = ext.metaData.version ?: "unknown",
                        enabled = ext.metaData.enabled
                    )
                }
            } catch (t: Throwable) {
                _message.value = t.message ?: "Failed to list extensions"
            }
        }
    }

    fun setEnabled(extensionId: String, enabled: Boolean) {
        scope.launch {
            val extension = extensionRefs[extensionId] ?: return@launch
            try {
                setExtensionEnabled(extension, enabled)
                refreshInstalledExtensions()
            } catch (t: Throwable) {
                _message.value = t.message ?: "Failed to update extension state"
            }
        }
    }


    private suspend fun setExtensionEnabled(extension: WebExtension, enabled: Boolean) {
        val methodName = if (enabled) "enable" else "disable"
        val methods = controller.javaClass.methods.filter { it.name == methodName }
        val target = methods.firstOrNull { it.parameterTypes.size >= 1 } ?: error("No $methodName method")
        val args = buildList {
            add(extension)
            repeat(target.parameterTypes.size - 1) {
                add(null)
            }
        }.toTypedArray()

        @Suppress("UNCHECKED_CAST")
        val result = target.invoke(controller, *args) as? GeckoResult<Any?>
            ?: error("$methodName did not return GeckoResult")
        await(result)
    }

    fun uninstall(extensionId: String) {
        scope.launch {
            val extension = extensionRefs[extensionId] ?: return@launch
            try {
                await(controller.uninstall(extension))
                _message.value = "Extension uninstalled"
                refreshInstalledExtensions()
            } catch (t: Throwable) {
                _message.value = t.message ?: "Failed to uninstall extension"
            }
        }
    }

    private suspend fun <T> await(result: GeckoResult<T>): T = suspendCancellableCoroutine { cont ->
        result.accept(
            { value ->
                if (cont.isActive) {
                    @Suppress("UNCHECKED_CAST")
                    cont.resume(value as T)
                }
            },
            { throwable ->
                if (cont.isActive) {
                    cont.resumeWithException(throwable ?: RuntimeException("Unknown GeckoResult error"))
                }
            }
        )
    }
}

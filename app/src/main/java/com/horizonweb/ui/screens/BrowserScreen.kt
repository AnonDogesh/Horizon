package com.horizonweb.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mozilla.geckoview.GeckoView

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onOpenTabs: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentUrl by viewModel.currentUrl.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val canGoForward by viewModel.canGoForward.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()

    var urlInput by remember(currentUrl) { mutableStateOf(currentUrl) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setActive(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setActive(false)
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.setActive(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setActive(false)
        }
    }

    BackHandler(enabled = canGoBack) { viewModel.goBack() }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BrowserToolbar(
                url = urlInput,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                tabCount = tabs.size,
                onUrlChange = { urlInput = it },
                onUrlSubmit = { viewModel.loadUrl(urlInput) },
                onBack = viewModel::goBack,
                onForward = viewModel::goForward,
                onReload = viewModel::reload,
                onNewTab = { viewModel.openNewTab() },
                onShowTabs = onOpenTabs,
                onShowDownloads = onOpenDownloads,
                onShowVault = onOpenVault,
                onShowExtensions = onOpenExtensions,
                onShowPrivacy = onOpenPrivacy
            )

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    GeckoView(context).apply {
                        viewModel.session()?.let(::setSession)
                    }
                },
                update = { geckoView ->
                    viewModel.session()?.let(geckoView::setSession)
                }
            )
        }
    }
}

@Composable
private fun BrowserToolbar(
    url: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    onUrlChange: (String) -> Unit,
    onUrlSubmit: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onNewTab: () -> Unit,
    onShowTabs: () -> Unit,
    onShowDownloads: () -> Unit,
    onShowVault: () -> Unit,
    onShowExtensions: () -> Unit,
    onShowPrivacy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = onBack, enabled = canGoBack) { Text("Back") }
        Button(onClick = onForward, enabled = canGoForward) { Text("Forward") }
        Button(onClick = onReload) { Text("Reload") }
        Button(onClick = onNewTab) { Text("+") }
        Button(onClick = onShowTabs) { Text("Tabs ($tabCount)") }
        Button(onClick = onShowDownloads) { Text("Downloads") }
        Button(onClick = onShowVault) { Text("Vault") }
        Button(onClick = onShowExtensions) { Text("Extensions") }
        Button(onClick = onShowPrivacy) { Text("Privacy") }
    }

    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        value = url,
        onValueChange = onUrlChange,
        singleLine = true,
        label = { Text("URL") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onUrlSubmit() })
    )
}

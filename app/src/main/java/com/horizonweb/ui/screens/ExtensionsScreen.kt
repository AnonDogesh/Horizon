package com.horizonweb.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ExtensionsScreen(
    viewModel: BrowserViewModel,
    onBackToBrowser: () -> Unit
) {
    val extensions by viewModel.extensions.collectAsStateWithLifecycle()
    val message by viewModel.extensionMessage.collectAsStateWithLifecycle()
    var extensionUrl by remember { mutableStateOf("") }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onBackToBrowser) {
                    Text("Back")
                }
            }

            OutlinedTextField(
                value = extensionUrl,
                onValueChange = { extensionUrl = it },
                label = { Text("Extension .xpi URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.installExtensionFromUrl(extensionUrl) }) {
                    Text("Install")
                }
                Button(onClick = { viewModel.refreshExtensions() }) {
                    Text("Refresh")
                }
            }

            message?.let { Text(it) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(extensions, key = { it.id }) { ext ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ext.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(ext.version)
                                Text(ext.id, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (ext.enabled) "Enabled" else "Disabled")
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = { viewModel.toggleExtension(ext.id, !ext.enabled) }) {
                                    Text(if (ext.enabled) "Disable" else "Enable")
                                }
                                Button(onClick = { viewModel.uninstallExtension(ext.id) }) {
                                    Text("Uninstall")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

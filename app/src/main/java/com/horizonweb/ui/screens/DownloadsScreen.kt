package com.horizonweb.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.horizonweb.download.DownloadItem
import com.horizonweb.download.DownloadStatus

@Composable
fun DownloadsScreen(
    viewModel: BrowserViewModel,
    onBackToBrowser: () -> Unit
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var dialogDownloadId by remember { mutableStateOf<Long?>(null) }

    val pendingActionDownload = downloads.firstOrNull {
        it.status == DownloadStatus.COMPLETED && it.requiresPostDownloadAction
    }

    LaunchedEffect(pendingActionDownload?.id, dialogDownloadId) {
        if (dialogDownloadId == null && pendingActionDownload != null) {
            dialogDownloadId = pendingActionDownload.id
        }
    }

    val activeDialogItem = downloads.firstOrNull { it.id == dialogDownloadId }

    if (activeDialogItem != null && activeDialogItem.requiresPostDownloadAction) {
        DownloadCompleteDialog(
            item = activeDialogItem,
            onSaveNormally = {
                viewModel.markDownloadHandled(activeDialogItem.id)
                dialogDownloadId = null
            },
            onMoveToVault = {
                viewModel.moveDownloadToVault(activeDialogItem.id)
                dialogDownloadId = null
            },
            onDismiss = {
                viewModel.markDownloadHandled(activeDialogItem.id)
                dialogDownloadId = null
            }
        )
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onBackToBrowser) {
                    Text("Back to Browser")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(downloads, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = item.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = "${item.status} • ${item.progress}% • ${item.mimeType}")
                            }
                            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED) {
                                Button(onClick = { viewModel.cancelDownload(item.id) }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadCompleteDialog(
    item: DownloadItem,
    onSaveNormally: () -> Unit,
    onMoveToVault: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download complete") },
        text = { Text("How would you like to keep ${item.fileName}?") },
        confirmButton = {
            Button(onClick = onMoveToVault) {
                Text("Move to vault")
            }
        },
        dismissButton = {
            Button(onClick = onSaveNormally) {
                Text("Save normally")
            }
        }
    )
}

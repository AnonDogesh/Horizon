package com.horizonweb.ui.screens

import android.graphics.BitmapFactory
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun VaultFileViewerScreen(
    viewModel: BrowserViewModel,
    fileId: Long,
    onClose: () -> Unit
) {
    val files by viewModel.vaultFiles.collectAsStateWithLifecycle()
    val tempPath by viewModel.viewerTempPath.collectAsStateWithLifecycle()
    val mimeType by viewModel.viewerMimeType.collectAsStateWithLifecycle()

    val targetFile = files.firstOrNull { it.id == fileId }

    DisposableEffect(fileId, targetFile?.id) {
        targetFile?.let(viewModel::openVaultFile)
        onDispose {
            viewModel.clearViewerTempFile()
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onClose) {
                Text("Close")
            }

            if (tempPath == null || mimeType == null || targetFile == null) {
                Text("Decrypting...")
                return@Column
            }

            Text(targetFile.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(mimeType ?: "unknown")

            if (mimeType?.startsWith("image/") == true) {
                val bitmap = remember(tempPath) { BitmapFactory.decodeFile(tempPath) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = targetFile.originalName,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("Unable to render image")
                }
            } else {
                Text("Preview for this file type is not available in-app.")
                Text(
                    text = "Temporary decrypted file extension: ${MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

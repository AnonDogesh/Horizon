package com.horizonweb.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.horizonweb.data.VaultFileEntity

@Composable
fun VaultGalleryScreen(
    viewModel: BrowserViewModel,
    onOpenFile: (Long) -> Unit,
    onBackToBrowser: () -> Unit
) {
    val vaultFiles by viewModel.vaultFiles.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnailCache.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::importVaultFile)
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { launcher.launch(arrayOf("*/*")) }) {
                    Text("Import File")
                }
                Button(onClick = onBackToBrowser) {
                    Text("Back")
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 128.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vaultFiles, key = { it.id }) { file ->
                    LaunchedEffect(file.id) {
                        viewModel.requestThumbnail(file)
                    }
                    VaultGridItem(
                        file = file,
                        hasThumbnail = thumbnails[file.id] != null,
                        thumbnailBitmap = thumbnails[file.id],
                        onOpen = { onOpenFile(file.id) },
                        onDelete = { viewModel.deleteVaultFile(file) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultGridItem(
    file: VaultFileEntity,
    hasThumbnail: Boolean,
    thumbnailBitmap: android.graphics.Bitmap?,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            if (hasThumbnail && thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap.asImageBitmap(),
                    contentDescription = file.originalName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Encrypted")
                }
            }

            Text(file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(file.fileType, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Button(onClick = onDelete, modifier = Modifier.padding(top = 8.dp)) {
                Text("Delete")
            }
        }
    }
}

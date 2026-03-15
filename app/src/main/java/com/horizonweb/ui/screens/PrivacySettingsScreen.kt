package com.horizonweb.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PrivacySettingsScreen(
    viewModel: BrowserViewModel,
    onBackToBrowser: () -> Unit
) {
    val privateMode by viewModel.privateBrowsingEnabled.collectAsStateWithLifecycle()
    val trackerBlocking by viewModel.trackerBlockingEnabled.collectAsStateWithLifecycle()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onBackToBrowser) {
                Text("Back")
            }

            Text("Privacy Settings")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Private browsing mode")
                Switch(checked = privateMode, onCheckedChange = viewModel::setPrivateBrowsingEnabled)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tracker blocking")
                Switch(checked = trackerBlocking, onCheckedChange = viewModel::setTrackerBlockingEnabled)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::clearCookies) {
                    Text("Clear Cookies")
                }
                Button(onClick = viewModel::clearBrowsingHistory) {
                    Text("Clear History")
                }
            }

            Button(onClick = viewModel::clearCache) {
                Text("Clear Cache")
            }

            Text("Private tabs are closed and cookies are cleared when private mode is turned off.")
        }
    }
}

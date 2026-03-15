package com.horizonweb.ui.screens

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@Composable
fun VaultUnlockScreen(
    viewModel: BrowserViewModel,
    onUnlocked: () -> Unit,
    onBackToBrowser: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Vault is locked")

            if (canUseBiometric(context) && activity != null) {
                Button(
                    onClick = {
                        showBiometricPrompt(
                            activity = activity,
                            onSuccess = {
                                viewModel.unlockVaultWithBiometric()
                                onUnlocked()
                            },
                            onFailure = {
                                message = it
                            }
                        )
                    },
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text("Unlock with biometrics")
                }
            }

            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text(if (viewModel.hasVaultPin()) "Enter PIN" else "Create PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.padding(top = 16.dp)
            )

            Button(
                onClick = {
                    if (pin.length < 4) {
                        message = "PIN must be at least 4 digits"
                        return@Button
                    }
                    if (viewModel.hasVaultPin()) {
                        val ok = viewModel.unlockVaultWithPin(pin)
                        if (ok) {
                            message = null
                            onUnlocked()
                        } else {
                            message = "Incorrect PIN"
                        }
                    } else {
                        viewModel.setVaultPin(pin)
                        message = null
                        onUnlocked()
                    }
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(if (viewModel.hasVaultPin()) "Unlock with PIN" else "Set PIN & Unlock")
            }

            message?.let {
                Text(it, modifier = Modifier.padding(top = 8.dp))
            }

            Button(onClick = onBackToBrowser, modifier = Modifier.padding(top = 8.dp)) {
                Text("Back")
            }
        }
    }
}

private fun canUseBiometric(context: Context): Boolean {
    return BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
    ) == BiometricManager.BIOMETRIC_SUCCESS
}

private fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onFailure(errString.toString())
        }

        override fun onAuthenticationFailed() {
            onFailure("Biometric auth failed. Use PIN.")
        }
    }

    val prompt = BiometricPrompt(activity, executor, callback)
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Vault")
        .setSubtitle("Authenticate to unlock your secure vault")
        .setNegativeButtonText("Use PIN")
        .build()

    prompt.authenticate(promptInfo)
}

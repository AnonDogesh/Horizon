package com.horizonweb.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var autoLockJob: Job? = null

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _isVaultLocked = MutableStateFlow(true)
    val isVaultLocked: StateFlow<Boolean> = _isVaultLocked.asStateFlow()

    fun hasPin(): Boolean = !securePrefs.getString(KEY_PIN, null).isNullOrBlank()

    fun setPin(pin: String) {
        securePrefs.edit().putString(KEY_PIN, pin.hashCode().toString()).apply()
        _isVaultLocked.value = false
    }

    fun verifyPin(pin: String): Boolean {
        val ok = securePrefs.getString(KEY_PIN, null) == pin.hashCode().toString()
        if (ok) {
            _isVaultLocked.value = false
        }
        return ok
    }

    fun unlockWithBiometric() {
        _isVaultLocked.value = false
    }

    fun lockNow() {
        _isVaultLocked.value = true
    }

    fun onAppBackgrounded() {
        autoLockJob?.cancel()
        autoLockJob = scope.launch {
            delay(AUTO_LOCK_MS)
            _isVaultLocked.value = true
        }
    }

    fun onAppForegrounded() {
        autoLockJob?.cancel()
    }

    companion object {
        private const val PREFS_NAME = "vault_security"
        private const val KEY_PIN = "pin_hash"
        private const val AUTO_LOCK_MS = 60_000L
    }
}

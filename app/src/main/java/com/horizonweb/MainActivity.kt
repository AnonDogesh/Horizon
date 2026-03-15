package com.horizonweb

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.horizonweb.security.SecurityManager
import com.horizonweb.ui.navigation.HorizonWebNavHost
import com.horizonweb.ui.theme.HorizonWebTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var vaultSecurityManager: SecurityManager

    private var recentsPrivacyOverlay: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            HorizonWebTheme {
                HorizonWebNavHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        removeRecentsPrivacyOverlay()
        vaultSecurityManager.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        addRecentsPrivacyOverlay()
        vaultSecurityManager.onAppBackgrounded()
    }

    private fun addRecentsPrivacyOverlay() {
        if (recentsPrivacyOverlay != null) return
        val content = findViewById<FrameLayout>(android.R.id.content)
        val overlay = View(this).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
            alpha = 0.95f
            isClickable = true
            isFocusable = true
        }
        content.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        recentsPrivacyOverlay = overlay
    }

    private fun removeRecentsPrivacyOverlay() {
        val content = findViewById<FrameLayout>(android.R.id.content)
        recentsPrivacyOverlay?.let(content::removeView)
        recentsPrivacyOverlay = null
    }
}

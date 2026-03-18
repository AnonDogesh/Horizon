package com.dean.browservault

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class ImageViewerActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        webView = findViewById(R.id.imageWebView)
        findViewById<ImageButton>(R.id.buttonClose).setOnClickListener { finish() }

        val imageUri = intent.getStringExtra(EXTRA_IMAGE_URI).orEmpty()
        if (imageUri.isBlank()) {
            finish()
            return
        }

        webView.settings.apply {
            javaScriptEnabled = false
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        webView.setBackgroundColor(android.graphics.Color.BLACK)
        webView.loadDataWithBaseURL(
            imageUri,
            """
            <html>
              <body style="margin:0;background:#000;display:flex;align-items:center;justify-content:center;min-height:100vh;">
                <img src="$imageUri" style="max-width:100%;max-height:100%;object-fit:contain;" />
              </body>
            </html>
            """.trimIndent(),
            "text/html",
            "utf-8",
            null
        )
    }

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
    }
}

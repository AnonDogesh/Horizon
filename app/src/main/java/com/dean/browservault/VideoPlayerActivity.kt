package com.dean.browservault

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class VideoPlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_video_player)

        playerView = findViewById(R.id.playerView)
        hideSystemBars()

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        if (videoUrl.isNullOrBlank()) {
            toast(getString(R.string.msg_invalid_video_url))
            finish()
            return
        }

        initializePlayer(videoUrl)
    }

    private fun initializePlayer(videoUrl: String) {
        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        playerView.player = exoPlayer
        playerView.useController = true

        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    override fun onStart() {
        super.onStart()
        hideSystemBars()
    }

    override fun onStop() {
        playerView.player = null
        player?.release()
        player = null
        super.onStop()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_VIDEO_URL = "extra_video_url"
    }
}

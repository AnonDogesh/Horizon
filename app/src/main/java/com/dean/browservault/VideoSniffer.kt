package com.dean.browservault

import java.util.Locale

data class VideoCandidate(
    val url: String,
    val type: String,
    val quality: Int?,
    val bitrate: Int?,
    val sourcePage: String,
    var score: Int = 0
)

object VideoSniffer {

    private val candidates = mutableListOf<VideoCandidate>()

    @Synchronized
    fun clear() {
        candidates.clear()
    }

    @Synchronized
    fun add(url: String, page: String) {
        val candidate = analyze(url, page)
        val existing = candidates.indexOfFirst { it.url == candidate.url }
        if (existing >= 0) {
            if (candidate.score > candidates[existing].score) {
                candidates[existing] = candidate
            }
        } else {
            candidates.add(candidate)
        }
    }

    @Synchronized
    fun getBest(): VideoCandidate? = candidates.maxByOrNull { it.score }

    @Synchronized
    fun all(): List<VideoCandidate> = candidates.toList()

    private fun analyze(url: String, page: String): VideoCandidate {
        val lower = url.lowercase(Locale.US)
        val type = when {
            lower.contains(".m3u8") -> "hls"
            lower.contains(".mp4") -> "mp4"
            lower.contains(".mpd") -> "mpd"
            else -> "unknown"
        }

        val quality = extractQuality(lower)
        val score = computeScore(lower, type, quality)
        return VideoCandidate(url, type, quality, null, page, score)
    }

    private fun extractQuality(url: String): Int? {
        return listOf(2160, 1440, 1080, 720, 480, 360, 240)
            .firstOrNull { q -> url.contains(q.toString()) }
    }

    private fun computeScore(url: String, type: String, quality: Int?): Int {
        var score = 0
        if (type == "hls") score += 50
        if (type == "mp4") score += 30
        score += (quality ?: 0)
        if (url.contains("ads") || url.contains("doubleclick")) score -= 200
        if (url.contains("preview")) score -= 100
        return score
    }
}

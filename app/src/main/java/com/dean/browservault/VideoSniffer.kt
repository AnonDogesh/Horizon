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

    fun parseM3u8Variants(content: String, baseUrl: String, sourcePage: String): List<VideoCandidate> {
        val lines = content.lines().map { it.trim() }
        val parsed = mutableListOf<VideoCandidate>()
        var pendingResolution: Int? = null

        lines.forEach { line ->
            when {
                line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) -> {
                    pendingResolution = extractResolution(line)
                }
                line.isBlank() || line.startsWith("#") -> Unit
                else -> {
                    val resolvedUrl = resolveRelativeUrl(baseUrl, line)
                    val quality = pendingResolution ?: extractQuality(resolvedUrl.lowercase(Locale.US))
                    parsed.add(
                        VideoCandidate(
                            url = resolvedUrl,
                            type = "hls",
                            quality = quality,
                            bitrate = null,
                            sourcePage = sourcePage,
                            score = computeScore(resolvedUrl.lowercase(Locale.US), "hls", quality)
                        )
                    )
                    pendingResolution = null
                }
            }
        }
        return parsed
    }

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

    private fun extractResolution(extLine: String): Int? {
        val match = Regex("""RESOLUTION=\d+x(\d+)""", RegexOption.IGNORE_CASE).find(extLine)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun resolveRelativeUrl(baseUrl: String, candidatePath: String): String {
        return runCatching {
            java.net.URL(java.net.URL(baseUrl), candidatePath).toString()
        }.getOrElse { candidatePath }
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

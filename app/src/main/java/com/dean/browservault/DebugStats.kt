package com.dean.browservault

object DebugStats {
    @Volatile var blocked: Int = 0
    @Volatile var allowed: Int = 0
    @Volatile var videoDetected: Int = 0
}

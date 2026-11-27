package com.autopilot.android.feature.traffic

import com.autopilot.android.domain.model.DeviceCategory

enum class TrafficMode(val label: String, val description: String) {
    REAL_BROWSER("Real Browser (JS & Analytics)", "Loads full pages with stealth WebViews, human-like scrolling, and dwell time for Google Analytics & tracking tags."),
    ORGANIC_SEARCH("Organic Search + Browser", "Simulates search engine discovery (Google, Bing, DuckDuckGo) with stealth WebView, human scrolling & dwell time."),
    PAGE_REQUEST("High-Speed HTTP/S Engine", "Maximum throughput requests with rotating mobile/desktop browser User-Agents, headers, and search referrers.")
}

enum class TrafficSpeed(val label: String, val eventsPerMinute: Int) {
    SLOW("Slow", 60),
    NORMAL("Normal", 300),
    FAST("Fast", 600),
    TURBO("Turbo", 1200)
}

enum class TrafficStatus {
    QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, STOPPED, INTERRUPTED
}

data class TrafficRun(
    val id: String,
    val websiteUrl: String,
    val plannedEvents: Int,
    val mode: TrafficMode,
    val speed: TrafficSpeed,
    val concurrency: Int,
    val pagePaths: List<String>,
    val status: TrafficStatus,
    val submitted: Int = 0,
    val failed: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val deviceCategory: DeviceCategory = DeviceCategory.ALL
) {
    val pending: Int get() = (plannedEvents - submitted - failed).coerceAtLeast(0)
    val progress: Float get() = if (plannedEvents == 0) 0f else (submitted + failed).toFloat() / plannedEvents
    val isActive: Boolean get() = status in setOf(TrafficStatus.QUEUED, TrafficStatus.RUNNING, TrafficStatus.PAUSED, TrafficStatus.INTERRUPTED)

    fun durationSeconds(now: Long = System.currentTimeMillis()): Long {
        val start = startedAt ?: createdAt
        return ((completedAt ?: now) - start).coerceAtLeast(0) / 1000
    }
}

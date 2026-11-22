package com.autopilot.android.domain.model

enum class ExecutionMode(val displayName: String, val description: String) {
    SIMPLE_SCROLL_REFRESH(
        "Simple Scroll + Refresh",
        "Periodically scrolls the page vertically and refreshes at the designated interval."
    ),
    DEEP_SCROLL(
        "Deep Scroll",
        "Progressively and smoothly scrolls through the entire document depth, detecting dynamic infinite-scroll content."
    ),
    TAB_SWITCHING(
        "Tab Switching",
        "Operates across 1 to 25 virtual/physical tabs, switching focus on intervals while maintaining independent scroll/refresh states."
    ),
    CUSTOM_JAVASCRIPT(
        "Sandboxed Custom JavaScript",
        "Executes user-defined JavaScript within an isolated DOM sandbox with strict timeouts, domain permissions, and zero native access."
    )
}

enum class ProfileStatus {
    IDLE,
    RUNNING,
    PAUSED,
    ERROR,
    COMPLETED
}

data class AutomationProfile(
    val id: String,
    val name: String,
    val description: String = "",
    val urls: List<String>,
    val tabCount: Int = 1, // 1 to 25
    val executionMode: ExecutionMode = ExecutionMode.SIMPLE_SCROLL_REFRESH,
    val refreshIntervalSec: Int = 30, // 10, 15, 20, 30, 45, 60, 90, 120
    val scrollIntervalSec: Int = 15,  // 5, 10, 15, 20, 30, 45, 60, 90, 120
    val randomizeIntervals: Boolean = false,
    val minRefreshIntervalSec: Int = 20,
    val maxRefreshIntervalSec: Int = 45,
    val minScrollIntervalSec: Int = 10,
    val maxScrollIntervalSec: Int = 25,
    val customScript: String? = null,
    val allowedDomains: List<String> = emptyList(),
    val userAgent: String? = null,
    val clearCookiesOnStart: Boolean = false,
    val clearCacheOnStart: Boolean = false,
    val timeoutSeconds: Int = 60,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: ProfileStatus = ProfileStatus.IDLE
) {
    companion object {
        val VALID_REFRESH_INTERVALS = listOf(10, 15, 20, 30, 45, 60, 90, 120)
        val VALID_SCROLL_INTERVALS = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)
        const val MIN_TABS = 1
        const val MAX_TABS = 25
    }
}

data class TabState(
    val tabIndex: Int,
    val currentUrl: String,
    val pageTitle: String = "",
    val loadProgress: Int = 0,
    val scrollDepthPercentage: Int = 0,
    val lastRefreshTime: Long = 0,
    val lastScrollTime: Long = 0,
    val isNavigating: Boolean = false,
    val lastError: String? = null
)

data class ExecutionHistoryItem(
    val id: String,
    val profileId: String,
    val profileName: String,
    val startedAt: Long,
    val endedAt: Long?,
    val durationSeconds: Long,
    val totalActions: Int,
    val status: ProfileStatus,
    val errorMessage: String? = null
)

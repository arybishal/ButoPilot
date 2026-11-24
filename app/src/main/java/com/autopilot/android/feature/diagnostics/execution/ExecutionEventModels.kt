package com.autopilot.android.feature.diagnostics.execution

import java.util.UUID

enum class ExecutionEventType(val displayName: String) {
    EXECUTION_REQUESTED("Execution Requested"),
    PROFILE_VALIDATED("Profile Validated"),
    PROXY_SELECTED("Proxy Selected"),
    PROXY_COMPATIBILITY_CHECKED("Proxy Compatibility Checked"),
    WEBVIEW_INITIALIZED("WebView Initialized"),
    URL_NAVIGATION_STARTED("Navigation Started"),
    PAGE_LOADED("Page Loaded"),
    REFRESH_PERFORMED("Refresh Performed"),
    SCROLL_PERFORMED("Scroll Performed"),
    TAB_SWITCHED("Tab Switched"),
    EXECUTION_PAUSED("Execution Paused"),
    EXECUTION_RESUMED("Execution Resumed"),
    EXECUTION_STOPPED("Execution Stopped"),
    EXECUTION_FAILED("Execution Failed")
}

enum class ExecutionErrorCategory(val displayName: String, val recoverySuggestion: String, val safeToRetry: Boolean) {
    INVALID_CONFIGURATION(
        "Invalid Configuration",
        "Check profile URLs, intervals, and tab settings for syntax or range errors.",
        false
    ),
    NETWORK_UNAVAILABLE(
        "Network Unavailable",
        "Verify your device has an active Wi-Fi or cellular Internet connection.",
        true
    ),
    PROXY_UNAVAILABLE(
        "Proxy Unavailable",
        "The selected proxy is offline or timing out. Test the proxy or choose another proxy.",
        true
    ),
    PROXY_AUTHENTICATION_FAILED(
        "Proxy Authentication Failed",
        "Proxy credentials rejected. Update your username and password in the Proxy Editor.",
        false
    ),
    WEBVIEW_INITIALIZATION_FAILED(
        "WebView Initialization Failed",
        "Android System WebView failed to start. Ensure Android System WebView is updated and memory is sufficient.",
        true
    ),
    NAVIGATION_TIMEOUT(
        "Navigation Timeout",
        "The target web page took too long to respond. Increase the profile timeout or inspect server availability.",
        true
    ),
    PERMISSION_DENIED(
        "Permission Denied",
        "Required permissions (notifications, exact alarms, or network) were revoked. Review app permissions in system settings.",
        false
    ),
    RESOURCE_LIMIT_REACHED(
        "Resource Limit Reached",
        "System memory is critically low (<150MB). Reduce tab count or close background applications.",
        true
    ),
    EXECUTION_INTERRUPTED(
        "Execution Interrupted",
        "Automation was interrupted by user action, process death, or system memory pressure.",
        true
    ),
    SCHEDULING_FAILED(
        "Scheduling Failed",
        "Schedule trigger could not be registered with AlarmManager/WorkManager.",
        true
    ),
    UNKNOWN_ERROR(
        "Unknown Error",
        "An unexpected error occurred during execution. Review logs or contact support.",
        true
    )
}

data class ExecutionEvent(
    val id: String = UUID.randomUUID().toString(),
    val executionId: String,
    val profileId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: ExecutionEventType,
    val errorCategory: ExecutionErrorCategory? = null,
    val message: String
)

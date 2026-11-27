package com.autopilot.android.feature.proxy

import android.content.Context

enum class FreeProxySelectionMode(val label: String, val description: String) {
    RANDOM(
        "Random Proxy",
        "Selects a random healthy proxy from your verified proxy pool."
    ),
    TOP_TIER_ONLY(
        "Top Tier Country Only",
        "Selects only healthy proxies from Tier-1 countries (US, GB, CA, AU, DE, FR, NL, JP, SG, etc.)."
    )
}

object AutomationProxyPreferences {
    private const val PREFS_NAME = "automation_proxy_prefs"
    private const val KEY_PREFIX = "free_proxy_mode_"

    fun getMode(context: Context, profileId: String?): FreeProxySelectionMode {
        if (profileId.isNullOrBlank()) return FreeProxySelectionMode.RANDOM
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString("$KEY_PREFIX$profileId", null) ?: return FreeProxySelectionMode.RANDOM
        return FreeProxySelectionMode.entries.firstOrNull { it.name == raw } ?: FreeProxySelectionMode.RANDOM
    }

    fun setMode(context: Context, profileId: String, mode: FreeProxySelectionMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("$KEY_PREFIX$profileId", mode.name).apply()
    }
}

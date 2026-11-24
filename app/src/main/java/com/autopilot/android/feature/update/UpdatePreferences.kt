package com.autopilot.android.feature.update

import android.content.Context
import android.content.SharedPreferences

class UpdatePreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "autopilot_update_preferences"
        private const val KEY_LAST_CHECK_TIME = "last_check_timestamp"
        private const val KEY_DISMISSED_TAG = "dismissed_release_tag"
        private const val KEY_LAST_KNOWN_TAG = "last_known_release_tag"
        private const val KEY_AUTO_CHECK_ENABLED = "auto_check_enabled"
    }

    var lastCheckTimestamp: Long
        get() = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK_TIME, value).apply()

    var dismissedReleaseTag: String?
        get() = prefs.getString(KEY_DISMISSED_TAG, null)
        set(value) = prefs.edit().putString(KEY_DISMISSED_TAG, value).apply()

    var lastKnownReleaseTag: String?
        get() = prefs.getString(KEY_LAST_KNOWN_TAG, null)
        set(value) = prefs.edit().putString(KEY_LAST_KNOWN_TAG, value).apply()

    var autoCheckEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CHECK_ENABLED, value).apply()

    fun dismissUpdate(tag: String) {
        dismissedReleaseTag = tag
    }

    fun isDismissed(tag: String): Boolean {
        return dismissedReleaseTag == tag
    }

    fun clearDismissed() {
        dismissedReleaseTag = null
    }
}

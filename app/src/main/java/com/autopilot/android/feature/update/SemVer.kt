package com.autopilot.android.feature.update

object SemVer {

    /**
     * Cleans a raw tag or version string (e.g., "v1.2.3-release" -> "1.2.3").
     */
    fun cleanVersionString(raw: String): String {
        return raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .substringBefore("+")
            .trim()
    }

    /**
     * Compares two semantic version strings.
     * Returns:
     *   > 0 if v1 > v2
     *   < 0 if v1 < v2
     *   0 if v1 == v2
     */
    fun compare(v1: String, v2: String): Int {
        val clean1 = cleanVersionString(v1)
        val clean2 = cleanVersionString(v2)

        val parts1 = clean1.split(".").mapNotNull { it.trim().toIntOrNull() }
        val parts2 = clean2.split(".").mapNotNull { it.trim().toIntOrNull() }

        val maxLen = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }

            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }

        return 0
    }

    /**
     * Returns true if the remote version is strictly newer than the current version.
     */
    fun isNewer(remoteVersion: String, currentVersion: String): Boolean {
        if (remoteVersion.isBlank() || currentVersion.isBlank()) return false
        return compare(remoteVersion, currentVersion) > 0
    }
}

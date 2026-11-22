package com.autopilot.android.core.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RootChecker {
    private val SU_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/su/bin/su",
        "/magisk/.core/bin/su"
    )

    fun isSuBinaryPresent(): Boolean {
        return SU_PATHS.any { File(it).exists() }
    }

    suspend fun checkRootAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    suspend fun requestRootAccess(): Boolean = withContext(Dispatchers.IO) {
        checkRootAccess()
    }
}

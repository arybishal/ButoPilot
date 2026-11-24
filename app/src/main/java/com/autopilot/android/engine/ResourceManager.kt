package com.autopilot.android.engine

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.atomic.AtomicInteger

/**
 * Monitors device memory conditions and regulates global concurrency
 * and active tab allocation across the automation engine.
 */
class ResourceManager(private val context: Context) {

    companion object {
        const val MAX_GLOBAL_CONCURRENT_TASKS = 2
        const val MAX_GLOBAL_ACTIVE_TABS = 200
        const val LOW_MEMORY_THRESHOLD_MB = 150
    }

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val taskSemaphore = Semaphore(MAX_GLOBAL_CONCURRENT_TASKS)
    private val activeTabCount = AtomicInteger(0)

    /**
     * Queries Android system memory metrics.
     * Returns true if device is experiencing critical memory pressure.
     */
    fun isMemoryCriticallyLow(): Boolean {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val availMb = memoryInfo.availMem / (1024 * 1024)
        return memoryInfo.lowMemory || availMb < LOW_MEMORY_THRESHOLD_MB
    }

    /**
     * Available RAM in Megabytes.
     */
    fun getAvailableMemoryMb(): Long {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem / (1024 * 1024)
    }

    fun acquireTabSlot(): Boolean {
        return activeTabCount.incrementAndGet() <= MAX_GLOBAL_ACTIVE_TABS
    }

    fun releaseTabSlot() {
        activeTabCount.decrementAndGet()
    }

    fun getActiveTabCount(): Int = activeTabCount.get()
}

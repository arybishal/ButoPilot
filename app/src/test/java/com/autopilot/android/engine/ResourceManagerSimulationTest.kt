package com.autopilot.android.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ResourceManagerSimulationTest {

    @Test
    fun resourceManager_tabLimitsSimulated() {
        val maxTabs = 25
        val activeTabs = AtomicInteger(0)

        // Allocate up to maxTabs
        for (i in 1..maxTabs) {
            val allocated = activeTabs.incrementAndGet() <= maxTabs
            assertTrue("Tab $i should be allocated", allocated)
        }

        // 26th tab should be rejected
        val rejected = activeTabs.incrementAndGet() <= maxTabs
        assertFalse("26th tab should exceed limit", rejected)

        // Release slots
        activeTabs.decrementAndGet()
        activeTabs.decrementAndGet()
        assertEquals(24, activeTabs.get())
    }

    @Test
    fun resourceManager_taskConcurrencyLimitsSimulated() = runBlocking {
        val maxConcurrency = 2
        val semaphore = kotlinx.coroutines.sync.Semaphore(maxConcurrency)

        assertTrue(semaphore.tryAcquire())
        assertTrue(semaphore.tryAcquire())
        assertFalse("Third concurrent task should be blocked", semaphore.tryAcquire())

        semaphore.release()
        assertTrue("Slot released, should acquire", semaphore.tryAcquire())
    }
}

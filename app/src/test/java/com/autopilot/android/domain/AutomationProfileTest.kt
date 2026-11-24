package com.autopilot.android.domain

import com.autopilot.android.domain.model.AutomationProfile
import com.autopilot.android.domain.model.ExecutionMode
import com.autopilot.android.domain.model.ProfileStatus
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AutomationProfileTest {

    @Test
    fun profile_defaultValuesValid() {
        val profile = AutomationProfile(
            id = UUID.randomUUID().toString(),
            name = "Test Automation",
            urls = listOf("https://example.com")
        )

        assertEquals(1, profile.tabCount)
        assertEquals(ExecutionMode.SIMPLE_SCROLL_REFRESH, profile.executionMode)
        assertEquals(30, profile.refreshIntervalSec)
        assertEquals(15, profile.scrollIntervalSec)
        assertFalse(profile.randomizeIntervals)
        assertEquals(ProfileStatus.IDLE, profile.status)
    }

    @Test
    fun profile_tabCountBoundaries() {
        assertTrue(AutomationProfile.MIN_TABS == 1)
        assertTrue(AutomationProfile.MAX_TABS == 200)

        val profile200Tabs = AutomationProfile(
            id = "test-200",
            name = "Multi Tab Profile",
            urls = (1..200).map { "https://site$it.com" },
            tabCount = 200
        )
        assertEquals(200, profile200Tabs.tabCount)
        assertEquals(200, profile200Tabs.urls.size)
    }

    @Test
    fun profile_allExecutionModesAvailable() {
        assertEquals(5, ExecutionMode.values().size)
        assertTrue(ExecutionMode.values().contains(ExecutionMode.SIMPLE_SCROLL_REFRESH))
        assertTrue(ExecutionMode.values().contains(ExecutionMode.DEEP_SCROLL))
        assertTrue(ExecutionMode.values().contains(ExecutionMode.AUTO_HUMAN_BEHAVIOUR))
        assertTrue(ExecutionMode.values().contains(ExecutionMode.TAB_SWITCHING))
        assertTrue(ExecutionMode.values().contains(ExecutionMode.CUSTOM_JAVASCRIPT))
    }

    @Test
    fun profile_copyAndDuplicatePreservesState() {
        val original = AutomationProfile(
            id = "orig-1",
            name = "Original",
            urls = listOf("https://a.com", "https://b.com"),
            tabCount = 5,
            executionMode = ExecutionMode.TAB_SWITCHING,
            refreshIntervalSec = 60,
            scrollIntervalSec = 30
        )

        val copy = original.copy(
            id = "copy-2",
            name = "Original (Copy)"
        )

        assertEquals("copy-2", copy.id)
        assertEquals("Original (Copy)", copy.name)
        assertEquals(5, copy.tabCount)
        assertEquals(ExecutionMode.TAB_SWITCHING, copy.executionMode)
        assertEquals(60, copy.refreshIntervalSec)
        assertEquals(30, copy.scrollIntervalSec)
    }
}

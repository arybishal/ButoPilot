package com.autopilot.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCatalogTest {

    @Test
    fun catalogCoversEveryRequestedDeviceGroup() {
        assertTrue(DeviceCatalog.count(DeviceCategory.WINDOWS) >= 10)
        assertTrue(DeviceCatalog.count(DeviceCategory.ANDROID) in 50..100)
        assertTrue(DeviceCatalog.count(DeviceCategory.IPHONE) >= 30)
        assertTrue(DeviceCatalog.count(DeviceCategory.MACBOOK_SILICON) >= 15)
        assertTrue(DeviceCatalog.count(DeviceCategory.MACBOOK_INTEL) >= 10)
        assertTrue(DeviceCatalog.count(DeviceCategory.LINUX) >= 8)
        assertEquals(
            DeviceCatalog.count(DeviceCategory.ALL),
            DeviceCategory.entries.filter { it != DeviceCategory.ALL }
                .sumOf { DeviceCatalog.count(it) }
        )
    }

    @Test
    fun randomProfilesStayInsideRequestedCategory() {
        DeviceCategory.entries.forEach { category ->
            val pool = DeviceCatalog.forCategory(category)
            repeat(100) {
                val profile = DeviceCatalog.random(category)
                assertTrue(pool.contains(profile))
                assertTrue(profile.userAgent.startsWith("Mozilla/5.0"))
            }
        }
    }

    @Test
    fun randomSelectionActuallyVaries() {
        val labels = (1..100).map { DeviceCatalog.random(DeviceCategory.ALL).label }.toSet()
        assertTrue(labels.size > 10)
    }
}

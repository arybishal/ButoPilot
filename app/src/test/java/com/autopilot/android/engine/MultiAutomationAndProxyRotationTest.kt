package com.autopilot.android.engine

import com.autopilot.android.data.repository.ProfileRepository
import com.autopilot.android.domain.model.AutomationProfile
import com.autopilot.android.domain.model.ExecutionMode
import com.autopilot.android.domain.model.ProfileStatus
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MultiAutomationAndProxyRotationTest {

    @Test
    fun testEngineStateDefaults() {
        val state = EngineState()
        assertEquals(ProfileStatus.IDLE, state.status)
        assertFalse(state.isMultiRun)
        assertEquals(emptyList<AutomationProfile>(), state.activeProfiles)
        assertEquals(600L, state.nextProxyRotationSeconds) // 10 minutes default
        assertNull(state.currentProxyName)
    }

    @Test
    fun testDuplicateProfileLogic() = runBlocking {
        val storedProfiles = mutableMapOf<String, AutomationProfile>()

        val fakeRepo = object : ProfileRepository {
            override fun getAllProfilesFlow() = flowOf(storedProfiles.values.toList())
            override suspend fun getAllProfiles() = storedProfiles.values.toList()
            override suspend fun getProfileById(id: String) = storedProfiles[id]
            override fun getProfileByIdFlow(id: String) = flowOf(storedProfiles[id])
            override suspend fun saveProfile(profile: AutomationProfile) {
                storedProfiles[profile.id] = profile
            }
            override suspend fun duplicateProfile(profileId: String): AutomationProfile? {
                val orig = getProfileById(profileId) ?: return null
                val copy = orig.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${orig.name} (Copy)",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    status = ProfileStatus.IDLE
                )
                saveProfile(copy)
                return copy
            }
            override suspend fun deleteProfile(id: String) { storedProfiles.remove(id) }
            override suspend fun updateStatus(id: String, status: ProfileStatus) {
                storedProfiles[id] = storedProfiles[id]?.copy(status = status) ?: return
            }
        }

        val original = AutomationProfile(
            id = "profile-100",
            name = "Scrape Alpha",
            urls = listOf("https://test.com", "https://test.com/sub"),
            tabCount = 50,
            executionMode = ExecutionMode.DEEP_SCROLL,
            enableSpoofing = true,
            enableFreeProxy = true
        )
        fakeRepo.saveProfile(original)

        val cloned = fakeRepo.duplicateProfile("profile-100")
        assertNotNull(cloned)
        assertNotEquals(original.id, cloned!!.id)
        assertEquals("Scrape Alpha (Copy)", cloned.name)
        assertEquals(50, cloned.tabCount)
        assertEquals(listOf("https://test.com", "https://test.com/sub"), cloned.urls)
        assertTrue(cloned.enableSpoofing)
        assertTrue(cloned.enableFreeProxy)
        assertEquals(ExecutionMode.DEEP_SCROLL, cloned.executionMode)
        assertEquals(ProfileStatus.IDLE, cloned.status)

        // Ensure both exist in repository
        val all = fakeRepo.getAllProfiles()
        assertEquals(2, all.size)
    }

    @Test
    fun testMultiProfileTabAggregation() {
        val p1 = AutomationProfile(
            id = "p1",
            name = "Task 1",
            urls = listOf("https://example1.com"),
            tabCount = 10
        )
        val p2 = AutomationProfile(
            id = "p2",
            name = "Task 2",
            urls = listOf("https://example2.com"),
            tabCount = 25
        )
        val p3 = AutomationProfile(
            id = "p3",
            name = "Task 3",
            urls = listOf("https://example3.com"),
            tabCount = 15
        )

        val profiles = listOf(p1, p2, p3)
        val totalTabsSum = profiles.sumOf { it.tabCount.coerceIn(1, 200) }.coerceIn(1, 200)

        assertEquals(50, totalTabsSum)
        val state = EngineState(
            status = ProfileStatus.RUNNING,
            activeProfiles = profiles,
            isMultiRun = true,
            totalTabs = totalTabsSum,
            nextProxyRotationSeconds = 600L
        )

        assertTrue(state.isMultiRun)
        assertEquals(3, state.activeProfiles.size)
        assertEquals(50, state.totalTabs)
        assertEquals(600L, state.nextProxyRotationSeconds)
    }
}

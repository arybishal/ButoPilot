package com.autopilot.android.feature.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

    @Test
    fun testSemanticVersionComparison() {
        // 1.9.0 -> 1.10.0: Update available
        assertTrue(SemVer.isNewer("1.10.0", "1.9.0"))

        // 1.9.0 -> 1.9.1: Update available
        assertTrue(SemVer.isNewer("1.9.1", "1.9.0"))

        // 1.9.0 -> 1.9.0: No update
        assertFalse(SemVer.isNewer("1.9.0", "1.9.0"))

        // 2.0.0 -> 1.9.9: No update
        assertFalse(SemVer.isNewer("1.9.9", "2.0.0"))
    }

    @Test
    fun testVersionTagsWithVPrefix() {
        assertTrue(SemVer.isNewer("v2.3.0", "2.2.9"))
        assertTrue(SemVer.isNewer("2.3.0", "v2.2.9"))
        assertFalse(SemVer.isNewer("v2.3.0", "2.3.0"))
        assertEquals(0, SemVer.compare("v2.3.0", "2.3.0"))
    }

    @Test
    fun testMultiDigitComponents() {
        assertTrue(SemVer.isNewer("1.2.14", "1.2.9"))
        assertFalse(SemVer.isNewer("1.2.9", "1.2.14"))
        assertTrue(SemVer.isNewer("1.12.0", "1.9.9"))
        assertTrue(SemVer.isNewer("10.0.0", "9.9.9"))
    }

    @Test
    fun testSuffixAndMetadataHandling() {
        assertTrue(SemVer.isNewer("1.0.1-release", "1.0.0"))
        assertTrue(SemVer.isNewer("v1.5.0+build123", "1.4.9"))
        assertEquals(0, SemVer.compare("1.0.0-beta", "1.0.0"))
    }

    @Test
    fun testEmptyOrInvalidInputs() {
        assertFalse(SemVer.isNewer("", "1.0.0"))
        assertFalse(SemVer.isNewer("1.0.0", ""))
        assertFalse(SemVer.isNewer("", ""))
    }
}

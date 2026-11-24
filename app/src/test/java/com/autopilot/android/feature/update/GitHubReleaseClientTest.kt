package com.autopilot.android.feature.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseClientTest {

    private val client = GitHubReleaseClient(owner = "arybishal", repo = "ButoPilot")

    @Test
    fun testParseValidReleaseJsonWithApk() {
        val sampleJson = """
            {
              "tag_name": "v1.2.0",
              "name": "AutoPilot v1.2.0 Performance & Proxy Update",
              "body": "## What's Changed\n* Added automatic GitHub update system\n* Improved proxy testing latency\n* Fixed crash on background reconciliation",
              "published_at": "2026-11-24T18:00:00Z",
              "html_url": "https://github.com/arybishal/ButoPilot/releases/tag/v1.2.0",
              "assets": [
                {
                  "name": "AutoPilot-v1.2.0-release.apk",
                  "size": 15482390,
                  "browser_download_url": "https://github.com/arybishal/ButoPilot/releases/download/v1.2.0/AutoPilot-v1.2.0-release.apk",
                  "content_type": "application/vnd.android.package-archive"
                },
                {
                  "name": "source-code.zip",
                  "size": 2500000,
                  "browser_download_url": "https://github.com/arybishal/ButoPilot/releases/download/v1.2.0/source-code.zip",
                  "content_type": "application/zip"
                }
              ]
            }
        """.trimIndent()

        val parsed = client.parseReleaseJson(sampleJson, isDebugBuild = false)
        assertNotNull(parsed)
        assertEquals("1.2.0", parsed?.versionName)
        assertEquals("v1.2.0", parsed?.releaseTag)
        assertEquals("AutoPilot v1.2.0 Performance & Proxy Update", parsed?.releaseName)
        assertEquals(15482390L, parsed?.apkSize)
        assertEquals("AutoPilot-v1.2.0-release.apk", parsed?.apkName)
        assertEquals("https://github.com/arybishal/ButoPilot/releases/download/v1.2.0/AutoPilot-v1.2.0-release.apk", parsed?.apkUrl)
        assertTrue(parsed?.changelog?.contains("Added automatic GitHub update system") == true)
    }

    @Test
    fun testAssetSelectionPrefersReleaseOverDebugInProduction() {
        val sampleJson = """
            {
              "tag_name": "v2.0.0",
              "name": "Version 2.0.0",
              "body": "Changelog text",
              "published_at": "2026-11-24T12:00:00Z",
              "html_url": "https://github.com/arybishal/ButoPilot/releases/tag/v2.0.0",
              "assets": [
                {
                  "name": "app-debug.apk",
                  "size": 14000000,
                  "browser_download_url": "https://github.com/.../app-debug.apk",
                  "content_type": "application/vnd.android.package-archive"
                },
                {
                  "name": "app-release.apk",
                  "size": 12000000,
                  "browser_download_url": "https://github.com/.../app-release.apk",
                  "content_type": "application/vnd.android.package-archive"
                }
              ]
            }
        """.trimIndent()

        // Release build should pick app-release.apk
        val releaseResult = client.parseReleaseJson(sampleJson, isDebugBuild = false)
        assertEquals("app-release.apk", releaseResult?.apkName)

        // Debug build should prefer app-debug.apk
        val debugResult = client.parseReleaseJson(sampleJson, isDebugBuild = true)
        assertEquals("app-debug.apk", debugResult?.apkName)
    }

    @Test
    fun testReleaseWithoutApkAssetReturnsNull() {
        val sampleJson = """
            {
              "tag_name": "v1.1.0",
              "name": "v1.1.0",
              "body": "No APK attached",
              "assets": [
                {
                  "name": "checksums.txt",
                  "size": 512,
                  "browser_download_url": "https://github.com/.../checksums.txt"
                }
              ]
            }
        """.trimIndent()

        val parsed = client.parseReleaseJson(sampleJson, isDebugBuild = false)
        assertNull(parsed)
    }

    @Test
    fun testMalformedReleaseJsonReturnsNull() {
        val malformedJson = """
            {
              "tag_name": "",
              "name": "Invalid"
            }
        """.trimIndent()

        val parsed = client.parseReleaseJson(malformedJson, isDebugBuild = false)
        assertNull(parsed)
    }
}

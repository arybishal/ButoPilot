package com.autopilot.android.feature.diagnostics

import com.autopilot.android.feature.diagnostics.execution.ExecutionErrorCategory
import com.autopilot.android.feature.diagnostics.execution.ExecutionEventTracker
import com.autopilot.android.feature.diagnostics.execution.ExecutionEventType
import org.junit.Assert.*
import org.junit.Test

class DiagnosticsTest {

    @Test
    fun testDiagnosticStatusEnums() {
        assertEquals("Healthy", DiagnosticStatus.HEALTHY.displayName)
        assertEquals("Warning", DiagnosticStatus.WARNING.displayName)
        assertEquals("Error", DiagnosticStatus.ERROR.displayName)
        assertEquals("Unavailable", DiagnosticStatus.UNAVAILABLE.displayName)
        assertEquals("Not Checked", DiagnosticStatus.NOT_CHECKED.displayName)
    }

    @Test
    fun testDiagnosticCategoryEnums() {
        assertEquals("Database", DiagnosticCategory.DATABASE.displayName)
        assertEquals("Automation", DiagnosticCategory.AUTOMATION.displayName)
        assertEquals("Scheduling", DiagnosticCategory.SCHEDULING.displayName)
        assertEquals("Network", DiagnosticCategory.NETWORK.displayName)
        assertEquals("Storage", DiagnosticCategory.STORAGE.displayName)
        assertEquals("Security", DiagnosticCategory.SECURITY.displayName)
    }

    @Test
    fun testExecutionEventUrlSanitization() {
        // Redacts query parameters containing secret tokens or user info
        val rawUrl = "https://example.com/api/v1/auth?token=supersecrettoken123&user=admin"
        val sanitized = ExecutionEventTracker.sanitizeUrl(rawUrl)

        assertFalse("Token must not appear in sanitized URL", sanitized.contains("supersecrettoken123"))
        assertFalse("User parameter must not appear in sanitized URL", sanitized.contains("user=admin"))
        assertTrue("Query must be redacted placeholder", sanitized.contains("?[query-redacted]"))
        assertTrue("Base URL preserved", sanitized.startsWith("https://example.com/api/v1/auth"))
    }

    @Test
    fun testExecutionEventSensitiveTextMasking() {
        val rawMessage = "Request failed with password=MySecretPassword123 and token: ABCXYZ789"
        val sanitized = ExecutionEventTracker.sanitizeSensitiveText(rawMessage)

        assertFalse(sanitized.contains("MySecretPassword123"))
        assertFalse(sanitized.contains("ABCXYZ789"))
        assertTrue(sanitized.contains("password=[REDACTED]"))
        assertTrue(sanitized.contains("token=[REDACTED]"))
    }

    @Test
    fun testExecutionErrorCategoryProperties() {
        assertTrue(ExecutionErrorCategory.NETWORK_UNAVAILABLE.safeToRetry)
        assertTrue(ExecutionErrorCategory.PROXY_UNAVAILABLE.safeToRetry)
        assertFalse(ExecutionErrorCategory.INVALID_CONFIGURATION.safeToRetry)
        assertFalse(ExecutionErrorCategory.PROXY_AUTHENTICATION_FAILED.safeToRetry)
        assertNotNull(ExecutionErrorCategory.NAVIGATION_TIMEOUT.recoverySuggestion)
    }

    @Test
    fun testExecutionEventTypeCoverage() {
        val types = ExecutionEventType.values()
        assertTrue(types.contains(ExecutionEventType.EXECUTION_REQUESTED))
        assertTrue(types.contains(ExecutionEventType.PROFILE_VALIDATED))
        assertTrue(types.contains(ExecutionEventType.PROXY_SELECTED))
        assertTrue(types.contains(ExecutionEventType.WEBVIEW_INITIALIZED))
        assertTrue(types.contains(ExecutionEventType.URL_NAVIGATION_STARTED))
        assertTrue(types.contains(ExecutionEventType.PAGE_LOADED))
        assertTrue(types.contains(ExecutionEventType.EXECUTION_STOPPED))
        assertTrue(types.contains(ExecutionEventType.EXECUTION_FAILED))
    }
}

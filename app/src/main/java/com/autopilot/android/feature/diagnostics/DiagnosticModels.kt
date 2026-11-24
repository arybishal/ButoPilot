package com.autopilot.android.feature.diagnostics

import java.util.UUID

enum class DiagnosticStatus(val displayName: String) {
    HEALTHY("Healthy"),
    WARNING("Warning"),
    ERROR("Error"),
    UNAVAILABLE("Unavailable"),
    NOT_CHECKED("Not Checked")
}

enum class DiagnosticCategory(val displayName: String) {
    DATABASE("Database"),
    AUTOMATION("Automation"),
    SCHEDULING("Scheduling"),
    NETWORK("Network"),
    STORAGE("Storage"),
    SECURITY("Security")
}

data class DiagnosticCheckResult(
    val id: String = UUID.randomUUID().toString(),
    val category: DiagnosticCategory,
    val name: String,
    val status: DiagnosticStatus,
    val shortResult: String,
    val technicalDetails: Map<String, String> = emptyMap(),
    val durationMs: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)

data class SystemHealthReport(
    val timestamp: Long = System.currentTimeMillis(),
    val overallStatus: DiagnosticStatus,
    val checkResults: List<DiagnosticCheckResult>,
    val appVersion: String,
    val buildNumber: Int,
    val androidRelease: String,
    val apiLevel: Int,
    val deviceManufacturer: String,
    val deviceModel: String,
    val isRooted: Boolean
)

data class DiagnosticHistoryRecord(
    val id: String,
    val timestamp: Long,
    val category: DiagnosticCategory,
    val status: DiagnosticStatus,
    val shortResult: String,
    val durationMs: Long,
    val technicalDetails: Map<String, String>
)

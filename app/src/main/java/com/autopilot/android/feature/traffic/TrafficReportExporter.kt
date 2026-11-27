package com.autopilot.android.feature.traffic

import com.google.gson.GsonBuilder

object TrafficReportExporter {

    fun toJson(run: TrafficRun): String = GsonBuilder().setPrettyPrinting().create().toJson(run)

    fun toCsv(run: TrafficRun): String = buildString {
        append("field,value\n")
        row("id", run.id)
        row("website", run.websiteUrl)
        row("test_date", run.createdAt.toString())
        row("started_at", run.startedAt?.toString().orEmpty())
        row("completed_at", run.completedAt?.toString().orEmpty())
        row("planned_events", run.plannedEvents.toString())
        row("successful_submissions", run.submitted.toString())
        row("failed_submissions", run.failed.toString())
        row("pending_events", run.pending.toString())
        row("duration_seconds", run.durationSeconds().toString())
        row("status", run.status.name)
        row("mode", run.mode.label)
        row("speed", run.speed.label)
        row("events_per_minute", run.speed.eventsPerMinute.toString())
        row("concurrency", run.concurrency.toString())
        row("page_paths", run.pagePaths.joinToString(" | "))
        row("device_spoof", run.deviceCategory.label)
        row("last_error", run.lastError.orEmpty())
        append("note,\"Counters are HTTP responses, not GA4-recorded events\"\n")
    }

    private fun StringBuilder.row(key: String, value: String) {
        append(key).append(',').append(csv(value)).append('\n')
    }

    private fun csv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else escaped
    }
}

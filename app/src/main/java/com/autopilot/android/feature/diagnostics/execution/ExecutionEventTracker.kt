package com.autopilot.android.feature.diagnostics.execution

import android.net.Uri
import com.autopilot.android.data.local.dao.ExecutionEventDao
import com.autopilot.android.data.local.entity.ExecutionEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

class ExecutionEventTracker(
    private val executionEventDao: ExecutionEventDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    fun recordEvent(
        executionId: String,
        profileId: String,
        eventType: ExecutionEventType,
        errorCategory: ExecutionErrorCategory? = null,
        message: String
    ) {
        val sanitizedMessage = sanitizeSensitiveText(message)
        scope.launch {
            val entity = ExecutionEventEntity(
                id = UUID.randomUUID().toString(),
                executionId = executionId,
                profileId = profileId,
                timestamp = System.currentTimeMillis(),
                eventType = eventType.name,
                errorCategory = errorCategory?.name,
                message = sanitizedMessage
            )
            try {
                executionEventDao.insert(entity)
            } catch (e: Exception) {
                // Non-fatal if logging fails
            }
        }
    }

    suspend fun getEventsForExecution(executionId: String): List<ExecutionEvent> {
        return executionEventDao.getEventsForExecution(executionId).map { it.toDomain() }
    }

    fun getEventsForExecutionFlow(executionId: String): Flow<List<ExecutionEvent>> {
        return executionEventDao.getEventsForExecutionFlow(executionId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun pruneOldEvents(daysToKeep: Int = 7): Int {
        val cutoff = System.currentTimeMillis() - (daysToKeep.toLong() * 24 * 3600 * 1000)
        return executionEventDao.deleteOlderThan(cutoff)
    }

    suspend fun clearAll() {
        executionEventDao.clearAll()
    }

    private fun ExecutionEventEntity.toDomain(): ExecutionEvent {
        val parsedType = try {
            ExecutionEventType.valueOf(eventType)
        } catch (e: Exception) {
            ExecutionEventType.EXECUTION_FAILED
        }

        val parsedCategory = errorCategory?.let {
            try {
                ExecutionErrorCategory.valueOf(it)
            } catch (e: Exception) {
                ExecutionErrorCategory.UNKNOWN_ERROR
            }
        }

        return ExecutionEvent(
            id = id,
            executionId = executionId,
            profileId = profileId,
            timestamp = timestamp,
            eventType = parsedType,
            errorCategory = parsedCategory,
            message = message
        )
    }

    companion object {
        /**
         * Sanitizes a URL by stripping sensitive query parameters and user info credentials.
         */
        fun sanitizeUrl(rawUrl: String): String {
            if (rawUrl.isBlank()) return rawUrl
            return try {
                val queryIdx = rawUrl.indexOf('?')
                val hashIdx = rawUrl.indexOf('#')
                if (queryIdx != -1) {
                    val base = rawUrl.substring(0, queryIdx)
                    val hashPart = if (hashIdx != -1 && hashIdx > queryIdx) rawUrl.substring(hashIdx) else ""
                    "$base?[query-redacted]$hashPart"
                } else {
                    rawUrl
                }
            } catch (e: Exception) {
                "[invalid-url]"
            }
        }

        /**
         * Sanitizes arbitrary text messages to remove potential credentials and query params.
         */
        fun sanitizeSensitiveText(text: String): String {
            var sanitized = text
            // Redact URLs embedded in messages
            val urlRegex = Regex("""https?://[^\s'"]+""")
            sanitized = urlRegex.replace(sanitized) { matchResult ->
                sanitizeUrl(matchResult.value)
            }
            // Redact common password / key patterns
            val passwordRegex = Regex("""(?i)(password|secret|token|key)\s*[:=]\s*[^\s,;]+""")
            sanitized = passwordRegex.replace(sanitized) { matchResult ->
                val key = matchResult.groupValues[1]
                "$key=[REDACTED]"
            }
            return sanitized
        }
    }
}

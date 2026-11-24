package com.autopilot.android.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopilot.android.AutoPilotApplication
import com.autopilot.android.domain.model.ExecutionHistoryItem
import com.autopilot.android.domain.model.ProfileStatus
import com.autopilot.android.feature.diagnostics.execution.ExecutionErrorCategory
import com.autopilot.android.feature.diagnostics.execution.ExecutionEvent
import com.autopilot.android.feature.diagnostics.execution.ExecutionEventType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutionTroubleshootingScreen(
    executionId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as AutoPilotApplication
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val fullDateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    var historyItem by remember { mutableStateOf<ExecutionHistoryItem?>(null) }
    val events by app.executionEventTracker.getEventsForExecutionFlow(executionId).collectAsState(initial = emptyList())

    LaunchedEffect(executionId) {
        // Query matching history item
        val allHistory = app.database.executionHistoryDao().getAll()
        val match = allHistory.find { it.id == executionId }
        if (match != null) {
            historyItem = ExecutionHistoryItem(
                id = match.id,
                profileId = match.profileId,
                profileName = match.profileName,
                startedAt = match.startedAt,
                endedAt = match.endedAt,
                durationSeconds = match.durationSeconds,
                totalActions = match.totalActions,
                status = try { ProfileStatus.valueOf(match.status) } catch (e: Exception) { ProfileStatus.COMPLETED },
                errorMessage = match.errorMessage
            )
        }
    }

    // Determine primary error category from events or message
    val primaryErrorEvent = events.findLast { it.errorCategory != null }
    val errorCategory = primaryErrorEvent?.errorCategory ?: historyItem?.errorMessage?.let { inferErrorCategory(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Execution Troubleshooting",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // --- 1. Session Overview Card ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                historyItem?.profileName ?: "Automation Session",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.Black
                            )
                            val status = historyItem?.status ?: ProfileStatus.COMPLETED
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (status == ProfileStatus.COMPLETED) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                            ) {
                                Text(
                                    text = status.name,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (status == ProfileStatus.COMPLETED) Color(0xFF15803D) else Color(0xFFB91C1C)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        historyItem?.let { item ->
                            Text(
                                "Started: ${fullDateFormat.format(Date(item.startedAt))}",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Duration: ${item.durationSeconds}s", fontSize = 12.sp, color = Color(0xFF475569))
                                Text("Actions: ${item.totalActions}", fontSize = 12.sp, color = Color(0xFF475569))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Execution ID: $executionId",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            // --- 2. Error Breakdown & Recovery Suggestions (if failed) ---
            if (errorCategory != null || historyItem?.status == ProfileStatus.ERROR) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    errorCategory?.displayName ?: "Execution Error",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF991B1B)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = historyItem?.errorMessage ?: primaryErrorEvent?.message ?: "An unhandled error interrupted the automation.",
                                fontSize = 13.sp,
                                color = Color(0xFF7F1D1D)
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFFFCA5A5))
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                "Recommended Resolution:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF991B1B)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                errorCategory?.recoverySuggestion
                                    ?: "Inspect network connectivity and verify that all target URLs and proxies are reachable.",
                                fontSize = 12.sp,
                                color = Color(0xFF450A0A)
                            )

                            // Safe Retry Action
                            if (errorCategory?.safeToRetry == true && historyItem != null) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val profileId = historyItem?.profileId ?: return@launch
                                            val profile = app.profileRepository.getProfileById(profileId)
                                            if (profile != null) {
                                                if (app.automationCoordinator.engineState.value.status == ProfileStatus.RUNNING) {
                                                    Toast.makeText(context, "Another automation is currently running.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    app.automationCoordinator.start(profile)
                                                    Toast.makeText(context, "Restarting automation for ${profile.name}...", Toast.LENGTH_SHORT).show()
                                                    onNavigateBack()
                                                }
                                            } else {
                                                Toast.makeText(context, "Profile no longer exists.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Safe Retry Automation")
                                }
                            }
                        }
                    }
                }
            }

            // --- 3. Event Timeline ---
            item {
                Text(
                    "Execution Event Timeline (${events.size} Events)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (events.isEmpty()) {
                item {
                    Text(
                        "No structured events recorded for this execution session.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            } else {
                items(events, key = { it.id }) { event ->
                    TimelineItemRow(event, dateFormat)
                }
            }
        }
    }
}

@Composable
private fun TimelineItemRow(event: ExecutionEvent, dateFormat: SimpleDateFormat) {
    val isError = event.eventType == ExecutionEventType.EXECUTION_FAILED || event.errorCategory != null
    val dotColor = if (isError) Color(0xFFEF4444) else Color(0xFF3B82F6)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Bullet dot and time
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(76.dp)
        ) {
            Text(
                dateFormat.format(Date(event.timestamp)),
                fontSize = 10.sp,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Card content
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        event.eventType.displayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isError) Color(0xFFDC2626) else Color.Black
                    )
                    event.errorCategory?.let { cat ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFEE2E2)
                        ) {
                            Text(
                                cat.displayName,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB91C1C),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    event.message,
                    fontSize = 12.sp,
                    color = Color(0xFF475569)
                )
            }
        }
    }
}

private fun inferErrorCategory(errorMessage: String): ExecutionErrorCategory {
    val lower = errorMessage.lowercase()
    return when {
        lower.contains("proxy") && (lower.contains("auth") || lower.contains("credential") || lower.contains("password")) ->
            ExecutionErrorCategory.PROXY_AUTHENTICATION_FAILED
        lower.contains("proxy") ->
            ExecutionErrorCategory.PROXY_UNAVAILABLE
        lower.contains("network") || lower.contains("connect") || lower.contains("unreachable") ->
            ExecutionErrorCategory.NETWORK_UNAVAILABLE
        lower.contains("webview") ->
            ExecutionErrorCategory.WEBVIEW_INITIALIZATION_FAILED
        lower.contains("timeout") ->
            ExecutionErrorCategory.NAVIGATION_TIMEOUT
        lower.contains("memory") || lower.contains("oom") ->
            ExecutionErrorCategory.RESOURCE_LIMIT_REACHED
        lower.contains("permission") ->
            ExecutionErrorCategory.PERMISSION_DENIED
        else ->
            ExecutionErrorCategory.UNKNOWN_ERROR
    }
}

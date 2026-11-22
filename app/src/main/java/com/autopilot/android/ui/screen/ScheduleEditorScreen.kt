package com.autopilot.android.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autopilot.android.data.repository.ProfileRepository
import com.autopilot.android.data.repository.ScheduleRepository
import com.autopilot.android.domain.model.*
import com.autopilot.android.domain.scheduling.ScheduleCalculator
import com.autopilot.android.domain.scheduling.ScheduleValidator
import com.autopilot.android.feature.scheduling.service.AutomationScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    scheduleId: String?,
    scheduleRepository: ScheduleRepository,
    profileRepository: ProfileRepository,
    scheduler: AutomationScheduler,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val profiles by profileRepository.getAllProfilesFlow().collectAsState(initial = emptyList())

    var scheduleName by remember { mutableStateOf("") }
    var selectedProfileId by remember { mutableStateOf("") }
    var selectedScheduleType by remember { mutableStateOf(ScheduleType.ONE_TIME) }
    var selectedRecurrence by remember { mutableStateOf(RecurrenceRule.DAILY) }
    var targetEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis() + 60_000L) }
    var maxDurationText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(scheduleId) {
        if (!scheduleId.isNullOrBlank()) {
            val existing = scheduleRepository.getScheduleById(scheduleId)
            if (existing != null) {
                scheduleName = existing.scheduleName
                selectedProfileId = existing.profileId
                selectedScheduleType = existing.scheduleType
                selectedRecurrence = existing.recurrenceRule
                targetEpochMillis = existing.targetEpochMillis
                maxDurationText = existing.maxDurationMinutes?.toString() ?: ""
            }
        }
        isLoaded = true
    }

    LaunchedEffect(profiles) {
        if (selectedProfileId.isBlank() && profiles.isNotEmpty()) {
            selectedProfileId = profiles.first().id
        }
    }

    val selectedProfile = profiles.find { it.id == selectedProfileId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (scheduleId == null) "New Schedule" else "Edit Schedule") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val prof = selectedProfile
                            if (prof == null) {
                                errorMessage = "Please select an automation profile."
                                return@IconButton
                            }

                            val maxDur = maxDurationText.toIntOrNull()
                            val initialSchedule = AutomationSchedule(
                                id = scheduleId ?: UUID.randomUUID().toString(),
                                profileId = prof.id,
                                profileName = prof.name,
                                scheduleName = scheduleName.ifBlank { "Schedule: ${prof.name}" },
                                scheduleType = selectedScheduleType,
                                recurrenceRule = if (selectedScheduleType == ScheduleType.ONE_TIME) RecurrenceRule.NONE else selectedRecurrence,
                                targetEpochMillis = targetEpochMillis,
                                nextExecutionAt = targetEpochMillis,
                                lastExecutionAt = null,
                                timezoneId = ZoneId.systemDefault().id,
                                enabled = true,
                                maxDurationMinutes = maxDur,
                                status = ScheduleStatus.SCHEDULED,
                                lastError = null
                            )

                            // Validate
                            val validation = ScheduleValidator.validate(initialSchedule, profileExists = true)
                            if (validation is ScheduleValidator.ValidationResult.Invalid) {
                                errorMessage = validation.reason
                                return@IconButton
                            }

                            // Calculate next execution
                            val nextRun = ScheduleCalculator.calculateNextExecution(initialSchedule)
                            val finalSchedule = initialSchedule.copy(nextExecutionAt = nextRun)

                            scope.launch {
                                scheduleRepository.saveSchedule(finalSchedule)
                                scheduler.scheduleAlarm(finalSchedule)
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save Schedule")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (errorMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Section 1: General Info
            Text("Schedule Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = scheduleName,
                onValueChange = { scheduleName = it },
                label = { Text("Schedule Name") },
                placeholder = { Text("Morning Web Check") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Divider()

            // Section 2: Automation Profile Selection
            Text("Target Automation Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (profiles.isEmpty()) {
                Text(
                    "No profiles available. Create a profile first.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                profiles.forEach { profile ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedProfileId == profile.id) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        onClick = { selectedProfileId = profile.id }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedProfileId == profile.id,
                                onClick = { selectedProfileId = profile.id }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(profile.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${profile.urls.size} URLs • ${profile.tabCount} Tabs • ${profile.executionMode.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            Divider()

            // Section 3: Schedule Type (One-Time vs Recurring)
            Text("Execution Type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedScheduleType == ScheduleType.ONE_TIME,
                    onClick = { selectedScheduleType = ScheduleType.ONE_TIME },
                    label = { Text("One-Time Execution") },
                    leadingIcon = { Icon(Icons.Default.Event, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedScheduleType == ScheduleType.RECURRING,
                    onClick = { selectedScheduleType = ScheduleType.RECURRING },
                    label = { Text("Recurring Schedule") },
                    leadingIcon = { Icon(Icons.Default.Repeat, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (selectedScheduleType == ScheduleType.RECURRING) {
                Text("Recurrence Frequency", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                RecurrenceRule.values().filter { it != RecurrenceRule.NONE }.forEach { rule ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedRecurrence == rule) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        onClick = { selectedRecurrence = rule }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedRecurrence == rule,
                                onClick = { selectedRecurrence = rule }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(rule.displayName, fontWeight = FontWeight.SemiBold)
                                Text(rule.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }

            Divider()

            // Section 4: Target Timing & Presets
            Text("Scheduled Execution Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Trigger: ${dateFormat.format(Date(targetEpochMillis))} (${ZoneId.systemDefault().id})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Text("Quick Presets for Testing & Scheduling:", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { targetEpochMillis = System.currentTimeMillis() + 15_000L },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+15s (Test)", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { targetEpochMillis = System.currentTimeMillis() + 60_000L },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+1 Min", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { targetEpochMillis = System.currentTimeMillis() + 300_000L },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+5 Min", style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { targetEpochMillis = System.currentTimeMillis() + 3600_000L },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+1 Hour", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { targetEpochMillis = System.currentTimeMillis() + 86400_000L },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+1 Day", style = MaterialTheme.typography.labelSmall)
                }
            }

            Divider()

            // Section 5: Maximum Duration
            Text("Execution Limits (Optional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = maxDurationText,
                onValueChange = { maxDurationText = it.filter { char -> char.isDigit() } },
                label = { Text("Max Duration (Minutes)") },
                placeholder = { Text("e.g. 15 (leave blank for unlimited)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

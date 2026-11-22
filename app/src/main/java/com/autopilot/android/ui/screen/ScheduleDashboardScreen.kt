package com.autopilot.android.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.autopilot.android.data.repository.ScheduleRepository
import com.autopilot.android.domain.model.AutomationSchedule
import com.autopilot.android.domain.model.RecurrenceRule
import com.autopilot.android.domain.model.ScheduleStatus
import com.autopilot.android.domain.model.ScheduleType
import com.autopilot.android.feature.scheduling.service.AutomationScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDashboardScreen(
    scheduleRepository: ScheduleRepository,
    scheduler: AutomationScheduler,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (String?) -> Unit
) {
    val context = LocalContext.current
    val schedules by scheduleRepository.getAllSchedulesFlow().collectAsState(initial = emptyList())
    var selectedTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val canExactAlarm = remember { scheduler.canScheduleExactAlarms() }

    var showNotificationBanner by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        showNotificationBanner = !isGranted
    }

    val filterOptions = listOf("All", "Upcoming", "Recurring", "Inactive")
    val filteredSchedules = remember(schedules, selectedTab) {
        when (selectedTab) {
            1 -> schedules.filter { it.enabled && it.status == ScheduleStatus.SCHEDULED }
            2 -> schedules.filter { it.scheduleType == ScheduleType.RECURRING }
            3 -> schedules.filter { !it.enabled || it.status == ScheduleStatus.COMPLETED || it.status == ScheduleStatus.MISSED || it.status == ScheduleStatus.CANCELLED }
            else -> schedules
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scheduled Automations", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!canExactAlarm) {
                        IconButton(onClick = {}) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Exact Alarms Inexact Fallback",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToEditor(null) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Schedule")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Android 13+ Notification Permission Educational Banner
            if (showNotificationBanner) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Notifications Disabled",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { showNotificationBanner = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "AutoPilot uses notifications to display live foreground automation controls (pause, stop) and scheduled task alerts. Automation runs even if notifications are off.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Enable Notifications", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Notification Settings", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // Android 12+ Exact Alarm Warning Banner with Settings Link
            if (!canExactAlarm) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Exact alarm permission not granted. Schedules will fire via Android inexact battery-saver windows.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                TextButton(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Grant Exact Alarm Permission", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            // Tab Filters
            TabRow(selectedTabIndex = selectedTab) {
                filterOptions.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            if (filteredSchedules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.EventBusy,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No Schedules Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Schedule your automation profiles to run automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { onNavigateToEditor(null) }) {
                            Icon(Icons.Default.Add, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Schedule")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredSchedules, key = { it.id }) { schedule ->
                        ScheduleCard(
                            schedule = schedule,
                            onToggleEnabled = { enabled ->
                                scope.launch {
                                    scheduleRepository.updateEnabled(schedule.id, enabled)
                                    if (enabled) {
                                        scheduler.scheduleAlarm(schedule.copy(enabled = true))
                                    } else {
                                        scheduler.cancelAlarm(schedule.id, schedule.profileId)
                                    }
                                }
                            },
                            onEdit = { onNavigateToEditor(schedule.id) },
                            onDelete = {
                                scope.launch {
                                    scheduler.cancelAlarm(schedule.id, schedule.profileId)
                                    scheduleRepository.deleteSchedule(schedule.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleCard(
    schedule: AutomationSchedule,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (schedule.enabled) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Schedule Name + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = schedule.scheduleName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Profile: ${schedule.profileName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = when (schedule.status) {
                        ScheduleStatus.SCHEDULED -> MaterialTheme.colorScheme.primaryContainer
                        ScheduleStatus.RUNNING -> MaterialTheme.colorScheme.primary
                        ScheduleStatus.COMPLETED -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                        ScheduleStatus.MISSED, ScheduleStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        text = schedule.status.displayName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (schedule.status) {
                            ScheduleStatus.RUNNING -> MaterialTheme.colorScheme.onPrimary
                            ScheduleStatus.MISSED, ScheduleStatus.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details Row: Recurrence Chip + Next Execution
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(if (schedule.scheduleType == ScheduleType.ONE_TIME) "One-Time" else schedule.recurrenceRule.displayName) },
                    leadingIcon = {
                        Icon(
                            if (schedule.scheduleType == ScheduleType.ONE_TIME) Icons.Default.Event else Icons.Default.Repeat,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )

                if (schedule.nextExecutionAt != null) {
                    Text(
                        text = "Next: ${dateFormat.format(Date(schedule.nextExecutionAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else if (schedule.status == ScheduleStatus.COMPLETED) {
                    Text(
                        text = "Finished",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50)
                    )
                }
            }

            if (!schedule.lastError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Notice: ${schedule.lastError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // Bottom Actions: Enable Switch, Edit, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = schedule.enabled,
                        onCheckedChange = onToggleEnabled
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (schedule.enabled) "Enabled" else "Disabled",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Schedule")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Schedule", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

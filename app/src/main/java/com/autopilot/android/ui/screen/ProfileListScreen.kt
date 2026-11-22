package com.autopilot.android.ui.screen

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autopilot.android.data.repository.ProfileRepository
import com.autopilot.android.domain.model.AutomationProfile
import com.autopilot.android.domain.model.ProfileStatus
import com.autopilot.android.engine.AutomationCoordinator
import com.autopilot.android.feature.automation.service.AutomationService
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    profileRepository: ProfileRepository,
    coordinator: AutomationCoordinator,
    onNavigateToEditor: (String?) -> Unit,
    onNavigateToMonitor: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSchedules: () -> Unit
) {
    val profiles by profileRepository.getAllProfilesFlow().collectAsState(initial = emptyList())
    val engineState by coordinator.engineState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AutoPilot Profiles", fontWeight = FontWeight.Bold) },
                actions = {
                    if (engineState.status != ProfileStatus.IDLE) {
                        IconButton(onClick = onNavigateToMonitor) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "Active Monitor",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToSchedules) {
                        Icon(Icons.Default.Schedule, contentDescription = "Schedules")
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToEditor(null) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Profile")
            }
        }
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No Automation Profiles Found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Create your first profile to begin browser automation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { onNavigateToEditor(null) }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Profile")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    val isRunning = engineState.status == ProfileStatus.RUNNING && engineState.profile?.id == profile.id
                    val isPaused = engineState.status == ProfileStatus.PAUSED && engineState.profile?.id == profile.id

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = when {
                                        isRunning -> MaterialTheme.colorScheme.primary
                                        isPaused -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            isRunning -> "RUNNING"
                                            isPaused -> "PAUSED"
                                            else -> "READY"
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isRunning || isPaused) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            if (profile.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = profile.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("${profile.urls.size} URLs") },
                                    leadingIcon = { Icon(Icons.Default.Language, null, modifier = Modifier.size(16.dp)) }
                                )
                                AssistChip(
                                    onClick = {},
                                    label = { Text("${profile.tabCount} Tabs") },
                                    leadingIcon = { Icon(Icons.Default.Tab, null, modifier = Modifier.size(16.dp)) }
                                )
                                AssistChip(
                                    onClick = {},
                                    label = { Text(profile.executionMode.displayName.take(12) + "…") }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        scope.launch { profileRepository.duplicateProfile(profile.id) }
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate")
                                }

                                IconButton(
                                    onClick = { onNavigateToEditor(profile.id) }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                                }

                                IconButton(
                                    onClick = {
                                        scope.launch { profileRepository.deleteProfile(profile.id) }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                if (isRunning) {
                                    FilledTonalButton(
                                        onClick = {
                                            AutomationService.stopAutomation(context)
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    ) {
                                        Icon(Icons.Default.Stop, null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Stop")
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            AutomationService.startAutomation(context, profile.id)
                                            onNavigateToMonitor()
                                        }
                                    ) {
                                        Icon(Icons.Default.PlayArrow, null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Run")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

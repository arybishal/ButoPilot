package com.autopilot.android.ui.screen

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.autopilot.android.domain.model.ProfileStatus
import com.autopilot.android.engine.AutomationCoordinator
import com.autopilot.android.feature.automation.service.AutomationService
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationMonitorScreen(
    coordinator: AutomationCoordinator,
    onNavigateBack: () -> Unit
) {
    val engineState by coordinator.engineState.collectAsState()
    val activeTab by coordinator.webViewManager.activeTabIndex.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (engineState.isMultiRun) "Multi-Run (${engineState.activeProfiles.size} Automations)"
                        else engineState.profile?.name ?: "Live Monitor"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = when (engineState.status) {
                            ProfileStatus.RUNNING -> MaterialTheme.colorScheme.primary
                            ProfileStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.outline
                        }
                    ) {
                        Text(
                            text = engineState.status.name,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Metrics bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Elapsed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text("${engineState.elapsedSeconds}s", fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Actions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text("${engineState.totalActions}", fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Memory Free", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text("${engineState.memoryMb} MB", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Real-time Status banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            ) {
                Text(
                    text = engineState.statusMessage,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            // 10-Minute Proxy Auto-Rotation & Multi-Run Status Strip
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    if (engineState.isMultiRun && engineState.activeProfiles.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Multi-Run Active (${engineState.activeProfiles.size} Profiles):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(engineState.activeProfiles) { prof ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("${prof.name} (${prof.tabCount} tabs)", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Proxy",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = engineState.currentProxyName ?: "No Verified Proxy Active",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                engineState.currentProxyLocation?.let { location ->
                                    Text(
                                        text = "Exit location: $location",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                }
                                val minutes = engineState.nextProxyRotationSeconds / 60
                                val seconds = engineState.nextProxyRotationSeconds % 60
                                Text(
                                    text = "Auto-rotates healthy proxy in ${minutes}:${seconds.toString().padStart(2, '0')}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        TextButton(
                            onClick = { coordinator.rotateProxyNow() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rotate Now", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Tab bar (1 to 25 tabs)
            if (engineState.totalTabs > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items((0 until engineState.totalTabs).toList()) { index ->
                        FilterChip(
                            selected = activeTab == index,
                            onClick = { coordinator.webViewManager.selectTab(index) },
                            label = { Text("Tab ${index + 1}") }
                        )
                    }
                }
            }

            // Embedded live WebView preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (engineState.status != ProfileStatus.IDLE) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { container ->
                            container.removeAllViews()
                            val activeWv = coordinator.webViewManager.getActiveWebView()
                            if (activeWv != null) {
                                (activeWv.parent as? ViewGroup)?.removeView(activeWv)
                                container.addView(activeWv)
                            }
                        }
                    )
                } else {
                    Text("Automation idle. Start a profile to preview browser execution.", color = MaterialTheme.colorScheme.outline)
                }
            }

            // Bottom Execution Control Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (engineState.status == ProfileStatus.RUNNING) {
                        Button(
                            onClick = { AutomationService.pauseAutomation(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Pause, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pause")
                        }
                    } else if (engineState.status == ProfileStatus.PAUSED) {
                        Button(
                            onClick = { AutomationService.resumeAutomation(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Resume")
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            AutomationService.stopAutomation(context)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop")
                    }
                }
            }
        }
    }
}

package com.autopilot.android.ui.screen
import com.autopilot.android.ui.theme.AppColors

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopilot.android.feature.recovery.RecoveryManager
import com.autopilot.android.feature.recovery.RecoverySummary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryScreen(
    recoveryManager: RecoveryManager,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var recoverySummary by remember { mutableStateOf<RecoverySummary?>(null) }
    var activeActionName by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Recovery Center",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
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
            // Overview banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.BuildCircle,
                            contentDescription = null,
                            tint = Color(0xFF1D4ED8),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                "Self-Healing & System Repairs",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E3A8A)
                            )
                            Text(
                                "Quickly recover from crashes, fix orphaned database links, reconcile background tasks, and clean temp caches.",
                                fontSize = 12.sp,
                                color = Color(0xFF3B82F6)
                            )
                        }
                    }
                }
            }

            // One-tap full recovery button
            item {
                Button(
                    onClick = {
                        confirmMessage = "Perform full system self-healing? This will reset stuck automations, repair invalid proxy references, purge caches, reschedule background workers, and vacuum the database."
                        showConfirmDialog = {
                            scope.launch {
                                isProcessing = true
                                activeActionName = "Full System Recovery"
                                val summary = recoveryManager.performFullRecovery()
                                recoverySummary = summary
                                isProcessing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, contentColor = AppColors.OnPrimary),
                    enabled = !isProcessing
                ) {
                    if (isProcessing && activeActionName == "Full System Recovery") {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AppColors.OnPrimary)
                    } else {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("One-Tap Full System Self-Healing")
                    }
                }
            }

            item {
                Text(
                    "Individual Maintenance Actions",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // Action 1: Reset Stuck Automations
            item {
                RecoveryActionCard(
                    icon = Icons.Filled.RestartAlt,
                    title = "Reset Stuck Automations",
                    subtitle = "Reconciles profiles stuck in RUNNING/PAUSED back to IDLE and releases proxy locks.",
                    actionLabel = "Reset Profiles",
                    isLoading = isProcessing && activeActionName == "Reset Stuck Automations",
                    onClick = {
                        confirmMessage = "Reset any stuck RUNNING or PAUSED profiles back to IDLE?"
                        showConfirmDialog = {
                            scope.launch {
                                isProcessing = true
                                activeActionName = "Reset Stuck Automations"
                                val count = recoveryManager.resetStuckAutomations()
                                isProcessing = false
                                Toast.makeText(context, "Reset $count stuck automation profiles to IDLE", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            // Action 2: Repair Orphaned Proxy References
            item {
                RecoveryActionCard(
                    icon = Icons.Filled.LinkOff,
                    title = "Repair Orphaned Proxy References",
                    subtitle = "Removes deleted proxy IDs from profiles to prevent execution aborts.",
                    actionLabel = "Repair Links",
                    isLoading = isProcessing && activeActionName == "Repair Orphaned Proxies",
                    onClick = {
                        confirmMessage = "Scan profiles and unlink any deleted or non-existent proxy IDs?"
                        showConfirmDialog = {
                            scope.launch {
                                isProcessing = true
                                activeActionName = "Repair Orphaned Proxies"
                                val count = recoveryManager.repairOrphanedProxyReferences()
                                isProcessing = false
                                Toast.makeText(context, "Repaired $count profiles with missing proxy references", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            // Action 3: Clean Cache & Temporary Files
            item {
                RecoveryActionCard(
                    icon = Icons.Filled.CleaningServices,
                    title = "Purge Cache & Temp Backups",
                    subtitle = "Frees internal storage by safely removing WebView caches and staging files.",
                    actionLabel = "Clean Storage",
                    isLoading = isProcessing && activeActionName == "Clean Cache",
                    onClick = {
                        confirmMessage = "Purge application cache and temporary backup working directories?"
                        showConfirmDialog = {
                            scope.launch {
                                isProcessing = true
                                activeActionName = "Clean Cache"
                                val freedBytes = recoveryManager.cleanTemporaryAndCacheFiles()
                                isProcessing = false
                                Toast.makeText(context, "Freed ${freedBytes / 1024} KB of temporary storage", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            // Action 4: Reconcile Background Schedules
            item {
                RecoveryActionCard(
                    icon = Icons.Filled.ScheduleSend,
                    title = "Reconcile Background Schedules",
                    subtitle = "Synchronizes database schedules with AlarmManager & WorkManager workers.",
                    actionLabel = "Sync Schedules",
                    isLoading = isProcessing && activeActionName == "Reconcile Schedules",
                    onClick = {
                        confirmMessage = "Trigger immediate background reconciliation for all active schedules?"
                        showConfirmDialog = {
                            scope.launch {
                                isProcessing = true
                                activeActionName = "Reconcile Schedules"
                                val count = recoveryManager.reconcileSchedules()
                                isProcessing = false
                                Toast.makeText(context, "Reconciled $count active schedules with system workers", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            // Action 5: Verify & Vacuum Database
            item {
                RecoveryActionCard(
                    icon = Icons.Filled.Storage,
                    title = "Verify & Vacuum Database",
                    subtitle = "Runs SQLite PRAGMA integrity check and defragments database file.",
                    actionLabel = "Optimize DB",
                    isLoading = isProcessing && activeActionName == "Optimize DB",
                    onClick = {
                        confirmMessage = "Run SQLite integrity verification and defragment (VACUUM) the database?"
                        showConfirmDialog = {
                            scope.launch {
                                isProcessing = true
                                activeActionName = "Optimize DB"
                                val result = recoveryManager.verifyAndOptimizeDatabase()
                                isProcessing = false
                                Toast.makeText(context, "Database check result: $result", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
        }
    }

    // Confirmation dialog
    if (showConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = null },
            title = { Text("Confirm Recovery Action") },
            text = { Text(confirmMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        val action = showConfirmDialog
                        showConfirmDialog = null
                        action?.invoke()
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Summary dialog for Full Recovery
    recoverySummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { recoverySummary = null },
            title = { Text("Self-Healing Summary") },
            text = {
                Column {
                    Text("• Stuck Profiles Reset: ${summary.stuckProfilesReset}", fontSize = 13.sp)
                    Text("• Orphaned Proxies Repaired: ${summary.orphanedProxiesRepaired}", fontSize = 13.sp)
                    Text("• Cache Cleaned: ${summary.cacheBytesFreed / 1024} KB", fontSize = 13.sp)
                    Text("• Schedules Reconciled: ${summary.schedulesReconciled}", fontSize = 13.sp)
                    Text("• Database Integrity: ${summary.databaseIntegrityMessage}", fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(onClick = { recoverySummary = null }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun RecoveryActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.Black)
                    Text(subtitle, fontSize = 11.sp, color = Color(0xFF64748B))
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF1E293B)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                enabled = !isLoading,
                modifier = Modifier.height(36.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

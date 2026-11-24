package com.autopilot.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.autopilot.android.AutoPilotApplication
import com.autopilot.android.feature.diagnostics.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    diagnosticsRepository: DiagnosticsRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as AutoPilotApplication
    val scope = rememberCoroutineScope()

    val runner = remember {
        DiagnosticRunner(
            context = context,
            database = app.database,
            profileRepository = app.profileRepository,
            scheduleRepository = app.scheduleRepository,
            proxyRepository = app.proxyRepository,
            coordinator = app.automationCoordinator
        )
    }

    var isRunning by remember { mutableStateOf(false) }
    var healthReport by remember { mutableStateOf<SystemHealthReport?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Current Health, 1 = History
    val historyItems by diagnosticsRepository.getAllHistoryFlow().collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    fun runDiagnostics() {
        scope.launch {
            isRunning = true
            val results = runner.runAllChecks()
            val report = runner.generateHealthReport(results)
            healthReport = report
            diagnosticsRepository.saveCheckResults(results)
            isRunning = false
        }
    }

    LaunchedEffect(Unit) {
        runDiagnostics()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Diagnostics Center",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { runDiagnostics() },
                        enabled = !isRunning
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.Black
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Run Checks")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(padding)
        ) {
            // Tabs: Current Health vs History
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = Color.Black
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("System Health", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Diagnostic History (${historyItems.size})", fontWeight = FontWeight.SemiBold) }
                )
            }

            if (selectedTab == 0) {
                // --- TAB 0: Current System Health ---
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        healthReport?.let { report ->
                            OverallStatusCard(report)
                        }
                    }

                    item {
                        healthReport?.let { report ->
                            DeviceMetricsCard(report)
                        }
                    }

                    item {
                        Text(
                            text = "Diagnostic Checks",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    val checks = healthReport?.checkResults ?: emptyList()
                    items(checks, key = { it.id }) { check ->
                        DiagnosticCheckCard(check)
                    }
                }
            } else {
                // --- TAB 1: History ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    if (historyItems.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Recorded Diagnostic Runs",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            TextButton(
                                onClick = { scope.launch { diagnosticsRepository.clearHistory() } }
                            ) {
                                Text("Clear History", color = Color(0xFFDC2626), fontSize = 13.sp)
                            }
                        }
                    }

                    if (historyItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.HistoryToggleOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = Color(0xFF94A3B8)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "No Diagnostic History",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(historyItems, key = { it.id }) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                item.category.displayName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = Color.Black
                                            )
                                            StatusBadge(item.status)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            item.shortResult,
                                            fontSize = 13.sp,
                                            color = Color(0xFF334155)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                dateFormat.format(Date(item.timestamp)),
                                                fontSize = 11.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                            Text(
                                                "${item.durationMs}ms",
                                                fontSize = 11.sp,
                                                color = Color(0xFF94A3B8)
                                            )
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
}

@Composable
private fun OverallStatusCard(report: SystemHealthReport) {
    val (bgColor, iconColor, icon) = when (report.overallStatus) {
        DiagnosticStatus.HEALTHY -> Triple(Color(0xFFECFDF5), Color(0xFF10B981), Icons.Filled.CheckCircle)
        DiagnosticStatus.WARNING -> Triple(Color(0xFFFEFCE8), Color(0xFFEAB308), Icons.Filled.Warning)
        DiagnosticStatus.ERROR -> Triple(Color(0xFFFEF2F2), Color(0xFFEF4444), Icons.Filled.Error)
        else -> Triple(Color(0xFFF1F5F9), Color(0xFF64748B), Icons.Filled.Info)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "System Health: ${report.overallStatus.displayName}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "${report.checkResults.count { it.status == DiagnosticStatus.HEALTHY }} of ${report.checkResults.size} checks passed healthy",
                    fontSize = 13.sp,
                    color = Color(0xFF475569)
                )
            }
        }
    }
}

@Composable
private fun DeviceMetricsCard(report: SystemHealthReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Environment Metrics",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(10.dp))
            MetricRow("Application Version", "${report.appVersion} (Build ${report.buildNumber})")
            MetricRow("Android Release", "Android ${report.androidRelease} (API ${report.apiLevel})")
            MetricRow("Device", "${report.deviceManufacturer} ${report.deviceModel}")
            MetricRow("Root Privilege", if (report.isRooted) "Granted (Magisk/su)" else "Standard unrooted sandbox")
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = Color(0xFF64748B))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.Black)
    }
}

@Composable
private fun DiagnosticCheckCard(check: DiagnosticCheckResult) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    val icon = getCategoryIcon(check.category)
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = check.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color.Black
                        )
                        Text(
                            text = check.category.displayName,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
                StatusBadge(check.status)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = check.shortResult,
                fontSize = 13.sp,
                color = Color(0xFF334155)
            )

            if (expanded && check.technicalDetails.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFE2E8F0))
                Spacer(modifier = Modifier.height(8.dp))
                for ((key, value) in check.technicalDetails) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(key, fontSize = 12.sp, color = Color(0xFF64748B))
                        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: DiagnosticStatus) {
    val (bg, fg) = when (status) {
        DiagnosticStatus.HEALTHY -> Pair(Color(0xFFDCFCE7), Color(0xFF15803D))
        DiagnosticStatus.WARNING -> Pair(Color(0xFFFEF9C3), Color(0xFFA16207))
        DiagnosticStatus.ERROR -> Pair(Color(0xFFFEE2E2), Color(0xFFB91C1C))
        else -> Pair(Color(0xFFF1F5F9), Color(0xFF475569))
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = status.displayName,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}

private fun getCategoryIcon(category: DiagnosticCategory): ImageVector {
    return when (category) {
        DiagnosticCategory.DATABASE -> Icons.Filled.Storage
        DiagnosticCategory.AUTOMATION -> Icons.Filled.PlayCircle
        DiagnosticCategory.SCHEDULING -> Icons.Filled.Schedule
        DiagnosticCategory.NETWORK -> Icons.Filled.Public
        DiagnosticCategory.STORAGE -> Icons.Filled.Folder
        DiagnosticCategory.SECURITY -> Icons.Filled.Security
    }
}

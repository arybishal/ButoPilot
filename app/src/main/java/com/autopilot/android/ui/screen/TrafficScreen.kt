package com.autopilot.android.ui.screen

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopilot.android.data.repository.TrafficRepository
import com.autopilot.android.feature.traffic.TrafficController
import com.autopilot.android.feature.traffic.TrafficMode
import com.autopilot.android.feature.traffic.TrafficReportExporter
import com.autopilot.android.feature.traffic.TrafficRun
import com.autopilot.android.feature.traffic.TrafficSpeed
import com.autopilot.android.feature.traffic.TrafficStatus
import com.autopilot.android.feature.traffic.TrafficUrlValidator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficScreen(
    repository: TrafficRepository,
    controller: TrafficController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runs by repository.observeRuns().collectAsState(initial = emptyList())
    val activeRun = runs.firstOrNull { it.isActive }

    var urlText by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(TrafficMode.REAL_BROWSER) }
    var volume by remember { mutableFloatStateOf(10_000f) }
    var speed by remember { mutableStateOf(TrafficSpeed.NORMAL) }
    var concurrency by remember { mutableIntStateOf(2) }
    var pagesText by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    var isStarting by remember { mutableStateOf(false) }
    var expandedRunId by remember { mutableStateOf<String?>(null) }
    var exportRun by remember { mutableStateOf<TrafficRun?>(null) }

    val normalizedUrl = remember(urlText) { TrafficUrlValidator.normalize(urlText) }
    val (pagePaths, pageError) = remember(pagesText) { TrafficUrlValidator.parsePagePaths(pagesText) }
    val canStart = normalizedUrl != null && pageError == null && activeRun == null && !isStarting
    val volumeInt = volume.toInt()
    val estimateMinutes = volumeInt.toDouble() / (speed.eventsPerMinute * concurrency.coerceAtLeast(1))

    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            exportRun?.let { run ->
                context.writeText(uri, TrafficReportExporter.toJson(run))
            }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            exportRun?.let { run ->
                context.writeText(uri, TrafficReportExporter.toCsv(run))
            }
        }
    }

    fun requestExport(run: TrafficRun, asJson: Boolean) {
        exportRun = run
        if (asJson) jsonLauncher.launch("traffic-report-${run.id}.json")
        else csvLauncher.launch("traffic-report-${run.id}.csv")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Traffic Generator",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "High-Volume Visitor & Browser Engine",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- 1. Hero Feature Banner ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Traffic Simulation Engine",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Simulate active website visits with full browser headers, rotating mobile/desktop User-Agents, search referrers, or client-side JavaScript execution for Google Analytics and live site stats.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FeatureBadge(label = "Real Browser Sessions", icon = Icons.Default.Language)
                        FeatureBadge(label = "UA & Referrer Rotation", icon = Icons.Default.Refresh)
                        FeatureBadge(label = "Analytics Friendly", icon = Icons.Default.CheckCircle)
                    }
                }
            }

            // --- 2. Live Execution Dashboard Card (Visible when active) ---
            activeRun?.let { run ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.5.dp, Color(0xFF1565C0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (run.status == TrafficStatus.RUNNING) Color(0xFF10B981)
                                            else Color(0xFFF59E0B)
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Active Run: ${run.status.name}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE0F2FE)
                            ) {
                                Text(
                                    text = run.mode.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0284C7),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = run.websiteUrl,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF334155)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Progress Bar & Percentage
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Progress",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "${(run.progress * 100).toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1565C0)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { run.progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF1565C0),
                            trackColor = Color(0xFFE2E8F0)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Stat 4-Pill Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DashboardStatBox(
                                title = "Success",
                                value = String.format(Locale.US, "%,d", run.submitted),
                                valueColor = Color(0xFF10B981),
                                modifier = Modifier.weight(1f)
                            )
                            DashboardStatBox(
                                title = "Failed",
                                value = String.format(Locale.US, "%,d", run.failed),
                                valueColor = if (run.failed > 0) Color(0xFFEF4444) else Color(0xFF64748B),
                                modifier = Modifier.weight(1f)
                            )
                            DashboardStatBox(
                                title = "Pending",
                                value = String.format(Locale.US, "%,d", run.pending),
                                valueColor = Color(0xFF0F172A),
                                modifier = Modifier.weight(1f)
                            )
                            DashboardStatBox(
                                title = "Speed",
                                value = "${run.speed.eventsPerMinute}/m",
                                valueColor = Color(0xFF0284C7),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (!run.lastError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Note: ${run.lastError}",
                                fontSize = 12.sp,
                                color = Color(0xFFEF4444)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Control Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (run.status == TrafficStatus.RUNNING) {
                                OutlinedButton(
                                    onClick = { scope.launch { controller.pause(run.id) } },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFFF59E0B))
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pause", color = Color(0xFFF59E0B))
                                }
                            } else if (run.status == TrafficStatus.PAUSED) {
                                OutlinedButton(
                                    onClick = { scope.launch { controller.resume(run.id) } },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF1565C0))
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Resume", color = Color(0xFF1565C0))
                                }
                            }

                            Button(
                                onClick = { scope.launch { controller.stop(run.id) } },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop Test", color = Color.White)
                            }
                        }
                    }
                }
            }

            // --- 3. Main Configuration Card ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Website URL
                    Text(
                        text = "Website Target URL",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://yourwebsite.com") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = Color(0xFF64748B)
                            )
                        },
                        trailingIcon = {
                            if (urlText.isNotBlank()) {
                                IconButton(onClick = { urlText = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1565C0),
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        singleLine = true,
                        isError = urlText.isNotBlank() && normalizedUrl == null
                    )

                    if (urlText.isNotBlank() && normalizedUrl == null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Please enter a valid website URL (e.g. https://example.com)",
                            fontSize = 12.sp,
                            color = Color(0xFFEF4444)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Engine Mode Selection
                    Text(
                        text = "Traffic Generation Mode",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Mode Option 1: Real Browser
                    ModeCard(
                        title = "Real Browser (JS & Analytics)",
                        subtitle = "Executes full client-side JavaScript in WebViews. Triggers Google Analytics, GTM, Meta Pixel, and site trackers.",
                        icon = Icons.Default.Language,
                        isSelected = mode == TrafficMode.REAL_BROWSER,
                        onSelect = { mode = TrafficMode.REAL_BROWSER }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Mode Option 2: High-Speed HTTP/S
                    ModeCard(
                        title = "High-Speed HTTP/S Engine",
                        subtitle = "Fastest throughput with rotating browser User-Agents, Google/Bing search referrers, and canonical redirect tracking.",
                        icon = Icons.Default.Bolt,
                        isSelected = mode == TrafficMode.PAGE_REQUEST,
                        onSelect = { mode = TrafficMode.PAGE_REQUEST }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Traffic Volume
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Traffic Volume",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "${String.format(Locale.US, "%,d", volumeInt)} visitors",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1565C0)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        valueRange = 1_000f..100_000f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF1565C0),
                            activeTrackColor = Color(0xFF1565C0),
                            inactiveTrackColor = Color(0xFFE0F2FE)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1_000, 5_000, 10_000, 25_000, 50_000, 100_000).forEach { preset ->
                            FilterChip(
                                selected = volumeInt == preset,
                                onClick = { volume = preset.toFloat() },
                                label = { Text(String.format(Locale.US, "%,d", preset), fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Speed & Concurrency
                    Text(
                        text = "Speed & Dispatch Rate",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TrafficSpeed.entries.forEach { s ->
                            FilterChip(
                                selected = speed == s,
                                onClick = { speed = s },
                                label = { Text("${s.label} (${s.eventsPerMinute}/m)", fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Concurrency (Parallel Workers)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (1..4).forEach { n ->
                            FilterChip(
                                selected = concurrency == n,
                                onClick = { concurrency = n },
                                label = { Text("$n Worker${if (n > 1) "s" else ""}", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Optional Page Paths
                    OutlinedTextField(
                        value = pagesText,
                        onValueChange = { pagesText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Target Paths (optional, comma-separated)") },
                        placeholder = { Text("/pricing, /about, /features") },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1565C0),
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        singleLine = true,
                        isError = pageError != null,
                        supportingText = { pageError?.let { Text(it) } }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Duration estimate banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF1F5F9)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Estimated run time: ${formatEstimate(estimateMinutes)} at ${speed.eventsPerMinute * concurrency} total events/min",
                                fontSize = 13.sp,
                                color = Color(0xFF475569)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Start Action Button
                    Button(
                        onClick = { showConfirm = true },
                        enabled = canStart,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1565C0),
                            disabledContainerColor = Color(0xFFE2E8F0)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = if (canStart) Color.White else Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (activeRun != null) "A Traffic Test is Already Running" else "Start Traffic Engine",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (canStart) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            // --- 4. Execution History Section ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Traffic Run History",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE2E8F0)
                ) {
                    Text(
                        text = "${runs.size} recorded",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (runs.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("No traffic runs recorded yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
                            Text("Configure a target above to launch your first traffic test.", fontSize = 13.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    runs.take(20).forEach { run ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        expandedRunId = if (expandedRunId == run.id) null else run.id
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            StatusChip(status = run.status)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = run.websiteUrl,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF0F172A),
                                                maxLines = 1
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${formatTimestamp(run.createdAt)} • ${String.format(Locale.US, "%,d", run.plannedEvents)} planned • ${run.submitted} ok • ${run.failed} fail",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                    Icon(
                                        imageVector = if (expandedRunId == run.id) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = "Toggle",
                                        tint = Color(0xFF64748B)
                                    )
                                }

                                AnimatedVisibility(visible = expandedRunId == run.id) {
                                    Column(modifier = Modifier.padding(top = 12.dp)) {
                                        HorizontalDivider(color = Color(0xFFF1F5F9))
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            DashboardStatBox("Success", "${run.submitted}", Color(0xFF10B981), Modifier.weight(1f))
                                            DashboardStatBox("Failed", "${run.failed}", if (run.failed > 0) Color(0xFFEF4444) else Color(0xFF64748B), Modifier.weight(1f))
                                            DashboardStatBox("Duration", "${run.durationSeconds()}s", Color(0xFF0F172A), Modifier.weight(1f))
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Mode: ${run.mode.label} • Speed: ${run.speed.label} (${run.speed.eventsPerMinute}/m)", fontSize = 12.sp, color = Color(0xFF475569))
                                        if (!run.lastError.isNullOrBlank()) {
                                            Text("Note: ${run.lastError}", fontSize = 12.sp, color = Color(0xFFEF4444))
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = { requestExport(run, true) },
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Export JSON", fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = { requestExport(run, false) },
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Export CSV", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // --- Confirmation Dialog ---
    if (showConfirm && normalizedUrl != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = {
                Text(
                    text = "Launch Traffic Test",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConfirmItem("Target URL", normalizedUrl)
                    ConfirmItem("Planned Visitors", String.format(Locale.US, "%,d", volumeInt))
                    ConfirmItem("Engine Mode", mode.label)
                    ConfirmItem("Speed", "${speed.label} (${speed.eventsPerMinute}/min)")
                    ConfirmItem("Workers", "$concurrency parallel")
                    ConfirmItem("Estimated Duration", formatEstimate(estimateMinutes))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Requests will simulate realistic browser traffic with rotating mobile/desktop headers and referrers to test performance and analytics reporting.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isStarting) {
                            isStarting = true
                            scope.launch {
                                try {
                                    if (repository.getActiveRuns().isEmpty()) {
                                        val run = TrafficRun(
                                            id = UUID.randomUUID().toString(),
                                            websiteUrl = normalizedUrl,
                                            plannedEvents = volumeInt,
                                            mode = mode,
                                            speed = speed,
                                            concurrency = concurrency,
                                            pagePaths = pagePaths,
                                            status = TrafficStatus.QUEUED
                                        )
                                        repository.createRun(run)
                                        controller.start(run.id)
                                    }
                                } finally {
                                    isStarting = false
                                    showConfirm = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Confirm & Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        )
    }
}

@Composable
private fun FeatureBadge(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF1E293B)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, fontSize = 11.sp, color = Color(0xFFE2E8F0), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)
        ),
        border = BorderStroke(
            1.5.dp,
            if (isSelected) Color(0xFF10B981) else Color(0xFFE2E8F0)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFF10B981),
                    unselectedColor = Color(0xFF94A3B8)
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color(0xFF059669) else Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun DashboardStatBox(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

@Composable
private fun StatusChip(status: TrafficStatus) {
    val (bgColor, textColor) = when (status) {
        TrafficStatus.RUNNING -> Pair(Color(0xFFDCFCE7), Color(0xFF15803D))
        TrafficStatus.COMPLETED -> Pair(Color(0xFFE0F2FE), Color(0xFF0369A1))
        TrafficStatus.PAUSED -> Pair(Color(0xFFFEF3C7), Color(0xFFB45309))
        TrafficStatus.FAILED -> Pair(Color(0xFFFEE2E2), Color(0xFFB91C1C))
        else -> Pair(Color(0xFFF1F5F9), Color(0xFF475569))
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = status.name,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ConfirmItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = Color(0xFF64748B))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
    }
}

private fun formatEstimate(minutes: Double): String = when {
    minutes < 1 -> "under 1 minute"
    minutes < 60 -> "~${minutes.toInt().coerceAtLeast(1)} minute(s)"
    else -> "~${String.format(Locale.US, "%.1f", minutes / 60)} hour(s)"
}

private fun formatTimestamp(timestamp: Long?): String = timestamp?.let {
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(it))
} ?: "—"

private fun Context.writeText(uri: android.net.Uri, text: String) {
    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
}

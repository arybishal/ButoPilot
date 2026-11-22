package com.autopilot.android.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopilot.android.core.system.RootChecker
import com.autopilot.android.data.repository.ProfileRepository
import com.autopilot.android.data.repository.ScheduleRepository
import com.autopilot.android.domain.model.AutomationProfile
import com.autopilot.android.domain.model.ProfileStatus
import com.autopilot.android.engine.AutomationCoordinator
import kotlinx.coroutines.launch

@Composable
fun AutoPilotRobotLogo(modifier: Modifier = Modifier, color: Color = Color(0xFF1565C0)) {
    Canvas(modifier = modifier.size(30.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()

        // 1. Antenna: Top vertical line & small top circle
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.08f),
            end = Offset(w * 0.5f, h * 0.22f),
            strokeWidth = stroke
        )
        drawCircle(
            color = color,
            radius = w * 0.06f,
            center = Offset(w * 0.5f, h * 0.08f)
        )

        // 2. Ear tabs (left & right)
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.05f, h * 0.42f),
            size = Size(w * 0.08f, h * 0.28f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.87f, h * 0.42f),
            size = Size(w * 0.08f, h * 0.28f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        // 3. Head box
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.15f, h * 0.24f),
            size = Size(w * 0.70f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
            style = Stroke(width = stroke)
        )

        // 4. Eyes (circles)
        drawCircle(
            color = color,
            radius = w * 0.065f,
            center = Offset(w * 0.35f, h * 0.48f)
        )
        drawCircle(
            color = color,
            radius = w * 0.065f,
            center = Offset(w * 0.65f, h * 0.48f)
        )

        // 5. Friendly smile
        val smilePath = Path().apply {
            moveTo(w * 0.36f, h * 0.66f)
            quadraticBezierTo(w * 0.50f, h * 0.76f, w * 0.64f, h * 0.66f)
        }
        drawPath(
            path = smilePath,
            color = color,
            style = Stroke(width = stroke)
        )
    }
}

@Composable
fun HomeScreen(
    profileRepository: ProfileRepository,
    scheduleRepository: ScheduleRepository,
    coordinator: AutomationCoordinator,
    onNavigateToEditor: (String?) -> Unit,
    onNavigateToScheduleEditor: (String?) -> Unit,
    onNavigateToAutomationsTab: () -> Unit,
    onNavigateToProfileTab: () -> Unit,
    onNavigateToMonitor: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val profiles by profileRepository.getAllProfilesFlow().collectAsState(initial = emptyList())
    val schedules by scheduleRepository.getAllSchedulesFlow().collectAsState(initial = emptyList())
    val engineState by coordinator.engineState.collectAsState()

    var isRootGranted by remember { mutableStateOf(false) }
    var isBannerDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isRootGranted = RootChecker.checkRootAccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // --- 1. Top Header Row ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand: Robot Logo + Text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AutoPilotRobotLogo()
                Text(
                    text = "AutoPilot",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1565C0)
                )
            }

            // User Avatar: Purple circle with "T"
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4A148C))
                    .clickable { onNavigateToProfileTab() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "T",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // --- 2. Greeting & Subtitle ---
        Text(
            text = "Hello, The 👋",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Let's automate and boost your productivity.",
            fontSize = 15.sp,
            color = Color(0xFF64748B)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // --- 3. Primary CTA Button: + Start New Automation ---
        Button(
            onClick = { onNavigateToEditor(null) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1565C0)
            )
        ) {
            Text(
                text = "+ Start New Automation",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // --- 4. Root Access Banner Card ---
        if (!isRootGranted && !isBannerDismissed) {
            Spacer(modifier = Modifier.height(18.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF9E6)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = "Security Shield",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(34.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Root access not granted",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Device spoofing needs root. Tap Grant and approve AutoPilot in Magisk.",
                            fontSize = 13.sp,
                            color = Color(0xFF334155),
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isRootGranted = RootChecker.requestRootAccess()
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = "Grant",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Dismiss",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.clickable {
                                isBannerDismissed = true
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 5. Active Automations Section ---
        Text(
            text = "Active Automations",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (engineState.status != ProfileStatus.IDLE && engineState.profile != null) {
            val active = engineState.profile!!
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToMonitor() },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = active.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${engineState.totalTabs} tab(s) active • Status: ${engineState.status.name}",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Button(
                        onClick = { onNavigateToMonitor() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Monitor", fontSize = 13.sp, color = Color.White)
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F5)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF94A3B8)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play Icon",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "No active automations",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 6. Scheduled Section ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scheduled",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Text(
                text = "+ Add",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1565C0),
                modifier = Modifier.clickable { onNavigateToScheduleEditor(null) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (schedules.isEmpty()) {
            Text(
                text = "No schedules yet — add one to run automation automatically at a fixed time.",
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                lineHeight = 20.sp
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                schedules.take(3).forEach { schedule ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToScheduleEditor(schedule.id) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = schedule.scheduleName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Type: ${schedule.scheduleType.displayName} • Enabled: ${schedule.enabled}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 7. Recent Automations Section ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Automations",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Text(
                text = "View All",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1565C0),
                modifier = Modifier.clickable { onNavigateToAutomationsTab() }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (profiles.isEmpty()) {
            Text(
                text = "No automations created yet. Tap Start New Automation to begin.",
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                lineHeight = 20.sp
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profiles.take(3).forEach { profile ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToEditor(profile.id) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "${profile.urls.size} URL(s) • ${profile.tabCount} Tab(s) • ${profile.executionMode.displayName}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Button(
                                onClick = {
                                    coordinator.start(profile)
                                    onNavigateToMonitor()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Run", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

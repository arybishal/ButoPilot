package com.autopilot.android.ui.screen

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopilot.android.core.system.RootChecker
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToSchedules: () -> Unit,
    onNavigateToProxies: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isRootGranted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isRootGranted = RootChecker.checkRootAccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // --- 1. Top Title ---
        Text(
            text = "Profile",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // --- 2. User Info Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4A148C)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "T",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "The",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "AutoPilot Power User",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 3. System & Root Status ---
        Text(
            text = "System & Security",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = if (isRootGranted) Color(0xFF16A34A) else Color(0xFFEAB308),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Root Access (Magisk)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = if (isRootGranted) "Granted • Spoofing Enabled" else "Not Granted • Tap to request",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    if (!isRootGranted) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isRootGranted = RootChecker.requestRootAccess()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Grant", fontSize = 12.sp, color = Color.White)
                        }
                    } else {
                        Text(
                            text = "Active",
                            color = Color(0xFF16A34A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE2E8F0))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Device", fontSize = 14.sp, color = Color(0xFF64748B))
                    Text("${Build.MANUFACTURER} ${Build.MODEL}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Android Version", fontSize = 14.sp, color = Color(0xFF64748B))
                    Text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 4. Navigation Shortcuts ---
        Text(
            text = "Quick Access",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
        ) {
            Column {
                ProfileMenuItem(
                    icon = Icons.Filled.History,
                    title = "Execution History",
                    subtitle = "Review past automation runs and logs",
                    onClick = onNavigateToHistory
                )
                HorizontalDivider(color = Color(0xFFE2E8F0))
                ProfileMenuItem(
                    icon = Icons.Filled.Schedule,
                    title = "Schedules Dashboard",
                    subtitle = "Manage background timer and recurring triggers",
                    onClick = onNavigateToSchedules
                )
                HorizontalDivider(color = Color(0xFFE2E8F0))
                ProfileMenuItem(
                    icon = Icons.Filled.Public,
                    title = "VPN & Proxy Configurations",
                    subtitle = "Manage HTTP, HTTPS, and SOCKS5 proxies",
                    onClick = onNavigateToProxies
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 5. App Information ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("AutoPilot Version", fontSize = 14.sp, color = Color(0xFF64748B))
                    Text("1.0.0 (Production)", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Target SDK", fontSize = 14.sp, color = Color(0xFF64748B))
                    Text("35 (Android 15)", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF1565C0),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A)
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color(0xFF64748B)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(14.dp)
        )
    }
}

package com.autopilot.android.ui.screen

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopilot.android.data.local.DefaultProxySeedData
import com.autopilot.android.data.repository.ProxyRepository
import com.autopilot.android.domain.model.proxy.ProxyHealthStatus
import com.autopilot.android.feature.proxy.ProxyHealthChecker
import kotlinx.coroutines.launch
import java.util.Locale

data class RegionItem(
    val code: String, // "AUTO", "ID", "US", "UNKNOWN", "RU", "PH"
    val displayName: String,
    val flagEmoji: String,
    val healthyCount: Int,
    val totalCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyRegionScreen(
    proxyRepository: ProxyRepository,
    healthChecker: ProxyHealthChecker,
    onNavigateBack: () -> Unit,
    onNavigateToProxyManager: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("proxy_settings", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    val dbProxies by proxyRepository.getAllProxiesFlow().collectAsState(initial = emptyList())
    val maintenanceManager = remember { com.autopilot.android.AutoPilotApplication.instance.proxyMaintenanceManager }
    val maintenanceSettings by maintenanceManager.settings.collectAsState()
    val maintenanceStatus by maintenanceManager.status.collectAsState()
    val deletedLogs by maintenanceManager.deletedLogs.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }

    // Seed default proxy pool if database is empty or has 0 healthy proxies
    LaunchedEffect(Unit) {
        val all = proxyRepository.getAllProxies()
        if (all.isEmpty() || all.none { it.lastHealthStatus == ProxyHealthStatus.HEALTHY }) {
            proxyRepository.saveProxies(DefaultProxySeedData.getSeedProxies())
        }
    }

    // Selected region: persisted in SharedPreferences, "AUTO" by default
    var selectedRegionCode by remember {
        mutableStateOf(prefs.getString("preferred_proxy_region", "AUTO") ?: "AUTO")
    }

    // Build the region list dynamically from database
    val (totalProxies, healthyProxies, regionItems) = remember(dbProxies) {
        val dbHealthy = dbProxies.count { it.lastHealthStatus == ProxyHealthStatus.HEALTHY }
        val dbTotal = dbProxies.size

        val autoItem = RegionItem(
            code = "AUTO",
            displayName = "Any Region (Auto)",
            flagEmoji = "",
            healthyCount = dbHealthy,
            totalCount = dbTotal
        )

        // Preferred reference order
        val referenceRegions = listOf(
            Triple("ID", "Indonesia", "🇮🇩"),
            Triple("US", "United States", "🇺🇸"),
            Triple("UNKNOWN", "Global / Unknown", "🌐"),
            Triple("RU", "Russia", "🇷🇺"),
            Triple("PH", "Philippines", "🇵🇭")
        )

        val list = mutableListOf(autoItem)

        for ((code, name, flag) in referenceRegions) {
            val count = dbProxies.count {
                if (code == "UNKNOWN") it.region.isNullOrBlank() && it.lastHealthStatus == ProxyHealthStatus.HEALTHY
                else it.region?.equals(code, ignoreCase = true) == true && it.lastHealthStatus == ProxyHealthStatus.HEALTHY
            }
            val totalForRegion = dbProxies.count {
                if (code == "UNKNOWN") it.region.isNullOrBlank()
                else it.region?.equals(code, ignoreCase = true) == true
            }

            list.add(
                RegionItem(
                    code = code,
                    displayName = name,
                    flagEmoji = flag,
                    healthyCount = count,
                    totalCount = totalForRegion
                )
            )
        }

        // Also add any other active regions in the database
        val existingCodes = setOf("AUTO", "ID", "US", "UNKNOWN", "RU", "PH")
        dbProxies.mapNotNull { it.region?.trim()?.uppercase() }
            .filter { it.isNotBlank() && !existingCodes.contains(it) }
            .distinct()
            .sorted()
            .forEach { extraCode ->
                val count = dbProxies.count { it.region?.equals(extraCode, ignoreCase = true) == true && it.lastHealthStatus == ProxyHealthStatus.HEALTHY }
                val totalForRegion = dbProxies.count { it.region?.equals(extraCode, ignoreCase = true) == true }
                list.add(
                    RegionItem(
                        code = extraCode,
                        displayName = getCountryName(extraCode),
                        flagEmoji = getCountryFlag(extraCode),
                        healthyCount = count,
                        totalCount = totalForRegion
                    )
                )
            }

        Triple(dbTotal, dbHealthy, list)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Proxy Region",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.Black
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isRefreshing = true
                                if (dbProxies.isEmpty()) {
                                    proxyRepository.saveProxies(DefaultProxySeedData.getSeedProxies())
                                } else {
                                    val toTest = dbProxies.filter { it.enabled }.take(15)
                                    for (p in toTest) {
                                        val res = healthChecker.checkHealth(p)
                                        proxyRepository.updateHealthStatus(p.id, res.status, res.latencyMs, res.errorMessage)
                                    }
                                }
                                isRefreshing = false
                            }
                        }
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.Black
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.Black
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- 1. Proxy Management & Auto-Maintenance Settings (VPN Tab) ---
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Proxy Management Settings",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = maintenanceStatus.statusMessage,
                                    fontSize = 11.sp,
                                    color = Color(0xFF2563EB),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            TextButton(
                                onClick = { maintenanceManager.triggerImmediateCycle() },
                                enabled = !maintenanceStatus.isRunningCycle
                            ) {
                                Text(
                                    if (maintenanceStatus.isRunningCycle) "Running..." else "Check Now",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Auto Background Health Check", fontSize = 13.sp, color = Color(0xFF334155))
                            Switch(
                                checked = maintenanceSettings.autoBackgroundHealthCheck,
                                onCheckedChange = { checked ->
                                    maintenanceManager.updateSettings { it.copy(autoBackgroundHealthCheck = checked) }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Delete Dead Proxies", fontSize = 13.sp, color = Color(0xFF334155))
                                Text("${deletedLogs.size} deleted logged in Proxy Manager", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                            Switch(
                                checked = maintenanceSettings.autoDeleteDeadProxies,
                                onCheckedChange = { checked ->
                                    maintenanceManager.updateSettings { it.copy(autoDeleteDeadProxies = checked) }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Auto-Search & Add Healthy Proxies", fontSize = 13.sp, color = Color(0xFF334155))
                            Switch(
                                checked = maintenanceSettings.autoFetchHealthyProxies,
                                onCheckedChange = { checked ->
                                    maintenanceManager.updateSettings { it.copy(autoFetchHealthyProxies = checked) }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Min Healthy Pool:", fontSize = 12.sp, color = Color(0xFF64748B))
                            listOf(50, 100, 200).forEach { target ->
                                FilterChip(
                                    selected = maintenanceSettings.targetHealthyPoolSize == target,
                                    onClick = {
                                        maintenanceManager.updateSettings { it.copy(targetHealthyPoolSize = target) }
                                    },
                                    label = { Text("$target", fontSize = 11.sp) }
                                )
                            }
                        }

                        Button(
                            onClick = onNavigateToProxyManager,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2563EB),
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                "Open VPN & Proxy Management",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // --- 2. Top Health Summary Card ---
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToProxyManager() },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFFFF3C4)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = "$healthyProxies healthy / $totalProxies total",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (isRefreshing) "Testing proxy health..." else "Updated just now",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            // --- 3. Explanatory Subtitle Text ---
            item {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Preferred region — sessions will prioritize healthy HTTPS proxies from this country.",
                    fontSize = 14.sp,
                    color = Color(0xFF475569),
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // --- 4. Region Selection Cards ---
            items(regionItems, key = { it.code }) { item ->
                val isSelected = selectedRegionCode == item.code

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .clickable {
                            selectedRegionCode = item.code
                            prefs.edit().putString("preferred_proxy_region", item.code).apply()
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = if (isSelected) {
                        BorderStroke(2.dp, Color.Black)
                    } else {
                        BorderStroke(1.dp, Color(0xFFE2E8F0))
                    },
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Left Icon or Flag
                            if (item.code == "AUTO" || item.flagEmoji.isBlank()) {
                                Icon(
                                    imageVector = Icons.Filled.Public,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(34.dp)
                                )
                            } else {
                                Text(
                                    text = item.flagEmoji,
                                    fontSize = 28.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Name & Count
                            Column(
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (item.displayName.isNotBlank()) {
                                    Text(
                                        text = item.displayName,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                                Text(
                                    text = "${item.healthyCount} healthy ${if (item.healthyCount == 1) "proxy" else "proxies"} available",
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        // Right Blue Checkmark if Selected
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Selected",
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

        }
    }
}

private fun getCountryName(code: String): String {
    return try {
        val locale = Locale("", code.uppercase())
        val name = locale.getDisplayCountry(Locale.ENGLISH)
        if (name.isNotBlank()) name else code
    } catch (e: Exception) {
        code
    }
}

private fun getCountryFlag(countryCode: String): String {
    if (countryCode.length != 2) return ""
    val code = countryCode.uppercase()
    if (!code.all { it in 'A'..'Z' }) return ""
    return try {
        val firstChar = Character.toChars(0x1F1E6 + (code[0] - 'A'))
        val secondChar = Character.toChars(0x1F1E6 + (code[1] - 'A'))
        String(firstChar) + String(secondChar)
    } catch (e: Exception) {
        ""
    }
}

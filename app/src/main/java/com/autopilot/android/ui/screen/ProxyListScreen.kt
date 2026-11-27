package com.autopilot.android.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopilot.android.AutoPilotApplication
import com.autopilot.android.data.local.DefaultProxySeedData
import com.autopilot.android.data.repository.ProxyRepository
import com.autopilot.android.domain.model.proxy.CountryFilterRule
import com.autopilot.android.domain.model.proxy.ProxyConfiguration
import com.autopilot.android.domain.model.proxy.ProxyCredentials
import com.autopilot.android.domain.model.proxy.ProxyHealthStatus
import com.autopilot.android.domain.model.proxy.ProxyProtocol
import com.autopilot.android.domain.model.proxy.ProxyTestResult
import com.autopilot.android.feature.proxy.AutomaticProxyGenerator
import com.autopilot.android.feature.proxy.ProxyCompatibilityChecker
import com.autopilot.android.feature.proxy.ProxyGenerationRequest
import com.autopilot.android.feature.proxy.ProxyGenerationResult
import com.autopilot.android.feature.proxy.ProxyHealthChecker
import com.autopilot.android.feature.proxy.keepHealthyProxies
import com.autopilot.android.feature.proxy.ProxySourceManager
import com.autopilot.android.feature.proxy.ProxyTier
import com.autopilot.android.feature.proxy.source.GeonodeFetchResult
import com.autopilot.android.feature.proxy.source.GeonodeProxySourceAdapter
import com.autopilot.android.feature.proxy.source.ProxiflyProxySourceAdapter
import com.autopilot.android.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

fun getProxyCountryFlag(countryCode: String?): String {
    val code = countryCode?.trim()?.uppercase() ?: return "🌐"
    if (code.length != 2) return "🌐"
    return try {
        val firstChar = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
        String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    } catch (e: Exception) {
        "🌐"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyListScreen(
    proxyRepository: ProxyRepository,
    healthChecker: ProxyHealthChecker,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToEditor: (String?) -> Unit
) {
    val proxies by proxyRepository.getAllProxiesFlow().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current

    // Ensure default curated proxy pool is loaded if empty or has 0 healthy proxies
    LaunchedEffect(Unit) {
        val all = proxyRepository.getAllProxies()
        if (all.isEmpty() || all.none { it.lastHealthStatus == ProxyHealthStatus.HEALTHY }) {
            proxyRepository.saveProxies(DefaultProxySeedData.getSeedProxies())
        }
    }

    // Tabs: 0 -> Healthy, 1 -> Unhealthy, 2 -> All
    var selectedTab by remember { mutableStateOf(0) }

    // Search and filters
    var searchQuery by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf("ALL") }
    var allFilterMode by remember { mutableStateOf("ALL") } // ALL, UNTESTED, ENABLED, DISABLED

    // Selection on Unhealthy Tab
    var selectedUnhealthyIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Dialog state
    var showQuickAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    val generatorManager = remember { AutoPilotApplication.instance.proxyGeneratorManager }
    val generatorState by generatorManager.state.collectAsState()
    val maintenanceManager = remember { AutoPilotApplication.instance.proxyMaintenanceManager }
    val deletedLogs by maintenanceManager.deletedLogs.collectAsState()
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(generatorState.completedSummary) {
        generatorState.completedSummary?.let { summary ->
            snackbarHostState.showSnackbar(summary)
            generatorManager.clearCompletedSummary()
        }
    }
    var showDeleteAllUnhealthyDialog by remember { mutableStateOf(false) }
    var proxyToDelete by remember { mutableStateOf<ProxyConfiguration?>(null) }

    // Testing state
    var testingProxyId by remember { mutableStateOf<String?>(null) }
    var isTestingBatch by remember { mutableStateOf(false) }
    var batchTotalCount by remember { mutableIntStateOf(0) }
    var batchTestedCount by remember { mutableIntStateOf(0) }
    var batchHealthyCount by remember { mutableIntStateOf(0) }
    var batchUnhealthyCount by remember { mutableIntStateOf(0) }
    var batchCurrentProxyInfo by remember { mutableStateOf<String?>(null) }
    var batchJob by remember { mutableStateOf<Job?>(null) }
    val batchMutex = remember { Mutex() }

    fun startBatchTest(targets: List<ProxyConfiguration>) {
        if (targets.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("No proxies available to test.") }
            return
        }
        batchJob?.cancel()
        batchJob = scope.launch {
            isTestingBatch = true
            batchTotalCount = targets.size
            batchTestedCount = 0
            batchHealthyCount = 0
            batchUnhealthyCount = 0
            batchCurrentProxyInfo = null

            val chunks = targets.chunked(6)
            for (chunk in chunks) {
                if (!isActive) break
                val deferreds = chunk.map { p ->
                    async {
                        if (!isActive) return@async
                        batchCurrentProxyInfo = "${p.host}:${p.port}"
                        val res = withContext(Dispatchers.IO) {
                            healthChecker.checkHealth(p)
                        }
                        if (res.status == ProxyHealthStatus.HEALTHY) {
                            proxyRepository.updateHealthStatus(p.id, res.status, res.latencyMs, res.errorMessage)
                        } else {
                            maintenanceManager.dropDeadProxy(p, res.errorMessage ?: res.status.displayName)
                        }
                        batchMutex.withLock {
                            batchTestedCount++
                            if (res.status == ProxyHealthStatus.HEALTHY) {
                                batchHealthyCount++
                            } else {
                                batchUnhealthyCount++
                            }
                        }
                    }
                }
                deferreds.awaitAll()
            }
            isTestingBatch = false
            batchCurrentProxyInfo = null
            snackbarHostState.showSnackbar("Batch test complete: $batchHealthyCount healthy, $batchUnhealthyCount unhealthy ($batchTestedCount total)")
        }
    }

    // Segregate proxies
    val healthyList = remember(proxies) {
        proxies.filter { it.lastHealthStatus == ProxyHealthStatus.HEALTHY }
            .sortedBy { it.lastLatencyMs ?: Long.MAX_VALUE }
    }
    val unhealthyList = remember(proxies) {
        proxies.filter {
            it.lastHealthStatus == ProxyHealthStatus.UNHEALTHY ||
            it.lastHealthStatus == ProxyHealthStatus.TIMEOUT ||
            it.lastHealthStatus == ProxyHealthStatus.AUTH_FAILURE
        }
    }
    val untestedList = remember(proxies) {
        proxies.filter { it.lastHealthStatus == ProxyHealthStatus.UNTESTED }
    }

    // Available countries across list
    val availableCountries = remember(proxies) {
        proxies.mapNotNull { it.region?.trim()?.uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    // Filter by search & country
    fun filterList(list: List<ProxyConfiguration>): List<ProxyConfiguration> {
        return list.filter { proxy ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                proxy.name.contains(searchQuery, ignoreCase = true) ||
                proxy.host.contains(searchQuery, ignoreCase = true) ||
                proxy.port.toString().contains(searchQuery) ||
                (proxy.region?.contains(searchQuery, ignoreCase = true) == true)
            }
            val matchesCountry = when (selectedCountry) {
                "ALL" -> true
                "UNKNOWN" -> proxy.region.isNullOrBlank()
                else -> proxy.region?.equals(selectedCountry, ignoreCase = true) == true
            }
            matchesSearch && matchesCountry
        }
    }

    val filteredHealthy = remember(healthyList, searchQuery, selectedCountry) {
        filterList(healthyList)
    }
    val filteredUnhealthy = remember(unhealthyList, searchQuery, selectedCountry) {
        filterList(unhealthyList)
    }
    val filteredAll = remember(proxies, searchQuery, selectedCountry, allFilterMode) {
        filterList(proxies).filter { proxy ->
            when (allFilterMode) {
                "UNTESTED" -> proxy.lastHealthStatus == ProxyHealthStatus.UNTESTED
                "ENABLED" -> proxy.enabled
                "DISABLED" -> !proxy.enabled
                else -> true
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "VPN & Proxy Management",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "${proxies.size} proxies • ${healthyList.size} healthy",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    // Test All on Current Tab / All
                    IconButton(
                        onClick = {
                            val targets = when (selectedTab) {
                                0 -> healthyList
                                1 -> unhealthyList
                                else -> proxies
                            }
                            startBatchTest(targets)
                        },
                        enabled = !isTestingBatch
                    ) {
                        if (isTestingBatch) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Speed, contentDescription = "Test All", tint = Color(0xFF2563EB))
                        }
                    }

                    // Import Button
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Import List", tint = Color(0xFF475569))
                    }

                    // More Options Dropdown
                    var showTopMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color(0xFF475569))
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add Proxy") },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    showQuickAddDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Automatic Proxy Generator") },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    generatorManager.openDialog()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Restore Default Proxies (200)") },
                                leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    scope.launch {
                                        proxyRepository.saveProxies(DefaultProxySeedData.getSeedProxies())
                                        snackbarHostState.showSnackbar("Restored 200 default healthy proxies.")
                                    }
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuickAddDialog = true },
                containerColor = AppColors.Primary,
                contentColor = AppColors.OnPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Quick Add Proxy")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- 1. Metrics Overview Header Dashboard ---
            item(key = "metrics_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${proxies.size}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A)
                            )
                            Text("Total", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                        }

                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .width(1.dp)
                                .background(Color(0xFFE2E8F0))
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${healthyList.size}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF16A34A)
                            )
                            Text("Healthy", fontSize = 11.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .width(1.dp)
                                .background(Color(0xFFE2E8F0))
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${unhealthyList.size}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFDC2626)
                            )
                            Text("Unhealthy", fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .width(1.dp)
                                .background(Color(0xFFE2E8F0))
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${untestedList.size}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF64748B)
                            )
                            Text("Untested", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // --- Action: Test All Proxies at Once ---
            item(key = "test_all_row") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { startBatchTest(proxies) },
                        enabled = !isTestingBatch && proxies.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF93C5FD),
                            disabledContentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                    ) {
                        if (isTestingBatch) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Testing ($batchTestedCount / $batchTotalCount)...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        } else {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Test All Proxies (${proxies.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    if (isTestingBatch) {
                        OutlinedButton(
                            onClick = {
                                batchJob?.cancel()
                                isTestingBatch = false
                            },
                            modifier = Modifier.height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFDC2626)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                        ) {
                            Text("Stop", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { generatorManager.openDialog() },
                            modifier = Modifier.height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF2563EB)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2563EB)),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (generatorState.isRunning) "Generator..." else "Auto Generate",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // --- Background Automatic Proxy Generator Status Card ---
            if (generatorState.isRunning && !generatorState.isDialogVisible) {
                item(key = "background_generator_card") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        border = BorderStroke(1.dp, Color(0xFF93C5FD))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF2563EB)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = generatorState.statusMessage ?: "Generating proxies in background...",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF1E3A8A),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = { generatorManager.openDialog() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Pop-up", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    TextButton(
                                        onClick = { generatorManager.cancel() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                                    ) {
                                        Text("Stop", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            if (generatorState.totalToCheck > 0) {
                                LinearProgressIndicator(
                                    progress = { generatorState.progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFF2563EB),
                                    trackColor = Color(0xFFDBEAFE)
                                )
                            }
                        }
                    }
                }
            }

            // --- Live Number Form Progress Card during Batch Testing ---
            if (isTestingBatch) {
                item(key = "testing_progress_card") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF2563EB)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Testing Proxies Live...",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                }

                                val pct = if (batchTotalCount > 0) ((batchTestedCount.toFloat() / batchTotalCount) * 100).toInt() else 0
                                Text(
                                    text = "$pct%",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF2563EB)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val progressFraction = if (batchTotalCount > 0) (batchTestedCount.toFloat() / batchTotalCount) else 0f
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF2563EB),
                                trackColor = Color(0xFFE2E8F0)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Loading numbers form: Total, Tested, Healthy, Unhealthy
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Total
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$batchTotalCount",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF334155)
                                    )
                                    Text("Total", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                                }

                                // Tested
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$batchTestedCount",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF2563EB)
                                    )
                                    Text("Tested", fontSize = 11.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                                }

                                // Healthy
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$batchHealthyCount",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF16A34A)
                                    )
                                    Text("Healthy", fontSize = 11.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                                }

                                // Unhealthy
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$batchUnhealthyCount",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = Color(0xFFDC2626)
                                    )
                                    Text("Unhealthy", fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                }
                            }

                            if (!batchCurrentProxyInfo.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Probing: $batchCurrentProxyInfo",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // --- 2. Segmented Navigation Tabs ---
            item(key = "tab_row") {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 16.dp,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (selectedTab == 0) Color(0xFF16A34A) else Color(0xFF94A3B8)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Healthy (${healthyList.size})",
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    )

                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (selectedTab == 1) Color(0xFFDC2626) else Color(0xFF94A3B8)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Unhealthy (${unhealthyList.size})",
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = if (selectedTab == 1) Color(0xFFDC2626) else Color(0xFF0F172A)
                                )
                            }
                        }
                    )

                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Layers,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (selectedTab == 2) Color(0xFF2563EB) else Color(0xFF94A3B8)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "All (${proxies.size})",
                                    fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    )

                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (selectedTab == 3) Color(0xFFDC2626) else Color(0xFF94A3B8)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Deleted (${deletedLogs.size})",
                                    fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    )
                }
            }

            // --- 3. Search & Region Filter Bar ---
            item(key = "search_filter_bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search IP, port, region...", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF94A3B8))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = AppColors.Primary,
                            unfocusedBorderColor = Color(0xFFE2E8F0)
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                    )

                    if (selectedTab == 2) {
                        var showFilterMenu by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { showFilterMenu = true },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF475569))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(allFilterMode, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                            }
                            DropdownMenu(
                                expanded = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false }
                            ) {
                                listOf("ALL", "UNTESTED", "ENABLED", "DISABLED").forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt, fontSize = 13.sp) },
                                        onClick = {
                                            allFilterMode = opt
                                            showFilterMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Region pills (if available)
            if (availableCountries.isNotEmpty()) {
                item(key = "region_chips") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedCountry == "ALL",
                            onClick = { selectedCountry = "ALL" },
                            label = { Text("All Regions", fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                        availableCountries.forEach { c ->
                            val flag = getProxyCountryFlag(c)
                            FilterChip(
                                selected = selectedCountry == c,
                                onClick = { selectedCountry = c },
                                label = { Text("$flag $c", fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }

            // --- 4. Tab Body Content ---
            when (selectedTab) {
                0 -> {
                    // HEALTHY TAB
                    item(key = "healthy_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF16A34A))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "${filteredHealthy.size} Healthy & Verified Proxies",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF16A34A)
                                )
                            }
                            Text(
                                "Sorted by lowest latency",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    if (filteredHealthy.isEmpty()) {
                        item(key = "healthy_empty") {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                EmptyProxyState(
                                    title = if (healthyList.isEmpty()) "No Healthy Proxies" else "No matching healthy proxies",
                                    description = if (healthyList.isEmpty())
                                        "None of your proxies have passed health verification yet. Tap 'Test All' or import verified proxies."
                                    else "Try clearing your search query or region filter.",
                                    actionText = if (healthyList.isEmpty()) "Import Free Proxies" else null,
                                    onAction = { showImportDialog = true }
                                )
                            }
                        }
                    } else {
                        items(filteredHealthy, key = { it.id }) { proxy ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                ModernProxyCard(
                                    proxy = proxy,
                                    isSelected = false,
                                    showSelection = false,
                                    isTesting = testingProxyId == proxy.id,
                                    onSelectToggle = {},
                                    onTestClick = {
                                        scope.launch {
                                            testingProxyId = proxy.id
                                            val result = healthChecker.checkHealth(proxy)
                                            proxyRepository.updateHealthStatus(proxy.id, result.status, result.latencyMs, result.errorMessage)
                                            testingProxyId = null
                                            snackbarHostState.showSnackbar("${proxy.host}:${proxy.port} -> ${result.status.displayName} (${result.latencyMs ?: 0}ms), exit ${result.locationLabel}")
                                        }
                                    },
                                    onToggleEnabled = { enabled ->
                                        scope.launch { proxyRepository.updateEnabled(proxy.id, enabled) }
                                    },
                                    onCopyClick = {
                                        clipboardManager.setText(AnnotatedString("${proxy.host}:${proxy.port}"))
                                        scope.launch { snackbarHostState.showSnackbar("Copied ${proxy.host}:${proxy.port}") }
                                    },
                                    onEditClick = { onNavigateToEditor(proxy.id) },
                                    onDeleteClick = { proxyToDelete = proxy }
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // UNHEALTHY TAB
                    item(key = "unhealthy_batch_card") {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                                border = BorderStroke(1.dp, Color(0xFFFECACA))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            selectedUnhealthyIds = if (selectedUnhealthyIds.size == filteredUnhealthy.size && filteredUnhealthy.isNotEmpty()) {
                                                emptySet()
                                            } else {
                                                filteredUnhealthy.map { it.id }.toSet()
                                            }
                                        }
                                    ) {
                                        Checkbox(
                                            checked = filteredUnhealthy.isNotEmpty() && selectedUnhealthyIds.size == filteredUnhealthy.size,
                                            onCheckedChange = { checked ->
                                                selectedUnhealthyIds = if (checked) filteredUnhealthy.map { it.id }.toSet() else emptySet()
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Column {
                                            Text(
                                                text = if (selectedUnhealthyIds.isEmpty()) "Select All (${filteredUnhealthy.size})"
                                                else "${selectedUnhealthyIds.size} of ${filteredUnhealthy.size} Selected",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF991B1B)
                                            )
                                            Text(
                                                text = "Bulk removal of dead endpoints",
                                                fontSize = 11.sp,
                                                color = Color(0xFFB91C1C)
                                            )
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (selectedUnhealthyIds.isNotEmpty()) {
                                            Button(
                                                onClick = { showDeleteSelectedDialog = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Delete (${selectedUnhealthyIds.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        if (unhealthyList.isNotEmpty()) {
                                            OutlinedButton(
                                                onClick = { showDeleteAllUnhealthyDialog = true },
                                                border = BorderStroke(1.dp, Color(0xFFDC2626)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Sweep All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (filteredUnhealthy.isEmpty()) {
                        item(key = "unhealthy_empty") {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                EmptyProxyState(
                                    title = if (unhealthyList.isEmpty()) "No Unhealthy Proxies 🎉" else "No matching unhealthy proxies",
                                    description = if (unhealthyList.isEmpty())
                                        "Great job! All your configured proxies are healthy, verified, or untested."
                                    else "Try clearing your search query or region filter.",
                                    actionText = null,
                                    onAction = {}
                                )
                            }
                        }
                    } else {
                        items(filteredUnhealthy, key = { it.id }) { proxy ->
                            val isSelected = selectedUnhealthyIds.contains(proxy.id)
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                ModernProxyCard(
                                    proxy = proxy,
                                    isSelected = isSelected,
                                    showSelection = true,
                                    isTesting = testingProxyId == proxy.id,
                                    onSelectToggle = {
                                        selectedUnhealthyIds = if (isSelected) {
                                            selectedUnhealthyIds - proxy.id
                                        } else {
                                            selectedUnhealthyIds + proxy.id
                                        }
                                    },
                                    onTestClick = {
                                        scope.launch {
                                            testingProxyId = proxy.id
                                            val result = healthChecker.checkHealth(proxy)
                                            if (result.status == ProxyHealthStatus.HEALTHY) {
                                                proxyRepository.updateHealthStatus(proxy.id, result.status, result.latencyMs, result.errorMessage)
                                                snackbarHostState.showSnackbar("${proxy.host}:${proxy.port} -> Healthy (${result.latencyMs ?: 0}ms)")
                                            } else {
                                                maintenanceManager.dropDeadProxy(proxy, result.errorMessage ?: result.status.displayName)
                                                snackbarHostState.showSnackbar("Dropped dead proxy ${proxy.host}:${proxy.port} (${result.status.displayName})")
                                            }
                                            testingProxyId = null
                                        }
                                    },
                                    onToggleEnabled = { enabled ->
                                        scope.launch { proxyRepository.updateEnabled(proxy.id, enabled) }
                                    },
                                    onCopyClick = {
                                        clipboardManager.setText(AnnotatedString("${proxy.host}:${proxy.port}"))
                                        scope.launch { snackbarHostState.showSnackbar("Copied ${proxy.host}:${proxy.port}") }
                                    },
                                    onEditClick = { onNavigateToEditor(proxy.id) },
                                    onDeleteClick = { proxyToDelete = proxy }
                                )
                            }
                        }
                    }
                }

                2 -> {
                    // ALL PROXIES TAB
                    item(key = "all_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "All Configured Proxies (${filteredAll.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF334155)
                            )
                            TextButton(
                                onClick = { showImportDialog = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (filteredAll.isEmpty()) {
                        item(key = "all_empty") {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                EmptyProxyState(
                                    title = if (proxies.isEmpty()) "No Proxies Found" else "No matching proxies",
                                    description = if (proxies.isEmpty())
                                        "You currently have 0 proxies in the database. Tap below to reload the 200 default healthy proxies or quick-add your own."
                                    else "Try clearing your search query or filter.",
                                    actionText = if (proxies.isEmpty()) "Load Default Proxies (200)" else null,
                                    onAction = {
                                        scope.launch {
                                            proxyRepository.saveProxies(DefaultProxySeedData.getSeedProxies())
                                            snackbarHostState.showSnackbar("Loaded 200 default healthy proxies.")
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        items(filteredAll, key = { it.id }) { proxy ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                ModernProxyCard(
                                    proxy = proxy,
                                    isSelected = false,
                                    showSelection = false,
                                    isTesting = testingProxyId == proxy.id,
                                    onSelectToggle = {},
                                    onTestClick = {
                                        scope.launch {
                                            testingProxyId = proxy.id
                                            val result = healthChecker.checkHealth(proxy)
                                            if (result.status == ProxyHealthStatus.HEALTHY) {
                                                proxyRepository.updateHealthStatus(proxy.id, result.status, result.latencyMs, result.errorMessage)
                                                snackbarHostState.showSnackbar("${proxy.host}:${proxy.port} -> Healthy (${result.latencyMs ?: 0}ms)")
                                            } else {
                                                maintenanceManager.dropDeadProxy(proxy, result.errorMessage ?: result.status.displayName)
                                                snackbarHostState.showSnackbar("Dropped dead proxy ${proxy.host}:${proxy.port} (${result.status.displayName})")
                                            }
                                            testingProxyId = null
                                        }
                                    },
                                    onToggleEnabled = { enabled ->
                                        scope.launch { proxyRepository.updateEnabled(proxy.id, enabled) }
                                    },
                                    onCopyClick = {
                                        clipboardManager.setText(AnnotatedString("${proxy.host}:${proxy.port}"))
                                        scope.launch { snackbarHostState.showSnackbar("Copied ${proxy.host}:${proxy.port}") }
                                    },
                                    onEditClick = { onNavigateToEditor(proxy.id) },
                                    onDeleteClick = { proxyToDelete = proxy }
                                )
                            }
                        }
                    }
                }

                else -> {
                    // DELETED DEAD PROXIES LOG TAB
                    item(key = "deleted_log_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Auto-Deleted Dead Proxies (${deletedLogs.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF334155)
                            )
                            if (deletedLogs.isNotEmpty()) {
                                TextButton(
                                    onClick = { maintenanceManager.clearDeletedLogs() },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Clear Log", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                }
                            }
                        }
                    }

                    if (deletedLogs.isEmpty()) {
                        item(key = "deleted_log_empty") {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                EmptyProxyState(
                                    title = "No Deleted Proxies Logged",
                                    description = "Dead or disconnected proxies dropped during health checks or automations will appear here.",
                                    actionText = null,
                                    onAction = {}
                                )
                            }
                        }
                    } else {
                        items(deletedLogs, key = { it.id }) { entry ->
                            val timeText = remember(entry.deletedAt) {
                                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                    .format(java.util.Date(entry.deletedAt))
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                                border = BorderStroke(1.dp, Color(0xFFFECACA))
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
                                            text = "${getProxyCountryFlag(entry.region)} ${entry.protocol} ${entry.host}:${entry.port}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF0F172A),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = entry.reason,
                                            fontSize = 11.sp,
                                            color = Color(0xFFB91C1C),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = timeText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(88.dp))
            }
        }
    }

    // --- DIALOGS ---

    // 1. Quick Add Proxy Dialog
    if (showQuickAddDialog) {
        QuickAddProxyDialog(
            onDismiss = { showQuickAddDialog = false },
            onSave = { newProxy, testImmediately ->
                scope.launch {
                    proxyRepository.saveProxy(newProxy)
                    showQuickAddDialog = false
                    snackbarHostState.showSnackbar("Added proxy ${newProxy.host}:${newProxy.port}")
                    if (testImmediately) {
                        testingProxyId = newProxy.id
                        val result = healthChecker.checkHealth(newProxy)
                        proxyRepository.updateHealthStatus(newProxy.id, result.status, result.latencyMs, result.errorMessage)
                        testingProxyId = null
                        snackbarHostState.showSnackbar("Test result: ${result.status.displayName} (${result.latencyMs ?: 0}ms)")
                    }
                }
            },
            onAdvancedClick = {
                showQuickAddDialog = false
                onNavigateToEditor(null)
            }
        )
    }

    // 2. Import Proxies Dialog
    if (showImportDialog) {
        ImportProxiesDialog(
            existingProxies = proxies,
            onDismiss = { showImportDialog = false },
            onImport = { imported ->
                scope.launch {
                    proxyRepository.saveProxies(imported)
                    showImportDialog = false
                    snackbarHostState.showSnackbar("Imported ${imported.size} proxies.")
                }
            }
        )
    }

    // 2b. Automatic Proxy Generator Dialog
    if (generatorState.isDialogVisible) {
        AutomaticProxyGeneratorDialog(
            existingProxies = proxies,
            generatorState = generatorState,
            onDismiss = { generatorManager.dismissDialog() },
            onMinimizeToBackground = { generatorManager.minimizeToBackground() },
            onStopGeneration = { generatorManager.cancel() },
            onStartGeneration = { request, onlyAddHealthy, autoTestAfterAdd, startInBackground ->
                generatorManager.startGeneration(
                    request = request,
                    onlyAddHealthy = onlyAddHealthy,
                    autoTestAfterAdd = autoTestAfterAdd,
                    existingProxies = proxies,
                    startInBackground = startInBackground
                )
            }
        )
    }

    // 3. Delete Single Proxy Confirmation Dialog
    proxyToDelete?.let { proxy ->
        AlertDialog(
            onDismissRequest = { proxyToDelete = null },
            title = { Text("Delete Proxy?") },
            text = { Text("Are you sure you want to delete '${proxy.name}' (${proxy.host}:${proxy.port})? Profiles referencing this proxy will enter protected status.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            proxyRepository.deleteProxy(proxy.id)
                            proxyToDelete = null
                            snackbarHostState.showSnackbar("Deleted ${proxy.name}")
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { proxyToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 4. Delete Selected Unhealthy Proxies Dialog
    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text("Delete Selected Proxies?") },
            text = { Text("Are you sure you want to delete ${selectedUnhealthyIds.size} selected unhealthy proxies?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val count = proxyRepository.deleteProxies(selectedUnhealthyIds.toList())
                            selectedUnhealthyIds = emptySet()
                            showDeleteSelectedDialog = false
                            snackbarHostState.showSnackbar("Deleted $count proxies.")
                        }
                    }
                ) {
                    Text("Delete Selected", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 5. Sweep All Unhealthy Proxies Dialog
    if (showDeleteAllUnhealthyDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllUnhealthyDialog = false },
            title = { Text("Remove All Unhealthy Proxies?") },
            text = { Text("Are you sure you want to permanently remove all ${unhealthyList.size} proxies that failed health verification?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val count = proxyRepository.deleteUnhealthyProxies()
                            selectedUnhealthyIds = emptySet()
                            showDeleteAllUnhealthyDialog = false
                            snackbarHostState.showSnackbar("Removed $count unhealthy proxies.")
                        }
                    }
                ) {
                    Text("Remove All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllUnhealthyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Modern Proxy Card with Country Flag, Protocol Chip, Latency Pill, and Action Controls.
 */
@Composable
fun ModernProxyCard(
    proxy: ProxyConfiguration,
    isSelected: Boolean,
    showSelection: Boolean,
    isTesting: Boolean,
    onSelectToggle: () -> Unit,
    onTestClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onCopyClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val flag = getProxyCountryFlag(proxy.region)
    val isHealthy = proxy.lastHealthStatus == ProxyHealthStatus.HEALTHY
    val isUnhealthy = proxy.lastHealthStatus == ProxyHealthStatus.UNHEALTHY ||
        proxy.lastHealthStatus == ProxyHealthStatus.TIMEOUT ||
        proxy.lastHealthStatus == ProxyHealthStatus.AUTH_FAILURE

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (showSelection) Modifier.clickable { onSelectToggle() } else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> Color(0xFFFEF2F2)
                isHealthy -> Color.White
                isUnhealthy -> Color(0xFFFFFBFB)
                else -> Color.White
            }
        ),
        border = BorderStroke(
            1.dp,
            when {
                isSelected -> Color(0xFFEF4444)
                isHealthy -> Color(0xFFDCFCE7)
                isUnhealthy -> Color(0xFFFEE2E2)
                else -> Color(0xFFE2E8F0)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Selection / Protocol / Country / Enabled Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (showSelection) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onSelectToggle() },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }

                    // Protocol Badge
                    val (protoBg, protoFg) = when (proxy.protocol) {
                        ProxyProtocol.SOCKS5 -> Color(0xFFF3E8FF) to Color(0xFF7E22CE)
                        ProxyProtocol.HTTPS -> Color(0xFFEEF2FF) to Color(0xFF4338CA)
                        ProxyProtocol.HTTP -> Color(0xFFEFF6FF) to Color(0xFF1D4ED8)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(protoBg)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = proxy.protocol.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = protoFg
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Country Flag & Region
                    Text(
                        text = "$flag ${proxy.region ?: "Global"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Switch(
                        checked = proxy.enabled,
                        onCheckedChange = onToggleEnabled,
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Endpoint & Copy Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${proxy.host}:${proxy.port}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        IconButton(
                            onClick = onCopyClick,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy IP",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    if (proxy.credentials != null) {
                        Text(
                            text = "Auth: ${proxy.getMaskedCredentials()}",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // Health Ping Pill
                HealthStatusBadge(
                    status = proxy.lastHealthStatus,
                    latencyMs = proxy.lastLatencyMs
                )
            }

            // Error display if unhealthy
            if (isUnhealthy && !proxy.lastErrorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFEF2F2))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = proxy.lastErrorMessage ?: "Connection failed",
                            fontSize = 11.sp,
                            color = Color(0xFFB91C1C),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Actions: Test button, Edit, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick Ping/Test button
                if (isTesting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testing probe...", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                } else {
                    OutlinedButton(
                        onClick = onTestClick,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF2563EB))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Ping", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                            .clickable { onEditClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF475569), modifier = Modifier.size(14.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEF2F2))
                            .clickable { onDeleteClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyProxyState(
    title: String,
    description: String,
    actionText: String?,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.VpnLock, contentDescription = null, modifier = Modifier.size(28.dp), tint = Color(0xFF94A3B8))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, fontSize = 12.sp, color = Color(0xFF64748B), modifier = Modifier.padding(horizontal = 16.dp))
            if (actionText != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, contentColor = AppColors.OnPrimary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(actionText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Super-easy Quick Add Proxy Dialog allowing 1-tap addition of custom proxy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddProxyDialog(
    onDismiss: () -> Unit,
    onSave: (ProxyConfiguration, Boolean) -> Unit,
    onAdvancedClick: () -> Unit
) {
    var host by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("8080") }
    var protocol by remember { mutableStateOf(ProxyProtocol.HTTP) }
    var region by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showAuth by remember { mutableStateOf(false) }
    var testImmediately by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Quick Add Proxy", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                TextButton(onClick = onAdvancedClick) {
                    Text("Advanced", fontSize = 12.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Protocol Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProxyProtocol.values().forEach { proto ->
                        FilterChip(
                            selected = protocol == proto,
                            onClick = { protocol = proto },
                            label = { Text(proto.displayName, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Host & Port Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = {
                            host = it.trim()
                            errorMessage = null
                        },
                        label = { Text("IP / Host") },
                        placeholder = { Text("104.244.42.1") },
                        singleLine = true,
                        modifier = Modifier.weight(2.2f)
                    )

                    OutlinedTextField(
                        value = portText,
                        onValueChange = {
                            portText = it.trim()
                            errorMessage = null
                        },
                        label = { Text("Port") },
                        placeholder = { Text("8080") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                }

                // Country Code
                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it.trim().uppercase() },
                    label = { Text("Country Code (Optional)") },
                    placeholder = { Text("US, DE, SG...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Auth Accordion Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAuth = !showAuth }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Authentication (Optional)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2563EB)
                    )
                    Icon(
                        if (showAuth) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF2563EB)
                    )
                }

                if (showAuth) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it.trim() },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it.trim() },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Test Immediately Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { testImmediately = !testImmediately }
                ) {
                    Checkbox(
                        checked = testImmediately,
                        onCheckedChange = { testImmediately = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Probe health and latency immediately", fontSize = 12.sp)
                }

                errorMessage?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedHost = host.trim()
                    val port = portText.toIntOrNull()
                    if (trimmedHost.isBlank()) {
                        errorMessage = "Host/IP cannot be blank"
                        return@Button
                    }
                    if (port == null || port !in 1..65535) {
                        errorMessage = "Invalid port (1-65535)"
                        return@Button
                    }

                    val creds = if (username.isNotBlank() || password.isNotBlank()) {
                        ProxyCredentials(username = username, password = password)
                    } else null

                    val config = ProxyConfiguration(
                        id = UUID.randomUUID().toString(),
                        name = "${protocol.displayName} $trimmedHost:$port",
                        protocol = protocol,
                        host = trimmedHost,
                        port = port,
                        credentials = creds,
                        region = region.ifBlank { null },
                        enabled = true,
                        lastHealthStatus = ProxyHealthStatus.UNTESTED
                    )
                    onSave(config, testImmediately)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary,
                    contentColor = AppColors.OnPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Proxy", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun HealthStatusBadge(status: ProxyHealthStatus, latencyMs: Long?) {
    val (bgColor, textColor, icon) = when (status) {
        ProxyHealthStatus.HEALTHY -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Icons.Default.CheckCircle)
        ProxyHealthStatus.UNHEALTHY, ProxyHealthStatus.TIMEOUT, ProxyHealthStatus.AUTH_FAILURE ->
            Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Icons.Default.Error)
        ProxyHealthStatus.TESTING -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), Icons.Default.Refresh)
        else -> Triple(Color(0xFFF1F5F9), Color(0xFF64748B), Icons.Default.VpnLock)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = textColor, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (latencyMs != null && status == ProxyHealthStatus.HEALTHY) {
                "⚡ ${latencyMs}ms"
            } else {
                status.displayName
            },
            fontSize = 11.sp,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ImportProxiesDialog(
    existingProxies: List<ProxyConfiguration>,
    onDismiss: () -> Unit,
    onImport: (List<ProxyConfiguration>) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }

    // Manual Paste Tab state
    var rawText by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf<com.autopilot.android.feature.proxy.ProxyImportSummary?>(null) }

    // Proxifly Tab state
    var selectedProtocol by remember { mutableStateOf<ProxyProtocol?>(null) }
    var countryFilterText by remember { mutableStateOf("") }
    var isFetchingProxifly by remember { mutableStateOf(false) }
    var proxiflyResult by remember { mutableStateOf<com.autopilot.android.feature.proxy.source.ProxiflyFetchResult?>(null) }

    // GeoNode Tab state
    var geonodeUrl by remember { mutableStateOf(GeonodeProxySourceAdapter.DEFAULT_URL) }
    var geonodeCountryFilterText by remember { mutableStateOf("") }
    var isFetchingGeonode by remember { mutableStateOf(false) }
    var geonodeResult by remember { mutableStateOf<GeonodeFetchResult?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Proxies", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Paste List", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Proxifly Free", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("GeoNode Free", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    Text(
                        "Paste lines in format: host:port or host:port:user:pass",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = {
                            rawText = it
                            summary = ProxySourceManager.parseAndValidateList(it, existingProxies = existingProxies)
                        },
                        label = { Text("Proxy list") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        placeholder = { Text("192.168.1.100:8080\nsocks5://user:pass@10.0.0.1:1080") }
                    )

                    summary?.let { s ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Found: ${s.validCount} valid, ${s.duplicateCount} duplicates, ${s.invalidCount} invalid.",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (s.validCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else if (selectedTab == 1) {
                    Text(
                        "Fetch freshly rotated public proxies from Proxifly's automated global repository.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedProtocol == null,
                            onClick = { selectedProtocol = null },
                            label = { Text("All", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedProtocol == ProxyProtocol.HTTP,
                            onClick = { selectedProtocol = ProxyProtocol.HTTP },
                            label = { Text("HTTP", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedProtocol == ProxyProtocol.HTTPS,
                            onClick = { selectedProtocol = ProxyProtocol.HTTPS },
                            label = { Text("HTTPS", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedProtocol == ProxyProtocol.SOCKS5,
                            onClick = { selectedProtocol = ProxyProtocol.SOCKS5 },
                            label = { Text("SOCKS5", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = countryFilterText,
                        onValueChange = { countryFilterText = it },
                        label = { Text("Country Allowlist (Optional)") },
                        placeholder = { Text("e.g. US, DE, JP") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isFetchingProxifly = true
                                val allowlist = countryFilterText.split(",")
                                    .map { it.trim().uppercase() }
                                    .filter { it.isNotEmpty() }
                                    .toSet()
                                val filterRule = if (allowlist.isNotEmpty()) CountryFilterRule(allowlist = allowlist) else null

                                val adapter = ProxiflyProxySourceAdapter()
                                proxiflyResult = adapter.fetchProxies(
                                    protocolFilter = selectedProtocol,
                                    countryFilter = filterRule,
                                    existingProxies = existingProxies
                                )
                                isFetchingProxifly = false
                            }
                        },
                        enabled = !isFetchingProxifly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isFetchingProxifly) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Fetching...")
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Fetch from Proxifly")
                        }
                    }

                    proxiflyResult?.let { res ->
                        Spacer(modifier = Modifier.height(8.dp))
                        if (res.success) {
                            Text(
                                "Fetched ${res.totalFetched} total. Ready to import: ${res.validProxies.size} new (${res.duplicatesSkipped} duplicates, ${res.filteredOutByCountry} filtered by country).",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                "Error: ${res.errorMessage}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    Text(
                        "Fetch proxies from the GeoNode public API.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = geonodeUrl,
                        onValueChange = { geonodeUrl = it },
                        label = { Text("GeoNode Endpoint URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = geonodeCountryFilterText,
                        onValueChange = { geonodeCountryFilterText = it },
                        label = { Text("Country Allowlist (Optional)") },
                        placeholder = { Text("e.g. US, DE, JP") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isFetchingGeonode = true
                                val allowlist = geonodeCountryFilterText.split(",")
                                    .map { it.trim().uppercase() }
                                    .filter { it.isNotEmpty() }
                                    .toSet()
                                val filterRule = if (allowlist.isNotEmpty()) CountryFilterRule(allowlist = allowlist) else null

                                val adapter = GeonodeProxySourceAdapter()
                                geonodeResult = adapter.fetchProxies(
                                    customUrl = geonodeUrl,
                                    countryFilter = filterRule,
                                    existingProxies = existingProxies
                                )
                                isFetchingGeonode = false
                            }
                        },
                        enabled = !isFetchingGeonode,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isFetchingGeonode) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Fetching...")
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Fetch from GeoNode")
                        }
                    }

                    geonodeResult?.let { res ->
                        Spacer(modifier = Modifier.height(8.dp))
                        if (res.success) {
                            Text(
                                "Fetched ${res.totalFetched} total. Ready to import: ${res.validProxies.size} new (${res.duplicatesSkipped} duplicates, ${res.filteredOutByCountry} filtered by country).",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                "Error: ${res.errorMessage}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (selectedTab) {
                0 -> {
                    Button(
                        onClick = {
                            summary?.importedProxies?.let { onImport(it) }
                        },
                        enabled = (summary?.validCount ?: 0) > 0
                    ) {
                        Text("Import (${summary?.validCount ?: 0})")
                    }
                }
                1 -> {
                    Button(
                        onClick = {
                            proxiflyResult?.validProxies?.let { onImport(it) }
                        },
                        enabled = (proxiflyResult?.validProxies?.size ?: 0) > 0
                    ) {
                        Text("Import (${proxiflyResult?.validProxies?.size ?: 0})")
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            geonodeResult?.validProxies?.let { onImport(it) }
                        },
                        enabled = (geonodeResult?.validProxies?.size ?: 0) > 0
                    ) {
                        Text("Import (${geonodeResult?.validProxies?.size ?: 0})")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AutomaticProxyGeneratorDialog(
    existingProxies: List<ProxyConfiguration>,
    generatorState: com.autopilot.android.feature.proxy.BackgroundProxyGenerationState,
    onDismiss: () -> Unit,
    onMinimizeToBackground: () -> Unit,
    onStopGeneration: () -> Unit,
    onStartGeneration: (
        request: ProxyGenerationRequest,
        onlyAddHealthy: Boolean,
        autoTestAfterAdd: Boolean,
        startInBackground: Boolean
    ) -> Unit
) {
    var selectedTier by remember { mutableStateOf(ProxyTier.TOP_TIER) }
    var selectedCountries by remember { mutableStateOf<Set<String>>(emptySet()) }
    var countrySearch by remember { mutableStateOf("") }
    var quantity by remember { mutableIntStateOf(AutomaticProxyGenerator.DEFAULT_QUANTITY) }
    var quantityText by remember { mutableStateOf(AutomaticProxyGenerator.DEFAULT_QUANTITY.toString()) }
    var selectedProtocol by remember { mutableStateOf<ProxyProtocol?>(null) }
    var autoTestAfterGenerate by remember { mutableStateOf(false) }
    var onlyAddHealthy by remember { mutableStateOf(true) }
    var runInBackground by remember { mutableStateOf(false) }

    val isGenerating = generatorState.isRunning

    val parsedQuantity = quantityText.toIntOrNull()
    val isQuantityValid = parsedQuantity != null &&
        parsedQuantity in AutomaticProxyGenerator.MIN_QUANTITY..AutomaticProxyGenerator.MAX_QUANTITY

    val filteredCountries = remember(countrySearch) {
        val query = countrySearch.trim()
        if (query.isEmpty()) {
            AutomaticProxyGenerator.SUPPORTED_COUNTRIES
        } else {
            AutomaticProxyGenerator.SUPPORTED_COUNTRIES.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.code.contains(query, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Automatic Proxy Generator", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Tier Selection (Top Tier, Mid Tier, Low Tier)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Proxy Tier",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProxyTier.entries.forEach { tier ->
                            val isSelected = selectedTier == tier
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedTier = tier },
                                label = {
                                    Text(
                                        tier.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isGenerating
                            )
                        }
                    }
                    Text(
                        text = selectedTier.description,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // 2. Country Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Countries",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = if (selectedCountries.isEmpty()) "All Countries" else "${selectedCountries.size} selected",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2563EB)
                        )
                    }

                    // Quick tier/all country presets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = { selectedCountries = emptySet() },
                            label = { Text("All", fontSize = 11.sp) },
                            enabled = !isGenerating
                        )
                        ProxyTier.entries.forEach { tier ->
                            val tierCodes = AutomaticProxyGenerator.countryCodesForTier(tier)
                            AssistChip(
                                onClick = { selectedCountries = tierCodes },
                                label = { Text("${tier.displayName} (${tierCodes.size})", fontSize = 11.sp) },
                                enabled = !isGenerating
                            )
                        }
                    }

                    OutlinedTextField(
                        value = countrySearch,
                        onValueChange = { countrySearch = it },
                        placeholder = { Text("Search country (e.g. US, Germany)", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isGenerating
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        color = Color(0xFFF8FAFC),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp, max = 160.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (filteredCountries.isEmpty()) {
                                Text(
                                    "No matching countries",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.padding(8.dp)
                                )
                            } else {
                                filteredCountries.forEach { country ->
                                    val isChecked = selectedCountries.contains(country.code)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable(enabled = !isGenerating) {
                                                selectedCountries = if (isChecked) {
                                                    selectedCountries - country.code
                                                } else {
                                                    selectedCountries + country.code
                                                }
                                            }
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${getProxyCountryFlag(country.code)} ${country.code} - ${country.name}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF0F172A),
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = country.tier.shortLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // 3. Quantity Selector (1 to 500)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Quantity (${AutomaticProxyGenerator.MIN_QUANTITY} - ${AutomaticProxyGenerator.MAX_QUANTITY})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "$quantity proxies",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB)
                        )
                    }

                    Slider(
                        value = quantity.toFloat(),
                        onValueChange = { value ->
                            val clamped = value.toInt().coerceIn(
                                AutomaticProxyGenerator.MIN_QUANTITY,
                                AutomaticProxyGenerator.MAX_QUANTITY
                            )
                            quantity = clamped
                            quantityText = clamped.toString()
                        },
                        valueRange = AutomaticProxyGenerator.MIN_QUANTITY.toFloat()..AutomaticProxyGenerator.MAX_QUANTITY.toFloat(),
                        enabled = !isGenerating
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedIconButton(
                            onClick = {
                                val next = (quantity - 10).coerceAtLeast(AutomaticProxyGenerator.MIN_QUANTITY)
                                quantity = next
                                quantityText = next.toString()
                            },
                            enabled = !isGenerating && quantity > AutomaticProxyGenerator.MIN_QUANTITY,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease quantity", modifier = Modifier.size(16.dp))
                        }

                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { input ->
                                val digitsOnly = input.filter { it.isDigit() }
                                quantityText = digitsOnly
                                val num = digitsOnly.toIntOrNull()
                                if (num != null && num in AutomaticProxyGenerator.MIN_QUANTITY..AutomaticProxyGenerator.MAX_QUANTITY) {
                                    quantity = num
                                }
                            },
                            label = { Text("Proxies", fontSize = 11.sp) },
                            isError = !isQuantityValid,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            enabled = !isGenerating
                        )

                        OutlinedIconButton(
                            onClick = {
                                val next = (quantity + 10).coerceAtMost(AutomaticProxyGenerator.MAX_QUANTITY)
                                quantity = next
                                quantityText = next.toString()
                            },
                            enabled = !isGenerating && quantity < AutomaticProxyGenerator.MAX_QUANTITY,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase quantity", modifier = Modifier.size(16.dp))
                        }
                    }

                    if (!isQuantityValid) {
                        Text(
                            "Enter a number between ${AutomaticProxyGenerator.MIN_QUANTITY} and ${AutomaticProxyGenerator.MAX_QUANTITY}.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(10, 50, 100, 250, 500).forEach { preset ->
                            FilterChip(
                                selected = quantity == preset && isQuantityValid,
                                onClick = {
                                    quantity = preset
                                    quantityText = preset.toString()
                                },
                                label = { Text("$preset", fontSize = 11.sp) },
                                enabled = !isGenerating
                            )
                        }
                    }
                }

                // 4. Optional Protocol Filter & Auto-test
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Protocol (Optional)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF0F172A)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedProtocol == null,
                            onClick = { selectedProtocol = null },
                            label = { Text("All", fontSize = 11.sp) },
                            enabled = !isGenerating
                        )
                        ProxyProtocol.entries.forEach { proto ->
                            FilterChip(
                                selected = selectedProtocol == proto,
                                onClick = { selectedProtocol = proto },
                                label = { Text(proto.displayName, fontSize = 11.sp) },
                                enabled = !isGenerating
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isGenerating) {
                                autoTestAfterGenerate = !autoTestAfterGenerate
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = autoTestAfterGenerate,
                            onCheckedChange = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Auto-test generated proxies after adding",
                            fontSize = 12.sp,
                            color = Color(0xFF334155)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isGenerating) {
                                onlyAddHealthy = !onlyAddHealthy
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = onlyAddHealthy,
                            onCheckedChange = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Only add healthy proxies (live check)",
                                fontSize = 12.sp,
                                color = Color(0xFF334155)
                            )
                            Text(
                                "Tests each generated proxy before adding; unhealthy ones are discarded",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isGenerating) {
                                runInBackground = !runInBackground
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = runInBackground,
                            onCheckedChange = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Run in background (continue while using app)",
                                fontSize = 12.sp,
                                color = Color(0xFF334155)
                            )
                            Text(
                                "Closes this pop-up and runs in background so you can start other automations",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    if (isGenerating) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = generatorState.statusMessage ?: "Generating proxies...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2563EB)
                            )
                            if (generatorState.totalToCheck > 0) {
                                LinearProgressIndicator(
                                    progress = { generatorState.progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFF2563EB),
                                    trackColor = Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }
                }

                generatorState.errorMessage?.let { err ->
                    Text(
                        text = err,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            if (isGenerating) {
                Button(onClick = onMinimizeToBackground) {
                    Text("Run in Background")
                }
            } else {
                Button(
                    onClick = {
                        val targetQty = parsedQuantity?.coerceIn(
                            AutomaticProxyGenerator.MIN_QUANTITY,
                            AutomaticProxyGenerator.MAX_QUANTITY
                        ) ?: return@Button
                        onStartGeneration(
                            ProxyGenerationRequest(
                                countries = selectedCountries,
                                tier = selectedTier,
                                quantity = targetQty,
                                protocolFilter = selectedProtocol
                            ),
                            onlyAddHealthy,
                            autoTestAfterGenerate && !onlyAddHealthy,
                            runInBackground
                        )
                    },
                    enabled = isQuantityValid
                ) {
                    Text(
                        if (runInBackground) "Start in Background (${parsedQuantity ?: quantity})"
                        else "Generate (${parsedQuantity ?: quantity})"
                    )
                }
            }
        },
        dismissButton = {
            if (isGenerating) {
                TextButton(
                    onClick = onStopGeneration,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                ) {
                    Text("Stop")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

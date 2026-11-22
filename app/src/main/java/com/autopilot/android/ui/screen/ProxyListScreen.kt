package com.autopilot.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autopilot.android.data.repository.ProxyRepository
import com.autopilot.android.domain.model.proxy.ProxyConfiguration
import com.autopilot.android.domain.model.proxy.ProxyHealthStatus
import com.autopilot.android.domain.model.proxy.ProxyProtocol
import com.autopilot.android.feature.proxy.ProxyCompatibilityChecker
import com.autopilot.android.feature.proxy.ProxyHealthChecker
import com.autopilot.android.feature.proxy.ProxySourceManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyListScreen(
    proxyRepository: ProxyRepository,
    healthChecker: ProxyHealthChecker,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (String?) -> Unit
) {
    val proxies by proxyRepository.getAllProxiesFlow().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedFilter by remember { mutableStateOf("ALL") }
    var showImportDialog by remember { mutableStateOf(false) }
    var proxyToDelete by remember { mutableStateOf<ProxyConfiguration?>(null) }
    var testingProxyId by remember { mutableStateOf<String?>(null) }

    val compatibility = remember { ProxyCompatibilityChecker.checkCompatibility(null) }

    val filteredProxies = remember(proxies, selectedFilter) {
        when (selectedFilter) {
            "HEALTHY" -> proxies.filter { it.lastHealthStatus == ProxyHealthStatus.HEALTHY }
            "UNHEALTHY" -> proxies.filter { it.lastHealthStatus == ProxyHealthStatus.UNHEALTHY || it.lastHealthStatus == ProxyHealthStatus.TIMEOUT }
            "UNTESTED" -> proxies.filter { it.lastHealthStatus == ProxyHealthStatus.UNTESTED }
            else -> proxies
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("VPN & Proxy Management", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Import List")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToEditor(null) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Proxy")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // VPN Tier 2 Architecture Notice
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Network Isolation Architecture",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "• Tier 1 (Active): WebView Proxy Override with sequential session locking.\n" +
                                "• Tier 2 (Planned): Dedicated VpnService tunnel integration.\n" +
                                "• WebKit Compatibility: ${if (compatibility.isProxyOverrideSupported) "Supported" else "Limited on this OS"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                // Public Proxy Security Warning
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Security Notice: Free public proxies can inspect, log, or tamper with unencrypted traffic. Use authenticated private proxies for sensitive automation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            item {
                // Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("All (${proxies.size})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "HEALTHY",
                        onClick = { selectedFilter = "HEALTHY" },
                        label = { Text("Healthy (${proxies.count { it.lastHealthStatus == ProxyHealthStatus.HEALTHY }})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "UNHEALTHY",
                        onClick = { selectedFilter = "UNHEALTHY" },
                        label = { Text("Unhealthy (${proxies.count { it.lastHealthStatus == ProxyHealthStatus.UNHEALTHY || it.lastHealthStatus == ProxyHealthStatus.TIMEOUT }})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "UNTESTED",
                        onClick = { selectedFilter = "UNTESTED" },
                        label = { Text("Untested (${proxies.count { it.lastHealthStatus == ProxyHealthStatus.UNTESTED }})") }
                    )
                }
            }

            if (filteredProxies.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.VpnLock,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (proxies.isEmpty()) "No Proxies Configured" else "No matching proxies found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (proxies.isEmpty()) "Add your first proxy or import a list to isolate sessions." else "Try selecting another filter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(filteredProxies, key = { it.id }) { proxy ->
                    ProxyItemCard(
                        proxy = proxy,
                        isTesting = testingProxyId == proxy.id,
                        onTestClick = {
                            scope.launch {
                                testingProxyId = proxy.id
                                val result = healthChecker.checkHealth(proxy)
                                proxyRepository.updateHealthStatus(
                                    id = proxy.id,
                                    status = result.status,
                                    latencyMs = result.latencyMs,
                                    error = result.errorMessage
                                )
                                testingProxyId = null
                                snackbarHostState.showSnackbar(
                                    "Test ${proxy.name}: ${result.status.displayName} (${result.latencyMs ?: 0}ms)"
                                )
                            }
                        },
                        onToggleEnabled = { enabled ->
                            scope.launch {
                                proxyRepository.updateEnabled(proxy.id, enabled)
                            }
                        },
                        onEditClick = { onNavigateToEditor(proxy.id) },
                        onDeleteClick = { proxyToDelete = proxy }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // Delete confirmation dialog
    proxyToDelete?.let { proxy ->
        AlertDialog(
            onDismissRequest = { proxyToDelete = null },
            title = { Text("Delete Proxy?") },
            text = { Text("Are you sure you want to delete '${proxy.name}' (${proxy.host}:${proxy.port})? Profiles referencing this proxy will enter protected error state rather than leaking IP.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            proxyRepository.deleteProxy(proxy.id)
                            proxyToDelete = null
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { proxyToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Import proxy list dialog
    if (showImportDialog) {
        ImportProxiesDialog(
            existingProxies = proxies,
            onDismiss = { showImportDialog = false },
            onImport = { imported ->
                scope.launch {
                    proxyRepository.saveProxies(imported)
                    showImportDialog = false
                    snackbarHostState.showSnackbar("Successfully imported ${imported.size} proxies.")
                }
            }
        )
    }
}

@Composable
fun ProxyItemCard(
    proxy: ProxyConfiguration,
    isTesting: Boolean,
    onTestClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = proxy.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = proxy.protocol.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Switch(
                    checked = proxy.enabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${proxy.host}:${proxy.port}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Auth: ${proxy.getMaskedCredentials()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "Region: ${proxy.getDisplayRegion()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Health Status & Latency row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HealthStatusBadge(
                    status = proxy.lastHealthStatus,
                    latencyMs = proxy.lastLatencyMs
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(4.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        OutlinedButton(
                            onClick = onTestClick,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test")
                        }
                    }

                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                    }

                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (!proxy.lastErrorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Error: ${proxy.lastErrorMessage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun HealthStatusBadge(status: ProxyHealthStatus, latencyMs: Long?) {
    val (bgColor, textColor, icon) = when (status) {
        ProxyHealthStatus.HEALTHY -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Icons.Default.CheckCircle)
        ProxyHealthStatus.UNHEALTHY, ProxyHealthStatus.TIMEOUT, ProxyHealthStatus.AUTH_FAILURE ->
            Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Icons.Default.Error)
        ProxyHealthStatus.TESTING -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), Icons.Default.Refresh)
        else -> Triple(Color(0xFFF5F5F5), Color(0xFF616161), Icons.Default.VpnLock)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = textColor, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (latencyMs != null && status == ProxyHealthStatus.HEALTHY) {
                "${status.displayName} (${latencyMs}ms)"
            } else {
                status.displayName
            },
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun ImportProxiesDialog(
    existingProxies: List<ProxyConfiguration>,
    onDismiss: () -> Unit,
    onImport: (List<ProxyConfiguration>) -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf<com.autopilot.android.feature.proxy.ProxyImportSummary?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Proxies") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Paste proxy list lines. Formats supported:\n• host:port\n• host:port:user:pass\n• protocol://host:port\n• protocol://user:pass@host:port",
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
                        .height(150.dp),
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    summary?.importedProxies?.let { onImport(it) }
                },
                enabled = (summary?.validCount ?: 0) > 0
            ) {
                Text("Import (${summary?.validCount ?: 0})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

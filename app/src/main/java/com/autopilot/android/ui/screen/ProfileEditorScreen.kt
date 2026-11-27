package com.autopilot.android.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopilot.android.data.repository.ProfileRepository
import com.autopilot.android.data.repository.ProxyRepository
import com.autopilot.android.domain.model.AutomationProfile
import com.autopilot.android.domain.model.DeviceCatalog
import com.autopilot.android.domain.model.DeviceCategory
import com.autopilot.android.domain.model.ExecutionMode
import com.autopilot.android.domain.validation.UrlValidator
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    profileId: String?,
    profileRepository: ProfileRepository,
    proxyRepository: ProxyRepository? = null,
    onNavigateBack: () -> Unit
) {
    val proxies by (proxyRepository?.getAllProxiesFlow() ?: flowOf(emptyList())).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var urlsText by remember { mutableStateOf("") }
    var tabCount by remember { mutableFloatStateOf(1f) }
    var selectedMode by remember { mutableStateOf(ExecutionMode.SIMPLE_SCROLL_REFRESH) }
    var refreshIntervalSec by remember { mutableIntStateOf(30) }
    var scrollIntervalSec by remember { mutableIntStateOf(15) }
    var randomizeIntervals by remember { mutableStateOf(false) }
    var minRefreshSec by remember { mutableIntStateOf(20) }
    var maxRefreshSec by remember { mutableIntStateOf(45) }
    var minScrollSec by remember { mutableIntStateOf(10) }
    var maxScrollSec by remember { mutableIntStateOf(25) }
    var customScript by remember { mutableStateOf("") }
    var allowedDomainsText by remember { mutableStateOf("") }
    var clearCookies by remember { mutableStateOf(false) }
    var clearCache by remember { mutableStateOf(false) }
    var enableSpoofing by remember { mutableStateOf(false) }
    var deviceCategory by remember { mutableStateOf(DeviceCategory.ALL) }
    var enableFreeProxy by remember { mutableStateOf(false) }
    var selectedProxyId by remember { mutableStateOf<String?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(profileId) {
        if (!profileId.isNullOrBlank()) {
            val existing = profileRepository.getProfileById(profileId)
            if (existing != null) {
                name = existing.name
                description = existing.description
                urlsText = existing.urls.joinToString("\n")
                tabCount = existing.tabCount.toFloat().coerceIn(1f, 200f)
                selectedMode = existing.executionMode
                refreshIntervalSec = existing.refreshIntervalSec
                scrollIntervalSec = existing.scrollIntervalSec
                randomizeIntervals = existing.randomizeIntervals
                minRefreshSec = existing.minRefreshIntervalSec
                maxRefreshSec = existing.maxRefreshIntervalSec
                minScrollSec = existing.minScrollIntervalSec
                maxScrollSec = existing.maxScrollIntervalSec
                enableSpoofing = existing.enableSpoofing
                deviceCategory = existing.deviceCategory
                enableFreeProxy = existing.enableFreeProxy
                customScript = existing.customScript ?: ""
                allowedDomainsText = existing.allowedDomains.joinToString(", ")
                clearCookies = existing.clearCookiesOnStart
                clearCache = existing.clearCacheOnStart
                selectedProxyId = existing.proxyId
            }
        }
        isLoaded = true
    }

    val (validUrls, invalidUrls) = remember(urlsText) {
        UrlValidator.parseMultiple(urlsText)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (profileId == null) "New Profile" else "Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val targetUrls = if (validUrls.isNotEmpty()) validUrls else listOf("https://example.com")
                            val allowedDomains = allowedDomainsText.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }
                            val profile = AutomationProfile(
                                id = profileId ?: UUID.randomUUID().toString(),
                                name = name.ifBlank { "Automation Profile" },
                                description = description,
                                urls = targetUrls,
                                tabCount = tabCount.toInt().coerceIn(AutomationProfile.MIN_TABS, AutomationProfile.MAX_TABS),
                                executionMode = selectedMode,
                                refreshIntervalSec = refreshIntervalSec,
                                scrollIntervalSec = scrollIntervalSec,
                                randomizeIntervals = randomizeIntervals,
                                minRefreshIntervalSec = minRefreshSec,
                                maxRefreshIntervalSec = maxRefreshSec,
                                minScrollIntervalSec = minScrollSec,
                                maxScrollIntervalSec = maxScrollSec,
                                enableSpoofing = enableSpoofing,
                                deviceCategory = deviceCategory,
                                enableFreeProxy = enableFreeProxy,
                                customScript = customScript.takeIf { it.isNotBlank() },
                                allowedDomains = allowedDomains,
                                clearCookiesOnStart = clearCookies,
                                clearCacheOnStart = clearCache,
                                proxyId = selectedProxyId,
                                updatedAt = System.currentTimeMillis()
                            )
                            scope.launch {
                                profileRepository.saveProfile(profile)
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save Profile")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: General Info
            Text("General Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Divider()

            // Section 2: Website URLs (Single & Multiple input)
            Text("Website URLs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = urlsText,
                onValueChange = { urlsText = it },
                label = { Text("Enter URLs (one per line, comma, or semicolon)") },
                placeholder = { Text("https://example.com\nhttps://news.ycombinator.com") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 6
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text("${validUrls.size} Valid URLs") },
                    leadingIcon = { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) }
                )
                if (invalidUrls.isNotEmpty()) {
                    AssistChip(
                        onClick = {},
                        label = { Text("${invalidUrls.size} Invalid") },
                        leadingIcon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }

            Divider()

            // Section 3: 1 to 200 Configurable Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Configurable Tabs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Badge { Text("${tabCount.toInt()} Active Tabs") }
            }
            Slider(
                value = tabCount,
                onValueChange = { tabCount = it.coerceIn(1f, 200f) },
                valueRange = 1f..200f
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1 tab", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Text("200 tabs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Text(
                "AutoPilot will maintain ${tabCount.toInt()} isolated browser sessions simultaneously (up to 200 tabs).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Divider()

            // Device Info Spoofing (Root)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Device Info Spoofing (Root)", fontWeight = FontWeight.SemiBold)
                    Text("Requires root access to spoof hardware and build props", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Switch(checked = enableSpoofing, onCheckedChange = { enableSpoofing = it })
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Device Pool", fontWeight = FontWeight.SemiBold)
            Text(
                "A new random device from this pool is used on every request",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B)
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeviceCategory.entries.forEach { category ->
                    FilterChip(
                        selected = deviceCategory == category,
                        onClick = { deviceCategory = category },
                        label = {
                            Text(
                                "${category.label} (${DeviceCatalog.count(category)})",
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }

            Divider()

            // Section 4: Execution Modes (mutually exclusive)
            Text("Execution Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ExecutionMode.values().forEach { mode ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedMode == mode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    onClick = { selectedMode = mode }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(mode.displayName, fontWeight = FontWeight.SemiBold)
                            Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            // Mode 4 JavaScript Sandbox Config
            if (selectedMode == ExecutionMode.CUSTOM_JAVASCRIPT) {
                val clipboardManager = LocalClipboardManager.current
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Custom JavaScript Code",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (customScript.isEmpty()) "0 chars" else "${customScript.lines().size}L • ${customScript.length} chars",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Toolbar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    customScript = com.autopilot.android.domain.model.AutomationScripts.AUTOBOT_HUMAN_SCRIPT
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("AutoBot Script", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        customScript = if (customScript.isBlank()) clip else customScript + "\n" + clip
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Paste", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            if (customScript.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { customScript = "" },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear", fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = customScript,
                            onValueChange = { customScript = it },
                            placeholder = {
                                Text(
                                    "// Enter or paste your JavaScript code here (unlimited length)...\n// Tap 'AutoBot Script' to load human-like automation code.",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 360.dp),
                            minLines = 8,
                            maxLines = 40,
                            singleLine = false,
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = allowedDomainsText,
                            onValueChange = { allowedDomainsText = it },
                            label = { Text("Allowed Domains (optional, comma-separated)") },
                            placeholder = { Text("Leave empty to allow all domains") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Divider()

            // Section 5: Interval Presets
            Text("Refresh Interval", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AutomationProfile.VALID_REFRESH_INTERVALS.take(4).forEach { sec ->
                    FilterChip(
                        selected = refreshIntervalSec == sec,
                        onClick = { refreshIntervalSec = sec },
                        label = { Text("${sec}s") }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AutomationProfile.VALID_REFRESH_INTERVALS.drop(4).forEach { sec ->
                    FilterChip(
                        selected = refreshIntervalSec == sec,
                        onClick = { refreshIntervalSec = sec },
                        label = { Text("${sec}s") }
                    )
                }
            }

            Text("Scroll Interval", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AutomationProfile.VALID_SCROLL_INTERVALS.take(4).forEach { sec ->
                    FilterChip(
                        selected = scrollIntervalSec == sec,
                        onClick = { scrollIntervalSec = sec },
                        label = { Text("${sec}s") }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AutomationProfile.VALID_SCROLL_INTERVALS.drop(4).forEach { sec ->
                    FilterChip(
                        selected = scrollIntervalSec == sec,
                        onClick = { scrollIntervalSec = sec },
                        label = { Text("${sec}s") }
                    )
                }
            }

            // Section 6: Interval Randomization
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Randomize Intervals (Jitter)", fontWeight = FontWeight.SemiBold)
                Switch(checked = randomizeIntervals, onCheckedChange = { randomizeIntervals = it })
            }

            if (randomizeIntervals) {
                Text("Refresh Range: $minRefreshSec to $maxRefreshSec seconds", style = MaterialTheme.typography.bodySmall)
                RangeSlider(
                    value = minRefreshSec.toFloat()..maxRefreshSec.toFloat(),
                    onValueChange = { range ->
                        minRefreshSec = range.start.toInt()
                        maxRefreshSec = range.endInclusive.toInt()
                    },
                    valueRange = 5f..120f
                )
            }

            Divider()

            // Section 7: Session Hygiene
            Text("Session & Privacy Policy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Clear Cookies on Start")
                Switch(checked = clearCookies, onCheckedChange = { clearCookies = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Clear Cache on Start")
                Switch(checked = clearCache, onCheckedChange = { clearCache = it })
            }

            Divider()

            // Section 8: Network Isolation & Proxy Selection
            Text("Network Isolation & Proxy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Free Proxy", fontWeight = FontWeight.SemiBold)
                    Text("Automatically route through available free public proxies", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                Switch(checked = enableFreeProxy, onCheckedChange = { enableFreeProxy = it })
            }

            val isConfiguredProxyMissing = selectedProxyId != null && proxies.none { it.id == selectedProxyId }
            if (isConfiguredProxyMissing) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Warning: The configured proxy was deleted or is unavailable. Automation execution will be safely blocked to prevent real IP exposure.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Text("Select Proxy for this profile:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedProxyId == null,
                    onClick = { selectedProxyId = null },
                    label = { Text("Direct Network (No Proxy)") }
                )
            }

            if (proxies.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    proxies.forEach { proxy ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedProxyId == proxy.id)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { selectedProxyId = proxy.id }
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
                                        text = proxy.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${proxy.protocol.displayName} • ${proxy.host}:${proxy.port} • Region: ${proxy.getDisplayRegion()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                if (selectedProxyId == proxy.id) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

package com.autopilot.android.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autopilot.android.data.repository.ProfileRepository
import com.autopilot.android.domain.model.AutomationProfile
import com.autopilot.android.domain.model.ExecutionMode
import com.autopilot.android.domain.validation.UrlValidator
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    profileId: String?,
    profileRepository: ProfileRepository,
    onNavigateBack: () -> Unit
) {
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
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(profileId) {
        if (!profileId.isNullOrBlank()) {
            val existing = profileRepository.getProfileById(profileId)
            if (existing != null) {
                name = existing.name
                description = existing.description
                urlsText = existing.urls.joinToString("\n")
                tabCount = existing.tabCount.toFloat()
                selectedMode = existing.executionMode
                refreshIntervalSec = existing.refreshIntervalSec
                scrollIntervalSec = existing.scrollIntervalSec
                randomizeIntervals = existing.randomizeIntervals
                minRefreshSec = existing.minRefreshIntervalSec
                maxRefreshSec = existing.maxRefreshIntervalSec
                minScrollSec = existing.minScrollIntervalSec
                maxScrollSec = existing.maxScrollIntervalSec
                customScript = existing.customScript ?: ""
                allowedDomainsText = existing.allowedDomains.joinToString(", ")
                clearCookies = existing.clearCookiesOnStart
                clearCache = existing.clearCacheOnStart
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
                                customScript = customScript.takeIf { it.isNotBlank() },
                                allowedDomains = allowedDomains,
                                clearCookiesOnStart = clearCookies,
                                clearCacheOnStart = clearCache,
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

            // Section 3: 1 to 25 Configurable Tabs
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
                onValueChange = { tabCount = it },
                valueRange = 1f..25f,
                steps = 23
            )
            Text(
                "AutoPilot will maintain ${tabCount.toInt()} isolated browser sessions simultaneously.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Divider()

            // Section 4: Four Execution Modes
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
                OutlinedTextField(
                    value = customScript,
                    onValueChange = { customScript = it },
                    label = { Text("Custom JavaScript Code") },
                    placeholder = { Text("document.title; // DOM scoped, 5s timeout") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )
                OutlinedTextField(
                    value = allowedDomainsText,
                    onValueChange = { allowedDomainsText = it },
                    label = { Text("Allowed Domains (comma-separated)") },
                    placeholder = { Text("example.com, google.com") },
                    modifier = Modifier.fillMaxWidth()
                )
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

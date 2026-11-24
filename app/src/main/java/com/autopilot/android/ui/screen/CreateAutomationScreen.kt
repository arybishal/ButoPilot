package com.autopilot.android.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopilot.android.data.repository.ProfileRepository
import com.autopilot.android.domain.model.AutomationProfile
import com.autopilot.android.domain.model.ExecutionMode
import com.autopilot.android.domain.validation.UrlValidator
import com.autopilot.android.engine.AutomationCoordinator
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAutomationScreen(
    profileId: String? = null,
    profileRepository: ProfileRepository,
    coordinator: AutomationCoordinator,
    onNavigateBack: () -> Unit,
    onNavigateToMonitor: () -> Unit,
    onNavigateToSavedProfiles: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var profileName by remember { mutableStateOf("") }
    var urlsText by remember { mutableStateOf("") }
    var tabCount by remember { mutableFloatStateOf(5f) }
    var refreshIntervalSec by remember { mutableIntStateOf(30) }
    var scrollIntervalSec by remember { mutableIntStateOf(10) }
    var enableSpoofing by remember { mutableStateOf(false) }
    var randomizeIntervals by remember { mutableStateOf(true) }
    var selectedMode by remember { mutableStateOf(ExecutionMode.SIMPLE_SCROLL_REFRESH) }
    var customScript by remember { mutableStateOf("") }
    var enableFreeProxy by remember { mutableStateOf(false) }

    var showRefreshDropdown by remember { mutableStateOf(false) }
    var showScrollDropdown by remember { mutableStateOf(false) }

    val refreshIntervalOptions = listOf(10, 15, 20, 30, 45, 60, 90, 120)
    val scrollIntervalOptions = listOf(5, 10, 15, 20, 30, 45, 60)

    LaunchedEffect(profileId) {
        if (!profileId.isNullOrBlank()) {
            val existing = profileRepository.getProfileById(profileId)
            if (existing != null) {
                profileName = existing.name
                urlsText = existing.urls.joinToString("\n")
                tabCount = existing.tabCount.toFloat().coerceIn(1f, 200f)
                refreshIntervalSec = existing.refreshIntervalSec
                scrollIntervalSec = existing.scrollIntervalSec
                enableSpoofing = existing.enableSpoofing
                randomizeIntervals = existing.randomizeIntervals
                selectedMode = existing.executionMode
                customScript = existing.customScript ?: ""
                enableFreeProxy = existing.enableFreeProxy
            }
        }
    }

    val (validUrls, invalidUrls) = remember(urlsText) {
        UrlValidator.parseMultiple(urlsText)
    }

    val isFormValid = urlsText.isNotBlank() && validUrls.isNotEmpty() &&
        (selectedMode != ExecutionMode.CUSTOM_JAVASCRIPT || customScript.isNotBlank())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (profileId == null) "Create Automation" else "Edit Automation",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = if (profileId == null) "Configure intelligent browser automation" else "Modify active automation parameters",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A)
                        )
                    }
                },
                actions = {
                    if (profileId == null) {
                        TextButton(onClick = onNavigateToSavedProfiles) {
                            Icon(
                                imageVector = Icons.Default.Bookmarks,
                                contentDescription = null,
                                tint = Color(0xFF1565C0),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Saved",
                                color = Color(0xFF1565C0),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    val cloned = profileRepository.duplicateProfile(profileId)
                                    if (cloned != null) {
                                        onNavigateBack()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Clone",
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clone",
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Hero Status Card ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (profileId == null) "New Automated Session" else "Editing: ${profileName.ifBlank { "Task" }}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Multi-tab isolated WebViews with automated scrolling, timing jitter, and cloaking.",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // --- Section 1: Target Destination ---
            SectionCard(
                title = "Target Website",
                subtitle = "Enter one or multiple website URLs to navigate",
                icon = Icons.Default.Language
            ) {
                if (profileId != null) {
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Profile Name") },
                        placeholder = { Text("e.g. News Portal Scroller") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1565C0),
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = urlsText,
                    onValueChange = { urlsText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    placeholder = {
                        Text(
                            text = "https://example.com\n(one per line or comma-separated)",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    },
                    trailingIcon = {
                        if (urlsText.isNotBlank()) {
                            IconButton(onClick = { urlsText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                            }
                        } else {
                            Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF64748B))
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1565C0),
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )

                if (urlsText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (validUrls.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFDCFCE7)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${validUrls.size} Valid URL(s)", fontSize = 11.sp, color = Color(0xFF15803D), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        if (invalidUrls.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEE2E2)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFB91C1C), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${invalidUrls.size} Invalid", fontSize = 11.sp, color = Color(0xFFB91C1C), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // --- Section 2: Concurrency & Sessions (1 to 200 Tabs) ---
            SectionCard(
                title = "Concurrent Browser Tabs",
                subtitle = "Operate isolated parallel sessions simultaneously",
                icon = Icons.Default.Layers
            ) {
                // Large Stepper Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${tabCount.toInt()}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1565C0)
                        )
                        Text(
                            text = if (tabCount.toInt() == 1) "Active Tab" else "Active Parallel Tabs",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.clickable {
                                if (tabCount > 1f) tabCount = (tabCount - 1f).coerceAtLeast(1f)
                            }
                        ) {
                            Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color(0xFF0F172A))
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1565C0),
                            modifier = Modifier.clickable {
                                if (tabCount < 200f) tabCount = (tabCount + 1f).coerceAtMost(200f)
                            }
                        ) {
                            Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Smooth Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1", fontSize = 12.sp, color = Color(0xFF64748B), modifier = Modifier.width(20.dp))
                    Slider(
                        value = tabCount,
                        onValueChange = { tabCount = it.coerceIn(1f, 200f) },
                        valueRange = 1f..200f,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF1565C0),
                            activeTrackColor = Color(0xFF1565C0),
                            inactiveTrackColor = Color(0xFFE0F2FE)
                        )
                    )
                    Text("200", fontSize = 12.sp, color = Color(0xFF64748B), modifier = Modifier.width(28.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Rapid Preset Chips
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 5, 10, 25, 50, 100, 200).forEach { count ->
                        FilterChip(
                            selected = tabCount.toInt() == count,
                            onClick = { tabCount = count.toFloat() },
                            label = { Text("$count Tabs", fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1565C0),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // --- Section 3: Execution Modes ---
            SectionCard(
                title = "Automation Mode",
                subtitle = "Choose interaction behaviour and execution engine",
                icon = Icons.Default.Tune
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeSelectionCard(
                        title = "Simple Scroll + Refresh",
                        description = "Periodically scrolls through the page and refreshes at designated intervals.",
                        icon = Icons.Default.Sync,
                        isSelected = selectedMode == ExecutionMode.SIMPLE_SCROLL_REFRESH,
                        onSelect = { selectedMode = ExecutionMode.SIMPLE_SCROLL_REFRESH }
                    )

                    ModeSelectionCard(
                        title = "Deep Scroll",
                        description = "Progressively scrolls through deep content, triggering dynamic infinite-scroll elements.",
                        icon = Icons.Default.South,
                        isSelected = selectedMode == ExecutionMode.DEEP_SCROLL,
                        onSelect = { selectedMode = ExecutionMode.DEEP_SCROLL }
                    )

                    // Auto Human Behaviour placed below Deep Scroll
                    ModeSelectionCard(
                        title = "Auto Human Behaviour",
                        description = "Randomized, human-like scrolling and clicks injected after each page load.",
                        icon = Icons.Default.TouchApp,
                        isSelected = selectedMode == ExecutionMode.AUTO_HUMAN_BEHAVIOUR,
                        onSelect = { selectedMode = ExecutionMode.AUTO_HUMAN_BEHAVIOUR }
                    )

                    ModeSelectionCard(
                        title = "Tab Switching",
                        description = "Simulates realistic multi-tasking by cycling active tab focus while maintaining state.",
                        icon = Icons.Default.Tab,
                        isSelected = selectedMode == ExecutionMode.TAB_SWITCHING,
                        onSelect = { selectedMode = ExecutionMode.TAB_SWITCHING }
                    )

                    ModeSelectionCard(
                        title = "Custom JavaScript",
                        description = "Executes custom sandboxed user scripts at designated intervals with DOM access.",
                        icon = Icons.Default.Code,
                        isSelected = selectedMode == ExecutionMode.CUSTOM_JAVASCRIPT,
                        onSelect = { selectedMode = ExecutionMode.CUSTOM_JAVASCRIPT }
                    )
                }

                AnimatedVisibility(
                    visible = selectedMode == ExecutionMode.CUSTOM_JAVASCRIPT,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = "Custom JavaScript Script",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = customScript,
                            onValueChange = { customScript = it },
                            placeholder = { Text("document.title;\n// executes in page DOM context") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1565C0),
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color(0xFFF8FAFC)
                            )
                        )
                    }
                }
            }

            // --- Section 4: Rhythm & Intervals ---
            SectionCard(
                title = "Timing & Rhythm",
                subtitle = "Set dispatch intervals and randomization jitter",
                icon = Icons.Default.Schedule
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Refresh Interval Box
                    Box(modifier = Modifier.weight(1f)) {
                        TimingBox(
                            label = "Auto Refresh",
                            value = "${refreshIntervalSec}s",
                            icon = Icons.Default.Refresh,
                            onClick = { showRefreshDropdown = !showRefreshDropdown }
                        )

                        DropdownMenu(
                            expanded = showRefreshDropdown,
                            onDismissRequest = { showRefreshDropdown = false }
                        ) {
                            refreshIntervalOptions.forEach { sec ->
                                DropdownMenuItem(
                                    text = { Text("$sec seconds") },
                                    onClick = {
                                        refreshIntervalSec = sec
                                        showRefreshDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Scroll Interval Box
                    Box(modifier = Modifier.weight(1f)) {
                        TimingBox(
                            label = "Auto Scroll",
                            value = "${scrollIntervalSec}s",
                            icon = Icons.Default.SwapVert,
                            onClick = { showScrollDropdown = !showScrollDropdown }
                        )

                        DropdownMenu(
                            expanded = showScrollDropdown,
                            onDismissRequest = { showScrollDropdown = false }
                        ) {
                            scrollIntervalOptions.forEach { sec ->
                                DropdownMenuItem(
                                    text = { Text("$sec seconds") },
                                    onClick = {
                                        scrollIntervalSec = sec
                                        showScrollDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Randomize Intervals Switch Row
                ToggleRow(
                    title = "Randomize Intervals (Jitter)",
                    subtitle = "Varies delay timings to simulate natural human browsing intervals.",
                    icon = Icons.Default.Shuffle,
                    checked = randomizeIntervals,
                    onCheckedChange = { randomizeIntervals = it }
                )
            }

            // --- Section 5: Stealth, Cloaking & Network ---
            SectionCard(
                title = "Privacy & Stealth Options",
                subtitle = "Anti-detection settings and hardware spoofing",
                icon = Icons.Default.Security
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Device Info Spoofing (Root)
                    ToggleRow(
                        title = "Device Info Spoofing (Root)",
                        subtitle = "Alters system build props and hardware markers. Requires Magisk root privileges.",
                        icon = Icons.Default.PermDeviceInformation,
                        checked = enableSpoofing,
                        onCheckedChange = { enableSpoofing = it },
                        badge = "ROOT"
                    )

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // Enable Free Proxy
                    ToggleRow(
                        title = "Device Info Spoofing (Root)",
                        subtitle = "Alters system build props and hardware markers. Requires Magisk root privileges.",
                        icon = Icons.Default.PermDeviceInformation,
                        checked = enableSpoofing,
                        onCheckedChange = { enableSpoofing = it },
                        badge = "ROOT"
                    )

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // Enable Free Proxy
                    ToggleRow(
                        title = "Enable Free Proxy",
                        subtitle = "Routes browser network traffic through rotating public proxy endpoints.",
                        icon = Icons.Default.VpnLock,
                        checked = enableFreeProxy,
                        onCheckedChange = { enableFreeProxy = it }
                    )
                }
            }

            // --- Bottom Primary Action Controls ---
            if (profileId == null) {
                // Create Mode Actions
                Button(
                    onClick = {
                        if (isFormValid) {
                            coroutineScope.launch {
                                val newProfile = AutomationProfile(
                                    id = UUID.randomUUID().toString(),
                                    name = profileName.ifBlank {
                                        validUrls.firstOrNull()?.let {
                                            try { java.net.URI(it).host ?: "Automation" } catch (e: Exception) { "Automation" }
                                        } ?: "Automation Task"
                                    },
                                    urls = validUrls,
                                    tabCount = tabCount.toInt().coerceIn(AutomationProfile.MIN_TABS, AutomationProfile.MAX_TABS),
                                    executionMode = selectedMode,
                                    refreshIntervalSec = refreshIntervalSec,
                                    scrollIntervalSec = scrollIntervalSec,
                                    enableSpoofing = enableSpoofing,
                                    randomizeIntervals = randomizeIntervals,
                                    customScript = customScript.takeIf { it.isNotBlank() },
                                    enableFreeProxy = enableFreeProxy,
                                    updatedAt = System.currentTimeMillis()
                                )
                                profileRepository.saveProfile(newProfile)
                                coordinator.start(newProfile)
                                onNavigateToMonitor()
                            }
                        }
                    },
                    enabled = isFormValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
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
                            tint = if (isFormValid) Color.White else Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Automation",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isFormValid) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onNavigateToSavedProfiles) {
                        Text(
                            text = "View Saved Automation Profiles →",
                            color = Color(0xFF1565C0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                // Edit Mode Actions
                Button(
                    onClick = {
                        if (isFormValid) {
                            coroutineScope.launch {
                                val updatedProfile = AutomationProfile(
                                    id = profileId,
                                    name = profileName.ifBlank {
                                        validUrls.firstOrNull()?.let {
                                            try { java.net.URI(it).host ?: "Automation" } catch (e: Exception) { "Automation" }
                                        } ?: "Automation Task"
                                    },
                                    urls = validUrls,
                                    tabCount = tabCount.toInt().coerceIn(AutomationProfile.MIN_TABS, AutomationProfile.MAX_TABS),
                                    executionMode = selectedMode,
                                    refreshIntervalSec = refreshIntervalSec,
                                    scrollIntervalSec = scrollIntervalSec,
                                    enableSpoofing = enableSpoofing,
                                    randomizeIntervals = randomizeIntervals,
                                    customScript = customScript.takeIf { it.isNotBlank() },
                                    enableFreeProxy = enableFreeProxy,
                                    updatedAt = System.currentTimeMillis()
                                )
                                profileRepository.saveProfile(updatedProfile)
                                onNavigateBack()
                            }
                        }
                    },
                    enabled = isFormValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1565C0),
                        disabledContainerColor = Color(0xFFE2E8F0)
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Changes", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedButton(
                    onClick = {
                        if (isFormValid) {
                            coroutineScope.launch {
                                val updatedProfile = AutomationProfile(
                                    id = profileId,
                                    name = profileName.ifBlank {
                                        validUrls.firstOrNull()?.let {
                                            try { java.net.URI(it).host ?: "Automation" } catch (e: Exception) { "Automation" }
                                        } ?: "Automation Task"
                                    },
                                    urls = validUrls,
                                    tabCount = tabCount.toInt().coerceIn(AutomationProfile.MIN_TABS, AutomationProfile.MAX_TABS),
                                    executionMode = selectedMode,
                                    refreshIntervalSec = refreshIntervalSec,
                                    scrollIntervalSec = scrollIntervalSec,
                                    enableSpoofing = enableSpoofing,
                                    randomizeIntervals = randomizeIntervals,
                                    customScript = customScript.takeIf { it.isNotBlank() },
                                    enableFreeProxy = enableFreeProxy,
                                    updatedAt = System.currentTimeMillis()
                                )
                                profileRepository.saveProfile(updatedProfile)
                                coordinator.start(updatedProfile)
                                onNavigateToMonitor()
                            }
                        }
                    },
                    enabled = isFormValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF1565C0))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFF1565C0))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save & Start Automation", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1565C0))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onNavigateBack) {
                        Text("Cancel", color = Color(0xFF64748B), fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE0F2FE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text(text = subtitle, fontSize = 12.sp, color = Color(0xFF64748B))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun ModeSelectionCard(
    title: String,
    description: String,
    icon: ImageVector,
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color(0xFFDCFCE7) else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFF15803D) else Color(0xFF64748B),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 16.sp
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFF10B981),
                    unselectedColor = Color(0xFF94A3B8)
                )
            )
        }
    }
}

@Composable
private fun TimingBox(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(label, fontSize = 11.sp, color = Color(0xFF64748B))
                Spacer(modifier = Modifier.height(2.dp))
                Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color(0xFF64748B))
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    badge: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                    badge?.let {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFEE2E2)
                        ) {
                            Text(
                                text = it,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB91C1C),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 15.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF1565C0),
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFFE2E8F0)
            )
        )
    }
}

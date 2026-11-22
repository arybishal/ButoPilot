package com.autopilot.android.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopilot.android.core.system.RootChecker
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
    profileRepository: ProfileRepository,
    coordinator: AutomationCoordinator,
    onNavigateBack: () -> Unit,
    onNavigateToMonitor: () -> Unit,
    onNavigateToSavedProfiles: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var urlsText by remember { mutableStateOf("") }
    var tabCount by remember { mutableFloatStateOf(5f) } // default 5 tabs as in screenshot
    var refreshIntervalSec by remember { mutableIntStateOf(30) } // default 30s as in screenshot
    var scrollIntervalSec by remember { mutableIntStateOf(10) } // default 10s as in screenshot
    var enableSpoofing by remember { mutableStateOf(false) }
    var randomizeIntervals by remember { mutableStateOf(true) } // default ON as in screenshot
    var isAdvancedExpanded by remember { mutableStateOf(true) } // default expanded as in screenshot 2
    var selectedMode by remember { mutableStateOf(ExecutionMode.SIMPLE_SCROLL_REFRESH) }
    var enableFreeProxy by remember { mutableStateOf(false) }

    var showRefreshDropdown by remember { mutableStateOf(false) }
    var showScrollDropdown by remember { mutableStateOf(false) }

    val refreshIntervalOptions = listOf(10, 15, 20, 30, 45, 60, 90, 120)
    val scrollIntervalOptions = listOf(5, 10, 15, 20, 30, 45, 60)

    val (validUrls, _) = remember(urlsText) {
        UrlValidator.parseMultiple(urlsText)
    }

    val isFormValid = urlsText.isNotBlank() && validUrls.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Create Automation",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // --- Main Form Card Container ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // 1. Website URL Input Field
                    OutlinedTextField(
                        value = urlsText,
                        onValueChange = { urlsText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(82.dp),
                        placeholder = {
                            Text(
                                text = "Website URL (comma =\nmultiple)",
                                color = Color(0xFF94A3B8),
                                fontSize = 15.sp,
                                lineHeight = 20.sp
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "URL Link Icon",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1565C0),
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        )
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    // 2. Number of Tabs Section
                    Text(
                        text = "Number of Tabs",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0F172A)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1",
                            fontSize = 14.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.width(20.dp)
                        )

                        Slider(
                            value = tabCount,
                            onValueChange = { tabCount = it },
                            valueRange = 1f..15f,
                            steps = 13,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF1565C0),
                                activeTrackColor = Color(0xFF1565C0),
                                inactiveTrackColor = Color(0xFFE0F2FE),
                                activeTickColor = Color.White,
                                inactiveTickColor = Color(0xFF64B5F6)
                            )
                        )

                        Text(
                            text = "15",
                            fontSize = 14.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.width(24.dp)
                        )
                    }

                    Text(
                        text = "${tabCount.toInt()} tabs",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1565C0)
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    // 3. Auto Refresh Interval (seconds)
                    Text(
                        text = "Auto Refresh Interval (seconds)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0F172A)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { showRefreshDropdown = !showRefreshDropdown },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            colors = CardDefaults.outlinedCardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$refreshIntervalSec",
                                    fontSize = 16.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color(0xFF0F172A)
                                )
                            }
                        }

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

                    Spacer(modifier = Modifier.height(22.dp))

                    // 4. Auto Scroll Interval (seconds)
                    Text(
                        text = "Auto Scroll Interval (seconds)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0F172A)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { showScrollDropdown = !showScrollDropdown },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            colors = CardDefaults.outlinedCardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$scrollIntervalSec",
                                    fontSize = 16.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color(0xFF0F172A)
                                )
                            }
                        }

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

                    Spacer(modifier = Modifier.height(22.dp))

                    // 5. Enable Device Info Spoofing (Root) Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Device Info Spoofing (Root)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Requires root access",
                                fontSize = 13.sp,
                                color = Color(0xFFDC2626)
                            )
                        }

                        Switch(
                            checked = enableSpoofing,
                            onCheckedChange = { enableSpoofing = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF1565C0),
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFFE2E8F0)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // 6. Randomize Intervals Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Randomize Intervals",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF0F172A)
                        )

                        Switch(
                            checked = randomizeIntervals,
                            onCheckedChange = { randomizeIntervals = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF1565C0),
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFFE2E8F0)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // 7. Advanced Options Header (Expandable)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Advanced Options",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )

                        Icon(
                            imageVector = if (isAdvancedExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Toggle Advanced Options",
                            tint = Color(0xFF0F172A)
                        )
                    }

                    AnimatedVisibility(visible = isAdvancedExpanded) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Text(
                                text = "Automation Mode",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0F172A)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Radio 1: Simple Scroll + Refresh
                            ModeOptionRow(
                                title = "Simple Scroll + Refresh",
                                subtitle = "Basic page scrolling with periodic refresh",
                                isSelected = selectedMode == ExecutionMode.SIMPLE_SCROLL_REFRESH,
                                onSelect = { selectedMode = ExecutionMode.SIMPLE_SCROLL_REFRESH }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Radio 2: Deep Scroll
                            ModeOptionRow(
                                title = "Deep Scroll",
                                subtitle = "Full page traversal with human-like interactions",
                                isSelected = selectedMode == ExecutionMode.DEEP_SCROLL,
                                onSelect = { selectedMode = ExecutionMode.DEEP_SCROLL }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Radio 3: Tab Switching
                            ModeOptionRow(
                                title = "Tab Switching",
                                subtitle = "Simulate multi-tab browsing behavior",
                                isSelected = selectedMode == ExecutionMode.TAB_SWITCHING,
                                onSelect = { selectedMode = ExecutionMode.TAB_SWITCHING }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Radio 4: Custom JavaScript
                            ModeOptionRow(
                                title = "Custom JavaScript",
                                subtitle = "Execute user-defined scripts at intervals",
                                isSelected = selectedMode == ExecutionMode.CUSTOM_JAVASCRIPT,
                                onSelect = { selectedMode = ExecutionMode.CUSTOM_JAVASCRIPT }
                            )

                            Spacer(modifier = Modifier.height(22.dp))

                            // Enable Free Proxy Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Enable Free Proxy",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF0F172A)
                                )

                                Switch(
                                    checked = enableFreeProxy,
                                    onCheckedChange = { enableFreeProxy = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF1565C0),
                                        uncheckedThumbColor = Color(0xFF94A3B8),
                                        uncheckedTrackColor = Color(0xFFE2E8F0)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Primary Action Button: Start Automation ---
            Button(
                onClick = {
                    if (isFormValid) {
                        coroutineScope.launch {
                            val newProfile = AutomationProfile(
                                id = UUID.randomUUID().toString(),
                                name = validUrls.firstOrNull()?.let {
                                    try { java.net.URI(it).host ?: "Automation" } catch (e: Exception) { "Automation" }
                                } ?: "Automation Task",
                                urls = validUrls,
                                tabCount = tabCount.toInt(),
                                executionMode = selectedMode,
                                refreshIntervalSec = refreshIntervalSec,
                                scrollIntervalSec = scrollIntervalSec,
                                randomizeIntervals = randomizeIntervals
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
                    disabledContainerColor = Color(0xFFF1F5F9),
                    contentColor = Color.White,
                    disabledContentColor = Color(0xFF94A3B8)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Start Icon",
                        modifier = Modifier.size(20.dp),
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

            Spacer(modifier = Modifier.height(14.dp))

            // Secondary Shortcut: View Saved Automation Profiles
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color(0xFF1565C0),
                unselectedColor = Color(0xFF64748B)
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}

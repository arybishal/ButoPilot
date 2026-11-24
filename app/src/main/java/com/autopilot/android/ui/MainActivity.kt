package com.autopilot.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.autopilot.android.AutoPilotApplication
import com.autopilot.android.ui.screen.*
import com.autopilot.android.ui.theme.AppColors
import com.autopilot.android.ui.theme.AutoPilotTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class NavTabItem(
    val title: String,
    val icon: ImageVector,
    val index: Int
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as AutoPilotApplication
        val profileRepository = app.profileRepository
        val historyRepository = app.historyRepository
        val scheduleRepository = app.scheduleRepository
        val proxyRepository = app.proxyRepository
        val healthChecker = app.proxyHealthChecker
        val scheduler = app.automationScheduler
        val coordinator = app.automationCoordinator
        val diagnosticsRepository = app.diagnosticsRepository
        val backupManager = app.backupManager
        val restoreCoordinator = app.restoreCoordinator
        val recoveryManager = app.recoveryManager
        val trafficRepository = app.trafficRepository
        val trafficController = app.trafficController

        setContent {
            AutoPilotTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "root",
                    modifier = Modifier.fillMaxSize()
                ) {
                    // --- 1. Root Screen with Horizontal Pager & Floating Rounded Menu ---
                    composable("root") {
                        val pagerState = rememberPagerState(initialPage = 0) { 5 }
                        val coroutineScope = rememberCoroutineScope()

                        Box(modifier = Modifier.fillMaxSize()) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(bottom = 74.dp)
                                ) {
                                    when (page) {
                                        0 -> HomeScreen(
                                            profileRepository = profileRepository,
                                            scheduleRepository = scheduleRepository,
                                            coordinator = coordinator,
                                            onNavigateToEditor = { profileId ->
                                                navController.navigate("editor?profileId=${profileId ?: ""}")
                                            },
                                            onNavigateToScheduleEditor = { scheduleId ->
                                                navController.navigate("schedule_editor?scheduleId=${scheduleId ?: ""}")
                                            },
                                            onNavigateToAutomationsTab = {
                                                coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                            },
                                            onNavigateToProfileTab = {
                                                coroutineScope.launch { pagerState.animateScrollToPage(4) }
                                            },
                                            onNavigateToMonitor = {
                                                navController.navigate("monitor")
                                            }
                                        )
                                        1 -> CreateAutomationScreen(
                                            profileRepository = profileRepository,
                                            coordinator = coordinator,
                                            onNavigateBack = {
                                                coroutineScope.launch { pagerState.animateScrollToPage(0) }
                                            },
                                            onNavigateToMonitor = {
                                                navController.navigate("monitor")
                                            },
                                            onNavigateToSavedProfiles = {
                                                navController.navigate("saved_profiles")
                                            }
                                        )
                                        2 -> ProxyRegionScreen(
                                            proxyRepository = proxyRepository,
                                            healthChecker = healthChecker,
                                            onNavigateBack = {
                                                coroutineScope.launch { pagerState.animateScrollToPage(0) }
                                            },
                                            onNavigateToProxyManager = {
                                                navController.navigate("proxies")
                                            }
                                        )
                                        3 -> TrafficScreen(
                                            repository = trafficRepository,
                                            controller = trafficController
                                        )
                                        4 -> ProfileScreen(
                                            onNavigateToHistory = {
                                                navController.navigate("history")
                                            },
                                            onNavigateToSchedules = {
                                                navController.navigate("schedules")
                                            },
                                            onNavigateToProxies = {
                                                coroutineScope.launch { pagerState.animateScrollToPage(2) }
                                            },
                                            onNavigateToDiagnostics = {
                                                navController.navigate("diagnostics")
                                            },
                                            onNavigateToBackup = {
                                                navController.navigate("backup")
                                            },
                                            onNavigateToRecovery = {
                                                navController.navigate("recovery")
                                            }
                                        )
                                    }
                                }
                            }

                            // Rounded Floating Navigation Dock Menu
                            FloatingNavigationDock(
                                pagerState = pagerState,
                                coroutineScope = coroutineScope,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 12.dp)
                            )
                        }
                    }

                    // Compatibility aliases for legacy root route destinations
                    composable("home") {
                        LaunchedEffect(Unit) {
                            navController.navigate("root") {
                                popUpTo("root") { inclusive = true }
                            }
                        }
                    }
                    composable("automations") {
                        LaunchedEffect(Unit) {
                            navController.navigate("root") {
                                popUpTo("root") { inclusive = true }
                            }
                        }
                    }
                    composable("vpn") {
                        LaunchedEffect(Unit) {
                            navController.navigate("root") {
                                popUpTo("root") { inclusive = true }
                            }
                        }
                    }
                    composable("traffic") {
                        LaunchedEffect(Unit) {
                            navController.navigate("root") {
                                popUpTo("root") { inclusive = true }
                            }
                        }
                    }
                    composable("profile") {
                        LaunchedEffect(Unit) {
                            navController.navigate("root") {
                                popUpTo("root") { inclusive = true }
                            }
                        }
                    }

                    // --- Sub-Screens ---
                    composable("saved_profiles") {
                        ProfileListScreen(
                            profileRepository = profileRepository,
                            coordinator = coordinator,
                            onNavigateToEditor = { profileId ->
                                navController.navigate("editor?profileId=${profileId ?: ""}")
                            },
                            onNavigateToMonitor = {
                                navController.navigate("monitor")
                            },
                            onNavigateToHistory = {
                                navController.navigate("history")
                            },
                            onNavigateToSchedules = {
                                navController.navigate("schedules")
                            },
                            onNavigateToProxies = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable("proxies") {
                        ProxyListScreen(
                            proxyRepository = proxyRepository,
                            healthChecker = healthChecker,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToEditor = { proxyId ->
                                navController.navigate("proxy_editor?proxyId=${proxyId ?: ""}")
                            }
                        )
                    }

                    composable("editor?profileId={profileId}") { backStackEntry ->
                        val profileId = backStackEntry.arguments?.getString("profileId")?.takeIf { it.isNotBlank() }
                        CreateAutomationScreen(
                            profileId = profileId,
                            profileRepository = profileRepository,
                            coordinator = coordinator,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToMonitor = { navController.navigate("monitor") },
                            onNavigateToSavedProfiles = { navController.navigate("saved_profiles") }
                        )
                    }

                    composable("monitor") {
                        AutomationMonitorScreen(
                            coordinator = coordinator,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("history") {
                        HistoryScreen(
                            historyRepository = historyRepository,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToTroubleshooting = { executionId ->
                                navController.navigate("troubleshooting?executionId=$executionId")
                            }
                        )
                    }

                    composable("diagnostics") {
                        DiagnosticsScreen(
                            diagnosticsRepository = diagnosticsRepository,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("backup") {
                        BackupScreen(
                            backupManager = backupManager,
                            restoreCoordinator = restoreCoordinator,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("recovery") {
                        RecoveryScreen(
                            recoveryManager = recoveryManager,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("troubleshooting?executionId={executionId}") { backStackEntry ->
                        val executionId = backStackEntry.arguments?.getString("executionId") ?: ""
                        ExecutionTroubleshootingScreen(
                            executionId = executionId,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("schedules") {
                        ScheduleDashboardScreen(
                            scheduleRepository = scheduleRepository,
                            scheduler = scheduler,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToEditor = { scheduleId ->
                                navController.navigate("schedule_editor?scheduleId=${scheduleId ?: ""}")
                            }
                        )
                    }

                    composable("schedule_editor?scheduleId={scheduleId}") { backStackEntry ->
                        val scheduleId = backStackEntry.arguments?.getString("scheduleId")?.takeIf { it.isNotBlank() }
                        ScheduleEditorScreen(
                            scheduleId = scheduleId,
                            scheduleRepository = scheduleRepository,
                            profileRepository = profileRepository,
                            scheduler = scheduler,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("proxy_editor?proxyId={proxyId}") { backStackEntry ->
                        val proxyId = backStackEntry.arguments?.getString("proxyId")?.takeIf { it.isNotBlank() }
                        ProxyEditorScreen(
                            proxyId = proxyId,
                            proxyRepository = proxyRepository,
                            healthChecker = healthChecker,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingNavigationDock(
    pagerState: PagerState,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val navTabs = listOf(
        NavTabItem("Home", Icons.Filled.Home, 0),
        NavTabItem("Auto", Icons.Filled.PlayCircle, 1),
        NavTabItem("VPN", Icons.Filled.Public, 2),
        NavTabItem("Traffic", Icons.Filled.Traffic, 3),
        NavTabItem("Profile", Icons.Filled.AccountCircle, 4)
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(30.dp),
                spotColor = Color(0x38000000),
                ambientColor = Color(0x1F000000)
            ),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navTabs.forEach { tab ->
                val isSelected = pagerState.currentPage == tab.index

                val animatedBgColor by animateColorAsState(
                    targetValue = if (isSelected) AppColors.Primary else Color.Transparent,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    ),
                    label = "dock_bg_${tab.index}"
                )

                val animatedIconTint by animateColorAsState(
                    targetValue = if (isSelected) Color.Black else Color(0xFF64748B),
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "dock_tint_${tab.index}"
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(animatedBgColor)
                        .clickable {
                            if (!isSelected) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(tab.index)
                                }
                            }
                        }
                        .padding(
                            horizontal = if (isSelected) 14.dp else 10.dp,
                            vertical = 8.dp
                        )
                        .animateContentSize(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioMediumBouncy
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = animatedIconTint,
                            modifier = Modifier.size(20.dp)
                        )

                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                                    expandHorizontally(spring(stiffness = Spring.StiffnessMediumLow)),
                            exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                                    shrinkHorizontally(spring(stiffness = Spring.StiffnessMediumLow))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

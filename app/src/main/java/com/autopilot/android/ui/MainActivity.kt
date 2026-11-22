package com.autopilot.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.autopilot.android.AutoPilotApplication
import com.autopilot.android.ui.screen.*
import com.autopilot.android.ui.theme.AutoPilotTheme

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

        setContent {
            AutoPilotTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val rootTabs = listOf("home", "automations", "vpn", "profile")
                val showBottomBar = currentRoute in rootTabs

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar(
                                containerColor = Color.White,
                                tonalElevation = 8.dp
                            ) {
                                // 1. Home Tab
                                NavigationBarItem(
                                    selected = currentRoute == "home",
                                    onClick = {
                                        if (currentRoute != "home") {
                                            navController.navigate("home") {
                                                popUpTo("home") { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                                    label = {
                                        Text(
                                            "Home",
                                            maxLines = 1,
                                            softWrap = false,
                                            fontSize = 11.sp,
                                            fontWeight = if (currentRoute == "home") FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF1565C0),
                                        selectedTextColor = Color(0xFF1565C0),
                                        indicatorColor = Color(0xFFE0F2FE),
                                        unselectedIconColor = Color(0xFF64748B),
                                        unselectedTextColor = Color(0xFF64748B)
                                    )
                                )

                                // 2. Automations Tab
                                NavigationBarItem(
                                    selected = currentRoute == "automations",
                                    onClick = {
                                        if (currentRoute != "automations") {
                                            navController.navigate("automations") {
                                                popUpTo("home") { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = { Icon(Icons.Filled.PlayCircle, contentDescription = "Automations") },
                                    label = {
                                        Text(
                                            "Automations",
                                            maxLines = 1,
                                            softWrap = false,
                                            fontSize = 11.sp,
                                            fontWeight = if (currentRoute == "automations") FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF1565C0),
                                        selectedTextColor = Color(0xFF1565C0),
                                        indicatorColor = Color(0xFFE0F2FE),
                                        unselectedIconColor = Color(0xFF64748B),
                                        unselectedTextColor = Color(0xFF64748B)
                                    )
                                )

                                // 3. VPN Tab
                                NavigationBarItem(
                                    selected = currentRoute == "vpn",
                                    onClick = {
                                        if (currentRoute != "vpn") {
                                            navController.navigate("vpn") {
                                                popUpTo("home") { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = { Icon(Icons.Filled.Public, contentDescription = "VPN") },
                                    label = {
                                        Text(
                                            "VPN",
                                            maxLines = 1,
                                            softWrap = false,
                                            fontSize = 11.sp,
                                            fontWeight = if (currentRoute == "vpn") FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF1565C0),
                                        selectedTextColor = Color(0xFF1565C0),
                                        indicatorColor = Color(0xFFE0F2FE),
                                        unselectedIconColor = Color(0xFF64748B),
                                        unselectedTextColor = Color(0xFF64748B)
                                    )
                                )

                                // 4. Profile Tab
                                NavigationBarItem(
                                    selected = currentRoute == "profile",
                                    onClick = {
                                        if (currentRoute != "profile") {
                                            navController.navigate("profile") {
                                                popUpTo("home") { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = { Icon(Icons.Filled.AccountCircle, contentDescription = "Profile") },
                                    label = {
                                        Text(
                                            "Profile",
                                            maxLines = 1,
                                            softWrap = false,
                                            fontSize = 11.sp,
                                            fontWeight = if (currentRoute == "profile") FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF1565C0),
                                        selectedTextColor = Color(0xFF1565C0),
                                        indicatorColor = Color(0xFFE0F2FE),
                                        unselectedIconColor = Color(0xFF64748B),
                                        unselectedTextColor = Color(0xFF64748B)
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        // --- 1. Home Tab ---
                        composable("home") {
                            HomeScreen(
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
                                    navController.navigate("automations") {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onNavigateToProfileTab = {
                                    navController.navigate("profile") {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onNavigateToMonitor = {
                                    navController.navigate("monitor")
                                }
                            )
                        }

                        // --- 2. Automations Tab ---
                        composable("automations") {
                            CreateAutomationScreen(
                                profileRepository = profileRepository,
                                coordinator = coordinator,
                                onNavigateBack = {
                                    navController.navigate("home") {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onNavigateToMonitor = {
                                    navController.navigate("monitor")
                                },
                                onNavigateToSavedProfiles = {
                                    navController.navigate("saved_profiles")
                                }
                            )
                        }

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
                                    navController.navigate("vpn")
                                }
                            )
                        }

                        // --- 3. VPN Tab ---
                        composable("vpn") {
                            ProxyRegionScreen(
                                proxyRepository = proxyRepository,
                                healthChecker = healthChecker,
                                onNavigateBack = {
                                    navController.navigate("home") {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onNavigateToProxyManager = {
                                    navController.navigate("proxies")
                                }
                            )
                        }

                        // Also keep "proxies" as route alias
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

                        // --- 4. Profile Tab ---
                        composable("profile") {
                            ProfileScreen(
                                onNavigateToHistory = {
                                    navController.navigate("history")
                                },
                                onNavigateToSchedules = {
                                    navController.navigate("schedules")
                                },
                                onNavigateToProxies = {
                                    navController.navigate("vpn")
                                }
                            )
                        }

                        // --- Sub-Screens ---
                        composable("editor?profileId={profileId}") { backStackEntry ->
                            val profileId = backStackEntry.arguments?.getString("profileId")?.takeIf { it.isNotBlank() }
                            ProfileEditorScreen(
                                profileId = profileId,
                                profileRepository = profileRepository,
                                proxyRepository = proxyRepository,
                                onNavigateBack = { navController.popBackStack() }
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
}

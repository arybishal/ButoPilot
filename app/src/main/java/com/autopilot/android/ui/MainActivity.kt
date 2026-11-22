package com.autopilot.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.autopilot.android.AutoPilotApplication
import com.autopilot.android.ui.screen.AutomationMonitorScreen
import com.autopilot.android.ui.screen.HistoryScreen
import com.autopilot.android.ui.screen.ProfileEditorScreen
import com.autopilot.android.ui.screen.ProfileListScreen
import com.autopilot.android.ui.theme.AutoPilotTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as AutoPilotApplication
        val profileRepository = app.profileRepository
        val historyRepository = app.historyRepository
        val coordinator = app.automationCoordinator

        setContent {
            AutoPilotTheme {
                val navController = rememberNavController()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "profiles",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("profiles") {
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
                                }
                            )
                        }

                        composable("editor?profileId={profileId}") { backStackEntry ->
                            val profileId = backStackEntry.arguments?.getString("profileId")?.takeIf { it.isNotBlank() }
                            ProfileEditorScreen(
                                profileId = profileId,
                                profileRepository = profileRepository,
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
                    }
                }
            }
        }
    }
}

package com.autopilot.android.ui.screen

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.autopilot.android.engine.AutomationCoordinator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPreviewScreen(
    coordinator: AutomationCoordinator,
    tabIndex: Int,
    onNavigateBack: () -> Unit
) {
    val engineState by coordinator.engineState.collectAsState()
    val selectedTab by coordinator.webViewManager.activeTabIndex.collectAsState()
    val activeWebView by coordinator.webViewManager.activeWebView.collectAsState()
    val webView = if (tabIndex == selectedTab) activeWebView else coordinator.webViewManager.getWebView(tabIndex)
    val tabState = engineState.tabStates.firstOrNull { it.tabIndex == tabIndex }
    val title = tabState?.pageTitle?.takeIf { it.isNotBlank() }
        ?: tabState?.currentUrl?.takeIf { it.isNotBlank() }
        ?: "Preview Tab ${tabIndex + 1}"

    LaunchedEffect(tabIndex) {
        coordinator.webViewManager.selectTab(tabIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (webView != null) {
                key(webView) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { container ->
                            if (container.getChildAt(0) != webView) {
                                container.removeAllViews()
                                (webView.parent as? ViewGroup)?.removeView(webView)
                                container.addView(
                                    webView,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        }
                    )
                }
            } else {
                Text(
                    text = "Tab ${tabIndex + 1} has no page loaded yet. Start automation to preview it.",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

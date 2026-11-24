package com.autopilot.android.feature.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppUpdatesSection(
    state: UpdateState,
    onCheckForUpdates: () -> Unit,
    onViewUpdateDialog: () -> Unit,
    onInstallUpdate: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "App Updates",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header with status indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        val iconColor = when {
                            state.checkStatus == UpdateCheckStatus.UPDATE_AVAILABLE || state.downloadProgress.status == DownloadStatus.COMPLETED -> Color(0xFF16A34A)
                            state.checkStatus == UpdateCheckStatus.CHECKING || state.downloadProgress.status == DownloadStatus.DOWNLOADING -> Color(0xFF2563EB)
                            state.checkStatus == UpdateCheckStatus.ERROR -> Color(0xFFDC2626)
                            else -> Color(0xFF64748B)
                        }

                        Icon(
                            imageVector = when {
                                state.downloadProgress.status == DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
                                state.checkStatus == UpdateCheckStatus.UPDATE_AVAILABLE -> Icons.Default.NewReleases
                                state.checkStatus == UpdateCheckStatus.CHECKING || state.downloadProgress.status == DownloadStatus.DOWNLOADING -> Icons.Default.Sync
                                state.checkStatus == UpdateCheckStatus.ERROR -> Icons.Default.ErrorOutline
                                else -> Icons.Default.SystemUpdate
                            },
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            val statusText = when {
                                state.downloadProgress.status == DownloadStatus.DOWNLOADING -> "Downloading update (${state.downloadProgress.percentage}%)"
                                state.downloadProgress.status == DownloadStatus.COMPLETED -> "Download completed • Installation pending"
                                state.downloadProgress.status == DownloadStatus.VERIFYING -> "Verifying download integrity..."
                                state.checkStatus == UpdateCheckStatus.CHECKING -> "Checking for updates..."
                                state.checkStatus == UpdateCheckStatus.UPDATE_AVAILABLE -> "Update available (v${state.updateInfo?.versionName})"
                                state.checkStatus == UpdateCheckStatus.UP_TO_DATE -> "You're using the latest version."
                                state.checkStatus == UpdateCheckStatus.ERROR -> "Unable to check for updates."
                                else -> "Check GitHub for new releases."
                            }

                            Text(
                                text = "GitHub Auto-Update",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                            Text(
                                text = statusText,
                                fontSize = 12.sp,
                                color = if (state.checkStatus == UpdateCheckStatus.UPDATE_AVAILABLE || state.downloadProgress.status == DownloadStatus.COMPLETED) Color(0xFF16A34A) else Color(0xFF64748B),
                                fontWeight = if (state.checkStatus == UpdateCheckStatus.UPDATE_AVAILABLE) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    // Check for Updates button
                    Button(
                        onClick = onCheckForUpdates,
                        enabled = state.checkStatus != UpdateCheckStatus.CHECKING && state.downloadProgress.status != DownloadStatus.DOWNLOADING,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        if (state.checkStatus == UpdateCheckStatus.CHECKING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Checking", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Check", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // If downloading, show progress bar
                if (state.downloadProgress.status == DownloadStatus.DOWNLOADING) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { state.downloadProgress.percentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF2563EB),
                        trackColor = Color(0xFFE2E8F0)
                    )
                }

                // Update Available / Ready Banner
                if (state.checkStatus == UpdateCheckStatus.UPDATE_AVAILABLE && state.updateInfo != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (state.downloadProgress.status == DownloadStatus.COMPLETED) {
                                    onInstallUpdate()
                                } else {
                                    onViewUpdateDialog()
                                }
                            }
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
                                    text = if (state.downloadProgress.status == DownloadStatus.COMPLETED) "Update Ready to Install" else "New Version: v${state.updateInfo.versionName}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF1E40AF)
                                )
                                Text(
                                    text = if (state.downloadProgress.status == DownloadStatus.COMPLETED) "Tap to launch package installer" else "Tap to view changelog and download",
                                    fontSize = 11.sp,
                                    color = Color(0xFF3B82F6)
                                )
                            }
                            Icon(
                                imageVector = if (state.downloadProgress.status == DownloadStatus.COMPLETED) Icons.Default.InstallMobile else Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFF1E40AF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE2E8F0))

                // Installed version
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Installed Version", fontSize = 13.sp, color = Color(0xFF64748B))
                    Text("v${state.currentVersion}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Latest known version
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Latest on GitHub", fontSize = 13.sp, color = Color(0xFF64748B))
                    val latestText = state.updateInfo?.versionName?.let { "v$it" } ?: "v${state.currentVersion}"
                    Text(latestText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Last checked timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Last Checked", fontSize = 13.sp, color = Color(0xFF64748B))
                    val timeStr = if (state.lastCheckTimestamp > 0) {
                        SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(state.lastCheckTimestamp))
                    } else {
                        "Never"
                    }
                    Text(timeStr, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF64748B))
                }
            }
        }
    }
}

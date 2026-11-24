package com.autopilot.android.feature.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    currentVersion: String,
    downloadProgress: DownloadProgress,
    onDownloadAndInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallNow: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (downloadProgress.status != DownloadStatus.DOWNLOADING) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = downloadProgress.status != DownloadStatus.DOWNLOADING,
            dismissOnClickOutside = downloadProgress.status != DownloadStatus.DOWNLOADING,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // --- 1. Header with Badge & Icon ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFEFF6FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RocketLaunch,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Update Available",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = updateInfo.releaseName.ifBlank { "New version released" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                    }

                    if (downloadProgress.status != DownloadStatus.DOWNLOADING) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- 2. Version Comparison Tags Row ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // New Version Badge
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF86EFAC))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "v${updateInfo.versionName}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D)
                            )
                        }
                    }

                    // Current Version Badge
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Installed: v$currentVersion",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // File Size Badge (if available)
                    if (updateInfo.apkSize > 0) {
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            val sizeMb = updateInfo.apkSize.toFloat() / (1024 * 1024)
                            Text(
                                text = "%.1f MB".format(Locale.US, sizeMb),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- 3. Changelog Container ---
                Text(
                    text = "Release Notes",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF334155)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 220.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    MarkdownChangelogView(
                        markdownText = updateInfo.changelog,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- 4. Download & Verification Status Panel ---
                when (downloadProgress.status) {
                    DownloadStatus.DOWNLOADING -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF0FDF4), RoundedCornerShape(12.dp))
                                .border(BorderStroke(1.dp, Color(0xFFBBF7D0)), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF16A34A)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Downloading update...",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF16A34A)
                                    )
                                }
                                Text(
                                    text = "${downloadProgress.percentage}%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF16A34A)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { downloadProgress.percentage / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF16A34A),
                                trackColor = Color(0xFFDCFCE7)
                            )

                            if (downloadProgress.totalBytes > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                val dlMb = downloadProgress.bytesDownloaded.toFloat() / (1024 * 1024)
                                val totalMb = downloadProgress.totalBytes.toFloat() / (1024 * 1024)
                                Text(
                                    text = "%.1f MB / %.1f MB".format(Locale.US, dlMb, totalMb),
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    DownloadStatus.VERIFYING -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF2563EB)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Verifying package signature & integrity...",
                                fontSize = 12.sp,
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    DownloadStatus.COMPLETED -> {
                        Surface(
                            color = Color(0xFFF0FDF4),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Download complete and verified. Ready to install.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    DownloadStatus.FAILED -> {
                        Surface(
                            color = Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = downloadProgress.errorMessage ?: "Download failed. Please try again.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFB91C1C)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    else -> {}
                }

                // --- 5. Dialog Actions ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (downloadProgress.status) {
                        DownloadStatus.DOWNLOADING -> {
                            OutlinedButton(
                                onClick = onCancelDownload,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFDC2626)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cancel Download", fontWeight = FontWeight.Bold)
                            }
                        }

                        DownloadStatus.COMPLETED -> {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                            ) {
                                Text("Later", color = Color(0xFF475569), fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = onInstallNow,
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF16A34A),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.InstallMobile,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Install Now", fontWeight = FontWeight.Bold)
                            }
                        }

                        DownloadStatus.FAILED -> {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                            ) {
                                Text("Close", color = Color(0xFF475569), fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = onDownloadAndInstall,
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2563EB),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry", fontWeight = FontWeight.Bold)
                            }
                        }

                        else -> {
                            // IDLE / UPDATE_AVAILABLE
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                            ) {
                                Text("Later", color = Color(0xFF475569), fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = onDownloadAndInstall,
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2563EB),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download & Install", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

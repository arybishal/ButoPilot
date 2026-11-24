package com.autopilot.android.ui.screen
import com.autopilot.android.ui.theme.AppColors

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopilot.android.AutoPilotApplication
import com.autopilot.android.data.local.entity.BackupRecordEntity
import com.autopilot.android.feature.backup.*
import com.autopilot.android.feature.backup.worker.AutoBackupWorker
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    backupManager: BackupManager,
    restoreCoordinator: RestoreCoordinator,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as AutoPilotApplication
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    var selectedCategories by remember {
        mutableStateOf(
            setOf(
                BackupCategory.PROFILES,
                BackupCategory.SCHEDULES,
                BackupCategory.PROXIES,
                BackupCategory.PROXY_SOURCES,
                BackupCategory.EXECUTION_HISTORY
            )
        )
    }

    var enableEncryption by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var pendingRestoreFile by remember { mutableStateOf<File?>(null) }
    var restorePreview by remember { mutableStateOf<RestorePreview?>(null) }
    var restorePasswordPrompt by remember { mutableStateOf(false) }
    var restorePasswordInput by remember { mutableStateOf("") }
    var restoreMode by remember { mutableStateOf(RestoreMode.MERGE) }
    var restoreResult by remember { mutableStateOf<RestoreResult?>(null) }

    val backupPreferences = remember { BackupPreferences(context) }
    var autoBackupSettings by remember { mutableStateOf(backupPreferences.getSettings()) }

    val backupRecords by app.database.backupRecordDao().getAllFlow().collectAsState(initial = emptyList())

    // SAF Create Document launcher
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destinationUri: Uri? ->
        if (destinationUri != null) {
            scope.launch {
                isProcessing = true
                try {
                    val passToUse = if (enableEncryption) password.takeIf { it.isNotBlank() } else null
                    val generatedFile = backupManager.createBackup(
                        categories = selectedCategories,
                        password = passToUse
                    )
                    backupManager.exportToUri(generatedFile, destinationUri)
                    Toast.makeText(context, "Backup successfully exported!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    // SAF Open Document launcher
    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { sourceUri: Uri? ->
        if (sourceUri != null) {
            scope.launch {
                isProcessing = true
                try {
                    val tempDir = File(context.filesDir, "backups/temp").apply { if (!exists()) mkdirs() }
                    val tempFile = File(tempDir, "restore_target_${System.currentTimeMillis()}.autopilot")
                    backupManager.importFromUri(sourceUri, tempFile)
                    pendingRestoreFile = tempFile

                    val preview = backupManager.generatePreview(tempFile, null)
                    if (preview.requiresPassword) {
                        restorePasswordPrompt = true
                    } else {
                        restorePreview = preview
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot read backup archive: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Backup & Recovery Center",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- SECTION 1: CREATE BACKUP ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Backup, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Create Backup (.autopilot)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.Black
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Select data categories to include in this archive:",
                            fontSize = 13.sp,
                            color = Color(0xFF475569)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        BackupCategory.values().forEach { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCategories = if (selectedCategories.contains(cat)) {
                                            selectedCategories - cat
                                        } else {
                                            selectedCategories + cat
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedCategories.contains(cat),
                                    onCheckedChange = { checked ->
                                        selectedCategories = if (checked) selectedCategories + cat else selectedCategories - cat
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(cat.displayName, fontSize = 14.sp, color = Color(0xFF1E293B))
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFE2E8F0))

                        // Security notice
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Security Rule: Proxy passwords and Keystore secrets are strictly excluded from backups.",
                                fontSize = 11.sp,
                                color = Color(0xFF059669)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Optional Encryption
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = enableEncryption,
                                onCheckedChange = { enableEncryption = it }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Password Protection (AES-256-GCM)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Encrypts backup archive with PBKDF2-HMAC-SHA256", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }

                        if (enableEncryption) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Backup Password") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text("Confirm Password") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (enableEncryption && (password.isBlank() || password != confirmPassword)) {
                                    Toast.makeText(context, "Passwords do not match or are blank", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val filename = "autopilot_backup_${System.currentTimeMillis()}.autopilot"
                                createDocLauncher.launch(filename)
                            },
                            enabled = selectedCategories.isNotEmpty() && !isProcessing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, contentColor = AppColors.OnPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AppColors.OnPrimary)
                            } else {
                                Icon(Icons.Filled.FileDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export Backup (.autopilot)")
                            }
                        }
                    }
                }
            }

            // --- SECTION: AUTOMATIC BACKUP CONFIGURATION ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color(0xFF6366F1))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "Automatic Periodic Backup",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.Black
                                    )
                                    Text(
                                        "Automated background backup via WorkManager",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                            Switch(
                                checked = autoBackupSettings.isEnabled,
                                onCheckedChange = { enabled ->
                                    val updated = autoBackupSettings.copy(isEnabled = enabled)
                                    autoBackupSettings = updated
                                    backupPreferences.saveSettings(updated)
                                    AutoBackupWorker.schedule(context, updated)
                                    Toast.makeText(
                                        context,
                                        if (enabled) "Auto-backup enabled" else "Auto-backup disabled",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }

                        if (autoBackupSettings.isEnabled) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Frequency selection
                            Text(
                                "Backup Frequency",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AutoBackupFrequency.values().forEach { freq ->
                                    FilterChip(
                                        selected = autoBackupSettings.frequency == freq,
                                        onClick = {
                                            val updated = autoBackupSettings.copy(frequency = freq)
                                            autoBackupSettings = updated
                                            backupPreferences.saveSettings(updated)
                                            AutoBackupWorker.schedule(context, updated)
                                        },
                                        label = { Text(freq.displayName, fontSize = 12.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Retention count
                            Text(
                                "Retained Archive Count",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                "Older automatic backups will be pruned automatically",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(3, 5, 10).forEach { count ->
                                    FilterChip(
                                        selected = autoBackupSettings.retentionCount == count,
                                        onClick = {
                                            val updated = autoBackupSettings.copy(retentionCount = count)
                                            autoBackupSettings = updated
                                            backupPreferences.saveSettings(updated)
                                        },
                                        label = { Text("$count Backups", fontSize = 12.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Include Execution History Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val updated = autoBackupSettings.copy(includeHistory = !autoBackupSettings.includeHistory)
                                        autoBackupSettings = updated
                                        backupPreferences.saveSettings(updated)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = autoBackupSettings.includeHistory,
                                    onCheckedChange = { checked ->
                                        val updated = autoBackupSettings.copy(includeHistory = checked)
                                        autoBackupSettings = updated
                                        backupPreferences.saveSettings(updated)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Include Execution History in auto-backups", fontSize = 13.sp, color = Color(0xFF1E293B))
                                    Text("Includes automation logs and session outcomes", fontSize = 11.sp, color = Color(0xFF64748B))
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Folder, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Destination: Internal App Storage (backups/auto)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }

            // --- SECTION 2: RESTORE BACKUP ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Restore, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Restore Backup Archive",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.Black
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Select a previously exported .autopilot file to preview contents and restore records safely.",
                            fontSize = 13.sp,
                            color = Color(0xFF475569)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                openDocLauncher.launch(arrayOf("*/*", "application/octet-stream"))
                            },
                            enabled = !isProcessing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, contentColor = AppColors.OnPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select .autopilot Archive to Restore")
                        }
                    }
                }
            }

            // --- SECTION 3: RECENT BACKUP RECORDS ---
            item {
                Text(
                    "Local Backup Activity",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (backupRecords.isEmpty()) {
                item {
                    Text("No local backup history recorded.", fontSize = 13.sp, color = Color(0xFF94A3B8))
                }
            } else {
                items(backupRecords, key = { it.id }) { rec ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    rec.status,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.Black
                                )
                                Text(
                                    dateFormat.format(Date(rec.timestamp)),
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${rec.itemCount} items • ${rec.sizeBytes / 1024} KB",
                                    fontSize = 12.sp,
                                    color = Color(0xFF475569)
                                )
                                if (rec.isEncrypted) {
                                    Text("Encrypted", fontSize = 10.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- RESTORE PASSWORD DIALOG ---
    if (restorePasswordPrompt && pendingRestoreFile != null) {
        AlertDialog(
            onDismissRequest = { restorePasswordPrompt = false },
            title = { Text("Encrypted Backup") },
            text = {
                Column {
                    Text("This .autopilot archive is encrypted. Please enter the password to unlock:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = restorePasswordInput,
                        onValueChange = { restorePasswordInput = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val file = pendingRestoreFile ?: return@launch
                            val preview = backupManager.generatePreview(file, restorePasswordInput)
                            restorePasswordPrompt = false
                            restorePreview = preview
                        }
                    }
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { restorePasswordPrompt = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- RESTORE PREVIEW & EXECUTION DIALOG ---
    restorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { restorePreview = null },
            title = { Text("Restore Preview") },
            text = {
                Column {
                    Text(
                        "Backup created on: ${dateFormat.format(Date(preview.manifest.createdAt))}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Automation Profiles: ${preview.profileCount}", fontSize = 13.sp)
                    Text("• Schedules: ${preview.scheduleCount} (Restored as PAUSED)", fontSize = 13.sp)
                    Text("• Proxies: ${preview.proxyCount} (Passwords excluded)", fontSize = 13.sp)
                    Text("• Proxy Sources: ${preview.sourceCount}", fontSize = 13.sp)
                    Text("• Execution History: ${preview.historyCount}", fontSize = 13.sp)

                    if (preview.warnings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Warnings:", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), fontSize = 12.sp)
                        preview.warnings.forEach { w ->
                            Text("• $w", color = Color(0xFFDC2626), fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Restore Mode:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = restoreMode == RestoreMode.MERGE,
                            onClick = { restoreMode = RestoreMode.MERGE }
                        )
                        Text("Merge (Preserve existing)", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = restoreMode == RestoreMode.REPLACE,
                            onClick = { restoreMode = RestoreMode.REPLACE }
                        )
                        Text("Replace (Safety backup taken)", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = pendingRestoreFile ?: return@Button
                        val pass = restorePasswordInput.takeIf { it.isNotBlank() }
                        scope.launch {
                            isProcessing = true
                            val result = restoreCoordinator.executeRestore(file, pass, restoreMode)
                            restorePreview = null
                            restoreResult = result
                            isProcessing = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, contentColor = AppColors.OnPrimary)
                ) {
                    Text("Proceed Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { restorePreview = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- RESTORE RESULT DIALOG ---
    restoreResult?.let { res ->
        AlertDialog(
            onDismissRequest = { restoreResult = null },
            title = { Text(if (res.success) "Restore Successful" else "Restore Failed") },
            text = {
                Column {
                    Text(res.message, fontSize = 13.sp)
                    if (res.success) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Profiles imported: ${res.importedProfiles}", fontSize = 12.sp)
                        Text("Schedules imported: ${res.importedSchedules}", fontSize = 12.sp)
                        Text("Proxies imported: ${res.importedProxies}", fontSize = 12.sp)
                        if (res.safetyBackupPath != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Safety backup saved at: ${res.safetyBackupPath}", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { restoreResult = null }) {
                    Text("OK")
                }
            }
        )
    }
}

package com.autopilot.android.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.autopilot.android.data.repository.ProxyRepository
import com.autopilot.android.domain.model.proxy.ProxyConfiguration
import com.autopilot.android.domain.model.proxy.ProxyCredentials
import com.autopilot.android.domain.model.proxy.ProxyHealthStatus
import com.autopilot.android.domain.model.proxy.ProxyProtocol
import com.autopilot.android.domain.model.proxy.ProxyTestResult
import com.autopilot.android.domain.validation.ProxyValidator
import com.autopilot.android.feature.proxy.ProxyHealthChecker
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyEditorScreen(
    proxyId: String?,
    proxyRepository: ProxyRepository,
    healthChecker: ProxyHealthChecker,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(ProxyProtocol.HTTP) }
    var host by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("8080") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var region by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }

    var isLoaded by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ProxyTestResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(proxyId) {
        if (!proxyId.isNullOrBlank()) {
            val existing = proxyRepository.getProxyById(proxyId)
            if (existing != null) {
                name = existing.name
                protocol = existing.protocol
                host = existing.host
                portText = existing.port.toString()
                username = existing.credentials?.username ?: ""
                password = existing.credentials?.password ?: ""
                region = existing.region ?: ""
                notes = existing.notes
                enabled = existing.enabled
            }
        }
        isLoaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (proxyId == null) "New Proxy" else "Edit Proxy", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val port = portText.toIntOrNull() ?: 0
                            val creds = if (username.isNotBlank() || password.isNotBlank()) {
                                ProxyCredentials(
                                    username = username.takeIf { it.isNotBlank() },
                                    password = password.takeIf { it.isNotBlank() }
                                )
                            } else null

                            val proxy = ProxyConfiguration(
                                id = proxyId ?: UUID.randomUUID().toString(),
                                name = name.ifBlank { "${protocol.displayName} $host:$port" },
                                protocol = protocol,
                                host = host.trim(),
                                port = port,
                                credentials = creds,
                                region = region.trim().takeIf { it.isNotBlank() },
                                notes = notes.trim(),
                                enabled = enabled
                            )

                            val validation = ProxyValidator.validateConfiguration(proxy)
                            if (!validation.isValid) {
                                errorMessage = validation.errors.firstOrNull()
                                return@IconButton
                            }

                            scope.launch {
                                proxyRepository.saveProxy(proxy)
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Proxy Name
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = null
                },
                label = { Text("Proxy Name") },
                placeholder = { Text("e.g. US Residential Proxy") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Protocol Selection
            Text("Protocol", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProxyProtocol.entries.forEach { p ->
                    FilterChip(
                        selected = protocol == p,
                        onClick = { protocol = p },
                        label = { Text(p.displayName) }
                    )
                }
            }

            // Host & Port Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = {
                        host = it
                        errorMessage = null
                    },
                    label = { Text("Host / IP Address") },
                    placeholder = { Text("192.168.1.1 or proxy.example.com") },
                    modifier = Modifier.weight(2f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() }) {
                            portText = it
                            errorMessage = null
                        }
                    },
                    label = { Text("Port") },
                    placeholder = { Text("8080") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }

            // Authentication
            Text("Authentication (Optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password"
                        )
                    }
                }
            )

            // Region & Notes
            OutlinedTextField(
                value = region,
                onValueChange = { region = it },
                label = { Text("Region / Country (Optional)") },
                placeholder = { Text("e.g. US, Germany, Tokyo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            // Enabled Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Proxy", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            // Test Connection Button & Result
            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        val port = portText.toIntOrNull() ?: 0
                        val creds = if (username.isNotBlank() || password.isNotBlank()) {
                            ProxyCredentials(
                                username = username.takeIf { it.isNotBlank() },
                                password = password.takeIf { it.isNotBlank() }
                            )
                        } else null

                        val tempProxy = ProxyConfiguration(
                            name = name.ifBlank { "Test Proxy" },
                            protocol = protocol,
                            host = host.trim(),
                            port = port,
                            credentials = creds,
                            region = region.trim().takeIf { it.isNotBlank() }
                        )

                        val validation = ProxyValidator.validateConfiguration(tempProxy)
                        if (!validation.isValid) {
                            errorMessage = validation.errors.firstOrNull()
                            return@Button
                        }

                        scope.launch {
                            isTesting = true
                            testResult = healthChecker.checkHealth(tempProxy)
                            isTesting = false
                        }
                    },
                    enabled = !isTesting && host.isNotBlank()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing...")
                    } else {
                        Icon(Icons.Default.Speed, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Connection")
                    }
                }

                testResult?.let { res ->
                    HealthStatusBadge(status = res.status, latencyMs = res.latencyMs)
                }
            }

            testResult?.errorMessage?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

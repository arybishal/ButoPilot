package com.autopilot.android.feature.diagnostics

import android.app.AlarmManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.autopilot.android.core.security.ProxyCredentialEncryptor
import com.autopilot.android.core.system.RootChecker
import com.autopilot.android.data.local.AutoPilotDatabase
import com.autopilot.android.data.repository.ProfileRepository
import com.autopilot.android.data.repository.ProxyRepository
import com.autopilot.android.data.repository.ScheduleRepository
import com.autopilot.android.domain.model.ProfileStatus
import com.autopilot.android.engine.AutomationCoordinator
import com.autopilot.android.engine.ResourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetAddress
import java.security.KeyStore
import java.util.concurrent.TimeUnit

class DiagnosticRunner(
    private val context: Context,
    private val database: AutoPilotDatabase,
    private val profileRepository: ProfileRepository,
    private val scheduleRepository: ScheduleRepository,
    private val proxyRepository: ProxyRepository,
    private val coordinator: AutomationCoordinator? = null
) {
    private val resourceManager = ResourceManager(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    suspend fun runAllChecks(): List<DiagnosticCheckResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiagnosticCheckResult>()

        results.addAll(checkDatabase())
        results.addAll(checkAutomation())
        results.addAll(checkScheduling())
        results.addAll(checkNetwork())
        results.addAll(checkStorage())
        results.addAll(checkSecurity())

        results
    }

    suspend fun generateHealthReport(results: List<DiagnosticCheckResult>): SystemHealthReport {
        val overallStatus = when {
            results.any { it.status == DiagnosticStatus.ERROR } -> DiagnosticStatus.ERROR
            results.any { it.status == DiagnosticStatus.WARNING } -> DiagnosticStatus.WARNING
            results.any { it.status == DiagnosticStatus.UNAVAILABLE } -> DiagnosticStatus.WARNING
            results.all { it.status == DiagnosticStatus.HEALTHY } -> DiagnosticStatus.HEALTHY
            else -> DiagnosticStatus.NOT_CHECKED
        }

        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (e: Exception) {
            null
        }

        val versionName = packageInfo?.versionName ?: "1.0.0"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode?.toInt() ?: 1
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode ?: 1
        }

        val isRooted = RootChecker.checkRootAccess()

        return SystemHealthReport(
            timestamp = System.currentTimeMillis(),
            overallStatus = overallStatus,
            checkResults = results,
            appVersion = versionName,
            buildNumber = versionCode,
            androidRelease = Build.VERSION.RELEASE ?: "Unknown",
            apiLevel = Build.VERSION.SDK_INT,
            deviceManufacturer = Build.MANUFACTURER ?: "Unknown",
            deviceModel = Build.MODEL ?: "Unknown",
            isRooted = isRooted
        )
    }

    suspend fun checkDatabase(): List<DiagnosticCheckResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiagnosticCheckResult>()
        val startTotal = System.currentTimeMillis()

        // 1. Room DB Availability & Read/Write Test
        try {
            val db = database.openHelper.writableDatabase
            val cur = db.query("SELECT 1")
            val hasRow = cur.moveToFirst()
            cur.close()

            val duration = System.currentTimeMillis() - startTotal
            results.add(
                DiagnosticCheckResult(
                    category = DiagnosticCategory.DATABASE,
                    name = "Room Database Read/Write",
                    status = if (hasRow) DiagnosticStatus.HEALTHY else DiagnosticStatus.ERROR,
                    shortResult = if (hasRow) "Database responsive" else "Query returned empty result",
                    technicalDetails = mapOf(
                        "isOpen" to db.isOpen.toString(),
                        "isReadOnly" to db.isReadOnly.toString(),
                        "path" to (db.path ?: "in-memory")
                    ),
                    durationMs = duration
                )
            )
        } catch (e: Exception) {
            results.add(
                DiagnosticCheckResult(
                    category = DiagnosticCategory.DATABASE,
                    name = "Room Database Read/Write",
                    status = DiagnosticStatus.ERROR,
                    shortResult = "Database access failed: ${e.message}",
                    technicalDetails = mapOf("exception" to (e.javaClass.simpleName)),
                    durationMs = System.currentTimeMillis() - startTotal
                )
            )
        }

        // 2. Migration Version & Integrity Check
        val integStart = System.currentTimeMillis()
        try {
            val db = database.openHelper.readableDatabase
            val version = db.version
            var integrityOk = false
            var integrityMsg = "Integrity check not run"

            val integrityCursor = db.query("PRAGMA quick_check")
            if (integrityCursor.moveToFirst()) {
                val check = integrityCursor.getString(0)
                integrityOk = check.equals("ok", ignoreCase = true)
                integrityMsg = check
            }
            integrityCursor.close()

            results.add(
                DiagnosticCheckResult(
                    category = DiagnosticCategory.DATABASE,
                    name = "Database Schema & Integrity",
                    status = if (integrityOk && version >= 4) DiagnosticStatus.HEALTHY else DiagnosticStatus.WARNING,
                    shortResult = if (integrityOk) "Schema v$version verified (Integrity OK)" else "Integrity warning: $integrityMsg",
                    technicalDetails = mapOf(
                        "schemaVersion" to version.toString(),
                        "pragmaCheck" to integrityMsg
                    ),
                    durationMs = System.currentTimeMillis() - integStart
                )
            )
        } catch (e: Exception) {
            results.add(
                DiagnosticCheckResult(
                    category = DiagnosticCategory.DATABASE,
                    name = "Database Schema & Integrity",
                    status = DiagnosticStatus.ERROR,
                    shortResult = "Integrity inspection failed: ${e.message}",
                    technicalDetails = mapOf("exception" to (e.javaClass.simpleName)),
                    durationMs = System.currentTimeMillis() - integStart
                )
            )
        }

        // 3. Foreign Key & Orphaned Reference Consistency
        val fkStart = System.currentTimeMillis()
        try {
            val profiles = profileRepository.getAllProfiles()
            val proxies = proxyRepository.getAllProxies()
            val proxyIds = proxies.map { it.id }.toSet()

            val orphanedCount = profiles.count { it.proxyId != null && !proxyIds.contains(it.proxyId) }

            results.add(
                DiagnosticCheckResult(
                    category = DiagnosticCategory.DATABASE,
                    name = "Referential Integrity",
                    status = if (orphanedCount == 0) DiagnosticStatus.HEALTHY else DiagnosticStatus.WARNING,
                    shortResult = if (orphanedCount == 0) "Zero orphaned references" else "$orphanedCount profiles reference missing proxies",
                    technicalDetails = mapOf(
                        "totalProfiles" to profiles.size.toString(),
                        "totalProxies" to proxies.size.toString(),
                        "orphanedProxyReferences" to orphanedCount.toString()
                    ),
                    durationMs = System.currentTimeMillis() - fkStart
                )
            )
        } catch (e: Exception) {
            results.add(
                DiagnosticCheckResult(
                    category = DiagnosticCategory.DATABASE,
                    name = "Referential Integrity",
                    status = DiagnosticStatus.WARNING,
                    shortResult = "Reference check encountered error: ${e.message}",
                    technicalDetails = mapOf("exception" to (e.javaClass.simpleName)),
                    durationMs = System.currentTimeMillis() - fkStart
                )
            )
        }

        results
    }

    suspend fun checkAutomation(): List<DiagnosticCheckResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiagnosticCheckResult>()
        val start = System.currentTimeMillis()

        // 1. Coordinator & Execution State
        val isCoordAvailable = coordinator != null
        val engineState = coordinator?.engineState?.value
        val coordStatus = engineState?.status ?: ProfileStatus.IDLE

        results.add(
            DiagnosticCheckResult(
                category = DiagnosticCategory.AUTOMATION,
                name = "Automation Coordinator",
                status = if (isCoordAvailable) DiagnosticStatus.HEALTHY else DiagnosticStatus.WARNING,
                shortResult = if (isCoordAvailable) "Coordinator active ($coordStatus)" else "Coordinator not attached",
                technicalDetails = mapOf(
                    "engineStatus" to coordStatus.name,
                    "activeProfile" to (engineState?.profile?.name ?: "None"),
                    "totalTabs" to (engineState?.totalTabs?.toString() ?: "0"),
                    "totalActions" to (engineState?.totalActions?.toString() ?: "0")
                ),
                durationMs = System.currentTimeMillis() - start
            )
        )

        // 2. Stuck Running Profile Detection
        val stuckStart = System.currentTimeMillis()
        try {
            val profiles = profileRepository.getAllProfiles()
            val stuckProfiles = profiles.filter {
                it.status == ProfileStatus.RUNNING && (coordStatus != ProfileStatus.RUNNING || engineState?.profile?.id != it.id)
            }

            results.add(
                DiagnosticCheckResult(
                    category = DiagnosticCategory.AUTOMATION,
                    name = "Stuck Execution Detection",
                    status = if (stuckProfiles.isEmpty()) DiagnosticStatus.HEALTHY else DiagnosticStatus.WARNING,
                    shortResult = if (stuckProfiles.isEmpty()) "No stuck RUNNING profiles" else "${stuckProfiles.size} profiles stuck in RUNNING state",
                    technicalDetails = mapOf(
                        "stuckCount" to stuckProfiles.size.toString(),
                        "stuckIds" to stuckProfiles.joinToString(",") { it.id }
                    ),
                    durationMs = System.currentTimeMillis() - stuckStart
                )
            )
        } catch (e: Exception) {
            results.add(
                DiagnosticCheckResult(
                    category = DiagnosticCategory.AUTOMATION,
                    name = "Stuck Execution Detection",
                    status = DiagnosticStatus.WARNING,
                    shortResult = "Unable to evaluate profile statuses: ${e.message}",
                    durationMs = System.currentTimeMillis() - stuckStart
                )
            )
        }

        // 3. Resource Manager Status
        val memStart = System.currentTimeMillis()
        val availMem = resourceManager.getAvailableMemoryMb()
        val isCritLow = resourceManager.isMemoryCriticallyLow()

        results.add(
            DiagnosticCheckResult(
                category = DiagnosticCategory.AUTOMATION,
                name = "Browser Memory & Resources",
                status = if (!isCritLow) DiagnosticStatus.HEALTHY else DiagnosticStatus.WARNING,
                shortResult = if (!isCritLow) "${availMem}MB available" else "Critically low memory: ${availMem}MB",
                technicalDetails = mapOf(
                    "availableMemoryMb" to availMem.toString(),
                    "isCriticallyLow" to isCritLow.toString()
                ),
                durationMs = System.currentTimeMillis() - memStart
            )
        )

        results
    }

    suspend fun checkScheduling(): List<DiagnosticCheckResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiagnosticCheckResult>()
        val start = System.currentTimeMillis()

        // 1. WorkManager Availability
        val wmAvailable = try {
            WorkManager.getInstance(context)
            true
        } catch (e: Exception) {
            false
        }

        results.add(
            DiagnosticCheckResult(
                category = DiagnosticCategory.SCHEDULING,
                name = "WorkManager Background Scheduler",
                status = if (wmAvailable) DiagnosticStatus.HEALTHY else DiagnosticStatus.ERROR,
                shortResult = if (wmAvailable) "WorkManager initialized" else "WorkManager unavailable",
                technicalDetails = mapOf("available" to wmAvailable.toString()),
                durationMs = System.currentTimeMillis() - start
            )
        )

        // 2. Alarm Permissions & Active Schedules
        val schedStart = System.currentTimeMillis()
        try {
            val schedules = scheduleRepository.getAllSchedules()
            val enabledCount = schedules.count { it.enabled }
            val now = System.currentTimeMillis()
            val expiredCount = schedules.count {
                it.enabled && it.scheduleType == com.autopilot.android.domain.model.ScheduleType.ONE_TIME &&
                        it.nextExecutionAt != null && it.nextExecutionAt < (now - 3600000)
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager?.canScheduleExactAlarms() ?: false
            } else {
                true
            }

            val notifEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

            val schedStatus = when {
                !canExact -> DiagnosticStatus.WARNING
                expiredCount > 0 -> DiagnosticStatus.WARNING
                else -> DiagnosticStatus.HEALTHY
            }

            results.add(
                DiagnosticCheckResult(
                    category = DiagnosticCategory.SCHEDULING,
                    name = "Schedule Readiness & Exact Alarms",
                    status = schedStatus,
                    shortResult = if (canExact) "$enabledCount active schedules configured" else "Exact alarm permission missing",
                    technicalDetails = mapOf(
                        "totalSchedules" to schedules.size.toString(),
                        "enabledSchedules" to enabledCount.toString(),
                        "expiredSchedules" to expiredCount.toString(),
                        "canScheduleExactAlarms" to canExact.toString(),
                        "notificationsEnabled" to notifEnabled.toString()
                    ),
                    durationMs = System.currentTimeMillis() - schedStart
                )
            )
        } catch (e: Exception) {
            results.add(
                DiagnosticCheckResult(
                    category = DiagnosticCategory.SCHEDULING,
                    name = "Schedule Readiness & Exact Alarms",
                    status = DiagnosticStatus.WARNING,
                    shortResult = "Schedule check error: ${e.message}",
                    durationMs = System.currentTimeMillis() - schedStart
                )
            )
        }

        results
    }

    suspend fun checkNetwork(): List<DiagnosticCheckResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiagnosticCheckResult>()
        val start = System.currentTimeMillis()

        // 1. Network Connectivity & Interface
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        results.add(
            DiagnosticCheckResult(
                category = DiagnosticCategory.NETWORK,
                name = "Active Network Connection",
                status = if (hasInternet) DiagnosticStatus.HEALTHY else DiagnosticStatus.ERROR,
                shortResult = if (hasInternet) "Internet connection detected" else "No active Internet interface",
                technicalDetails = mapOf(
                    "hasInternet" to hasInternet.toString(),
                    "isWifi" to (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true).toString(),
                    "isCellular" to (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true).toString(),
                    "isVpn" to (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true).toString()
                ),
                durationMs = System.currentTimeMillis() - start
            )
        )

        // 2. DNS Resolution Test
        val dnsStart = System.currentTimeMillis()
        var dnsOk = false
        var dnsDetails = "DNS query failed"
        try {
            val addresses = InetAddress.getAllByName("google.com")
            dnsOk = addresses.isNotEmpty()
            dnsDetails = "Resolved ${addresses.size} host addresses"
        } catch (e: Exception) {
            dnsDetails = e.message ?: "DNS lookup failed"
        }

        results.add(
            DiagnosticCheckResult(
                category = DiagnosticCategory.NETWORK,
                name = "DNS Resolution",
                status = if (dnsOk) DiagnosticStatus.HEALTHY else DiagnosticStatus.ERROR,
                shortResult = if (dnsOk) "DNS resolution functional" else dnsDetails,
                technicalDetails = mapOf("dnsResult" to dnsDetails),
                durationMs = System.currentTimeMillis() - dnsStart
            )
        )

        // 3. Controlled HTTPS Probe (Strictly no sensitive data/content sent)
        val httpStart = System.currentTimeMillis()
        var httpOk = false
        var httpCode = 0
        try {
            val request = Request.Builder()
                .url("https://www.google.com/generate_204")
                .header("User-Agent", "AutoPilot-Diagnostics/1.0")
                .build()
            val response = httpClient.newCall(request).execute()
            httpCode = response.code
            httpOk = (response.isSuccessful || httpCode == 204)
            response.close()
        } catch (e: Exception) {
            httpCode = -1
        }

        results.add(
            DiagnosticCheckResult(
                category = DiagnosticCategory.NETWORK,
                name = "HTTPS Diagnostic Probe",
                status = if (httpOk) DiagnosticStatus.HEALTHY else DiagnosticStatus.WARNING,
                shortResult = if (httpOk) "HTTPS connectivity verified (HTTP 204)" else "Probe failed (code $httpCode)",
                technicalDetails = mapOf(
                    "endpoint" to "https://www.google.com/generate_204",
                    "httpCode" to httpCode.toString()
                ),
                durationMs = System.currentTimeMillis() - httpStart
            )
        )

        // 4. Proxy Subsystem Status
        val proxyStart = System.currentTimeMillis()
        try {
            val proxies = proxyRepository.getAllProxies()
            val enabledProxies = proxies.filter { it.enabled }
            val healthyCount = proxies.count { it.lastHealthStatus == com.autopilot.android.domain.model.proxy.ProxyHealthStatus.HEALTHY }
            val unhealthyCount = proxies.count { it.lastHealthStatus == com.autopilot.android.domain.model.proxy.ProxyHealthStatus.UNHEALTHY }

            results.add(
                DiagnosticCheckResult(
                    category = DiagnosticCategory.NETWORK,
                    name = "Proxy Subsystem",
                    status = DiagnosticStatus.HEALTHY,
                    shortResult = "${proxies.size} proxies (${enabledProxies.size} enabled, $healthyCount healthy, $unhealthyCount unhealthy)",
                    technicalDetails = mapOf(
                        "totalProxies" to proxies.size.toString(),
                        "enabledProxies" to enabledProxies.size.toString(),
                        "healthyCount" to healthyCount.toString(),
                        "unhealthyCount" to unhealthyCount.toString()
                    ),
                    durationMs = System.currentTimeMillis() - proxyStart
                )
            )
        } catch (e: Exception) {
            results.add(
                DiagnosticCheckResult(
                    category = DiagnosticCategory.NETWORK,
                    name = "Proxy Subsystem",
                    status = DiagnosticStatus.WARNING,
                    shortResult = "Proxy check error: ${e.message}",
                    durationMs = System.currentTimeMillis() - proxyStart
                )
            )
        }

        results
    }

    suspend fun checkStorage(): List<DiagnosticCheckResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiagnosticCheckResult>()
        val start = System.currentTimeMillis()

        // 1. Available Internal Storage
        try {
            val stat = StatFs(context.filesDir.absolutePath)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val availableMb = availableBytes / (1024 * 1024)
            val percentFree = if (totalBytes > 0) (availableBytes.toDouble() / totalBytes * 100).toInt() else 0

            val status = when {
                availableMb < 100 -> DiagnosticStatus.ERROR
                availableMb < 500 -> DiagnosticStatus.WARNING
                else -> DiagnosticStatus.HEALTHY
            }

            results.add(
                DiagnosticCheckResult(
                    category = DiagnosticCategory.STORAGE,
                    name = "Internal Storage",
                    status = status,
                    shortResult = "${availableMb}MB free ($percentFree%)",
                    technicalDetails = mapOf(
                        "availableMb" to availableMb.toString(),
                        "percentFree" to "$percentFree%"
                    ),
                    durationMs = System.currentTimeMillis() - start
                )
            )
        } catch (e: Exception) {
            results.add(
                DiagnosticCheckResult(
                    category = DiagnosticCategory.STORAGE,
                    name = "Internal Storage",
                    status = DiagnosticStatus.WARNING,
                    shortResult = "Storage query error: ${e.message}",
                    durationMs = System.currentTimeMillis() - start
                )
            )
        }

        // 2. Database & Cache Directory Sizes
        val dbStart = System.currentTimeMillis()
        try {
            val dbFile = context.getDatabasePath("autopilot_database.db")
            val dbSizeKb = if (dbFile.exists()) dbFile.length() / 1024 else 0
            val cacheSizeKb = calculateDirSize(context.cacheDir) / 1024

            results.add(
                DiagnosticCheckResult(
                    category = DiagnosticCategory.STORAGE,
                    name = "Database & Cache Footprint",
                    status = DiagnosticStatus.HEALTHY,
                    shortResult = "Database: ${dbSizeKb}KB, Cache: ${cacheSizeKb}KB",
                    technicalDetails = mapOf(
                        "dbSizeKb" to dbSizeKb.toString(),
                        "cacheSizeKb" to cacheSizeKb.toString()
                    ),
                    durationMs = System.currentTimeMillis() - dbStart
                )
            )
        } catch (e: Exception) {
            results.add(
                DiagnosticCheckResult(
                    category = DiagnosticCategory.STORAGE,
                    name = "Database & Cache Footprint",
                    status = DiagnosticStatus.HEALTHY,
                    shortResult = "Filesystem check partial: ${e.message}",
                    durationMs = System.currentTimeMillis() - dbStart
                )
            )
        }

        results
    }

    suspend fun checkSecurity(): List<DiagnosticCheckResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiagnosticCheckResult>()
        val start = System.currentTimeMillis()

        // 1. Android KeyStore Availability
        var ksOk = false
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            ksOk = true
        } catch (e: Exception) {
            ksOk = false
        }

        results.add(
            DiagnosticCheckResult(
                category = DiagnosticCategory.SECURITY,
                name = "Android KeyStore Provider",
                status = if (ksOk) DiagnosticStatus.HEALTHY else DiagnosticStatus.ERROR,
                shortResult = if (ksOk) "Hardware/Software KeyStore active" else "KeyStore provider unavailable",
                technicalDetails = mapOf("keyStoreLoaded" to ksOk.toString()),
                durationMs = System.currentTimeMillis() - start
            )
        )

        // 2. Credential Encryption Availability
        val encStart = System.currentTimeMillis()
        val encOk = try {
            val encryptor = ProxyCredentialEncryptor()
            val enc = encryptor.encrypt("diag_probe")
            enc != null && encryptor.decrypt(enc.ciphertextBase64, enc.ivBase64) == "diag_probe"
        } catch (e: Exception) {
            false
        }

        results.add(
            DiagnosticCheckResult(
                category = DiagnosticCategory.SECURITY,
                name = "Credential Encryption Subsystem",
                status = if (encOk) DiagnosticStatus.HEALTHY else DiagnosticStatus.WARNING,
                shortResult = if (encOk) "AES-256-GCM Keystore cipher operational" else "Credential encryption fallback active",
                technicalDetails = mapOf("encryptionAvailable" to encOk.toString()),
                durationMs = System.currentTimeMillis() - encStart
            )
        )

        // 3. Root Status Check
        val rootStart = System.currentTimeMillis()
        val isRooted = RootChecker.checkRootAccess()

        results.add(
            DiagnosticCheckResult(
                category = DiagnosticCategory.SECURITY,
                name = "Superuser / Root Status",
                status = DiagnosticStatus.HEALTHY,
                shortResult = if (isRooted) "Root granted (Magisk / su available)" else "Standard unrooted sandbox",
                technicalDetails = mapOf("isRootGranted" to isRooted.toString()),
                durationMs = System.currentTimeMillis() - rootStart
            )
        )

        results
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0
        var total = 0L
        val children = dir.listFiles() ?: return 0
        for (child in children) {
            total += if (child.isDirectory) calculateDirSize(child) else child.length()
        }
        return total
    }
}

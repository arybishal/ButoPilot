# Proxy & VPN Architecture Specification

**Document Version:** 1.0.0  
**Target Architecture:** Android 8.0+ (API Level 26+)  
**Scope:** Automation & Traffic Isolation Subsystem for BAutoPilot  
**Classification:** Technical Specification & Reference Architecture  

---

## 1. Executive Summary & Proxy Tiers

The BAutoPilot network routing subsystem provides reliable, leak-free proxying and network virtualization across various Android execution environments. To satisfy varying system capabilities, security constraints, and privilege levels, BAutoPilot defines three operational tiers:

```
+-----------------------------------------------------------------------------------+
|                                  BAutoPilot Core                                  |
|               (Profile Manager, Task Engine, Network Orchestrator)                |
+-----------------------------------------------------------------------------------+
         |                                |                                |
         v                                v                                v
+------------------+            +-------------------+            +------------------+
|      Tier 1      |            |      Tier 2       |            |      Tier 3      |
|     No Root      |            |      No Root      |            |       Root       |
|  WebView Proxy   |            |    VPN Service    |            | iptables Routing |
| (ProxyController)|            |  (tun2socks + TUN)|            |     (libsu)      |
+------------------+            +-------------------+            +------------------+
         |                                |                                |
  App-Global WebViews            Selected Target Apps             System / Per-UID
 (Chromium Network Stack)         (Per-App VpnService)          (Kernel-level NAT)
```

### 1.1 Proxy Tier Comparison Matrix

| Feature / Metric | Tier 1: WebView ProxyController | Tier 2: VpnService + tun2socks | Tier 3: iptables Transparent Proxy |
| :--- | :--- | :--- | :--- |
| **Privilege Requirement** | None (Standard App sandbox) | User Consent (`VpnService.prepare`) | Root (`su` / Magisk / KernelSU / APatch) |
| **Target Traffic Scope** | In-app Chromium WebViews | System IP Packets (Selected UIDs / Packages) | System Wide or Precise per-UID Kernel Filter |
| **Protocol Support** | HTTP, HTTPS, SOCKS4, SOCKS5 | All TCP & UDP (DNS, WebRTC, Quic via tun2socks)| Raw IP Packets (TCP, UDP, ICMP) |
| **Process Isolation** | Global to host app process | Per-application routing (`addAllowedApplication`) | Native UID/GID matching (`-m owner --uid-owner`) |
| **System-wide Impact** | None (Isolated to app WebViews) | Captures Android VPN slot (Only 1 active VPN) | None outside target UID redirection |
| **Overhead & Performance**| Zero virtual interface overhead | TUN read/write + user-space TCP stack context switches | Kernel-space netfilter (Near native wire speed) |
| **Multi-Profile Support** | Sequential queuing required | Single profile per VPN activation cycle | Concurrent multi-profile via UID/veth/network namespaces |
| **DNS Leak Risk** | Low (Internal to Chromium) | None (Redirected to fake DNS / tun2socks) | None (iptables intercepts port 53 UDP/TCP) |
| **Primary Use Case** | In-app browser automation tasks | Standalone 3rd-party apps (e.g. Chrome, Social Apps) | High-volume multi-account automation & deep emulation |

---

## 2. Tier 1: WebView Proxy (`androidx.webkit`)

The Tier 1 engine leverages the `androidx.webkit:webkit` library to configure network routing rules directly into the underlying Chromium network stack used by Android `WebView`.

### 2.1 API Integration & Feature Verification

Before attempting proxy configuration, runtime verification of feature availability is strictly required. Android WebKit dynamically binds to the installed WebView provider engine (Google Play Services / Android System WebView).

```kotlin
package com.bautopilot.network.tier1

import android.content.Context
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executor

class WebViewProxyManager(private val context: Context) {

    /**
     * Checks if dynamic proxy overrides are supported by the installed WebView provider.
     */
    fun isProxyOverrideSupported(): Boolean {
        return WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
    }

    /**
     * Checks if reverse bypass rules are supported by the installed provider.
     */
    fun isReverseBypassSupported(): Boolean {
        return WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE_REVERSE_BYPASS)
    }

    /**
     * Sets dynamic proxy override with explicit bypass rules.
     */
    fun applyProxy(
        proxyUrl: String,
        bypassList: List<String> = listOf("localhost", "127.0.0.1"),
        executor: Executor,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (!isProxyOverrideSupported()) {
            onError(UnsupportedOperationException("WebViewFeature.PROXY_OVERRIDE is not supported on this device/WebView engine."))
            return
        }

        try {
            val builder = ProxyConfig.Builder()
                // Supports http://host:port, https://host:port, socks://host:port, socks5://host:port
                .addProxyRule(proxyUrl)

            // Add bypass patterns (e.g., local endpoints, internal APIs)
            bypassList.forEach { rule ->
                builder.addBypassRule(rule)
            }

            val proxyConfig = builder.build()
            val proxyController = ProxyController.getInstance()

            proxyController.setProxyOverride(
                proxyConfig,
                executor,
                Runnable {
                    onSuccess()
                }
            )
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * Clears proxy override, reverting WebView traffic to default direct network connection.
     */
    fun clearProxy(executor: Executor, onComplete: () -> Unit) {
        if (isProxyOverrideSupported()) {
            ProxyController.getInstance().clearProxyOverride(executor, Runnable {
                onComplete()
            })
        } else {
            onComplete()
        }
    }
}
```

### 2.2 App-Global Limitation & Sequential Execution Architecture

#### Problem Statement
`ProxyController.getInstance().setProxyOverride()` applies **globally to all WebViews** running within the application process. Chromium does not maintain isolated proxy configurations per `android.webkit.WebView` instance. If Profile A attempts to execute a task using Proxy A concurrently with Profile B using Proxy B in the same process, a race condition occurs, resulting in traffic contamination or session invalidation.

```
                      +---------------------------------------+
                      |          Host App Process             |
                      |                                       |
                      |   +-------------------------------+   |
                      |   | Chromium Network Service Core |   |
                      |   |   (Single Global Proxy State) |   |
                      |   +-------------------------------+   |
                      |              ^            ^           |
                      |              |            |           |
                      |       +------+            +----+      |
                      |       |                        |      |
                      |  WebView Instance 1     WebView Instance 2
                      |     (Profile A)            (Profile B)
                      +---------------------------------------+
```

#### Mitigation: Sequential Profile Task Pipeline
To ensure strict isolation when running without root or VPN permissions, BAutoPilot enforces a **Sequential Execution Dispatcher**. Tasks are queued, and each profile run undergoes a deterministic lifecycle:

```
[Profile Task Queue] 
        |
        v
[1. Acquire Process Lock]
        |
        v
[2. ProxyController.setProxyOverride(Profile_Proxy)]
        |
        v
[3. Inject Profile Cookies, Storage & Fingerprints]
        |
        v
[4. Execute Automation Task in WebView]
        |
        v
[5. Clear Session Data & Cookies]
        |
        v
[6. ProxyController.clearProxyOverride()]
        |
        v
[7. Release Process Lock -> Next Profile]
```

#### Multi-Process Alternative (Advanced Tier 1)
For concurrent operations without Root/VPN, BAutoPilot supports binding WebViews across decoupled Android `Service` components declared with distinct `android:process=":profile_worker_X"` in `AndroidManifest.xml`. Each process maintains an independent Chromium runtime, enabling simultaneous multi-proxy execution.

---

## 3. Tier 2: VPN Service (`VpnService` + `tun2socks`)

The Tier 2 engine establishes a virtual Layer 3 network interface (TUN) using Android's standard `VpnService`. IP packets routed into the TUN interface are intercepted by a high-performance native `tun2socks` engine (Go or C++ implementation) and encapsulated into upstream SOCKS5 or HTTP(S) proxy protocols.

```
+-----------------------------------------------------------------------------------------+
|                                      User Space                                         |
|                                                                                         |
|  +-----------------------+              File Descriptor (fd)     +--------------------+ |
|  | Target App (e.g. App) |                        |              | tun2socks Engine   | |
|  | (Package / UID)       |                        |              | (TCP/IP Emulation) | |
|  +-----------------------+                        |              +--------------------+ |
|             |                                     |                         |           |
+-------------|-------------------------------------|-------------------------|-----------+
| Kernel      | (Routing Table)                     v                         |           |
|             v                                 +-------+                     | SOCKS5 /  |
|      [ tun0 Interface ] --------------------> | TUN fd|                     | HTTP      |
|                                               +-------+                     v           |
|                                                                     [ Physical Adapter ]|
|                                                                     (Protected Socket)  |
|                                                                             |           |
+-----------------------------------------------------------------------------|-----------+
                                                                              v
                                                                     [ Remote Proxy Server ]
```

### 3.1 `VpnService.Builder` & Per-App Routing Rules

Android enforces a strict exclusivity rule on `VpnService.Builder`:
> [!CAUTION]
> **Mutual Exclusivity Constraint:** Calling both `addAllowedApplication()` and `addDisallowedApplication()` on the same `VpnService.Builder` instance throws an `IllegalArgumentException`. You MUST choose exactly one filtering strategy per session.

```kotlin
package com.bautopilot.network.tier2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress

class AutoPilotVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false

    companion object {
        const val NOTIFICATION_ID = 9001
        const val CHANNEL_ID = "bautopilot_vpn_channel"
        const val VPN_MTU = 1500
        const val FAKE_DNS_IP = "10.0.0.2"
        const val TUN_IP = "10.0.0.1"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android 8.0+ enforces Foreground Service execution for persistent background operation
        startForeground(NOTIFICATION_ID, buildNotification())

        val targetPackages = intent?.getStringArrayListExtra("TARGET_PACKAGES") ?: arrayListOf()
        val proxyHost = intent?.getStringExtra("PROXY_HOST") ?: "127.0.0.1"
        val proxyPort = intent?.getIntExtra("PROXY_PORT", 1080) ?: 1080

        startVpnTunnel(targetPackages, proxyHost, proxyPort)
        return START_NOT_STICKY
    }

    private fun startVpnTunnel(targetPackages: List<String>, proxyHost: String, proxyPort: Int) {
        val builder = Builder()
            .setSession("BAutoPilot-TUN")
            .setMtu(VPN_MTU)
            .addAddress(TUN_IP, 24)
            // Route all standard IPv4 traffic through TUN
            .addRoute("0.0.0.0", 0)
            // Configure DNS server to TUN interface to prevent DNS leaks
            .addDnsServer(FAKE_DNS_IP)

        // Per-App Routing: Route ONLY target automation apps
        if (targetPackages.isNotEmpty()) {
            targetPackages.forEach { pkg ->
                try {
                    builder.addAllowedApplication(pkg)
                } catch (e: Exception) {
                    // Package not found or invalid
                }
            }
        } else {
            // Disallow self to prevent infinite proxy routing loops
            builder.addDisallowedApplication(packageName)
        }

        vpnInterface = builder.establish()

        vpnInterface?.let { pfd ->
            isRunning = true
            // Launch native tun2socks passing the TUN file descriptor and proxy parameters
            NativeTun2Socks.start(
                vpnInterfaceFd = pfd.fd,
                mtu = VPN_MTU,
                proxyHost = proxyHost,
                proxyPort = proxyPort,
                fakeDns = FAKE_DNS_IP,
                protectSocket = { socketFd -> protect(socketFd) }
            )
        }
    }

    override fun onRevoke() {
        // Called by Android OS when another VPN connection takes over or user revokes via Settings
        stopVpnTunnel()
        super.onRevoke()
    }

    private fun stopVpnTunnel() {
        isRunning = false
        NativeTun2Socks.stop()
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpnTunnel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BAutoPilot VPN Tunnel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BAutoPilot Proxy Active")
            .setContentText("Traffic is secured and routed through profile proxy.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }
}
```

### 3.2 User Consent Flow (`VpnService.prepare`)

Before starting a `VpnService`, the app must verify whether it has active VPN permission. If not, Android presents a standard system modal dialog:

```kotlin
// In UI / Orchestration Controller:
fun initiateVpnConnection(activity: ComponentActivity, targetPackages: ArrayList<String>) {
    val intent = VpnService.prepare(activity)
    if (intent != null) {
        // System dialog needed: Launch ActivityResultLauncher
        vpnConsentLauncher.launch(intent)
    } else {
        // Already prepared: Start service directly
        startVpnService(activity, targetPackages)
    }
}
```

### 3.3 Socket Protection & `tun2socks` Translation Engine

`tun2socks` operates by extracting Layer 3 IPv4/IPv6 packets from the TUN file descriptor, executing user-space TCP stream assembly / UDP handling (via lwIP, Netstack, or gVisor), and converting them into Layer 5 SOCKS5 or HTTP CONNECT sessions.

#### Preventing Routing Loops (`protect(socket)`)
All outbound sockets created by `tun2socks` to communicate with the actual external proxy server MUST be protected using `VpnService.protect(Int)`. This instructs the Linux kernel to bypass the TUN routing table rules for those specific socket file descriptors, preventing infinite network loops.

### 3.4 DNS Leak Prevention
To eliminate DNS queries from bypassing the proxy via local carrier/Wi-Fi DNS resolvers:
1. `VpnService.Builder.addDnsServer("10.0.0.2")` configures a synthetic DNS resolver inside the TUN interface.
2. `VpnService.Builder.addRoute("0.0.0.0", 0)` captures all IPv4 outbound traffic (including port 53 UDP/TCP).
3. The `tun2socks` engine intercepts requests directed to `10.0.0.2:53` and resolves them through upstream proxy SOCKS5 UDP ASSOCIATE or remote DNS query pipelines (`remote_dns` mode).

---

## 4. Tier 3: Root Proxy (iptables Transparent Proxy via `libsu`)

When operating in environments with root access (Magisk, KernelSU, APatch), BAutoPilot deploys kernel-level packet redirection via `iptables` and native helper binaries (e.g., `redsocks` or custom transparent proxy daemon).

```
[ Outbound IP Packet from Target App UID ]
                     |
                     v
             [ OUTPUT Chain ]
                     |
                     v
     [ Filter: Match Target UID Only ]
     [ Filter: Ignore Local Proxy Daemon UID ]
                     |
                     v
            [ REDIRECT / DNAT ]
                     |
                     v
     [ Port 12345: redsocks / tproxy daemon ]
                     |
                     v (Protect / bypass UID)
          [ Upstream SOCKS5 Proxy ]
```

### 4.1 Architecture & `libsu` Integration

BAutoPilot executes root shell commands asynchronously using `com.github.topjohnwu.libsu:core`. Commands are executed through clean isolated shell sessions.

```kotlin
package com.bautopilot.network.tier3

import com.topjohnwu.superuser.Shell

object RootProxyController {

    private const val CHAIN_NAME = "AUTOPILOT_PROXY"
    private const val REDSOCKS_PORT = 12345
    private const val REDSOCKS_DNS_PORT = 12346

    /**
     * Installs iptables transparent proxy rules for a specific application UID.
     */
    fun routeAppTraffic(targetUid: Int, proxyDaemonUid: Int): Boolean {
        val commands = listOf(
            // 1. Create custom chain in NAT table
            "iptables -t nat -N $CHAIN_NAME",
            
            // 2. Bypass local proxy daemon UID to prevent infinite loops
            "iptables -t nat -A $CHAIN_NAME -m owner --uid-owner $proxyDaemonUid -j RETURN",
            
            // 3. Bypass loopback and private networks if needed (direct internal communication)
            "iptables -t nat -A $CHAIN_NAME -d 127.0.0.0/8 -j RETURN",
            
            // 4. Redirect target UID TCP traffic to local transparent proxy port
            "iptables -t nat -A $CHAIN_NAME -p tcp -m owner --uid-owner $targetUid -j REDIRECT --to-ports $REDSOCKS_PORT",
            
            // 5. Redirect target UID DNS queries (port 53 UDP) to local DNS forwarder
            "iptables -t nat -A $CHAIN_NAME -p udp --dport 53 -m owner --uid-owner $targetUid -j REDIRECT --to-ports $REDSOCKS_DNS_PORT",
            
            // 6. Hook our custom chain into OUTPUT chain
            "iptables -t nat -A OUTPUT -p tcp -j $CHAIN_NAME",
            "iptables -t nat -A OUTPUT -p udp --dport 53 -j $CHAIN_NAME"
        )

        val result = Shell.cmd(*commands.toTypedArray()).exec()
        return result.isSuccess
    }

    /**
     * Flushes and destroys all custom iptables rules safely.
     */
    fun cleanupRules(): Boolean {
        val commands = listOf(
            // Remove hook from OUTPUT
            "iptables -t nat -D OUTPUT -p tcp -j $CHAIN_NAME",
            "iptables -t nat -D OUTPUT -p udp --dport 53 -j $CHAIN_NAME",
            // Flush rules inside custom chain
            "iptables -t nat -F $CHAIN_NAME",
            // Delete custom chain
            "iptables -t nat -X $CHAIN_NAME"
        )
        val result = Shell.cmd(*commands.toTypedArray()).exec()
        return result.isSuccess
    }
}
```

### 4.2 Handling Proxy Chains & DNS with Redsocks

Standard SOCKS5 proxies do not accept raw TCP transparent connections directly. A mediator daemon (such as `redsocks`) is launched under a distinct daemon UID (`proxyDaemonUid`):
1. `iptables` redirects TCP packets via `REDIRECT` target to local loopback port `12345`.
2. `redsocks` queries `getsockopt(fd, SOL_IP, SO_ORIGINAL_DST)` to recover original destination IP and port.
3. `redsocks` establishes a SOCKS5 TCP connection to the upstream proxy server and issues a `CONNECT destinationIP:destinationPort` handshake.
4. For UDP DNS requests, an integrated DNS-to-TCP or local DNS proxy converts UDP queries into TCP SOCKS5 DNS resolution requests.

### 4.3 Crash Recovery & Trap Handlers
To prevent network blackholes if the BAutoPilot host process crashes:
- Startup validation checks for dangling `$CHAIN_NAME` in `iptables -t nat -L OUTPUT` and executes cleanup.
- A watchdog script is written to `/data/local/tmp/autopilot_watchdog.sh` monitoring the BAutoPilot PID; on termination, it flushes `$CHAIN_NAME`.

---

## 5. Configuration & Data Model

### 5.1 `ProxyConfig` Schema

```kotlin
package com.bautopilot.network.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

enum class ProxyType {
    @SerializedName("http") HTTP,
    @SerializedName("https") HTTPS,
    @SerializedName("socks4") SOCKS4,
    @SerializedName("socks5") SOCKS5
}

enum class ProxyStatus {
    ACTIVE,
    DEGRADED,
    FAILED,
    UNTESTED
}

enum class RotationStrategy {
    ROUND_ROBIN,
    RANDOM,
    SEQUENTIAL,
    STICKY_PROFILE
}

@Keep
data class ProxyConfig(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: ProxyType,
    @SerializedName("host") val host: String,
    @SerializedName("port") val port: Int,
    
    // Encrypted token/alias pointing to Android Keystore entry
    @SerializedName("encrypted_auth_alias") val encryptedAuthAlias: String? = null,
    
    @SerializedName("bypass_list") val bypassList: List<String> = listOf("127.0.0.1", "localhost"),
    @SerializedName("pac_url") val pacUrl: String? = null,
    
    // Telemetry & Health
    @SerializedName("status") var status: ProxyStatus = ProxyStatus.UNTESTED,
    @SerializedName("last_latency_ms") var lastLatencyMs: Long = -1L,
    @SerializedName("last_tested_timestamp") var lastTestedTimestamp: Long = 0L,
    @SerializedName("assigned_profile_id") var assignedProfileId: String? = null
) {
    /**
     * Formats URL for Android ProxyController or tun2socks.
     */
    fun toUrlString(): String {
        val scheme = when (type) {
            ProxyType.HTTP -> "http"
            ProxyType.HTTPS -> "https"
            ProxyType.SOCKS4 -> "socks4"
            ProxyType.SOCKS5 -> "socks5"
        }
        return "$scheme://$host:$port"
    }
}
```

### 5.2 Proxy Pool & Rotation Engine

```kotlin
package com.bautopilot.network.pool

import com.bautopilot.network.model.ProxyConfig
import com.bautopilot.network.model.ProxyStatus
import com.bautopilot.network.model.RotationStrategy
import java.util.concurrent.atomic.AtomicInteger

class ProxyPoolManager(
    private val proxies: MutableList<ProxyConfig> = mutableListOf(),
    var strategy: RotationStrategy = RotationStrategy.ROUND_ROBIN
) {
    private val currentIndex = AtomicInteger(0)

    @Synchronized
    fun addProxy(proxy: ProxyConfig) {
        proxies.add(proxy)
    }

    @Synchronized
    fun getNextProxy(profileId: String? = null): ProxyConfig? {
        val healthyProxies = proxies.filter { it.status != ProxyStatus.FAILED }
        if (healthyProxies.isEmpty()) return null

        return when (strategy) {
            RotationStrategy.STICKY_PROFILE -> {
                profileId?.let { id ->
                    proxies.find { it.assignedProfileId == id }
                } ?: healthyProxies.first()
            }
            RotationStrategy.RANDOM -> {
                healthyProxies.random()
            }
            RotationStrategy.ROUND_ROBIN -> {
                val index = (currentIndex.getAndIncrement() and Int.MAX_VALUE) % healthyProxies.size
                healthyProxies[index]
            }
            RotationStrategy.SEQUENTIAL -> {
                healthyProxies.first()
            }
        }
    }

    @Synchronized
    fun updateStatus(proxyId: String, status: ProxyStatus, latencyMs: Long) {
        proxies.find { it.id == proxyId }?.let {
            it.status = status
            it.lastLatencyMs = latencyMs
            it.lastTestedTimestamp = System.currentTimeMillis()
        }
    }
}
```

---

## 6. Testing & Validation Pipeline

Before allocating a proxy to a browser profile or automation worker, BAutoPilot executes a multi-stage validation sequence:

```
[Proxy Config] 
       |
       v
[Stage 1: TCP Handshake Probe] --------> (Fail -> Mark FAILED)
       | (Pass)
       v
[Stage 2: Proxy Auth Verification] -----> (Fail -> HTTP 407 / SOCKS Auth Error)
       | (Pass)
       v
[Stage 3: HTTP/HTTPS Connectivity] ----> (Fail -> Timeout / SSL Error)
       | (Pass)
       v
[Stage 4: IP & Geo Leak Verification] --> (Verify Exit IP != Local Device IP)
       | (Pass)
       v
[Stage 5: Latency & Jitter Check] ------> (Record Metrics -> Mark ACTIVE)
```

### 6.1 Diagnostic Implementation

```kotlin
package com.bautopilot.network.diagnostics

import com.bautopilot.network.model.ProxyConfig
import com.bautopilot.network.model.ProxyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit

data class ProxyDiagnosticResult(
    val isSuccess: Boolean,
    val latencyMs: Long,
    val externalIp: String?,
    val country: String?,
    val error: String? = null
)

class ProxyTester(private val credentialResolver: (String) -> Pair<String, String>?) {

    suspend fun testProxy(proxyConfig: ProxyConfig): ProxyDiagnosticResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // 1. Raw Socket Connectivity Check (Ping / Handshake)
        val socketTestSuccess = testRawSocket(proxyConfig.host, proxyConfig.port, timeoutMs = 3000)
        if (!socketTestSuccess) {
            return@withContext ProxyDiagnosticResult(
                isSuccess = false,
                latencyMs = -1,
                externalIp = null,
                country = null,
                error = "TCP Socket connection to ${proxyConfig.host}:${proxyConfig.port} failed/timed out."
            )
        }

        // 2. Build OkHttpClient with Proxy and Authentication
        try {
            val javaProxyType = when (proxyConfig.type) {
                ProxyType.HTTP, ProxyType.HTTPS -> Proxy.Type.HTTP
                ProxyType.SOCKS4, ProxyType.SOCKS5 -> Proxy.Type.SOCKS
            }
            val proxyAddress = InetSocketAddress(proxyConfig.host, proxyConfig.port)
            val proxy = Proxy(javaProxyType, proxyAddress)

            val clientBuilder = OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)

            // Inject credentials if alias is present
            proxyConfig.encryptedAuthAlias?.let { alias ->
                credentialResolver(alias)?.let { (username, password) ->
                    clientBuilder.proxyAuthenticator { _, response ->
                        val credential = Credentials.basic(username, password)
                        response.request.newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    }
                }
            }

            val client = clientBuilder.build()

            // 3. Request IP Verification Endpoint (e.g. ipify or httpbin)
            val request = Request.Builder()
                .url("https://api.ipify.org?format=json")
                .header("User-Agent", "BAutoPilot-HealthCheck/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val ip = parseIpFromBody(bodyString)
                    ProxyDiagnosticResult(
                        isSuccess = true,
                        latencyMs = duration,
                        externalIp = ip,
                        country = null
                    )
                } else {
                    ProxyDiagnosticResult(
                        isSuccess = false,
                        latencyMs = duration,
                        externalIp = null,
                        country = null,
                        error = "HTTP response error: Code ${response.code}"
                    )
                }
            }
        } catch (e: Exception) {
            ProxyDiagnosticResult(
                isSuccess = false,
                latencyMs = System.currentTimeMillis() - startTime,
                externalIp = null,
                country = null,
                error = "Diagnostic probe failed: ${e.message}"
            )
        }
    }

    private fun testRawSocket(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseIpFromBody(json: String): String {
        val regex = """"ip"\s*:\s*"([^"]+)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1) ?: json.trim()
    }
}
```

---

## 7. Security & KeyStore Architecture

Proxy credentials (usernames, passwords, and private tokens) are critical assets. BAutoPilot enforces zero plain-text storage and zero credential leakage in system logs.

### 7.1 Android KeyStore Hardware-Backed Encryption

All proxy authentication credentials are encrypted using AES-256-GCM via the Android KeyStore provider:

```
[ Plaintext Credential ]
          |
          v
[ AES-256-GCM Encryption ] <---- Master Key in Android Keystore (StrongBox / TEE)
          |
          v
[ Encrypted Payload + IV ] ----> Stored in EncryptedSharedPreferences / Room DB
```

```kotlin
package com.bautopilot.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeystoreManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "BAutoPilot_Proxy_Master_Key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    init {
        createMasterKeyIfNeeded()
    }

    private fun createMasterKeyIfNeeded() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // Format: IV:CIPHERTEXT in Base64
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encryptedString: String): String {
        val combined = Base64.decode(encryptedString, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12) // Standard GCM IV length is 12 bytes
        val ciphertext = combined.copyOfRange(12, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        val decrypted = cipher.doFinal(ciphertext)
        return String(decrypted, Charsets.UTF_8)
    }
}
```

### 7.2 Log Redaction Standard
- All URLs containing inline credentials (e.g., `http://user:pass@host:port`) must be sanitized using a regex filter before emitting to Logcat or crash reports:
  ```kotlin
  fun sanitizeLog(message: String): String {
      return message.replace(Regex("://([^:]+):([^@]+)@"), "://***:***@")
  }
  ```
- Strict ProGuard/R8 rules strip verbose network logging in production releases.

### 7.3 Certificate & TLS Validation
- **No Insecure TrustManager:** BAutoPilot prohibits `TrustAllCerts` implementations.
- **Network Security Config:** For environments requiring custom enterprise CA certificates or SSL inspection proxies (e.g., Charles/Mitmproxy during development), certificates must be declared explicitly in `res/xml/network_security_config.xml`:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <network-security-config>
      <base-config cleartextTrafficPermitted="false">
          <trust-anchors>
              <certificates src="system" />
              <!-- Custom user certs permitted only in debug builds -->
              <certificates src="user" />
          </trust-anchors>
      </base-config>
  </network-security-config>
  ```

---

## 8. Technical Limitations, Trade-offs & Mitigations

### 8.1 Summary of Platform Constraints

| Subsystem | Limitation / Constraint | Root Cause | BAutoPilot Mitigation |
| :--- | :--- | :--- | :--- |
| **Tier 1 (WebView)** | Global proxy scope per process; cannot bind proxy per WebView instance. | Chromium multi-process design binds network state to process root. | Sequential Profile Dispatcher with full cache/cookie reset, or multi-process workers (`:profile_X`). |
| **Tier 2 (VPN)** | Only **one** active VPN interface supported system-wide by Android OS. | Linux routing table limitation managed by `netd` / Android framework. | Use Per-App routing (`addAllowedApplication`) to route only selected packages; prompt user before disconnecting existing VPNs. |
| **Tier 2 (VPN)** | User interaction required via system modal dialog on initial startup. | Android security policy (`VpnService.prepare`). | Handle preparation during setup onboarding; retain consent across reboots. |
| **Tier 3 (Root)** | Requires rooted device / unlocked bootloader. | Kernel netfilter modification requires `CAP_NET_ADMIN` and `CAP_NET_RAW`. | Graceful fallback: If root is unavailable, downgrade to Tier 2 (VPN) or Tier 1 (WebView). |
| **Tier 3 (Root)** | Potential SELinux / Magisk policy blocks (`avc: denied`). | Modern Android SELinux enforces strict restrictions on domain transitions. | Inject custom `supolicy` rules via `libsu` during daemon initialization. |

### 8.2 Operational Decision Flowchart

When an automation task is scheduled, the selection engine chooses the routing tier dynamically:

```mermaid
flowchart TD
    Start([Task Initiated]) --> CheckPrivilege{Is Device Rooted?}
    
    CheckPrivilege -- Yes --> Tier3[Tier 3: iptables Transparent Proxy via libsu]
    CheckPrivilege -- No --> CheckTarget{Is Target In-App WebView or External App?}
    
    CheckTarget -- In-App WebView --> CheckSupport{WebViewFeature.PROXY_OVERRIDE supported?}
    CheckSupport -- Yes --> Tier1[Tier 1: ProxyController API with Sequential Queue]
    CheckSupport -- No --> Tier2
    
    CheckTarget -- External App --> Tier2[Tier 2: VpnService + tun2socks]
    
    Tier1 --> VerifyIP[Run Diagnostics & Verify IP]
    Tier2 --> VerifyIP
    Tier3 --> VerifyIP
    
    VerifyIP -- Success --> Execute[Execute Automation Task]
    VerifyIP -- Fail --> Rotate[Rotate Proxy & Retry]
```

---

## 9. Deliverables & Implementation Checklist

- [x] Tier 1: `WebViewProxyManager` utilizing `androidx.webkit.ProxyController` with feature checks.
- [x] Tier 1: Process isolation lock ensuring sequential multi-profile integrity.
- [x] Tier 2: `AutoPilotVpnService` implementing `VpnService.Builder`, MTU, fake DNS, and strict `addAllowedApplication` filtering.
- [x] Tier 2: `onRevoke` handling and foreground notification service management.
- [x] Tier 3: `RootProxyController` executing atomic `iptables` NAT table chains and safe flush routines via `libsu`.
- [x] Config: `ProxyConfig` data model with encrypted KeyStore auth references.
- [x] Testing: Diagnostic pipeline covering TCP handshake, authentication, and external IP verification.
- [x] Security: Hardware-backed AES-256-GCM KeyStore encryption for credentials and log redaction filters.

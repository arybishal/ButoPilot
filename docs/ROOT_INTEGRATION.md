# Root Integration Architecture & Implementation Guide

> **Module**: `:integration:root`  
> **Target Platform**: Android 8.0+ (API Level 26+)  
> **Core Library**: `com.github.topjohnwu.libsu:core:6.0.0` (or 6.x series)  
> **Status**: Production Reference Architecture  

---

## 1. Architectural Philosophy

### 1.1 Root Access is Strictly Optional
Root privilege in BAutoPilot is treated purely as an **enhancement layer**, never a hard prerequisite. The core application must install, launch, and execute its complete baseline feature set on standard, unrooted production Android devices without throwing exceptions, deadlocking, or nagging the user.

```
+-----------------------------------------------------------------------+
|                             :app / :ui                                |
+-----------------------------------------------------------------------+
                                   |
                                   v
+-----------------------------------------------------------------------+
|                      :core:domain (Contracts & UseCases)               |
|      (e.g., ProxyController, CertInstaller, SystemIntegrityManager)   |
+-----------------------------------------------------------------------+
                  |                                     |
    [Root Available & Permitted]            [Unrooted / Root Denied]
                  |                                     |
                  v                                     v
+-----------------------------------+ +---------------------------------+
|       :integration:root           | |      :integration:standard      |
| - iptables DNAT/REDIRECT          | | - Android VpnService            |
| - /system/etc/hosts direct/bind   | | - User CA / App Network Security|
| - System CA store (/apex, /system)| | - Private DNS / DoH             |
| - Kernel WakeLock / sysfs         | | - Standard PowerManager         |
+-----------------------------------+ +---------------------------------+
```

### 1.2 Module Isolation (`:integration:root`)
All elevated operations, libsu dependencies, privileged shell parsers, and native command builders are isolated within the `:integration:root` Gradle module.
- **Zero Leakage**: Core modules (`:core:model`, `:core:data`, `:core:domain`, `:core:ui`) have no compile-time dependency on `libsu` or superuser binaries.
- **Dependency Inversion**: Core defines domain interfaces (e.g., `TrafficRouter`, `CertificateManager`, `PowerGovernor`). `:integration:root` provides implementations bound conditionally via Hilt/Dagger dependency injection.
- **Clean Flavors**: Allows seamless compilation of "no-root / play-store compliant" flavors without including su-probing strings or binaries that may trigger anti-cheat or banking app detection flags.

### 1.3 Graceful Degradation Matrix
Every root-backed feature maps directly to a non-root fallback path:

| Feature Area | Elevated / Root Implementation (`:integration:root`) | Standard Non-Root Fallback |
| :--- | :--- | :--- |
| **Traffic Routing** | Transparent `iptables` / `ip6tables` per-UID redirection | Android `VpnService` tunnel |
| **Domain Interception** | Direct/Bind modification of `/system/etc/hosts` | Local loopback DNS proxy / DNS-over-HTTPS |
| **SSL/TLS Decryption** | System CA injection (`/system/etc/security/cacerts`) | User store installation prompt (`CERT_INSTALL`) |
| **Process Keep-Alive** | `/sys/power/wake_lock` + `deviceidle whitelist` | Foreground Service + Partial `WakeLock` |
| **Network Isolation** | Kernel Network Namespaces (`ip netns`) | Process-level socket binding |

### 1.4 Dynamic Feature Detection
Capabilities are verified at runtime through a structured capability registry rather than assuming all root commands work uniformly across different Android versions, OEM kernels, and superuser daemons.

---

## 2. Detection, Authentication & Session Lifecycle

### 2.1 libsu 6.x Setup
Integration relies on TopJohnWu's `libsu` 6.x library, which provides connection pooling, safe process management, robust I/O handling, and seamless Magisk/KernelSU/APatch compatibility.

#### `build.gradle.kts` (:integration:root)
```kotlin
dependencies {
    val libsuVersion = "6.0.0"
    implementation("com.github.topjohnwu.libsu:core:$libsuVersion")
    implementation("com.github.topjohnwu.libsu:io:$libsuVersion")
    
    // Coroutines integration for async shell dispatch
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
}
```

### 2.2 Asynchronous Shell Acquisition & Authentication
Requesting superuser elevation must **never** occur on Android's Main/UI thread. Doing so risks immediate Application Not Responding (ANR) crashes when the user manager dialog (Magisk, KernelSU, SuperSU) pauses execution waiting for user authorization.

#### Recommended Shell Manager Implementation
```kotlin
package com.bautopilot.integration.root

import android.os.SystemClock
import com.topjohnwu.superuser.NoShellException
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RootState {
    data object Unchecked : RootState
    data object Checking : RootState
    data class Granted(val uid: Int, val shellVersion: String) : RootState
    data object Denied : RootState
    data object NotRooted : RootState
    data class Error(val throwable: Throwable) : RootState
}

@Singleton
class RootSessionManager @Inject constructor() {

    private val _rootState = MutableStateFlow<RootState>(RootState.Unchecked)
    val rootState: StateFlow<RootState> = _rootState.asStateFlow()

    @Volatile
    private var cachedIsRoot: Boolean? = null

    /**
     * Probes root status asynchronously with an explicit timeout allowing the user
     * to respond to the superuser prompt dialog.
     */
    suspend fun getOrRequestRoot(promptTimeoutSeconds: Long = 15): Boolean = withContext(Dispatchers.IO) {
        cachedIsRoot?.let { return@withContext it }

        _rootState.value = RootState.Checking

        return@withContext try {
            // Shell.getShell() blocks the calling worker thread until the user approves/denies
            // or the internal timeout expires. We enforce a 15s+ window for user interaction.
            val shell = Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(promptTimeoutSeconds)
                .build()

            val isElevated = shell.isRoot
            if (isElevated) {
                cachedIsRoot = true
                _rootState.value = RootState.Granted(
                    uid = shell.status,
                    shellVersion = Shell.getCachedShell()?.let { "libsu-active" } ?: "unknown"
                )
                true
            } else {
                cachedIsRoot = false
                _rootState.value = RootState.Denied
                false
            }
        } catch (e: NoShellException) {
            // Thrown when no `su` binary exists on PATH or cannot be executed
            cachedIsRoot = false
            _rootState.value = RootState.NotRooted
            false
        } catch (t: Throwable) {
            cachedIsRoot = false
            _rootState.value = RootState.Error(t)
            false
        }
    }

    fun isRootAvailable(): Boolean = cachedIsRoot == true

    fun invalidateCache() {
        cachedIsRoot = null
        _rootState.value = RootState.Unchecked
        try {
            Shell.getCachedShell()?.close()
        } catch (_: Exception) {}
    }
}
```

### 2.3 Status Caching & Session Resilience
1. **In-Memory Cache**: Cache the verified root status for the lifetime of the process to prevent continuous su process invocations.
2. **Foreground Revalidation**: If an operational failure occurs indicating a broken pipe or permission loss, call `invalidateCache()` and re-probe.
3. **Handle NoShellException**: Explicitly catch `com.topjohnwu.superuser.NoShellException`. This denotes a pristine stock device lacking an `su` implementation; avoid recurring checks once this exception is encountered.

---

## 3. Privileged Root Features

```
+-------------------------------------------------------------------+
|                     Privileged Root Subsystems                    |
+-------------------------------------------------------------------+
| 1. Global / Per-UID Proxy  -> iptables & ip6tables (PREROUTING)  |
| 2. Hosts Redirection       -> /system/etc/hosts atomic swap       |
| 3. System CA Store         -> /system/etc/security/cacerts (644)  |
| 4. Power & Sleep Engine    -> /sys/power/wake_lock + Doze bypass  |
| 5. Isolated Network NS     -> ip netns add & veth pairs (Exp.)    |
+-------------------------------------------------------------------+
```

### 3.1 Transparent Global & Per-UID Proxy (`iptables` / `ip6tables`)

#### Principles
- Transparent redirection to a local proxy port (e.g., `127.0.0.1:8080`) using `REDIRECT` or `DNAT`.
- Support both **IPv4** (`iptables`) and **IPv6** (`ip6tables`).
- **Loop Prevention**: Traffic originating from the proxy application's own UID must **never** be redirected back to the proxy.
- **Dedicated Chains**: Always isolate rules within a custom chain (`BAUTOPILOT_OUTPUT`, `BAUTOPILOT_PREROUTING`) for atomic creation, inspection, and flushing.

#### Rule Configuration Blueprint
```bash
# 1. Create custom chain in 'nat' table
iptables -t nat -N BAUTOPILOT_OUTPUT
ip6tables -t nat -N BAUTOPILOT_OUTPUT

# 2. Bypass local loopback traffic
iptables -t nat -A BAUTOPILOT_OUTPUT -o lo -j RETURN
ip6tables -t nat -A BAUTOPILOT_OUTPUT -o lo -j RETURN

# 3. Bypass the proxy daemon UID to avoid infinite loop (e.g., UID 10245)
iptables -t nat -A BAUTOPILOT_OUTPUT -m owner --uid-owner 10245 -j RETURN
ip6tables -t nat -A BAUTOPILOT_OUTPUT -m owner --uid-owner 10245 -j RETURN

# 4. Optional: Route ONLY specific Target App UID (e.g., UID 10182)
# iptables -t nat -A BAUTOPILOT_OUTPUT -p tcp -m owner --uid-owner 10182 -j REDIRECT --to-ports 8080

# 5. Global redirection: Redirect all remaining outbound TCP traffic to proxy port
iptables -t nat -A BAUTOPILOT_OUTPUT -p tcp -j REDIRECT --to-ports 8080
ip6tables -t nat -A BAUTOPILOT_OUTPUT -p tcp -j REDIRECT --to-ports 8080

# 6. Hook our custom chain into top-level OUTPUT chain
iptables -t nat -A OUTPUT -p tcp -j BAUTOPILOT_OUTPUT
ip6tables -t nat -A OUTPUT -p tcp -j BAUTOPILOT_OUTPUT
```

#### Safe Cleanup Blueprint
```bash
# Unhook chain from OUTPUT
iptables -t nat -D OUTPUT -p tcp -j BAUTOPILOT_OUTPUT
ip6tables -t nat -D OUTPUT -p tcp -j BAUTOPILOT_OUTPUT

# Flush custom chain
iptables -t nat -F BAUTOPILOT_OUTPUT
ip6tables -t nat -F BAUTOPILOT_OUTPUT

# Delete empty custom chain
iptables -t nat -X BAUTOPILOT_OUTPUT
ip6tables -t nat -X BAUTOPILOT_OUTPUT
```

---

### 3.2 Hosts File Modification (`/system/etc/hosts`)

Modern Android utilizes `system-as-root` and read-only filesystems (ext4/erofs) across `/system`. Directly writing to `/system/etc/hosts` will fail on Android 10+.

#### Modification Strategies
1. **Magisk Systemless Hosts Module (Preferred)**:
   - Check if `/system/etc/hosts` is already bind-mounted by Magisk's systemless hosts feature.
   - Magisk provides `/data/adb/modules/hosts/system/etc/hosts` or `/sbin/.magisk/mirror/system/etc/hosts`.
2. **Direct Bind Mount (Fallback for custom rooted kernels)**:
   - Create a staging copy in `/data/local/tmp/hosts_autopilot`.
   - Mount using `mount -o bind /data/local/tmp/hosts_autopilot /system/etc/hosts`.

#### Safe Backup, Modify & Restore Protocol
```kotlin
package com.bautopilot.integration.root.hosts

import com.bautopilot.integration.root.command.RootCommandExecutor
import java.io.File
import javax.inject.Inject

class HostsFileManager @Inject constructor(
    private val executor: RootCommandExecutor
) {
    private val hostsPath = "/system/etc/hosts"
    private val backupPath = "/data/local/tmp/hosts.autopilot.bak"
    private val stagingPath = "/data/local/tmp/hosts.autopilot.new"

    suspend fun backup(): Boolean {
        return executor.execute(
            command = "cp",
            args = listOf(hostsPath, backupPath)
        ).isSuccess
    }

    suspend fun applyCustomEntries(entries: List<String>): Boolean {
        // Validate input safety (entries must match strict host format)
        val validEntries = entries.filter { it.matches(Regex("""^[0-9a-fA-F.:]+\s+[a-zA-Z0-9.-]+$""")) }
        
        // Write staging file in private storage first, push to tmp, then swap
        // 1. Ensure backup exists
        backup()

        // 2. Build new content safely using libsu SuFile or parameterized echo
        val stagingContent = buildString {
            appendLine("127.0.0.1 localhost")
            appendLine("::1 localhost")
            validEntries.forEach { appendLine(it) }
        }

        // 3. Atomically overwrite and set permissions
        return executor.executeTransaction(
            listOf(
                "cat << 'EOF' > $stagingPath\n$stagingContent\nEOF",
                "chmod 644 $stagingPath",
                "chown 0:0 $stagingPath",
                "mount -o bind $stagingPath $hostsPath || cp $stagingPath $hostsPath"
            )
        )
    }

    suspend fun restore(): Boolean {
        return executor.executeTransaction(
            listOf(
                "umount $hostsPath || true",
                "if [ -f $backupPath ]; then cp $backupPath $hostsPath; rm $backupPath; fi",
                "chmod 644 $hostsPath",
                "chown 0:0 $hostsPath"
            )
        )
    }
}
```

---

### 3.3 System CA Certificate Installation

To intercept HTTPS traffic transparently without modifying target applications (`res/xml/network_security_config.xml`), the CA certificate must reside in the trusted system CA storage.

#### Storage Locations
- **Legacy Android (Android 8.0 - 13)**: `/system/etc/security/cacerts/`
- **Modern Android (Android 14+ APEX Conscrypt)**: `/apex/com.android.conscrypt/cacerts/`

#### Certificate Naming & Permissions
The certificate must be converted to PEM format and named using its OpenSSL subject hash: `<hash>.0`.

```bash
# Calculate hash name:
# openssl x509 -inform PEM -subject_hash_old -in cert.pem | head -1
# Output: 9a5ba575 -> Destination file: 9a5ba575.0
```

#### Installation Sequence
1. Compute the OpenSSL old subject hash (or accept pre-hashed `.0` asset).
2. Write file to `/data/local/tmp/<hash>.0`.
3. Set file ownership to `root:root` (`0:0`) and permissions to `644` (`-rw-r--r--`).
4. Set SELinux file context: `chcon u:object_r:system_file:s0 /data/local/tmp/<hash>.0` (or `system_security_cacerts_file`).
5. Copy or bind mount into `/system/etc/security/cacerts/<hash>.0`.
6. For Android 14+, mount an overlay/tmpfs over `/apex/com.android.conscrypt/cacerts/` or inject via Magisk module directory (`/data/adb/modules/bautopilot_ca/`).

---

### 3.4 Wake Lock & Background Performance Enhancement

Standard Android battery optimizers, Doze mode, and OEM killers (AppStandby) can terminate long-running automation and proxy tasks. With root access, BAutoPilot stabilizes execution by:

1. **System Whitelisting**:
   ```bash
   dumpsys deviceidle whitelist +com.bautopilot.app
   cmd appops set com.bautopilot.app RUN_IN_BACKGROUND allow
   ```
2. **Direct Kernel WakeLock (`/sys/power/wake_lock`)**:
   ```bash
   echo "BAutoPilot_Lock" > /sys/power/wake_lock
   ```
   *Release on shutdown*:
   ```bash
   echo "BAutoPilot_Lock" > /sys/power/wake_unlock
   ```
3. **Out-Of-Memory (OOM) Protection**:
   Setting `oom_score_adj` of the core worker process to `-1000` (system process tier) ensures it is never killed during background execution.
   ```bash
   echo -1000 > /proc/$PID/oom_score_adj
   ```

---

### 3.5 Network Namespace Isolation (`ip netns`) [Experimental]

Network namespaces provide isolated routing tables and virtual network interfaces for automated workloads without affecting the primary system network stack.

#### Operational Workflow
```bash
# 1. Create a isolated namespace named 'bp_isolated'
ip netns add bp_isolated

# 2. Create a virtual ethernet pair (veth0 <-> veth1)
ip link add veth0 type veth peer name veth1

# 3. Move peer interface into the target namespace
ip link set veth1 netns bp_isolated

# 4. Assign subnets and bring interfaces up
ip addr add 192.168.99.1/24 dev veth0
ip link set veth0 up

ip netns exec bp_isolated ip addr add 192.168.99.2/24 dev veth1
ip netns exec bp_isolated ip link set veth1 up
ip netns exec bp_isolated ip link set lo up

# 5. Set default gateway inside the namespace
ip netns exec bp_isolated ip route add default via 192.168.99.1

# 6. Teardown
ip netns del bp_isolated
ip link del veth0
```

> [!WARNING]
> Many standard OEM kernels omit `CONFIG_NET_NS=y`. Always verify namespace support via `zcat /proc/config.gz | grep CONFIG_NET_NS` or testing `ip netns` exit codes before offering this feature to the user.

---

## 4. Command Execution Safety & Hardening

Unrestricted root shells introduce significant vulnerabilities. Strict command hygiene must be enforced at the architectural level.

```
+-----------------------------------------------------------------------------+
|                          Root Command Safety Funnel                         |
+-----------------------------------------------------------------------------+
|  User / Action Request                                                      |
|         |                                                                   |
|         v                                                                   |
|  [Whitelisted Command Specification Enum]                                   |
|         |                                                                   |
|         v                                                                   |
|  [Strict Parameter Sanitizer & Regex Validation]                            |
|         |                                                                   |
|         v                                                                   |
|  [Parameterized Command Builder (No Freeform String Concat)]                 |
|         |                                                                   |
|         v                                                                   |
|  [10-Second Hard Execution Timeout]                                         |
|         |                                                                   |
|         v                                                                   |
|  [Output Validation & Return Code Verification (Exit == 0)]                 |
|         |                                                                   |
|         v                                                                   |
|  [Redacted Audit Logging -> In-Memory Ring Buffer]                          |
+-----------------------------------------------------------------------------+
```

### 4.1 Strict Whitelisting & Parameterization
No arbitrary strings from UI inputs, remote servers, or external files may be passed to `Shell.cmd()`. Commands must be modeled as strongly-typed, immutable objects.

```kotlin
package com.bautopilot.integration.root.command

sealed class WhitelistedRootCommand {
    data class IptablesRedirect(val uid: Int, val proxyPort: Int) : WhitelistedRootCommand()
    data class IptablesFlushChain(val chainName: String) : WhitelistedRootCommand()
    data class MountBind(val source: String, val target: String) : WhitelistedRootCommand()
    data class SetPermissions(val path: String, val octalMode: String) : WhitelistedRootCommand()
    data class SetOomScore(val pid: Int, val score: Int) : WhitelistedRootCommand()
}

object CommandCompiler {
    fun compile(cmd: WhitelistedRootCommand): List<String> = when (cmd) {
        is WhitelistedRootCommand.IptablesRedirect -> {
            require(cmd.uid in 1000..99999) { "Invalid UID" }
            require(cmd.proxyPort in 1024..65535) { "Invalid port" }
            listOf(
                "iptables -t nat -A BAUTOPILOT_OUTPUT -p tcp -m owner --uid-owner ${cmd.uid} -j REDIRECT --to-ports ${cmd.proxyPort}"
            )
        }
        is WhitelistedRootCommand.IptablesFlushChain -> {
            require(cmd.chainName.matches(Regex("^[A-Z0-9_]{3,32}$"))) { "Unsafe chain name" }
            listOf(
                "iptables -t nat -F ${cmd.chainName}",
                "ip6tables -t nat -F ${cmd.chainName}"
            )
        }
        is WhitelistedRootCommand.MountBind -> {
            require(cmd.source.startsWith("/data/") || cmd.source.startsWith("/system/")) { "Invalid source path" }
            require(cmd.target.startsWith("/system/") || cmd.target.startsWith("/apex/")) { "Invalid target path" }
            listOf("mount -o bind '${cmd.source}' '${cmd.target}'")
        }
        is WhitelistedRootCommand.SetPermissions -> {
            require(cmd.octalMode.matches(Regex("^[0-7]{3,4}$"))) { "Invalid file mode" }
            require(!cmd.path.contains("..") && cmd.path.startsWith("/")) { "Path traversal rejected" }
            listOf("chmod ${cmd.octalMode} '${cmd.path}'")
        }
        is WhitelistedRootCommand.SetOomScore -> {
            require(cmd.pid > 0) { "Invalid PID" }
            require(cmd.score in -1000..1000) { "Invalid OOM score" }
            listOf("echo ${cmd.score} > /proc/${cmd.pid}/oom_score_adj")
        }
    }
}
```

### 4.2 Execution Safeguards
1. **10-Second Hard Timeout**: Every single shell job must specify a maximum runtime of 10,000 milliseconds to prevent locks on unresponsive binaries or blocked pipes.
2. **Output & Return Code Validation**: Execution is considered successful if and only if:
   - The shell exit code is `0`.
   - Stderr does not contain unexpected fatal error signatures.
3. **Audit Logging**: Maintain a revolving in-memory log buffer containing timestamps, commands executed (with sensitive tokens sanitized), execution time, and exit status. Never log private keys or user credentials.

---

## 5. libsu Integration Patterns & Best Practices

### 5.1 Application-Level Configuration
Initialize `Shell.setDefaultBuilder` inside the `Application.onCreate()` entry point. This configures global defaults across all subsequent root requests.

```kotlin
package com.bautopilot.app

import android.app.Application
import com.bautopilot.BuildConfig
import com.topjohnwu.superuser.Shell

class BAutoPilotApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initLibSu()
    }

    private fun initLibSu() {
        // Set verbose logging only for debug builds
        Shell.enableVerboseLogging = BuildConfig.DEBUG

        // Configure default shell parameters
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR) // Combine stdout and stderr
                .setTimeout(10)                       // 10s timeout default
        )
    }
}
```

### 5.2 Streaming Output with `CallbackList`
When executing commands with potentially extensive stdout (such as `logcat`, `dmesg`, or `iptables -L -n -v`), avoid buffering entire string outputs in RAM. Stream lines asynchronously via `CallbackList`:

```kotlin
package com.bautopilot.integration.root.command

import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LogcatStreamer {

    fun streamPrivilegedLogs(tagFilter: String): Flow<String> = callbackFlow {
        val callbackList = object : CallbackList<String>() {
            override fun onAddElement(line: String) {
                trySend(line)
            }
        }

        val job = Shell.getShell().newJob()
            .add("logcat -s '$tagFilter' -v time")
            .to(callbackList)

        job.enqueue {
            channel.close()
        }

        awaitClose {
            // Cancel background logcat when subscriber cancels Flow
            Shell.cmd("pkill -f 'logcat -s $tagFilter'").submit()
        }
    }
}
```

### 5.3 High-Performance Privileged I/O (`SuFile`, `SuFileInputStream`)
Instead of issuing fragile shell commands (`cat /path > ...` or `echo "..." > ...`), utilize `libsu:io` components. They offer full `java.io.File`, `InputStream`, and `OutputStream` compatibility backed by an underlying root daemon.

```kotlin
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream

fun replacePrivilegedFile(sourceData: ByteArray, destinationPath: String): Boolean {
    val suFile = SuFile(destinationPath)
    
    // Parent directories created safely
    suFile.parentFile?.let { parent ->
        if (!parent.exists()) parent.mkdirs()
    }

    return try {
        SuFileOutputStream.open(suFile).use { output ->
            output.write(sourceData)
            output.flush()
        }
        // Set canonical permissions
        suFile.setReadable(true, false)
        suFile.setWritable(true, true)
        true
    } catch (e: Exception) {
        false
    }
}
```

---

## 6. Comprehensive Error Handling & Recovery

```
+------------------------------------------------------------------------+
|                      Root State & Recovery Lifecycle                   |
+------------------------------------------------------------------------+
                               |
               +---------------+---------------+
               |                               |
       [Su Not Detected]               [Prompt Rejected]
               |                               |
               v                               v
    Show informational banner:         Disable root-exclusive toggles;
    "Root unavailable; using          revert to standard fallback
    standard non-root services."       (e.g., Android VpnService).
               |                               |
               +---------------+---------------+
                               |
                   [Root Granted & Active]
                               |
                               v
                  Execute Root Workload
                               |
               +---------------+---------------+
               |                               |
       [Command Failure]              [Root Lost Mid-Session]
               |                               |
               v                               v
      Retry ONCE with backoff.        1. Catch Broken Pipe / SIGPIPE.
      If still failing:               2. Trigger atomic cleanup / unmount.
      Rollback partial rules          3. Invalidate root session cache.
      and fall back cleanly.          4. Notify user and activate non-root.
```

### 6.1 User Denied Root Access
- **Behavior**: Treat denial as a standard configuration state, not a fatal failure.
- **Action**: Disable elevated features in UI settings. Present a clear badge: *"Operating in Standard Mode (Root Disabled)"*. Do not re-prompt automatically unless the user explicitly toggles a root-only setting.

### 6.2 Root Lost Mid-Session
- **Causes**: Magisk Superuser permissions revoked in manager, root daemon crashed by OEM low-memory killer, or SELinux policies dynamically enforced.
- **Detection**: Catch `BrokenPipeException` or `Shell.isRoot` flipping to `false` during standard checks.
- **Recovery Workflow**:
  1. Trigger safe teardown scripts (attempt unbind, delete temporary files, notify proxy service).
  2. Invalidate internal session cache (`RootSessionManager.invalidateCache()`).
  3. Seamlessly spin up the standard fallback subsystem (e.g., invoke Android's `VpnService` to maintain proxy continuity).
  4. Post a user notification informing them of the automatic downgrade.

### 6.3 Command Failure with Single-Retry Backoff
Transient system issues (such as `xtables.lock` contention during concurrent iptables updates) should be mitigated via a single retry before failing.

```kotlin
suspend fun <T> executeWithRetry(
    block: suspend () -> CommandResult<T>
): CommandResult<T> {
    val initialResult = block()
    if (initialResult.isSuccess) return initialResult

    // Delay 500ms for temporary locks to release
    kotlinx.coroutines.delay(500)

    val secondResult = block()
    if (!secondResult.isSuccess) {
        // Rollback and fallback trigger
        handleCommandFailure(secondResult)
    }
    return secondResult
}
```

### 6.4 Magisk / Root Absent
When a device is verified unrooted, suppress all root checks for the remainder of the application session to optimize performance and prevent unnecessary log noise.

---

## 7. Testing & Verification Strategies

### 7.1 Test Environment Configuration
Root testing requires dedicated environments that simulate privileged access and permission edge cases:

1. **Android Emulator (AOSP / Google APIs)**:
   - Use emulator system images with writable system flags:
     ```bash
     emulator -avd Root_Test_API_34 -writable-system -selinux permissive
     ```
   - Run root daemon:
     ```bash
     adb root
     adb remount
     ```
2. **Magisk-Rooted Emulator (Production Mirroring)**:
   - Utilize tools like `rootAVD` to install real Magisk onto official Google Play emulator images to test standard user authorization dialog flows.

### 7.2 Mocking libsu for Unit Testing
Isolate root command generation and output parsing logic by abstracting `Shell` operations behind a clean interface:

```kotlin
interface RootShellExecutor {
    suspend fun execute(command: String): ShellOutput
}

data class ShellOutput(
    val exitCode: Int,
    val stdout: List<String>,
    val stderr: List<String>
) {
    val isSuccess: Boolean get() = exitCode == 0
}
```

#### Unit Test Example (MockK / JUnit 5)
```kotlin
package com.bautopilot.integration.root

import com.bautopilot.integration.root.command.CommandCompiler
import com.bautopilot.integration.root.command.WhitelistedRootCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RootCommandSafetyTest {

    @Test
    fun `iptables redirect compiler validates UID correctly`() {
        val validCommand = WhitelistedRootCommand.IptablesRedirect(uid = 10123, proxyPort = 8080)
        val compiled = CommandCompiler.compile(validCommand)
        
        assertEquals(
            "iptables -t nat -A BAUTOPILOT_OUTPUT -p tcp -m owner --uid-owner 10123 -j REDIRECT --to-ports 8080",
            compiled.first()
        )
    }

    @Test
    fun `iptables redirect compiler rejects system UID`() {
        assertThrows(IllegalArgumentException::class.java) {
            CommandCompiler.compile(WhitelistedRootCommand.IptablesRedirect(uid = 0, proxyPort = 8080))
        }
    }

    @Test
    fun `mount bind compiler rejects path traversal attacks`() {
        assertThrows(IllegalArgumentException::class.java) {
            CommandCompiler.compile(
                WhitelistedRootCommand.SetPermissions(path = "/system/etc/../xbin/su", octalMode = "777")
            )
        }
    }
}
```

### 7.3 Integration Assertion Checklist
Before releasing any build interacting with privileged commands:

- [ ] **Clean Uninstall/Disable**: Disabling root proxy or uninstalling the app leaves `iptables -t nat -L` in its original factory state.
- [ ] **No Unchecked Shell Invocations**: 100% of shell invocations utilize `WhitelistedRootCommand` and are covered by output assertions.
- [ ] **Root Refusal Verification**: Manually tapping "Deny" in the Magisk prompt activates fallback features without throwing an uncaught exception.
- [ ] **SELinux Compliance**: System CA injection checks enforce valid file contexts (`chcon`) so Conscrypt/Keystore loads the certificate without audit denials.
- [ ] **IPv6 Parity**: Every IPv4 iptables rule applied is matched with an identical rule in `ip6tables`.

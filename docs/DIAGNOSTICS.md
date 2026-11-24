# AutoPilot Android - Diagnostics & Execution Troubleshooting

**Document Version:** 1.0.0  
**Phase:** 5 (Diagnostics, Backup & Data Recovery)  
**Status:** Accepted & Verified  

---

## 1. Overview

The Diagnostics Center provides automated, safe, non-destructive health verification of the application and its environment. It evaluates core subsystems—including SQLite database integrity, Automation Coordinator readiness, WorkManager and exact alarm scheduling capabilities, network connectivity and proxy subsystem state, internal filesystem footprint, and Android KeyStore cryptographic providers.

In addition, an Execution Event Tracking architecture records structured, sanitized automation session timelines. When an automation fails or halts unexpectedly, the Execution Troubleshooting system categorizes the error and provides user-friendly diagnostic guidance and safe-retry capabilities.

---

## 2. Diagnostics Architecture

```
+-----------------------------------------------------------------------------------------+
|                                    ProfileScreen                                        |
|                          (Quick Access: Diagnostics Center)                             |
+-----------------------------------------------------------------------------------------+
                                            │
                                            ▼
+-----------------------------------------------------------------------------------------+
|                                  DiagnosticsScreen                                      |
|    - System Health Dashboard (App Version, Android Release, API Level, Root Status)     |
|    - Diagnostic Checks List (Database, Automation, Scheduling, Network, Storage, Sec)   |
|    - Diagnostic History Tab (Persistent Local Records, Clear History, Pruning)          |
+-----------------------------------------------------------------------------------------+
                                            │
                                            ▼
+-----------------------------------------------------------------------------------------+
|                                  DiagnosticRunner                                       |
|    - checkDatabase()   : Room DB read/write, PRAGMA quick_check, orphaned proxy refs    |
|    - checkAutomation() : Coordinator state, stuck RUNNING profiles, memory availability  |
|    - checkScheduling() : WorkManager init, exact alarm permission, expired timers       |
|    - checkNetwork()    : Active interfaces, DNS resolution, non-sensitive HTTPS probe   |
|    - checkStorage()    : StatFs free space, DB file size, cache directory footprint     |
|    - checkSecurity()   : KeyStore provider, AES-GCM credential cipher, Magisk root      |
+-----------------------------------------------------------------------------------------+
```

---

## 3. Diagnostic Check Categories

### 3.1 Database Subsystem
- **Room Database Read/Write Test:** Queries SQLite via a non-destructive read test. Confirms database handle is open and responsive.
- **Schema & PRAGMA Integrity Check:** Evaluates `PRAGMA quick_check` to ensure B-Tree indices and pages are intact. Confirms database schema version is 4.
- **Referential Consistency & Orphaned References:** Scans `automation_profiles` to verify whether any profile references non-existent `proxyId` entries.

### 3.2 Automation Subsystem
- **Coordinator Availability:** Inspects the singleton state of `AutomationCoordinator`, active profile status, and tab counts.
- **Stuck Execution Detection:** Identifies any profiles marked with `status = 'RUNNING'` when the engine is actually idle or stopped (e.g., following process death).
- **Resource Manager Status:** Evaluates free memory available to WebView processes against the 150MB critical threshold.

### 3.3 Scheduling Subsystem
- **WorkManager Availability:** Confirms background job queue initialization.
- **Alarm Readiness & Permissions:** Inspects Android 12+ `AlarmManager.canScheduleExactAlarms()` permission and notification enabled status.
- **Expired Schedule Detection:** Flags one-time schedules that passed their execution target without completion.

### 3.4 Network Subsystem
- **Active Network Interfaces:** Checks `ConnectivityManager.activeNetwork` and transport types (Wi-Fi, Cellular, VPN).
- **DNS Resolution:** Confirms name resolution functionality against standard hostnames.
- **Controlled HTTPS Probe:** Issues a lightweight probe to `https://www.google.com/generate_204`. **Strict Security Rule:** Never transmits user content, credentials, or cookies.
- **Proxy Subsystem Health:** Summarizes total configured, active, healthy, and unhealthy proxy endpoints.

### 3.5 Storage Subsystem
- **Internal Storage Footprint:** Calculates available bytes and percentage free space using `StatFs`.
- **Database & Cache Footprint:** Audits SQLite database file size and cache directory size.

### 3.6 Security Subsystem
- **Android KeyStore Provider:** Loads `AndroidKeyStore` provider to verify hardware/software key storage availability.
- **Credential Encryption Subsystem:** Runs a live encryption and decryption verification probe using `ProxyCredentialEncryptor`.
- **Superuser / Root Status:** Inspects Magisk/su binary availability via `RootChecker`.

---

## 4. Execution Event Tracking & Troubleshooting

For every automation run initiated via `AutomationCoordinator.start()`, a unique execution ID is generated and correlated directly with `ExecutionHistoryItem.id`.

### 4.1 Structured Event Types
- `EXECUTION_REQUESTED`
- `PROFILE_VALIDATED`
- `PROXY_SELECTED`
- `PROXY_COMPATIBILITY_CHECKED`
- `WEBVIEW_INITIALIZED`
- `URL_NAVIGATION_STARTED`
- `PAGE_LOADED`
- `REFRESH_PERFORMED`
- `SCROLL_PERFORMED`
- `TAB_SWITCHED`
- `EXECUTION_PAUSED`
- `EXECUTION_RESUMED`
- `EXECUTION_STOPPED`
- `EXECUTION_FAILED`

### 4.2 Error Categories & Safe Recovery
| Category | Recovery Guidance | Safe to Retry |
|---|---|---|
| `INVALID_CONFIGURATION` | Check URLs and interval settings for syntax errors. | No |
| `NETWORK_UNAVAILABLE` | Check device Wi-Fi or mobile data connection. | Yes |
| `PROXY_UNAVAILABLE` | Proxy offline or timeout. Choose another proxy. | Yes |
| `PROXY_AUTHENTICATION_FAILED` | Credentials rejected. Update in Proxy Editor. | No |
| `WEBVIEW_INITIALIZATION_FAILED` | Update Android System WebView or restart device. | Yes |
| `NAVIGATION_TIMEOUT` | Web page took too long. Increase profile timeout. | Yes |
| `PERMISSION_DENIED` | Required system permissions revoked. Re-grant in settings. | No |
| `RESOURCE_LIMIT_REACHED` | System memory low (<150MB). Close background apps. | Yes |
| `EXECUTION_INTERRUPTED` | Execution stopped by user or process memory pressure. | Yes |
| `SCHEDULING_FAILED` | Alarm could not be scheduled with system scheduler. | Yes |
| `UNKNOWN_ERROR` | Unexpected error encountered. Review logcat. | Yes |

### 4.3 Data Sanitization Requirements
- **URL Sanitization:** `ExecutionEventTracker.sanitizeUrl(rawUrl)` strips all query parameters (`?[query-redacted]`) and removes embedded credentials.
- **Credential Redaction:** `ExecutionEventTracker.sanitizeSensitiveText(text)` masks patterns matching passwords, API keys, tokens, and secrets with `[REDACTED]`.
- **Zero Content Capture:** Webpage DOM, cookies, and authentication headers are never written to execution logs.

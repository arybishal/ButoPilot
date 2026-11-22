# AutoPilot: Phase 3 Modern Android Compatibility Audit
## Background Execution & Scheduling (API 31, 33, 34, 35)

**Audit Version:** 1.0.0  
**Phase Under Audit:** Phase 3 — Background Execution & Scheduling  
**Target Build:** AutoPilot Debug APK (`com.autopilot.android.debug`, `targetSdk 35`)  
**Audit Date:** November 22, 2026  
**Auditor:** AutoPilot Senior Android Architect & QA Engineering  
**Overall Modern Android Audit Status:** **CONDITIONAL PASS / PARTIALLY BLOCKED**  

---

## 1. Executive Summary & Audit Matrix

This audit evaluates the completed AutoPilot Phase 3 background execution and task scheduling implementation against modern Android platform requirements and runtime restrictions across API 31 (Android 12), API 33 (Android 13), API 34 (Android 14), and API 35 (Android 15).

### High-Level API Compatibility Matrix

| API Level | Platform Name | Key Restriction Under Audit | Implementation Status | Audit Finding |
| :---: | :---: | :--- | :---: | :--- |
| **API 26–30** | Android 8.0–11 | Foreground Service baseline, Notification Channels | **PASS** | Fully supported and verified on LDPlayer 9 (API 28). |
| **API 31** | Android 12 | `SCHEDULE_EXACT_ALARM`, `PendingIntent.FLAG_IMMUTABLE`, Background FGS start restrictions | **PASS** | Exact alarm permission checked (`canScheduleExactAlarms()`), all PendingIntents declare `FLAG_IMMUTABLE`. |
| **API 33** | Android 13 | Runtime `POST_NOTIFICATIONS`, `USE_EXACT_ALARM` | **CONDITIONAL PASS** | Manifest declares permissions, but `POST_NOTIFICATIONS` runtime prompt is not invoked in UI (alerts silently suppressed). |
| **API 34** | Android 14 | Mandatory FGS types (`specialUse`), `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` | **PASS** | `<service>` declares `specialUse` type and valid `<property>` subtype. |
| **API 35** | Android 15 | `BOOT_COMPLETED` FGS launch prohibition, `dataSync` 6h budget | **PASS** | `BootCompletedReceiver` does not launch FGS directly; `specialUse` avoids `dataSync` 6h cap. |
| **Emulator** | API 34/35 Live | On-device execution on Android 14/15 emulator | **BLOCKED** | Modern system-images not installed in Android SDK. Required configuration documented. |

---

## 2. Manifest & Permission Audit

### 2.1 Manifest Permission Inventory

```xml
<!-- Manifest Permissions in AutoPilot Android -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
```

### 2.2 Detailed Permission Justification & Compliance Analysis

| Permission | Introduced | Purpose in AutoPilot | Compliance Status | Justification & Risk Assessment |
| :--- | :---: | :--- | :---: | :--- |
| `FOREGROUND_SERVICE` | API 28 | Baseline permission to run `AutomationService` in foreground | **PASS** | Required for long-running browser sessions to survive background memory reclamation. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | API 34 | Required foreground service permission when `foregroundServiceType="specialUse"` is specified | **PASS** | Mandatory under Android 14+. Missing this permission throws `SecurityException`. |
| `POST_NOTIFICATIONS` | API 33 | User-visible notifications for schedule lifecycle alerts and ongoing FGS notification | **DEFECT / CONDITIONAL** | Declared in manifest, but **runtime prompt is never triggered** in UI. On API 33+, notifications are suppressed by OS until user manually enables them in App Settings. |
| `SCHEDULE_EXACT_ALARM` | API 31 | Exact scheduling via `AlarmManager.setExactAndAllowWhileIdle()` | **PASS** | Evaluated via `AlarmManager.canScheduleExactAlarms()`. Gracefully falls back to inexact `setAndAllowWhileIdle()`. |
| `USE_EXACT_ALARM` | API 33 | Auto-granted exact alarm permission at install time | **PASS / POLICY NOTICE** | Ensures zero-prompt exact alarms on API 33+. *Policy Notice:* Restricted under Google Play Store policy to alarm/clock/calendar apps; approved for direct APK deployment. |
| `RECEIVE_BOOT_COMPLETED` | API 1 | Device reboot notification to restore alarms | **PASS** | Allows `BootCompletedReceiver` to reconcile stale tasks and re-arm scheduled alarms. |
| `WAKE_LOCK` | API 1 | Prevents CPU sleep during critical alarm trigger transitions | **PASS** | Ensures `ScheduleReceiver` finishes coroutine setup before device returns to sleep. |

### 2.3 Component Declaration & Security Flags

1. **`AutomationService`**:
   - `android:exported="false"`: Confined strictly to internal app triggers.
   - `android:foregroundServiceType="specialUse"`: Accurately reflects general automation.
   - `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="General purpose browser automation and testing" />`: **PASS**. Strictly satisfies Android 14 manifest validation.
2. **`ScheduleReceiver`**:
   - `android:exported="false"`: Cannot be invoked by external applications. Receives only internal intents from `AutomationScheduler`.
3. **`BootCompletedReceiver`**:
   - `android:exported="true"`: Required to receive OS broadcast intents (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `QUICKBOOT_POWERON`).
4. **PendingIntent Security**:
   - All PendingIntents explicitly declare `PendingIntent.FLAG_IMMUTABLE` combined with explicit component targets (`MainActivity`, `AutomationService`, `ScheduleReceiver`). **100% compliant with Android 12+ mutability enforcement**.

---

## 3. Android 12+ (API 31/32) Exact Alarm Testing & Analysis

### 3.1 Permission Evaluation (`canScheduleExactAlarms`)
* **Implementation ([AutomationScheduler.kt](file:///D:/Webapp/BAutoPilot/app/src/main/java/com/autopilot/android/feature/scheduling/service/AutomationScheduler.kt#L26-L32))**:
  ```kotlin
  fun canScheduleExactAlarms(): Boolean {
      return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          alarmManager?.canScheduleExactAlarms() == true
      } else {
          true
      }
  }
  ```
* **Evaluation**: Properly queries system capability on API 31+ while returning `true` on legacy platforms.

### 3.2 Exact vs Inexact Scheduling Behavior
* **Exact Path**: When `canScheduleExactAlarms()` is true, calls `alarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, triggerAtMillis, pendingIntent)`. Verified to survive Doze mode.
* **Inexact Fallback Path**: When permission is denied, calls `alarmManager.setAndAllowWhileIdle(RTC_WAKEUP, triggerAtMillis, pendingIntent)`.
* **SecurityException Guard**: Line 85 wraps the call in a `try-catch (e: SecurityException)` and falls back to standard inexact alarm if the platform revokes permission unexpectedly.

### 3.3 Identified Gap: Dynamic Permission Revocation Listener
* **Defect / Observation**: AutoPilot does NOT register a receiver for `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
* **Impact**: If a user on Android 12 or 13 manually toggles "Alarms & Reminders" permission in System Settings, all previously scheduled exact alarms are wiped by the kernel. Because AutoPilot does not listen for this broadcast, the alarms remain un-armed until either:
  1. The user opens the AutoPilot app, or
  2. The periodic 2-hour `ScheduleReconciliationWorker` runs.
* **Correction Proposal**: Register a broadcast receiver for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` that invokes `scheduler.rescheduleAll()`.

---

## 4. Android 13+ (API 33) Notification Testing & Analysis

### 4.1 Runtime Permission Request Status
* **Requirement**: Android 13 (API 33) introduced `android.permission.POST_NOTIFICATIONS` as a dangerous runtime permission requiring explicit user approval before notifications appear in the notification shade.
* **Audit Finding (DEFECT)**:
  - `POST_NOTIFICATIONS` is declared in `AndroidManifest.xml`.
  - **No UI prompt or runtime permission launcher** (`ActivityResultContracts.RequestPermission()`) exists in `MainActivity.kt` or any Composable screen.
* **Impact on Device**:
  - Notifications dispatched by `ScheduleNotificationManager` (`notifyScheduleStarted`, `notifyScheduleCompleted`, etc.) are silently discarded by the system notification daemon.
  - Calling `NotificationManager.notify()` without permission does **NOT** throw an exception, so application code does not crash.
  - Schedule execution continues cleanly.
* **Correction Proposal**:
  Add runtime permission request in `MainActivity.kt`:
  ```kotlin
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
      // Handle granted state
  }
  LaunchedEffect(Unit) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
  }
  ```

---

## 5. Android 14+ (API 34) Foreground Service Testing & Analysis

### 5.1 FGS Type & Subtype Property
* **Audit Finding**: **PASS**.
* Under Android 14, every foreground service must declare `android:foregroundServiceType` in manifest and pass matching types to `startForeground()`.
* AutoPilot uses `specialUse`, which requires `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" ... />`. This property is present in `AndroidManifest.xml` line 42 with value `"General purpose browser automation and testing"`.

### 5.2 Background Launch Exemptions
* **Requirement**: Android 14 strictly enforces restrictions against calling `startForegroundService()` when the app is in the background (`ForegroundServiceStartNotAllowedException`).
* **Evaluation in AutoPilot**:
  - AutoPilot triggers `AutomationService.startAutomation()` from `ScheduleReceiver`.
  - Under Android OS rules, exact alarms (`setExactAndAllowWhileIdle`) grant a temporary platform exemption window allowing the receiver to launch a foreground service.
  - **Edge Case**: If an inexact alarm fires when the app is deeply backgrounded, or if an aggressive OEM battery optimizer blocks the transition, calling `context.startForegroundService()` could throw `ForegroundServiceStartNotAllowedException`.
* **Recommendation**: Wrap `AutomationService.startAutomation()` inside `ScheduleReceiver.kt` with a `try-catch (e: Exception)` to record a `ScheduleStatus.FAILED` state instead of terminating the unhandled coroutine.

### 5.3 Minor Version Branching in `AutomationService.kt`
* **Observation**:
  In `AutomationService.kt` line 106:
  ```kotlin
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
      )
  }
  ```
  `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` was introduced in API 34 (`UPSIDE_DOWN_CAKE`), not API 29 (`Q`). While it compiles because `compileSdk` is 35, passing `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on Android 10–13 (API 29–33) could theoretically trigger an `IllegalArgumentException` on strict platform builds. On API 28 (LDPlayer), the branch evaluates to false so no issue occurs. On API 34+, it is valid.
* **Correction Proposal**: Change condition to `Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE`.

---

## 6. Android 15 (API 35) Compatibility Audit

### 6.1 `BOOT_COMPLETED` Foreground Service Launch Prohibition
* **Rule**: In Android 15 (API 35), apps targeting API 35 are **strictly prohibited** from starting foreground services directly from `BroadcastReceiver`s handling `ACTION_BOOT_COMPLETED`. Attempting `context.startForegroundService()` immediately throws `ForegroundServiceStartNotAllowedException`.
* **Audit Finding**: **100% COMPLIANT (PASS)**.
* In [BootCompletedReceiver.kt](file:///D:/Webapp/BAutoPilot/app/src/main/java/com/autopilot/android/feature/scheduling/receiver/BootCompletedReceiver.kt):
  - The receiver reconciles stale Room database records.
  - It re-registers upcoming alarms via `alarmManager.setExactAndAllowWhileIdle()`.
  - It enqueues WorkManager periodic reconciliation via `ScheduleReconciliationWorker.enqueuePeriodic(context)`.
  - It **NEVER** attempts to launch `AutomationService` directly upon boot!

### 6.2 Android 15 `dataSync` 6-Hour Daily Cap
* **Rule**: Services declaring `foregroundServiceType="dataSync"` have a hard 6-hour daily execution cap.
* **Audit Finding**: **PASS**.
* AutoPilot uses `specialUse`, which is not subject to the 6-hour `dataSync` rolling limit.

### 6.3 16 KB Page Alignment
* **Rule**: Android 15 introduces support for devices configured with 16 KB memory pages.
* **Audit Finding**: **PASS**.
* AutoPilot currently contains pure Kotlin code and standard Room SQLite persistence without unaligned custom NDK C/C++ shared libraries.

---

## 7. Modern Emulator Testing Status & Environment Audit

### 7.1 Environment Discovery Results
* **Connected Devices**: `emulator-5554` (LDPlayer 9, Android 9 / API 28, Vivo V2329A profile).
* **Android SDK Inspection**:
  - `C:\Android\Sdk\platforms`: android-34, android-35, android-36, android-37.0 are present.
  - `C:\Android\Sdk\system-images`: **NOT INSTALLED** (`Test-Path` returned `False`).
  - `C:\Android\Sdk\emulator\emulator.exe`: **NOT INSTALLED**.
  - Secondary LDPlayer folder (`D:\LDPlayer\LDPlayer14`): Verified to be an unconfigured instance using the shared `ldplayer9box` driver on the same port.
* **Status**: **BLOCKED** for live on-device execution on an Android 14 or 15 emulator.

### 7.2 Exact Emulator Configuration Required to Unblock Live Modern Testing

To provision a dedicated Android 14 (API 34) or Android 15 (API 35) emulator for hardware testing:

```powershell
# Step 1: Install official Google Android Emulator and system image
& "C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --install "emulator" "system-images;android-34;google_apis;x86_64"

# Step 2: Create target Android 14 Virtual Device
& "C:\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd -n "AutoPilot_API34" -k "system-images;android-34;google_apis;x86_64" -d "pixel_6" --force

# Step 3: Launch Android 14 Emulator in headless/test mode
& "C:\Android\Sdk\emulator\emulator.exe" -avd "AutoPilot_API34" -no-boot-anim -netdelay none -netspeed full

# Step 4: Verify connection via ADB
& "C:\Android\Sdk\platform-tools\adb.exe" wait-for-device
& "C:\Android\Sdk\platform-tools\adb.exe" shell "getprop ro.build.version.sdk"  # Outputs: 34
```

---

## 8. Defect & Remediation Summary

| Defect ID | Severity | Category | Description | Recommended Remediation |
| :---: | :---: | :---: | :--- | :--- |
| **DEF-001** | Medium | Notification (API 33+) | `POST_NOTIFICATIONS` is declared in manifest but never requested at runtime via Activity result contract. | Add runtime permission request launcher in `MainActivity.kt`. |
| **DEF-002** | Low | Alarms (API 31+) | No receiver for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. | Register receiver in `AndroidManifest.xml` to re-arm alarms if permission is revoked/re-granted in Settings. |
| **DEF-003** | Low | FGS Version Check | `AutomationService.kt` uses `SDK_INT >= Q` for `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`. | Update check to `Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE` (API 34). |
| **DEF-004** | Low | Exception Guard | `ScheduleReceiver.kt` doesn't explicitly catch `ForegroundServiceStartNotAllowedException`. | Wrap service launch in `try-catch` to cleanly update schedule status to `FAILED`. |

---

## 9. Final Audit Verdict

- **Manifest & Permission Architecture**: **PASS**
- **Android 12 (API 31) Exact Alarm Compliance**: **PASS**
- **Android 13 (API 33) Notification Compliance**: **PASS (Remediated)**
- **Android 14 (API 34) Foreground Service Compliance**: **PASS (Remediated)**
- **Android 15 (API 35) Boot & Budget Compliance**: **PASS**
- **Modern Emulator Live On-Device Execution**: **BLOCKED** (Modern system-image not installed in SDK)

---

## 10. Post-Audit Remediation Summary (Implemented & Verified)

Following explicit user authorization, all four findings were remediated and verified:

| Defect ID | Status | Files Modified / Created | Summary of Correction | Verification Evidence |
| :---: | :---: | :--- | :--- | :--- |
| **DEF-001** | **FIXED** | [ScheduleDashboardScreen.kt](file:///D:/Webapp/BAutoPilot/app/src/main/java/com/autopilot/android/ui/screen/ScheduleDashboardScreen.kt), [ScheduleNotificationManager.kt](file:///D:/Webapp/BAutoPilot/app/src/main/java/com/autopilot/android/feature/scheduling/notification/ScheduleNotificationManager.kt) | Added `ActivityResultContracts.RequestPermission()` flow with educational banner, direct Settings route, and `areNotificationsEnabled()` check. Execution never blocked. | `CompatibilityRemediationTest.testNotificationDecouplingFromAutomationExecution` |
| **DEF-002** | **FIXED** | [AndroidManifest.xml](file:///D:/Webapp/BAutoPilot/app/src/main/AndroidManifest.xml), [AlarmPermissionReceiver.kt](file:///D:/Webapp/BAutoPilot/app/src/main/java/com/autopilot/android/feature/scheduling/receiver/AlarmPermissionReceiver.kt), [AutomationScheduler.kt](file:///D:/Webapp/BAutoPilot/app/src/main/java/com/autopilot/android/feature/scheduling/service/AutomationScheduler.kt) | Removed `USE_EXACT_ALARM` per Play policy. Registered `AlarmPermissionReceiver` for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. Added `rescheduleAll()` with unique PendingIntent reuse. | `CompatibilityRemediationTest.testDuplicatePreventionUniqueRequestCodes`, `testReconciliationReRegistersOnlyActiveFutureSchedules` |
| **DEF-003** | **FIXED** | [AutomationService.kt](file:///D:/Webapp/BAutoPilot/app/src/main/java/com/autopilot/android/feature/automation/service/AutomationService.kt) | Updated FGS check to `Build.VERSION_CODES.UPSIDE_DOWN_CAKE` (API 34). Older versions (API 28–33) use standard `startForeground()`. | `CompatibilityRemediationTest.testForegroundServiceApiBoundaryClassification` |
| **DEF-004** | **FIXED** | [ScheduleReceiver.kt](file:///D:/Webapp/BAutoPilot/app/src/main/java/com/autopilot/android/feature/scheduling/receiver/ScheduleReceiver.kt) | Wrapped service launch in `try-catch` handling `ForegroundServiceStartNotAllowedException`, persisting failure diagnostics to Room and preserving recurring schedules. | `CompatibilityRemediationTest.testForegroundServiceStartupExceptionContainmentAndState` |

Refer to [docs/testing/PHASE_3_COMPATIBILITY_REMEDIATION_REPORT.md](file:///D:/Webapp/BAutoPilot/docs/testing/PHASE_3_COMPATIBILITY_REMEDIATION_REPORT.md) for full remediation test report.

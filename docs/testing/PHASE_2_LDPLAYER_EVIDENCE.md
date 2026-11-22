# AUTOPILOT ANDROID: PHASE 2 LDPLAYER EVIDENCE INDEX

This document catalogues all raw telemetry, device specifications, logs, and screenshots captured during the Android runtime verification of AutoPilot Phase 2 on the **LDPlayer 9 Android emulator (`emulator-5554`)**.

---

## 1. DEVICE TELEMETRY FILE

- **Path:** [`docs/testing/phase-2-ldplayer/device-info.txt`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/device-info.txt)
- **Host System:** Microsoft Windows 11 Pro (x64)
- **Target Serial:** `emulator-5554` (transport_id: 1)
- **Target Profile:** Vivo `V2329A` (`star2qltechn`), Android 9.0 (API 28), ABI `x86_64`
- **Resolution:** 540x960 (240 dpi)
- **RAM:** ~4GB (~3GB Free)

---

## 2. RUNTIME LOG FILES

| Log File | Description | Relative Path |
| :--- | :--- | :--- |
| **Install & Launch Log** | Captures Gradle build, APK checksum verification, ADB stream install, and initial process launch telemetry. | [`docs/testing/phase-2-ldplayer/logs/install-and-launch.log`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/logs/install-and-launch.log) |
| **Automation Execution Log** | Comprehensive Android logcat dump (1.35 MB) capturing WebView initialization, HTTP/DOM events, navigation cycles, and foreground service lifecycle. | [`docs/testing/phase-2-ldplayer/logs/automation-execution.log`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/logs/automation-execution.log) |

---

## 3. CAPTURED SCREENSHOTS INDEX

All 19 screenshots were captured directly from the display frame buffer of `emulator-5554` and saved under `docs/testing/phase-2-ldplayer/screenshots/`:

| # | Screenshot Filename | Screen / State | Description |
| :---: | :--- | :--- | :--- |
| 01 | [`01_home_dashboard.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/01_home_dashboard.png) | Home / Profile List | Initial launch state rendering profiles list, top bar, and "New Profile" FAB. |
| 02 | [`02_execution_history.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/02_execution_history.png) | Execution History | Empty state view of execution history screen with back navigation. |
| 03 | [`03_create_profile_screen.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/03_create_profile_screen.png) | Profile Editor | Full profile creation form with name, multi-URL parsing, and tab slider (1–25). |
| 04 | [`04_duplicate_profile_result.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/04_duplicate_profile_result.png) | Home / Profile List | Profile cloned successfully with `(Copy)` suffix added to title. |
| 05 | [`05_edit_profile_screen.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/05_edit_profile_screen.png) | Profile Editor | Editing existing profile; confirms preloaded parameters and form reactivity. |
| 06 | [`06_delete_profile_result.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/06_delete_profile_result.png) | Home / Profile List | Deleting duplicate profile cleanly updates the list in real time. |
| 07 | [`07_landscape_layout.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/07_landscape_layout.png) | Landscape Orientation | UI responsive rendering in 960x540 landscape orientation with zero layout crash. |
| 08 | [`08_running_monitor.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/08_running_monitor.png) | Live Monitor (Running) | Mode 1 running: active WebView rendering `https://example.com/`, metrics bar counting elapsed time. |
| 09 | [`09_paused_monitor.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/09_paused_monitor.png) | Live Monitor (Paused) | Mode 1 paused: execution loop halted, status tag displays `PAUSED`, Resume button active. |
| 10 | [`10_stopped_monitor.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/10_stopped_monitor.png) | Live Monitor (Stopped) | Mode 1 stopped: WebView destroyed, memory reclaimed, status displays `IDLE`. |
| 11 | [`11_recorded_session_history.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/11_recorded_session_history.png) | Execution History | Session record persisted in Room DB: `Best Free PDF tools`, `COMPLETED`, `Duration: 11s`. |
| 12 | [`12_deep_scroll_monitor.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/12_deep_scroll_monitor.png) | Live Monitor (Mode 2) | Mode 2 Deep Scroll: progressive scrolling in 5 steps with depth evaluation banner. |
| 13 | [`13_tab_switching_monitor.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/13_tab_switching_monitor.png) | Live Monitor (Mode 3) | Mode 3 Tab Switching: multi-tab bar with Tab 1, Tab 2, Tab 3 chips and round-robin switching. |
| 14 | [`14_custom_js_success.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/14_custom_js_success.png) | Live Monitor (Mode 4) | Mode 4 Authorized JS: executing `document.title` on `example.com`, returned `"Example Domain"`. |
| 15 | [`15_custom_js_blocked.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/15_custom_js_blocked.png) | Live Monitor (Mode 4) | Mode 4 Unauthorized JS: domain mismatch strictly blocked by URL permission checker. |
| 16 | [`16_multi_profile_concurrency.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/16_multi_profile_concurrency.png) | Home / Profile List | 4 distinct profiles concurrently stored and managed in Room database. |
| 17 | [`17_foreground_notification.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/17_foreground_notification.png) | Notification Shade | Android ongoing foreground notification with Pause/Stop action controls. |
| 18 | [`18_process_recovery_profiles.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/18_process_recovery_profiles.png) | Home / Cold Relaunch | Force-stop recovery: all profiles preserved and rendered after app process killed. |
| 19 | [`19_process_recovery_history.png`](file:///D:/Webapp/BAutoPilot/docs/testing/phase-2-ldplayer/screenshots/19_process_recovery_history.png) | Execution History | Force-stop recovery: all historical execution session items preserved in SQLite DB. |

---

## 4. COMMAND MAPPING & VALIDATION SUMMARY

```powershell
# Clean build and test execution
.\gradlew.bat clean testDebugUnitTest assembleDebug

# ADB Target Discovery
& "C:\LDPlayer\LDPlayer9\adb.exe" devices -l
& "C:\LDPlayer\LDPlayer9\adb.exe" -s emulator-5554 shell getprop ro.product.model

# APK Stream Installation
& "C:\LDPlayer\LDPlayer9\adb.exe" -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk

# Process Death Simulation
& "C:\LDPlayer\LDPlayer9\adb.exe" -s emulator-5554 shell am force-stop com.autopilot.android.debug
& "C:\LDPlayer\LDPlayer9\adb.exe" -s emulator-5554 shell am start -W -n com.autopilot.android.debug/com.autopilot.android.ui.MainActivity
```

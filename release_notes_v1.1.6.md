# ButoPilot v1.1.6 - Automation Visibility and Full Screen Preview

This release fixes automation runs that stopped before loading any website and adds a full screen browser preview.

---

### Fixes

- Profiles using free proxy mode or no proxy now load websites even when no proxy passes verification. The monitor shows a direct connection warning so the location is not mistaken for a proxy location.
- Profiles with an explicitly selected proxy still abort if that proxy is unavailable or unverified, so those sessions cannot silently leak the device IP.
- The embedded live WebView is no longer detached and re-added on every status update, so the page stays rendered while automation runs.
- Tab selection in the monitor now controls which WebView is displayed consistently.
- The foreground service no longer stops a run that just started because it saw the previous idle state.

### Features

- Added a full screen preview button in the automation monitor. It opens the selected tab in its own screen with the page title or URL in the top bar.

---

### Included Release Assets

| Asset Name | Description |
| :--- | :--- |
| **`ButoPilot-v1.1.6-Release.apk`** | Production signed release APK |
| **`ButoPilot-v1.1.6-Debug.apk`** | Debug APK with extended logging |

---

### Installation & Requirements

- **OS**: Android 8.0 (Oreo / API 26) through Android 15 (API 35).
- **Architecture**: Universal build for physical devices and emulators.

# ButoPilot v1.5.0 - Immersive Fullscreen & Persistent Background Execution

## New Features
- **Immersive Fullscreen Mode (`MainActivity`)**: Enabled edge-to-edge immersive fullscreen hiding the system status bar (battery, Wi-Fi, clock, signal) and navigation bar across the app (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` + display cutout short edges).
- **Persistent Background Keep-Alive (`AutomationService`, `AndroidManifest`)**: Added `ACTION_KEEP_ALIVE` sticky foreground service mode with a `PARTIAL_WAKE_LOCK` so Android does not kill background automations, traffic runs, or proxy monitoring.
- **Battery Optimization Exemption Prompt (`MainActivity`)**: Added `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and an automatic prompt on startup if the app is not yet set to unrestricted battery usage.

## Release Assets
| File | Variant | Version |
| :--- | :--- | :--- |
| `ButoPilot-v1.5.0-Release.apk` | Release APK | 1.5.0 (Build 24) |
| `ButoPilot-v1.5.0-Debug.apk` | Debug APK | 1.5.0 (Build 24) |

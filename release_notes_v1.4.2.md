# ButoPilot v1.4.2 - Active Automation Proxy, Monitor & Spoof Device Rotation

## Fixes & Features
- **Active Automation Proxy & Rotate Now Fix (`AutomationCoordinator`)**: Fixed proxy connection and `Rotate Now` failing when WebView `PROXY_OVERRIDE` is unavailable (headless HTTP mode now routes through `activeVerifiedProxy` via `OkHttpClient`), and sorted `rotateToNextHealthyProxy` candidates by `HEALTHY` first (lowest latency).
- **Monitor Screen Tab & Headless Status (`AutomationMonitorScreen`)**: Replaced the stuck `"Waiting for the first browser tab to load..."` state with live initialization progress and a **Headless HTTP Mode Active** status card when WebView is unavailable on the device.
- **Rotate Spoof Device (`AutomationCoordinator`, `AutomationMonitorScreen`)**: Added a **Rotate Device** button and `Spoof Device` label in the Monitor screen to rotate the spoofed device profile (root system properties when granted + browser User-Agent) on demand.

## Release Assets
| File | Variant | Version |
| :--- | :--- | :--- |
| `ButoPilot-v1.4.2-Release.apk` | Release APK | 1.4.2 (Build 23) |
| `ButoPilot-v1.4.2-Debug.apk` | Debug APK | 1.4.2 (Build 23) |

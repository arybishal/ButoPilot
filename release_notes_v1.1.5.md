# ButoPilot v1.1.5 — Verified Proxy Routing and Exit Location

This release closes the location leak where analytics attributed visits to the device's own location instead of the proxy location.

---

### Fixes

- Removed the WebView `addDirect()` fallback. A proxy that cannot route now fails closed instead of silently sending requests over the device connection.
- Automation profile runs verify a proxy's public exit IP and country before starting. If no proxy can be verified, the run stops with a clear error instead of running direct.
- Traffic runs now use the same proxy path as automation: the OkHttp client and the background WebViews are both routed through a verified proxy.
- Proxy health checks now reject a proxy whose exit IP matches the device's own public IP.
- Proxy state can no longer report active when the device WebView does not support proxy overrides.

### Features

- Proxy test results report the exit country and IP. This is shown in the proxy editor, proxy list test notifications, and the automation monitor while a profile is running.
- Documentation now states that routing covers WebView and OkHttp traffic only; there is no OS-level VPN service.

---

### Important Behavior Change

Runs that previously fell back to a direct connection will now fail with a proxy unavailable message. Import or test at least one working proxy before starting automation or traffic runs. System-wide capture of other apps or non-WebView traffic still requires a separate VPN service, which is not part of this release.

---

### Included Release Assets

| Asset Name | Description |
| :--- | :--- |
| **`ButoPilot-v1.1.5-Release.apk`** | Production signed release APK |
| **`ButoPilot-v1.1.5-Debug.apk`** | Debug APK with extended logging |

---

### Installation & Requirements

- **OS**: Android 8.0 (Oreo / API 26) through Android 15 (API 35).
- **Architecture**: Universal build for physical devices and emulators.

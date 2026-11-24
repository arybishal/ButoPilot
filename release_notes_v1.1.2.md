# ButoPilot v1.1.2 — Proxy Routing & Tab Navigation Reliability Fix

We are pleased to release **ButoPilot v1.1.2**, resolving a critical issue where target websites failed to open in browser tabs when proxy routing was enabled.

---

### 🚀 Highlights & Fixes

#### 1. 🛡️ Universal HTTP/HTTPS Proxy Routing Architecture
- **Dual Scheme Rule Provisioning**: Refactored `ProxyExecutionCoordinator` to route both `http://` and `https://` requests seamlessly through forward proxies by registering universal host:port rules in AndroidX WebKit.
- **Direct Connection Fallback**: Added graceful `.addDirect()` fallback to `ProxyConfig.Builder`, preventing permanent browser freezes or blank white screens if a proxy experiences network drops or unsupported schemes.
- **Localhost & Loopback Isolation**: Automatically bypasses `<local>`, `localhost`, and `127.0.0.1` so that internal engine communications and WebView IPCs are never routed through remote proxies.

#### 2. 🔒 Proxy SSL & Certificate Negotiation Bypass
- **Automatic SSL Handshake Handling**: Configured `WebViewClient.onReceivedSslError` to proceed past proxy-induced TLS/SSL certificate mismatch warnings (common with transparent proxies, gateway re-encryption, or untrusted CA chains), ensuring web pages actually load rather than aborting.

#### 3. 🧪 Active Proxy Health Verification Before Selection
- **Recency-Guaranteed Selection**: In `obtainHealthyFreeProxy()`, proxies are now verified via live network probe if not tested within the last 3 minutes, preventing dead or stale proxies from being assigned to WebViews.
- **Graceful Direct Fallback**: If no healthy free proxy is reachable on the current network, the engine automatically falls back to direct connection with clear user status feedback instead of assigning a dead candidate.
- **Fresh Seed Initialization**: Reset all default seed proxies to `UNTESTED` so the app always performs real-time health checks on the device's network.

#### 4. 🔄 Automatic Tab Recovery & Reload on Rotation
- **Instant Tab Refresh**: When a proxy connection failure is detected (`ERR_PROXY_CONNECTION_FAILED`, `ERR_TIMED_OUT`, etc.), the coordinator now auto-rotates to a healthy proxy and immediately reloads all active tabs so navigation resumes without user intervention.

---

### 📦 Included Release Assets

| Asset Name | Description | Size |
| :--- | :--- | :--- |
| **`ButoPilot-v1.1.2-Release.apk`** | Production Signed Release APK (Universal for ARM64, ARMv7, x86_64) | ~12.5 MB |
| **`ButoPilot-v1.1.2-Debug.apk`** | Debug APK with extended logging and inspector enabled | ~19.5 MB |

---

### 📲 Installation & Requirements
- **OS**: Android 8.0 (Oreo / API 26) through Android 15 (Vanilla Ice Cream / API 35).
- **Architecture**: Universal build supporting physical devices and emulators (LDPlayer, BlueStacks, Nox, Genymotion, Android Studio AVD).

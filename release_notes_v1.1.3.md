# ButoPilot v1.1.3 — Navigation Stability, Mixed-Content & Sub-Resource Resilience

We are pleased to release **ButoPilot v1.1.3**, delivering major improvements to WebView page navigation reliability, mixed-content rendering, and third-party script isolation.

---

### 🚀 Highlights & Fixes

#### 1. 🌐 Mixed-Content & Comprehensive WebView Capabilities
- **Unrestricted Mixed-Content Rendering**: Changed `mixedContentMode` from `MIXED_CONTENT_NEVER_ALLOW` to `MIXED_CONTENT_ALWAYS_ALLOW`. HTTPS websites with HTTP assets (scripts, fonts, stylesheets, images, iframes) now load and render completely instead of failing silently.
- **Window & Media Capabilities**: Enabled `javaScriptCanOpenWindowsAutomatically = true` and `mediaPlaybackRequiresUserGesture = false` for modern interactive websites.
- **Internal Redirect Handling**: Implemented `shouldOverrideUrlLoading` to ensure all HTTP/HTTPS redirects remain within the WebView without being intercepted or dropped by OS intent handlers.

#### 2. 🛡️ Main-Frame vs Sub-Resource Error Isolation
- **Prevented False Proxy Failures**: Refactored `onReceivedError` to only report errors occurring on the **main frame** (`request.isForMainFrame == true`). Blocked third-party trackers, ad scripts, or external CDN pixels failing will no longer trigger fatal tab errors or unwarranted proxy rotations.

#### 3. ⏱️ Profile-Adaptive Navigation Watchdog
- **Extended Timeout Ceiling**: The navigation watchdog timer now scales according to `profile.timeoutSeconds` (up to 120s) instead of prematurely terminating slow or international proxy connections at 25s.

#### 4. 💬 Polished Telemetry & Status Reporting
- Cleaned up JavaScript execution feedback so completion of functions returning void/undefined/null reports as `"Script executed successfully"` instead of alarming users with `"Script result on Tab 0: null"`.

---

### 📦 Included Release Assets

| Asset Name | Description | Size |
| :--- | :--- | :--- |
| **`ButoPilot-v1.1.3-Release.apk`** | Production Signed Release APK (Universal for ARM64, ARMv7, x86_64) | ~12.5 MB |
| **`ButoPilot-v1.1.3-Debug.apk`** | Debug APK with extended logging and inspector enabled | ~19.5 MB |

---

### 📲 Installation & Requirements
- **OS**: Android 8.0 (Oreo / API 26) through Android 15 (Vanilla Ice Cream / API 35).
- **Architecture**: Universal build supporting physical devices and emulators (LDPlayer, BlueStacks, Nox, Genymotion, Android Studio AVD).

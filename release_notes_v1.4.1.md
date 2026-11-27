# ButoPilot v1.4.1 - WebView Analytics & Visitor Isolation Fix

## Fixes
- **Real Browser WebView Layout & Visibility (`TrafficExecutor`)**: Set explicit mobile/desktop viewport dimensions (`layout(0, 0, w, h)`), called `onResume()` and `resumeTimers()`, and spoofed `document.hidden = false` / `document.visibilityState = 'visible'` so GTM/GA4 scripts execute properly in background WebViews.
- **Unique Visitor Cookie Isolation**: Cleared `CookieManager` cookies between `Real Browser` / `Organic Search` visits so Google Analytics assigns a fresh `_ga` Client ID to each simulated visitor instead of merging all hits into a single user.
- **SSL & GTM Dwell Window**: Added `onReceivedSslError` (`handler?.proceed()`) and extended post-load dwell time to `4.5s–6.5s` so `gtm.js` -> `gtag.js` -> `/g/collect` beacons finish sending over proxies.

## Release Assets
| File | Variant | Version |
| :--- | :--- | :--- |
| `ButoPilot-v1.4.1-Release.apk` | Release APK | 1.4.1 (Build 22) |
| `ButoPilot-v1.4.1-Debug.apk` | Debug APK | 1.4.1 (Build 22) |

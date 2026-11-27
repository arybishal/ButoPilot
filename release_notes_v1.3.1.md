# ButoPilot v1.3.1 - Top 200 Healthy Default Proxies & Health Checker Fix

## Fixes & Improvements
- **Proxy Health Checker Exit Verification (`ProxyHealthChecker`)**: Added `http://ip-api.com/json/?fields=query,countryCode` fallback alongside `https://ipinfo.io/json` in `verifyExitLocation` so HTTP-only proxies and rate-limited exit checks are no longer falsely marked `UNHEALTHY` after passing the primary connectivity probe (while still rejecting IP-leaking proxies).
- **Top 200 Live-Verified Default Proxies (`DefaultProxySeedData`)**: Replaced the outdated 60-proxy seed pool with the top 200 fastest live-verified healthy open-source proxies (sourced from Proxifly and GeoNode) marked `HEALTHY` with measured latencies by default.
- **UI Labels (`ProxyListScreen`)**: Updated restore/load default proxy actions to reflect the 200-proxy default pool.

## Release Assets
| File | Variant | Version |
| :--- | :--- | :--- |
| `ButoPilot-v1.3.1-Release.apk` | Release APK | 1.3.1 (Build 18) |
| `ButoPilot-v1.3.1-Debug.apk` | Debug APK | 1.3.1 (Build 18) |

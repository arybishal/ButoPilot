# ButoPilot v1.3.2 - Proxy Connection Reset & Rotation Fix

## Fixes
- **Proxy Connection Reset & Keep-Alive Fix (`TrafficExecutor`)**: Disabled stale keep-alive socket reuse on free proxy `OkHttpClient` instances (`ConnectionPool(0, 1, TimeUnit.MILLISECONDS)`) to prevent `Connection reset` errors from proxies that drop idle sockets.
- **HTTPS-Aware Proxy Selection & Automatic Rotation**: `TrafficExecutor` now probes up to 6 proxies in parallel (prioritizing `HTTPS`/`SOCKS5` for `https://` targets), builds a fallback client pool, and automatically rotates to the next healthy proxy or direct connection on transient `Connection reset` / `IOException` failures.

## Release Assets
| File | Variant | Version |
| :--- | :--- | :--- |
| `ButoPilot-v1.3.2-Release.apk` | Release APK | 1.3.2 (Build 19) |
| `ButoPilot-v1.3.2-Debug.apk` | Debug APK | 1.3.2 (Build 19) |

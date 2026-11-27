# ButoPilot v1.2.2 - Traffic Generator Reliability Fixes

## Fixes
- **Traffic Executor Proxy Fallback**: Fixed `TrafficExecutor` aborting or stalling for minutes when free proxies are offline or slow. Now prioritizes already-healthy proxies, caps probing to 8 candidates with a 3s timeout, uses `OkHttpClient` directly for `PAGE_REQUEST` mode, and falls back to a direct connection if no verified proxy is available.
- **Real Browser WebView Timeout & Cleanup**: Added a 15-second page load timeout, cancellation cleanup (`stopLoading`/`destroy`), and automatic HTTP-engine fallback when `WebView` fails to initialize in `executeBrowserVisit`.
- **WorkManager Scheduling**: Switched `TrafficController` and `TrafficWorker` from `ExistingWorkPolicy.KEEP` to `ExistingWorkPolicy.REPLACE` so starting or resuming a traffic run reliably schedules execution.

## Release Assets
| File | Variant | Version |
| :--- | :--- | :--- |
| `ButoPilot-v1.2.2-Release.apk` | Release APK | 1.2.2 (Build 11) |
| `ButoPilot-v1.2.2-Debug.apk` | Debug APK | 1.2.2 (Build 11) |

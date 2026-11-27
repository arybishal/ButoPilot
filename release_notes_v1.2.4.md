# ButoPilot v1.2.4 - Instant Traffic Generator Execution

## Fixes
- **Direct In-Process Traffic Execution (`TrafficController`)**: `TrafficController` now launches `TrafficExecutor` directly in-process (`appScope`) instead of waiting on WorkManager's background `JobScheduler` queue, eliminating the issue where traffic runs remained stuck at `0` (`QUEUED`). `pause`, `resume`, and `stop` cancel and restart jobs immediately.
- **Instant `RUNNING` Transition & Fast Parallel Proxy Probe (`TrafficExecutor`)**: Transitions runs to `RUNNING` immediately on start and probes up to 3 proxies in parallel (2s timeout) instead of sequential blocking, then falls back to direct connection if no healthy proxy is available.

## Release Assets
| File | Variant | Version |
| :--- | :--- | :--- |
| `ButoPilot-v1.2.4-Release.apk` | Release APK | 1.2.4 (Build 13) |
| `ButoPilot-v1.2.4-Debug.apk` | Debug APK | 1.2.4 (Build 13) |

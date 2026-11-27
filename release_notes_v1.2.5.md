# ButoPilot v1.2.5 - Live Proxy Health Filtering for Auto Generate

## New Features
- **Live Health Check Option**: The Automatic Proxy Generator now includes an "Only add healthy proxies (live check)" toggle, enabled by default.
- **Health-First Import**: With the option enabled, each generated proxy is probed live before being saved; only proxies reporting HEALTHY are added to the managed proxy list, and unhealthy or timed-out proxies are discarded.
- **Live Status Stamping**: Proxies that pass the check are stored with their real health status, latency and test timestamp, so they appear as Healthy immediately in the proxy list.
- **Progress Feedback**: The dialog shows live checking progress (e.g. "Live checking proxies (16/50)...") while batches of proxies are tested.
- **Clear Empty Result Message**: If no generated proxy passes the health check, the dialog explains that no healthy proxies were found and suggests changing tier, country or quantity.

## Fixes
- Avoids redundant re-testing when live filtering is active by disabling the separate "Auto-test after adding" step.

## Tests
- Added a unit test for the healthy-only filter covering healthy, unhealthy, timeout and missing-result cases.

## Release Assets
| File | Variant | Version |
| :--- | :--- | :--- |
| `ButoPilot-v1.2.5-Release.apk` | Release APK | 1.2.5 (Build 14) |
| `ButoPilot-v1.2.5-Debug.apk` | Debug APK | 1.2.5 (Build 14) |

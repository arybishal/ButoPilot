# ButoPilot v1.2.3 - WebView Missing Provider & Headless Fallback Fix

## Fixes
- **Missing WebView Provider Handling (`WebViewManager`)**: `createOrGetTab` now safely catches `MissingWebViewPackageException` / `AndroidRuntimeException` and returns `null` instead of crashing automation execution, and adds `isWebViewAvailable()`.
- **Headless HTTP Fallback (`AutomationCoordinator`)**: When a device lacks an installed Android System WebView provider, single-run and multi-run automations automatically fall back to a headless `OkHttpClient` execution loop (with active proxy support and configured refresh intervals) instead of failing with `Unknown Error`.
- **Error Classification**: Properly categorizes any WebView initialization exception under `ExecutionErrorCategory.WEBVIEW_INITIALIZATION_FAILED`.

## Release Assets
| File | Variant | Version |
| :--- | :--- | :--- |
| `ButoPilot-v1.2.3-Release.apk` | Release APK | 1.2.3 (Build 12) |
| `ButoPilot-v1.2.3-Debug.apk` | Debug APK | 1.2.3 (Build 12) |

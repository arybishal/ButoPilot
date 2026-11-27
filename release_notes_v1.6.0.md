# ButoPilot v1.6.0 - Random vs Top-Tier Proxy Mode for Automations

## New Features
- **Proxy Selection Mode (`AutomationProxyPreferences`, `CreateAutomationScreen`, `ProfileEditorScreen`)**: Added a **Proxy Selection Mode** selector inside the **Enable Free Proxy** section when creating or editing an automation, allowing users to choose between **Random Proxy** and **Top Tier Country Only**.
- **Mode-Aware Selection & Rotation (`AutomationCoordinator`)**: `obtainHealthyFreeProxy` and `rotateToNextHealthyProxy` now honor the profile's chosen mode—shuffling healthy proxies for **Random Proxy**, or filtering to Tier-1 countries (with automatic Top-Tier proxy fetching if none are cached) for **Top Tier Country Only**.

## Release Assets
| File | Variant | Version |
| :--- | :--- | :--- |
| `ButoPilot-v1.6.0-Release.apk` | Release APK | 1.6.0 (Build 26) |
| `ButoPilot-v1.6.0-Debug.apk` | Debug APK | 1.6.0 (Build 26) |

# ButoPilot v1.3.1 - Expanded Device Spoofing

## New Features
- **Device Spoof Catalog**: Added a shared catalog of 172 device identities across Windows, Android, iPhone, MacBook Silicon, MacBook Intel and Linux groups. Android includes 65 models, iPhone 40 models, MacBook Silicon 22, MacBook Intel 15, Windows 18 and Linux 12. Each chip label shows the available device count.
- **Traffic Generator Device Spoof Option**: A Device Spoofing chip group lets each traffic run select a device pool, including All / Random. Every HTTP request and every browser visit picks a new random device from that pool, and the chosen platform now drives the Sec-CH-Ua-Mobile and Sec-Ch-Ua-Platform client hints. The selected pool is shown in run history and exported reports.
- **Automation Profile Device Pools**: Create Profile and Edit Profile now expose the same device pool selector next to Device Info Spoofing. Root build props are randomized per run from the selected pool, and WebView navigations, reloads and headless HTTP visits rotate to a new random User-Agent on every request unless a custom User-Agent is set.

## Fixes
- Replaced the fixed 7-entry User-Agent list and hardcoded Pixel 8 Pro fallback with the categorized catalog.

## Data
- Database schema moved from version 7 to 8 with an additive migration for the selected device category on traffic runs and automation profiles.

## Tests
- Added catalog tests for group sizes, category containment and random variation.
- Added a traffic executor test asserting every request uses a User-Agent from the selected category and that identities vary across requests.
- Added a version 7 to 8 migration test.

## Release Assets
| File | Variant | Version |
| :--- | :--- | :--- |
| `ButoPilot-v1.3.1-Release.apk` | Release APK | 1.3.1 (Build 18) |
| `ButoPilot-v1.3.1-Debug.apk` | Debug APK | 1.3.1 (Build 18) |

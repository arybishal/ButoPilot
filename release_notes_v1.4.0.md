# ButoPilot v1.4.0 - Background Proxy Maintenance, Auto-Drop Dead Proxies & VPN Settings

## New Features & Fixes
1. **Healthy-Only Proxy Connections**: `AutomationCoordinator` and `TrafficExecutor` strictly connect only to verified `HEALTHY` proxies and immediately drop dead/failing proxies.
2. **Instant Dead Proxy Removal & Deleted Proxy Log (`ProxyMaintenanceManager`, `ProxyListScreen`)**: Any proxy that fails health verification or connection is automatically deleted from the pool and recorded in a persistent **Deleted** log tab in VPN & Proxy Management (with timestamp, protocol, country flag, reason, and Clear Log control).
3. **Connected Proxy Country & Flag on Home Page (`HomeScreen`)**: Displays the connected proxy's country name and flag badge in the Home telemetry card whenever a proxy is active, as well as in the Active Engine card.
4. **Proxy Management Settings in VPN Tab (`ProxyRegionScreen`)**: Added a dedicated Proxy Management Settings card directly on the VPN tab with toggles for Auto Background Health Check, Auto-Delete Dead Proxies, Auto-Search & Add Healthy Proxies, Target Healthy Pool size (50 / 100 / 200), a Check Now trigger, and a direct button to open VPN & Proxy Management.
5. **Non-Disruptive Background Proxy Monitor (`ProxyMaintenanceManager`)**: Continuously checks proxy health in the background (skipping any currently active automation proxy), deletes dead proxies, and automatically searches and adds verified healthy open-source proxies when the pool drops below the target size.

## Release Assets
| File | Variant | Version |
| :--- | :--- | :--- |
| `ButoPilot-v1.4.0-Release.apk` | Release APK | 1.4.0 (Build 21) |
| `ButoPilot-v1.4.0-Debug.apk` | Debug APK | 1.4.0 (Build 21) |

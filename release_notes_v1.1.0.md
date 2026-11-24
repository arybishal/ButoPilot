# ButoPilot v1.1.0 — In-App Update Engine & Proxy Management Redesign

We are excited to release **ButoPilot v1.1.0**, featuring our new **Automatic GitHub In-App Update System**, a completely overhauled **VPN & Proxy Management Engine**, enhanced **Live Telemetry & Controls**, and comprehensive performance improvements.

---

### 🚀 Highlights & New Features

#### 1. 🔄 Automatic GitHub In-App Update System
- **Background Release Detection**: Automatically checks GitHub Releases on launch without impacting startup performance.
- **Semantic Versioning**: Precise SemVer comparison (`v1.0.0` → `v1.1.0`) with dismissed version caching.
- **Native Update Dialog**: Premium in-app dialog with rich Markdown changelog rendering, release date, and package size.
- **Integrated Download Manager**: Real-time progress bar, percentage tracking, cancel/retry mechanisms, and network interruption recovery.
- **Secure Installation Flow**: Official Android package installer integration via secure `FileProvider` with certificate integrity and package validation.
- **Settings Integration**: Dedicated **App Updates** section in Settings with manual "Check for Updates" and status indicators.

#### 2. 🛡️ Complete VPN & Proxy Management Redesign
- **Unified Fluid Scrolling**: Full vertical page scrolling architecture accommodating any screen size and orientation.
- **Live Verification Metrics**: High-contrast real-time status cards displaying Total, Healthy, Unhealthy, and Untested proxies.
- **Batch Health Testing**: One-tap *"Test All Proxies"* with live numerical counters, progress indicator, and active endpoint probing.
- **Healthy vs Unhealthy Segregation**: Dedicated tabs for verified healthy proxies (sorted by lowest latency) and dead endpoints.
- **Bulk Cleanup Controls**: Multi-select deletion and one-click *"Sweep All"* to purge dead endpoints instantly.
- **Default Seed Proxy Pool**: Pre-loaded with 60 verified global proxies with one-tap restore.
- **Quick Import & Add**: Flexible proxy import modal supporting raw host:port lists and quick-add dialog.
- **Country Flags & Region Filters**: Visual ISO flag badges and horizontal filter chips.

#### 3. ⚡ Home Screen & Active Engine Live Telemetry
- **Live Status Strip**: Real-time telemetry monitoring elapsed run time, concurrent tabs, completed DOM actions, and memory footprint.
- **Quick Control Actions**: Direct Pause, Stop, and Monitor controls embedded into the active task card.
- **Streamlined Card Layout**: High-contrast modern Material 3 cards with single-line tag badges.

#### 4. 🛠️ Automation & Stability Fixes
- Fixed duplicate Device Info Spoofing configuration card in the automation creation flow.
- Enhanced free proxy connectivity and auto-rotation loop reliability.
- Full Android 8.0 to Android 15+ (API 26–35) compatibility.

---

### 📦 Included Release Assets

| Asset Name | Description | Size |
| :--- | :--- | :--- |
| **`ButoPilot-v1.1.0-Release.apk`** | Production Signed Release APK (Universal for ARM64, ARMv7, x86_64) | ~12.5 MB |
| **`ButoPilot-v1.1.0-Debug.apk`** | Debug APK with extended logging and inspector enabled | ~19.1 MB |

---

### 📲 Installation & Requirements
- **OS**: Android 8.0 (Oreo / API 26) through Android 15 (Vanilla Ice Cream / API 35).
- **Architecture**: Universal build supporting all major physical devices and emulators (LDPlayer, BlueStacks, Nox, Genymotion, Android Studio AVD).
- **Permissions**: Standard internet permissions; Root is completely optional.

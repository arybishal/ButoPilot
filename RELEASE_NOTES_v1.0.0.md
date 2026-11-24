# ButoPilot v1.0.0 — Official Release

**ButoPilot** is an enterprise-grade, high-concurrency Android automation and web traffic simulation engine built for modern Android runtimes (Android 8.0 to Android 15+ / API 26–35+). It turns any Android device or emulator (e.g., LDPlayer, Nox, physical devices) into a powerful, self-healing automation workstation.

---
### Highlights & Key Capabilities

#### 1. Multi-Tab Concurrency (1 to 200 Concurrent Tabs)
- Scalable tab manager supporting from 1 up to **200 concurrent tabs**.
- Intelligent memory watchdog and dynamic throttling to prevent OOM errors on resource-constrained devices.

#### 2. Multi-Automation Parallel Execution
- Run multiple distinct automation profiles concurrently in a single session.
- Dynamic tab partitioning across selected automations for high-throughput operational efficiency.

#### 3. One-Tap Profile Cloning
- Instantly duplicate any configured profile, including custom DOM actions, headers, timing parameters, and spoofing configurations.

#### 4. 10-Minute Healthy Proxy Auto-Rotation
- Integrated proxy pool with **Geonode** and custom HTTP/SOCKS5 sources.
- Autonomous background rotation loop that probes and swaps to verified healthy proxies every 10 minutes (600 seconds).
- Live countdown timer and manual *"Rotate Now"* override in the Automation Monitor.

#### 5. Real-Browser Web Traffic Generator
- Full Android WebView execution engine that renders live DOM and executes client-side scripts.
- Compatible with real analytics platforms (**Google Analytics 4 / GA4**, **Meta Pixel**, Google Tag Manager).
- Natural human-like behavior simulation: organic mouse/touch curves, deep scrolling, randomized dwell times, and multi-page link following.

#### 6. Hardware & Fingerprint Cloaking (Device Spoofing)
- Spoof device hardware parameters: Manufacturer, Model, Board, Hardware Fingerprint, Screen Dimensions, and User-Agent strings.
- Bypasses basic fingerprinting and bot detection checks.

#### 7. Modern Floating Dock UI & Fluid Swipe Navigation
- Redesigned Material 3 dark-themed interface with glassmorphic cards and telemetry badges.
- Rounded floating capsule bottom navigation dock with smooth horizontal page swipe gestures.

#### 8. Diagnostics, Self-Healing & Root Integrations
- Optional **libsu** integration for Magisk, KernelSU, and APatch environments.
- Comprehensive diagnostic center, encrypted database backup/restore, and automatic recovery protocols.

---
### Included Release Assets

| Asset Name | Description | Size |
| :--- | :--- | :--- |
| **`ButoPilot-v1.0.0-Universal.apk`** | Signed Universal Release APK (ready to install on Android 8.0+ / API 26–35+) | ~32 MB |
| **`ButoPilot-v1.0.0-Debug.apk`** | Debug APK with extended logging and debuggable inspector enabled | ~32 MB |

---
### Installation & Requirements
- **OS**: Android 8.0 (Oreo / API 26) or higher, up to Android 15 (Vanilla Ice Cream / API 35).
- **Emulators**: Supported on LDPlayer 9, BlueStacks, Nox, Genymotion, and Android Studio AVDs.
- **Root**: Optional (Non-root mode fully supported; root enables transparent iptables proxying and system hosts modifications).

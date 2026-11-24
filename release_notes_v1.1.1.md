# ButoPilot v1.1.1 — Custom JavaScript Automation & AutoBot Script Engine

We are pleased to announce **ButoPilot v1.1.1**, introducing high-power custom JavaScript automation authoring, one-tap AutoBot human-like simulation templates, adaptive domain boundaries, and custom app branding.

---

### 🚀 Highlights & New Features

#### 1. 🤖 One-Tap AutoBot Automation Script Template
- **Human-like Interaction Simulation**: Pre-loaded with sophisticated natural browsing behaviors (smooth randomized scrolling, dynamic viewport observation, organic dwell times, human-like micro-delays).
- **One-Tap Injection**: Quick-action **AutoBot Script** button in both Automation Creation and Profile Editor flows instantly populates comprehensive human-simulation scripts.
- **Script Normalization Engine**: Added `AutomationScripts.normalizeScript()` to sanitize and prepare complex multi-line JavaScript blocks for error-free WebView execution.

#### 2. 📝 Advanced In-App Script Editor & Clipboard Controls
- **Monospace Code Editor**: Resizable, multi-line code area optimized for writing and reviewing JavaScript snippets.
- **One-Tap Paste & Clear**: Quick **Paste** button appends clipboard content directly into the editor; **Clear** button resets script instantly.
- **Expanded Timeout Handling**: JavaScript execution timeout now dynamically scales based on profile configuration (minimum 60s safety window) to support intensive routines without artificial 5s timeouts.

#### 3. 🌐 Flexible Domain Boundaries
- **Optional Domain Constraints**: Allowed domains are now fully optional—leaving the domain filter empty allows script execution across all visited websites, enabling universal automation tasks.

#### 4. 🎨 Complete Brand Identity & Launcher Icons
- **Official ButoPilot Icons**: Integrated adaptive high-resolution launcher icons across all density buckets (`hdpi`, `mdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`, `anydpi-v26`).
- **Unified Naming**: Updated application label to **ButoPilot** across Android system surfaces.

---

### 📦 Included Release Assets

| Asset Name | Description | Size |
| :--- | :--- | :--- |
| **`ButoPilot-v1.1.1-Release.apk`** | Production Signed Release APK (Universal for ARM64, ARMv7, x86_64) | ~12.5 MB |
| **`ButoPilot-v1.1.1-Debug.apk`** | Debug APK with extended logging and inspector enabled | ~19.5 MB |

---

### 📲 Installation & Requirements
- **OS**: Android 8.0 (Oreo / API 26) through Android 15 (Vanilla Ice Cream / API 35).
- **Architecture**: Universal build supporting all major physical devices and emulators (LDPlayer, BlueStacks, Nox, Genymotion, Android Studio AVD).
- **Permissions**: Standard internet permissions; Root is completely optional.

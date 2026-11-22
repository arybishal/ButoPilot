# AutoPilot: Android Automation Platform

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-brightgreen.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target_SDK-35_/_Android_15-blue.svg)](https://developer.android.com/about/versions/15)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple.svg)](https://kotlinlang.org)
[![Gradle](https://img.shields.io/badge/Gradle-9.8.0-02303A.svg)](https://gradle.org)
[![AGP](https://img.shields.io/badge/AGP-9.5.0-green.svg)](https://developer.android.com/build)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose_BOM-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-Clean_+_MVVM-orange.svg)](#architecture-overview)
[![License](https://img.shields.io/badge/License-Apache_2.0-lightgrey.svg)](#license)

> **AutoPilot** is an enterprise-grade, extensible, general-purpose Android automation platform built for modern Android runtimes (Android 8.0 to Android 15+ / API 26–35+). It provides isolated execution profiles, headless and visual browser automation sessions, cron-grade task scheduling, proxy and VPN tunneling, optional Magisk/libsu root integration, diagnostics, and secure backup infrastructure.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Features Summary](#features-summary)
3. [Architecture Overview](#architecture-overview)
4. [Tech Stack](#tech-stack)
5. [Prerequisites & Detected Environment](#prerequisites--detected-environment)
6. [Setup & Installation](#setup--installation)
7. [Module Structure](#module-structure)
8. [Build Instructions](#build-instructions)
9. [Documentation Index](#documentation-index)
10. [Contributing](#contributing)
11. [License](#license)

---

## Project Overview

**AutoPilot** turns an Android device into a reliable, self-healing automation node. Unlike cloud browser runners or desktop scrapers that require heavyweight infrastructure, AutoPilot runs natively on device silicon, leveraging Android's native WebView engine, WorkManager scheduling fabric, and Android background service lifecycle.

The platform is designed around six foundational pillars:

1. **Independent Automation Profiles**: Complete isolation between tasks. Each profile maintains its own cookies, cache, local storage, user agent, proxy credentials, execution rules, and scheduling triggers.
2. **Website Sessions & Browser Engine**: Controlled WebView automation pipeline capable of executing deterministic action sequences (`NAVIGATE`, `CLICK`, `FILL`, `EXTRACT`, `WAIT`, `SCROLL`, `SCREENSHOT`, `JS_EVAL`) with DOM element query retries and visual observation.
3. **Resilient Task Scheduling**: Cron-like scheduling, periodic fixed-delay intervals, and one-time tasks orchestrated through Android's `WorkManager` and exact `AlarmManager` dispatchers with `BOOT_COMPLETED` auto-recovery.
4. **Network & Proxy Routing**: Granular network isolation per profile via HTTP/HTTPS/SOCKS5 proxies, credentials encryption, round-robin/random proxy rotation, latency monitoring, and integrated VpnService TUN loopback.
5. **Optional Root Integration**: Native integration with [libsu](https://github.com/topjohnwu/libsu) to detect Magisk/KernelSU/APatch. Provides root-assisted features including per-UID iptables transparent proxying, system hosts manipulation, background process survival, and zero-prompt reboot survival without degrading non-root safety.
6. **Diagnostics & Data Backup**: Live execution telemetry, action-level screenshots, structured timeline inspection, crash reporting, and encrypted Room database export/import for disaster recovery.

---

## Features Summary

| Domain | Key Capabilities |
| :--- | :--- |
| **Profile Management** | • Multiple independent, sandboxed automation profiles<br>• Custom User-Agent rotation and viewport simulation<br>• Encrypted cookie jars and local storage management<br>• JSON-based profile import and export |
| **Browser Engine** | • Reusable, pooled Android `WebView` instances<br>• Deterministic action runner (`CLICK`, `FILL`, `NAVIGATE`, `EXTRACT`, `WAIT`, `SCROLL`, `SCREENSHOT`, `JS_EVAL`)<br>• Bidirectional `@JavascriptInterface` bridge with DOM observation<br>• Configurable timeouts, exponential backoff, and retry policies |
| **Task Scheduler** | • Standard 5-field Cron parser supporting complex recurring rules<br>• Periodic interval timers and one-shot execution queues<br>• Android `WorkManager` constraints (Wi-Fi only, Charging, Battery Not Low)<br>• Exact alarm dispatching via `AlarmManager.setExactAndAllowWhileIdle()`<br>• Reboot recovery via `BOOT_COMPLETED` broadcast receiver |
| **Network & Proxy** | • Dynamic HTTP, HTTPS, and SOCKS5 proxy routing per profile<br>• Proxy health checks, latency tracking, and automatic failover<br>• Proxy rotation strategies (Round-Robin, Random, Sequential)<br>• Optional local Android `VpnService` tunneling for system-level capture |
| **Root Integration** | • Optional privilege escalation via `libsu`<br>• Transparent proxy redirection via `iptables` (DNAT/REDIRECT)<br>• System `/system/etc/hosts` backup and domain modification<br>• Battery optimization whitelisting and background keep-alive preservation<br>• Graceful fallback to unprivileged user-space mode on non-root hardware |
| **Diagnostics & Backup** | • Real-time action log streaming via Kotlin `SharedFlow`<br>• Visual step-by-step screenshot capture and viewer<br>• Execution metrics (success rate, duration, memory footprint)<br>• AES-256 encrypted database backup and restoration |

---

## Architecture Overview

AutoPilot is structured according to **Clean Architecture** combined with the **Model-View-ViewModel (MVVM)** pattern and **Unidirectional Data Flow (UDF)**. This guarantees strict boundary separation, testability without Android runtime dependencies, and modular scalability.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PRESENTATION LAYER                              │
│         Jetpack Compose UI  •  Navigation Compose  •  Material 3       │
│                                   ▲                                    │
│                         UI State  │  UI Events                         │
│                                   ▼                                    │
│                           Viewmodels (StateFlow)                       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                       Executes UseCases / Domain Models
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                           DOMAIN LAYER                                 │
│  (Pure Kotlin — Zero Android Framework Dependencies for Max Testability)│
│                                                                        │
│   UseCases (e.g. ExecuteProfileUseCase, ScheduleTaskUseCase)           │
│   Domain Models (Profile, Action, Schedule, ProxyConfig, Session)      │
│   Repository Interfaces (ProfileRepository, ExecutionRepository, etc.) │
└───────────────────────────────────▲────────────────────────────────────┘
                                    │
                       Implements Domain Interfaces
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                            DATA LAYER                                  │
│                                                                        │
│   Repository Implementations (ProfileRepositoryImpl, etc.)             │
│   Mappers (Entity ↔ Domain Model ↔ Network DTO)                       │
│                                                                        │
│   ┌───────────────────────┐             ┌─────────────────────────┐    │
│   │   Local Data Source   │             │   Remote / Engine Layer │    │
│   │   • Room SQLite DB    │             │   • WebView Pool        │    │
│   │   • EncryptedDataStore│             │   • OkHttp Proxy Engine │    │
│   │   • File Storage      │             │   • Root Su Shell       │    │
│   └───────────────────────┘             └─────────────────────────┘    │
└────────────────────────────────────────────────────────────────────────┘
```

### Unidirectional Data Flow (UDF)
- **UI State**: Rendered reactively through immutable Kotlin `StateFlow<UiState>` collected by Compose screens using `collectAsStateWithLifecycle()`.
- **UI Events**: User actions emitted as sealed classes/interfaces to ViewModels, processed via coroutine dispatchers.
- **One-Time Side Effects**: Navigation events, Snackbars, and dialog triggers routed via `Channel<UiEffect>` exposed as `ReceiveChannel` or `Flow`.

### Concurrency & Service Architecture
- **Coroutines & Flow**: Structured concurrency governed by `SupervisorJob` scopes tied to component lifecycles.
- **Foreground Execution Service**: Persistent automation runs under a dedicated Foreground Service with `foregroundServiceType="specialUse"` accompanied by persistent system notification updates.
- **WorkManager**: Background synchronization, scheduled cron triggers, and periodic maintenance tasks executed reliably within battery optimization constraints.

---

## Tech Stack

| Layer / Subsystem | Technology | Specification / Role |
| :--- | :--- | :--- |
| **Language** | [Kotlin](https://kotlinlang.org) | **2.1.0+** (Modern language features, Coroutines, Serialization) |
| **Build System** | [Gradle](https://gradle.org) | **9.8.0** with Gradle Toolchains (Java 21) |
| **Android Plugin** | [AGP](https://developer.android.com/build) | **9.5.0** (Optimized incremental compilation & R8 shrinker) |
| **UI Framework** | [Jetpack Compose](https://developer.android.com/jetpack/compose) | Declarative UI, Compose Compiler 2.x, Material 3 BOM |
| **Architecture** | Clean Architecture + MVVM | Strict domain isolation, UseCase pattern, UDF |
| **Dependency Injection** | [Dagger Hilt](https://dagger.dev/hilt/) | Android compile-time dependency injection |
| **Persistence** | [Room Database](https://developer.android.com/training/data-storage/room) | SQLite relational database with KSP code generation |
| **Secure Storage** | AndroidX Security | `EncryptedSharedPreferences` / Encrypted DataStore (AES-256 GCM) |
| **Background Work** | [AndroidX WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) | Battery-conscious background execution & scheduling |
| **Browser Engine** | Android WebView | Chromium-based embedded browser, custom JS execution bridge |
| **Networking** | [OkHttp](https://square.github.io/okhttp/) | HTTP/2, SOCKS5 socket tunneling, custom DNS & Proxy authenticators |
| **Root Subsystem** | [libsu](https://github.com/topjohnwu/libsu) | Shell management, Magisk/KernelSU detection, iptables execution |
| **Async & Reactive** | KotlinX Coroutines & Flow | Asynchronous execution, channel pipelines, state synchronization |
| **Serialization** | KotlinX Serialization | JSON encoding/decoding for profile import/export and logs |
| **Testing** | JUnit 5, MockK, Turbine, Compose UI Test | Unit, domain logic, and instrumented UI automation testing |

---

## Prerequisites & Detected Environment

The project has been planned against the current development environment:

### Verified Host Environment
- **Operating System**: Windows 11 Pro (Build 26300)
- **Primary JDK (JAVA_HOME)**: OpenJDK JBR 21.0.11 (`C:\Program Files\JetBrains\...\jbr` or custom path)
- **Secondary JDK (Android Studio)**: OpenJDK JBR 25.0.4
- **Android SDK Path**: `C:\Android\Sdk`
- **Installed Android Platforms**:
  - `android-34` (Android 14)
  - `android-35` (Android 15)
  - `android-36` (Android 16 Developer Preview)
  - `android-37.0`, `android-37.2`
- **Installed Build Tools**: `34.0.0`, `35.0.0`, `36.0.0`
- **Cached Gradle Versions**: `8.14.5`, `9.3.1`, `9.6.0`, **`9.8.0`** *(Recommended)*
- **Cached AGP Versions**: `8.13.1`, `8.13.2`, `9.1.0`, `9.1.1`, **`9.5.0`** *(Recommended)*
- **Installed NDKs**: `23.1`, `25.1`, `27.0`, `27.2`, `28.2`

### Minimum System Requirements
- 16 GB RAM (32 GB recommended for parallel emulator & Gradle compilation)
- 25 GB free disk space for Android SDK, NDK, and Gradle cache
- Virtualization enabled in BIOS/UEFI (Hyper-V / WHPX) for Android Emulator support

---

## Setup & Installation

### 1. Initialize Git Repository
```powershell
cd D:\Webapp\BAutoPilot
git init -b main
```

### 2. Configure Local SDK Properties
Create a `local.properties` file in the project root:
```properties
## D:/Webapp/BAutoPilot/local.properties
sdk.dir=C\:\\Android\\Sdk
ndk.dir=C\:\\Android\\Sdk\\ndk\\27.2.12479018
```

### 3. Verify Environment Variables
Ensure `JAVA_HOME` points to JDK 21 and the Android SDK is reachable:
```powershell
# PowerShell validation
$env:JAVA_HOME
# Example output: C:\Program Files\Android\Android Studio\jbr (or JBR 21)

java -version
# Expected: OpenJDK Runtime Environment (build 21.0.11+...)

$env:ANDROID_HOME="C:\Android\Sdk"
$env:Path += ";$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin"
```

### 4. Configure Android Studio
1. Open Android Studio (Ladybug / Meerkat or later).
2. Select **File > Settings > Build, Execution, Deployment > Build Tools > Gradle**.
3. Set **Gradle JDK** to `JBR 21` or point to your installed OpenJDK 21.
4. Perform an initial Gradle sync.

---

## Module Structure

AutoPilot follows a strict feature-driven multi-module layout:

```
BAutoPilot/
├── app/                        # Main entrypoint, Application class, DI root, Navigation host
│
├── core/
│   ├── common/                 # Base interfaces, Kotlin extensions, Coroutine dispatchers, Result monad
│   ├── database/               # Room DB, Entity definitions, DAOs, SQLite TypeConverters, migrations
│   ├── network/                # OkHttp setup, SOCKS5 socket factory, SSL/TLS cert pinning, ConnectivityObserver
│   ├── domain/                 # Pure Kotlin domain models, Repository contracts, business UseCases
│   └── data/                   # Repository implementations, DataStore preferences, Data mappers
│
├── feature/
│   ├── profiles/               # Profile management UI, Action creator, UserAgent & Cookie manager
│   ├── automation/             # Execution coordinator, Action runner pipeline, WebView pool, JS engine
│   ├── scheduler/              # Cron scheduling UI, WorkManager workers, exact AlarmManager dispatchers
│   ├── proxy/                  # Proxy setup UI, latency tester, rotation policies, local VPN service
│   ├── diagnostics/            # Real-time log inspector, timeline viewer, screenshot gallery, health checks
│   ├── settings/               # App preferences, theme switcher, battery whitelist prompts, storage cleanup
│   └── backup/                 # Encrypted database backup/restore, JSON export/import handlers
│
└── integration/
    └── root/                   # Optional libsu integration, iptables manager, Magisk su privilege auditing
```

### Module Responsibilities & Dependency Rules

| Module | Role & Purpose | Direct Dependencies |
| :--- | :--- | :--- |
| **`:app`** | Assembles the complete application, wires Dagger Hilt dependency graphs, and defines root navigation. | `:core:*`, `:feature:*`, `:integration:root` |
| **`:core:common`** | Shared utilities, coroutine dispatchers, loggers, error types, and extension functions. | Kotlin Standard Library, Coroutines |
| **`:core:database`** | Room database schema, SQLite entities, DAOs, TypeConverters, SQLCipher hooks. | `:core:common`, Room, SQLCipher |
| **`:core:network`** | HTTP/SOCKS5 proxy socket handling, OkHttp interceptors, TLS setup, network monitor. | `:core:common`, OkHttp 4.x/5.x |
| **`:core:domain`** | Pure Kotlin domain entities (`Profile`, `Action`, `ProxyConfig`), UseCases, Repository interfaces. | `:core:common` *(No Android framework deps)* |
| **`:core:data`** | Implements domain repository contracts, bridges DB and Network layers to domain. | `:core:domain`, `:core:database`, `:core:network` |
| **`:feature:profiles`** | UI for creating, editing, and managing automation profiles and cookie jars. | `:core:domain`, Compose, Hilt |
| **`:feature:automation`**| Core WebView session runner, action execution pipeline, JS bridge, visual runner. | `:core:domain`, `:core:network`, Compose, Hilt |
| **`:feature:scheduler`** | Cron-based scheduling UI, WorkManager worker implementations, exact alarms. | `:core:domain`, WorkManager, Compose, Hilt |
| **`:feature:proxy`** | Proxy manager, connection latency tester, rotation algorithms, VpnService tunneling. | `:core:domain`, `:core:network`, Compose, Hilt |
| **`:feature:diagnostics`**| Real-time log viewer, screenshot gallery, session metrics, and diagnostic report exporter. | `:core:domain`, Compose, Hilt |
| **`:feature:settings`** | Global settings, dark/light theme, storage cleaner, battery optimization prompts. | `:core:domain`, Compose, Hilt |
| **`:feature:backup`** | Encrypted database backup/restore, profile JSON import/export validation. | `:core:domain`, `:core:database`, Compose, Hilt |
| **`:integration:root`** | Magisk/libsu integration, transparent proxy iptables rules, privileged system actions. | `:core:common`, `:core:domain`, libsu |

> **Architectural Guardrail**: Feature modules must **never** depend directly on other feature modules. Cross-feature navigation is coordinated via deep links or domain-level interfaces defined in `:core:domain`.

---

## Build Instructions

### Basic Build Commands (Gradle Wrapper)

```powershell
# Navigate to project root
cd D:\Webapp\BAutoPilot

# Clean build cache and temporary artifacts
.\gradlew clean

# Build debug APK for all variants
.\gradlew assembleDebug

# Build production release bundle (AAB)
.\gradlew bundleRelease

# Run all unit tests across all modules
.\gradlew test

# Run static analysis and code style verifications
.\gradlew ktlintCheck detekt

# Run Compose UI instrumented tests (requires connected device/emulator)
.\gradlew connectedAndroidTest
```

### Build Variants
- **`debug`**: Includes LeakCanary, detailed Logcat telemetry, unrestricted WebView inspection (`setWebContentsDebuggingEnabled(true)`), and verbose coroutine debug logs.
- **`release`**: ProGuard/R8 code shrinking and resource obfuscation enabled, logging stripped, strict certificate pinning enforced.

---

## Documentation Index

The complete AutoPilot architectural, engineering, and design specifications are organized into **15 standalone markdown documents**:

| # | Document | File Link | Description & Key Topics |
| :---: | :--- | :--- | :--- |
| **01** | **Master README** | [README.md](file:///D:/Webapp/BAutoPilot/README.md) | High-level project blueprint, module map, prerequisites, build guide, and documentation index. |
| **02** | **Product Requirements Document** | [docs/PRD.md](file:///D:/Webapp/BAutoPilot/docs/PRD.md) | Product vision, target personas, functional & non-functional requirements, acceptance criteria, and constraints. |
| **03** | **System Architecture** | [docs/ARCHITECTURE.md](file:///D:/Webapp/BAutoPilot/docs/ARCHITECTURE.md) | Comprehensive architecture guide, Clean Architecture layer contracts, module dependency graphs, and WebView pool design. |
| **04** | **Features Specification** | [docs/FEATURES.md](file:///D:/Webapp/BAutoPilot/docs/FEATURES.md) | Full catalog of features categorized from P0 (Must Have) to P3 (Power User), edge cases, and user workflows. |
| **05** | **UI/UX Design Specification** | [docs/DESIGN.md](file:///D:/Webapp/BAutoPilot/docs/DESIGN.md) | Compose Material 3 theme, design tokens, responsive typography, screen inventory, navigation flows, and interactive mockups. |
| **06** | **Security & Privacy Specification** | [docs/SECURITY.md](file:///D:/Webapp/BAutoPilot/docs/SECURITY.md) | Cryptographic storage (AES-256 GCM), WebView isolation (`file://` blocking, CSP), root privilege containment, and permission auditing. |
| **07** | **Background Execution & Services** | [docs/BACKGROUND_EXECUTION.md](file:///D:/Webapp/BAutoPilot/docs/BACKGROUND_EXECUTION.md) | Foreground Service architecture (`specialUse`), WorkManager job constraints, Doze mode handling, and `BOOT_COMPLETED` recovery. |
| **08** | **Database Schema & Persistence** | [docs/DATABASE_SCHEMA.md](file:///D:/Webapp/BAutoPilot/docs/DATABASE_SCHEMA.md) | Relational Room database schema (`profiles`, `actions`, `schedules`, `sessions`, `logs`), indexes, foreign keys, and migration strategy. |
| **09** | **Automation Engine & Actions** | [docs/AUTOMATION_ENGINE.md](file:///D:/Webapp/BAutoPilot/docs/AUTOMATION_ENGINE.md) | Action execution pipeline, state machine, DOM selector query engine, JavaScript bridge, retry mechanisms, and error handlers. |
| **10** | **Proxy & VPN Specification** | [docs/PROXY_VPN_SPECIFICATION.md](file:///D:/Webapp/BAutoPilot/docs/PROXY_VPN_SPECIFICATION.md) | HTTP/SOCKS5 proxy routing, credential encryption, health check pingers, rotation policies, and local VpnService loopback. |
| **11** | **Root Integration & Magisk** | [docs/ROOT_INTEGRATION.md](file:///D:/Webapp/BAutoPilot/docs/ROOT_INTEGRATION.md) | Privileged shell execution using `libsu`, iptables transparent proxy routing, system hosts modifications, and safe unprivileged fallbacks. |
| **12** | **Testing Strategy & QA Plan** | [docs/TESTING_STRATEGY.md](file:///D:/Webapp/BAutoPilot/docs/TESTING_STRATEGY.md) | Unit test matrix, MockK domain testing, Turbine Flow assertions, Compose UI tests, Robolectric simulations, and CI/CD pipelines. |
| **13** | **Project Roadmap & Releases** | [docs/ROADMAP.md](file:///D:/Webapp/BAutoPilot/docs/ROADMAP.md) | Chronological delivery phases (Phase 0 Documentation through Phase 5 Polish), milestone deliverables, and estimated timelines. |
| **14** | **Development Rules & Standards** | [docs/DEVELOPMENT_RULES.md](file:///D:/Webapp/BAutoPilot/docs/DEVELOPMENT_RULES.md) | Kotlin style conventions, linting & detekt policies, clean architecture constraints, PR requirements, and Git branching rules. |
| **15** | **Architecture Decisions (ADRs)** | [docs/DECISIONS.md](file:///D:/Webapp/BAutoPilot/docs/DECISIONS.md) | Architecture Decision Records capturing context, alternatives, trade-offs, and rationale for all foundational architectural choices. |

---

## Contributing

We welcome contributions to the AutoPilot platform! To maintain code quality and architectural integrity, please adhere to the following workflow:

### Branching Strategy
- **`main`**: Production-ready, stable releases.
- **`develop`**: Primary integration branch for new features.
- Feature branches: `feature/<feature-name>` (e.g., `feature/socks5-rotation`)
- Bugfix branches: `bugfix/<issue-description>` (e.g., `bugfix/webview-crash-api35`)
- Documentation branches: `docs/<doc-update>` (e.g., `docs/update-adr-004`)

### Pull Request Guidelines
1. Fork or branch from `develop`.
2. Ensure all code conforms to the rules in [docs/DEVELOPMENT_RULES.md](file:///D:/Webapp/BAutoPilot/docs/DEVELOPMENT_RULES.md).
3. Max function length: **30 lines**; max file length: **400 lines**.
4. Pure Kotlin domain code must contain **zero** Android SDK dependencies.
5. All new business logic must include corresponding JUnit unit tests with minimum **80% branch coverage**.
6. Verify code quality before pushing:
   ```powershell
   .\gradlew ktlintCheck detekt test
   ```
7. Submit a Pull Request targeting `develop` with a clear description of changes, linked issues, and test evidence.

---

## License

```
Copyright 2026 AutoPilot Project Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

*AutoPilot is maintained under an open architectural specification. For detailed architectural questions, refer to [docs/ARCHITECTURE.md](file:///D:/Webapp/BAutoPilot/docs/ARCHITECTURE.md) or open an issue on the project issue tracker.*

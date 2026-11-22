# Architecture Decision Records (ADR) - BAutoPilot

This document records the architectural and technology decisions made for the **BAutoPilot** Android automation platform. Each record details the context, the decision taken, its status, and the resulting architectural consequences and trade-offs.

---

## Index of Decisions

| ADR ID | Title | Status | Date |
| :--- | :--- | :--- | :--- |
| [ADR-001](#adr-001-kotlin-2x-no-java) | Kotlin 2.x as Sole Implementation Language (No Java) | Accepted | 2026-11-22 |
| [ADR-002](#adr-002-jetpack-compose--material-3-with-androidview-for-webview) | Jetpack Compose + Material 3 with AndroidView for WebView | Accepted | 2026-11-22 |
| [ADR-003](#adr-003-room--sqlcipher-for-encrypted-local-persistence) | Room + SQLCipher for Encrypted Local Persistence | Accepted | 2026-11-22 |
| [ADR-004](#adr-004-dagger-hilt-for-dependency-injection) | Dagger Hilt for Dependency Injection | Accepted | 2026-11-22 |
| [ADR-005](#adr-005-foreground-service-with-specialuse-type-for-automation-execution) | Foreground Service with `specialUse` Type for Automation Execution | Accepted | 2026-11-22 |
| [ADR-006](#adr-006-hybrid-task-scheduling-workmanager--alarmmanager) | Hybrid Task Scheduling (WorkManager + AlarmManager) | Accepted | 2026-11-22 |
| [ADR-007](#adr-007-android-system-webview-over-custom-embedded-engine) | Android System WebView over Custom Embedded Engine | Accepted | 2026-11-22 |
| [ADR-008](#adr-008-optional-root-integration-via-libsu-with-graceful-degradation) | Optional Root Integration via libsu with Graceful Degradation | Accepted | 2026-11-22 |
| [ADR-009](#adr-009-okhttp-as-standard-http-client-for-network-operations) | OkHttp as Standard HTTP Client for Network Operations | Accepted | 2026-11-22 |
| [ADR-010](#adr-010-kotlin-coroutines--flow-for-concurrency-and-reactive-streams) | Kotlin Coroutines + Flow for Concurrency and Reactive Streams | Accepted | 2026-11-22 |
| [ADR-011](#adr-011-multi-module-gradle-architecture) | Multi-Module Gradle Architecture | Accepted | 2026-11-22 |
| [ADR-012](#adr-012-androidx-proxycontroller-api-for-webview-proxying) | AndroidX ProxyController API for WebView Proxying | Accepted | 2026-11-22 |
| [ADR-013](#adr-013-polymorphic-json-configuration-for-actions-with-room-typeconverters) | Polymorphic JSON Configuration for Actions with Room TypeConverters | Accepted | 2026-11-22 |
| [ADR-014](#adr-014-offline-first-client-only-architecture-no-external-backend) | Offline-First, Client-Only Architecture (No External Backend) | Accepted | 2026-11-22 |
| [ADR-015](#adr-015-minimum-sdk-26-android-80-oreo) | Minimum SDK 26 (Android 8.0 Oreo) | Accepted | 2026-11-22 |
| [ADR-016](#adr-016-room-schema-migration-v1-to-v2-with-foreign-key-cascade-for-scheduling) | Room Schema Migration v1 to v2 with Foreign Key Cascade for Scheduling | Accepted | 2026-11-22 |
| [ADR-017](#adr-017-dual-background-engine-alarmmanager-for-precision--workmanager-for-self-healing-reconciliation) | Dual Background Engine: AlarmManager for Precision & WorkManager for Self-Healing Reconciliation | Accepted | 2026-11-22 |
| [ADR-018](#adr-018-process-wide-webview-proxyoverride-sequential-execution-locking) | Process-Wide WebView ProxyOverride Sequential Execution Locking with Coroutines Mutex | Accepted | 2026-11-22 |
| [ADR-019](#adr-019-keystore-aes-256-gcm-envelope-encryption-for-proxy-credentials) | Android KeyStore AES-256-GCM Envelope Encryption for Proxy Credentials | Accepted | 2026-11-22 |

---

## ADR-018: Process-Wide WebView ProxyOverride Sequential Execution Locking

### Status
**Accepted**

### Context
AndroidX WebKit `ProxyController.getInstance().setProxyOverride()` applies to all WebViews across the host process. When multiple profiles require distinct proxies (or direct network), running them concurrently in the same process causes cross-profile traffic contamination and IP leaks.

### Decision
Implement `ProxyExecutionCoordinator` backed by a Coroutines `Mutex`. All profile automation executions acquire this mutual exclusion lock before applying proxy rules and releasing them in an unconditional `finally` block with `clearProxyOverride()`.

### Consequences
- **Positive:**
  - Absolute leak protection between profiles with different proxy configurations.
  - Guarantees clean network state restoration even if an automation crashes or is cancelled.
- **Negative:**
  - Profiles with different proxies must execute sequentially rather than concurrently in Tier 1.

---

## ADR-019: Keystore AES-256-GCM Envelope Encryption for Proxy Credentials

### Status
**Accepted**

### Context
Proxy authentication credentials (usernames and passwords) are sensitive data-at-rest. Storing them in plaintext SQLite tables exposes user credentials to root dumps and device backups.

### Decision
Implement `ProxyCredentialEncryptor` using hardware-backed Android KeyStore AES-256-GCM with unique 12-byte initialization vectors (IVs) per field. Mask all credentials in logs and user interfaces (`username:••••••`).

### Consequences
- **Positive:**
  - Hardware-isolated key storage.
  - Zero plaintext credential leakage in Room database or memory dumps.
  - Deterministic fallback key support for JVM unit testing environments.
- **Negative:**
  - Slight encryption/decryption CPU overhead during proxy retrieval and persistence.

---

## ADR-001: Kotlin 2.x (No Java)

### Status
**Accepted**

### Context
BAutoPilot is an advanced Android automation engine requiring expressive, concise, and safe programming constructs. Java in modern Android development presents several critical shortcomings:
- Lack of language-level null safety leading to potential `NullPointerException`s at runtime.
- Verbose boilerplate syntax for data classes, delegation, and functional transformations.
- Absence of first-class coroutine-based structured concurrency, forcing reliance on complex callback chains or heavy reactive libraries.
- Deprecation of Java-first APIs across Jetpack libraries (e.g., Jetpack Compose, Room Coroutines extensions, Navigation).

Kotlin 2.x introduces the production-ready K2 compiler frontend, unlocking significantly faster build and compilation times, unified cross-platform frontend infrastructure, improved type inference, and built-in support for the Compose compiler plugin (migrated directly into the Kotlin repository).

### Decision
Adopt **Kotlin 2.x** as the exclusive programming language for BAutoPilot across all modules and tests. Prohibit Java source files completely from the repository (`.kt` files only). Enforce strict compiler settings (`allWarningsAsErrors = true`, explicit API mode where applicable).

### Consequences
- **Positive:**
  - Absolute null safety guarantees at compile time across data modeling and engine execution.
  - Native coroutines and structured concurrency primitives (`suspend`, `Flow`, `Channel`).
  - Access to modern language features (data objects, value classes, context parameters).
  - Faster incremental and clean compilation with the K2 compiler architecture.
  - Native compatibility with the Jetpack Compose compiler.
- **Negative:**
  - Requires developers to be proficient in idiomatic Kotlin 2.x idioms and coroutines.
  - Interop with legacy Java-based Android libraries requires Kotlin wrapper facades.

---

## ADR-002: Jetpack Compose + Material 3 with AndroidView for WebView

### Status
**Accepted**

### Context
User interface development in Android has shifted from imperative XML layouts (`View` system, fragments, view-binding, complex lifecycle wiring) to declarative UI frameworks. 
BAutoPilot requires:
- Dynamic UI updates reflecting real-time automation state, log streams, and live execution statistics.
- Single-Activity architecture (`MainActivity`) without Fragment transaction overhead.
- Modern visual language adhering to Material 3 (Material You) guidelines, including dynamic color theming.
- Live rendering and interaction preview of the underlying web automation target via Android `WebView`.

However, `WebView` remains an imperative View hierarchy component that does not natively belong to the Compose node tree.

### Decision
1. Standardize UI development on **Jetpack Compose** with **Material 3 (M3)** components.
2. Maintain a strict **Single-Activity Architecture** using Compose Navigation (`androidx.navigation:navigation-compose`).
3. Encapsulate all WebView instances inside Compose using the `AndroidView` interop composable wrapper, ensuring proper lifecycle management (`DisposableEffect`, state retention across recompositions).

### Consequences
- **Positive:**
  - Fully declarative, reactive UI synchronized with state flows without manual view mutations.
  - Cohesive design system using Material 3 color schemes, typography, and elevation tokens.
  - Eliminated boilerplate of XML layouts, custom adapters, and Fragment managers.
  - Preview capability using `@Preview` for isolated composable components.
- **Negative:**
  - `WebView` embedded in `AndroidView` does not participate in Compose's native layout pass optimizations.
  - Careful state detachment and teardown (`destroy()`, clearing callbacks) must be handled inside `onRelease` / `DisposableEffect` to prevent Activity/Context memory leaks.

---

## ADR-003: Room + SQLCipher for Encrypted Local Persistence

### Status
**Accepted**

### Context
BAutoPilot stores critical operational data locally on the host device:
- Automation recipes, sequence workflows, and step definitions.
- Sensitive authentication payloads, session cookies, injected credentials, and proxy authentication tokens.
- Historical execution logs, timing diagnostics, and capture metadata.

Storing this data in plain SQLite, standard Room, or unencrypted `SharedPreferences` creates a severe vulnerability: rooted devices, physical access, or backup extraction can expose private credentials and target credentials.

### Decision
Utilize **Android Jetpack Room** backed by **SQLCipher** (`net.zetetic:sqlcipher-android`) via Room's `SupportFactory`. 
All database files are encrypted at rest using 256-bit AES encryption. Passphrase material is generated and securely preserved within the hardware-backed **Android Keystore** (`MasterKey` / EncryptedSharedPreferences). Room DAOs expose reactive Kotlin `Flow` streams for live state updates.

### Consequences
- **Positive:**
  - Complete transparent encryption-at-rest for all persistent relational data.
  - Compile-time query validation and type-safe abstraction over SQLite through Room.
  - First-class Coroutines and Flow integration within DAOs.
  - Prevents forensic data extraction from rooted devices or external file dumps.
- **Negative:**
  - Moderate performance overhead during crypto operations on large query payloads (mitigated by indexing and paginated Room queries).
  - Native binary (.so) footprint increase (~3–5 MB APK size overhead for multi-ABI SQLCipher binaries).
  - Migration scripts must be verified carefully to maintain SQLCipher encryption consistency across schema versions.

---

## ADR-004: Dagger Hilt for Dependency Injection

### Status
**Accepted**

### Context
A modular Android automation platform consists of decoupled engines, repositories, use cases, background services, and UI components. Managing dependencies manually (manual DI or Service Locator) leads to boilerplate, test fragility, and potential memory leaks. Runtime-reflection DI frameworks (like traditional Koin or Guice) incur runtime startup overhead and defer dependency resolution errors to runtime crashes.

### Decision
Adopt **Google Dagger Hilt** as the standard dependency injection framework across the application. Utilize Hilt's Android-specific scopes and integrations:
- `@HiltAndroidApp` on `Application` class.
- `@AndroidEntryPoint` on `MainActivity` and `ForegroundService`.
- `@HiltViewModel` for ViewModel injection.
- `@HiltWorker` for WorkManager `CoroutineWorker` injection.

### Consequences
- **Positive:**
  - Compile-time verification of the entire dependency graph: missing dependencies fail the build, never crash at runtime.
  - Zero runtime reflection cost; code generation creates direct invocation factories.
  - Built-in scoping aligned with Android lifecycles (`@Singleton`, `@ViewModelScoped`, `@ServiceScoped`).
  - Seamless support for injecting background workers (`HiltWorkerFactory`).
- **Negative:**
  - Annotation processing overhead during compilation (mitigated by Kotlin Symbol Processing - KSP).
  - Learning curve for multi-module dependency propagation (Hilt entry points across dynamic boundaries).

---

## ADR-005: Foreground Service with `specialUse` Type for Automation Execution

### Status
**Accepted**

### Context
Automation workflows in BAutoPilot involve sequential web interactions, DOM polling, script execution, network retries, and sleep delays that span minutes or hours. On modern Android versions (Android 8.0 through Android 15), background processes are severely throttled:
- Doze mode suspends CPU execution and network access when the screen is turned off.
- The Android low-memory killer (LMK) aggressively kills non-foreground processes.
- Background execution limits forbid starting background services from the background.

To guarantee uninterrupted execution while the screen is off or the app is minimized, the automation runner must run as a high-priority process.

### Decision
Implement the core automation execution engine inside an Android **Foreground Service** declared with `foregroundServiceType="specialUse"` (under Android 14+ / API 34+ requirement). 
Attach a persistent, non-dismissible notification displaying:
1. Currently active automation recipe and step progress.
2. Live status / duration counter.
3. Interactive notification actions (e.g., Pause, Resume, Abort).

### Consequences
- **Positive:**
  - Elevated process priority (low OOM adjustment score), preventing OS termination during execution.
  - Uninterrupted CPU wake-lock and network access while automation is running with the device locked.
  - Transparent user awareness and direct control through persistent notification action buttons.
  - Full compliance with Android 14+ foreground service type declarations.
- **Negative:**
  - Google Play Console requires clear developer declaration and rationale for the `specialUse` foreground service type.
  - User sees a permanent notification while automation is running (by design, ensures transparency).
  - Battery consumption must be managed responsibly via explicit partial wake locks (`WakeLock.acquire()` with timeouts).

---

## ADR-006: Hybrid Task Scheduling (WorkManager + AlarmManager)

### Status
**Accepted**

### Context
BAutoPilot supports two distinct scheduling use cases:
1. **Deferrable / Periodic Automation:** Tasks that should execute periodically (e.g., scrape data every 6 hours, clean temporary caches) where exact minute precision is unnecessary, but device charging and unmetered Wi-Fi constraints should be respected.
2. **Exact / Time-Critical Automation:** Tasks that must trigger at a precise wall-clock millisecond (e.g., submitting a form, checking a flash drop at exactly 09:00:00 AM), regardless of Doze state.

Using WorkManager alone cannot guarantee exact-time firing due to OS battery batching. Using AlarmManager alone for all tasks leads to battery waste and ignores system health constraints.

### Decision
Adopt a **Hybrid Scheduling Model**:
- **WorkManager (`androidx.work:work-runtime-ktx`):** Used for all periodic, deferrable, and constraint-based automation tasks (specifying `Constraints` like `NetworkType.UNMETERED`, `RequiresCharging`).
- **AlarmManager (`setExactAndAllowWhileIdle` / `setAlarmClock`):** Used strictly for deterministic, time-critical tasks requiring exact timestamp execution. Upon alarm delivery via `BroadcastReceiver`, the app immediately spins up the Foreground Service to proceed with execution.

### Consequences
- **Positive:**
  - Optimal battery efficiency for recurring background syncs and non-urgent jobs via WorkManager.
  - Guaranteed exact-second execution for time-sensitive automation via AlarmManager.
  - Native integration with WorkManager's exponential backoff retry policies.
- **Negative:**
  - Dual scheduling codebase requiring clear abstraction layers to route schedules appropriately.
  - Exact alarms require declaring `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM` permissions on Android 12+ (API 31+), which may require explicit user grant via system settings.

---

## ADR-007: Android System WebView over Custom Embedded Engine

### Status
**Accepted**

### Context
BAutoPilot executes browser automation by rendering HTML/JS, inspecting DOM trees, evaluating JavaScript, and dispatching virtual user actions. Two primary engine options were evaluated:
1. **Custom Embedded Engine (e.g., bundled GeckoView or Chromium Content Shell):** Ships a full browser engine binary inside the APK.
2. **Android System WebView (`android.webkit.WebView` + `androidx.webkit:webkit`):** Uses the platform-provided Chromium engine updated independently by Google Play Services / OS.

### Decision
Utilize the **Android System WebView** augmented with `androidx.webkit` compatibility libraries. Reject bundling a third-party browser engine (GeckoView / Chromium).

### Consequences
- **Positive:**
  - Drastically smaller APK distribution footprint (saves 50MB–100MB+ per target CPU architecture).
  - Automatically benefits from monthly Chromium security patches and zero-day fixes pushed through Google Play.
  - Leverages device-specific hardware acceleration and graphics drivers out of the box.
  - Minimal native memory consumption compared to managing a distinct standalone browser process.
- **Negative:**
  - Dependent on the user's installed System WebView version; variations across non-Google Android ROMs can cause minor engine divergence.
  - Single renderer process architecture per application context: crashes in the WebView renderer affect the hosting app process unless handled via `WebViewClient.onRenderProcessGone`.
  - Advanced low-level browser primitives (such as distinct isolated storage contexts per WebView instance) are more constrained than in custom GeckoView/Chromium builds.

---

## ADR-008: Optional Root Integration via libsu with Graceful Degradation

### Status
**Accepted**

### Context
Certain high-power automation capabilities cannot be achieved through standard non-root Android APIs:
- Injecting global touch/pointer events outside the app's own WebView boundary (`input tap x y`, `input swipe`).
- Toggling hardware states (Airplane Mode, Mobile Data, GPS) without user prompts.
- Clearing third-party app caches or terminating external processes.
- Changing system-level network routing tables.

However, requiring root privileges would eliminate 99% of normal users and flag the application on security scanners.

### Decision
Incorporate root functionality as an **optional, isolated enhancement** using topjohnwu's **libsu** (`com.github.topjohnwu.libsu:core`). 
Isolate all root operations in a separate module (`:core:root`). At runtime, execute capability detection (`Shell.isAppGrantedRoot()`). If root is absent, **gracefully degrade**: disable shell-level capabilities and fall back entirely to pure in-app WebView DOM automation and non-root accessibility/proxy features.

### Consequences
- **Positive:**
  - Power users with rooted devices unlock advanced OS-level automation capabilities.
  - Unrooted standard devices experience 100% functionality for all standard WebView automation workflows.
  - Clean separation: core engine logic does not assume elevated privileges.
  - `libsu` handles concurrency, root shell pooling, and root daemon communication safely.
- **Negative:**
  - Additional testing matrix required to validate both rooted and unrooted runtime behavior.
  - Elevated risk of security review scrutiny if shell command generation is not strictly sanitized.

---

## ADR-009: OkHttp as Standard HTTP Client for Network Operations

### Status
**Accepted**

### Context
Beyond in-browser navigation, BAutoPilot requires direct out-of-band network operations:
- Pre-flight proxy verification and latency checks.
- Direct API integration (REST webhooks, status reporting, alert dispatches).
- Downloading remote automation script assets and user resources.
- Raw HTTP header inspection, cookie synchronization, and certificate testing.

Using `HttpURLConnection` is archaic and error-prone. Ktor Client introduces additional multiplatform abstraction layers unnecessary for an Android-focused codebase.

### Decision
Adopt **Square OkHttp** (`com.squareup.okhttp3:okhttp`) as the sole HTTP networking engine for out-of-band network operations. Configure centralized client instances with:
- Dedicated connection pooling (`ConnectionPool`).
- Custom interceptors for logging, metrics, retry handling, and user-agent rotation.
- First-class support for explicit per-call or per-client proxy definitions (`java.net.Proxy`).

### Consequences
- **Positive:**
  - Mature, robust HTTP/1.1, HTTP/2, and HTTP/3 support with industry-standard reliability.
  - Powerful interceptor pipeline enabling clean cross-cutting concerns (token injection, automated retries, diagnostic metrics).
  - Explicit proxy configuration allowing connection testing via HTTP, HTTPS, and SOCKS5 proxies before applying them to WebViews.
- **Negative:**
  - Proxy settings configured on an `OkHttpClient` instance do not automatically apply to the System WebView; network layers must synchronize proxy settings explicitly.

---

## ADR-010: Kotlin Coroutines + Flow for Concurrency and Reactive Streams

### Status
**Accepted**

### Context
Browser automation is fundamentally asynchronous and stateful:
- Waiting for specific DOM elements to appear or mutate with configurable timeouts.
- Interleaving delays, JavaScript executions, page reloads, and network calls.
- Streaming real-time execution logs, metrics, and progress percentages to the UI.
- Handling immediate user cancellations (e.g., clicking "Abort" on notification or UI).

Traditional threading or callback-based patterns result in deeply nested logic and memory leaks. RxJava introduces substantial APK method count bloat and steep cognitive overhead.

### Decision
Standardize on **Kotlin Coroutines** for all asynchronous control flow and **Kotlin Flow** (`StateFlow`, `SharedFlow`, cold `Flow`) for all reactive streaming data:
- Execute automation recipes inside a dedicated `CoroutineScope` bound to the Foreground Service lifecycle.
- Propagate cancellation via standard coroutine cooperative cancellation (`ensureActive()`, `yield()`).
- Expose all database streams, log events, and engine state machines via `StateFlow` and `SharedFlow`.
- Consume flows in Compose UI using `collectAsStateWithLifecycle()`.

### Consequences
- **Positive:**
  - Sequential, readable syntax for inherently asynchronous multi-step automation scripts.
  - Structured concurrency guarantees that child tasks are cancelled when parent recipes abort or fail.
  - First-class backpressure support natively built into Flow collectors.
  - Native integration across Jetpack Room, WorkManager, and Compose.
- **Negative:**
  - Long-running blocking operations (e.g., native code, synchronous I/O) must explicitly switch to `Dispatchers.IO` to prevent blocking the main thread.
  - Uncaught exceptions inside improperly scoped coroutines can crash the process if `CoroutineExceptionHandler` is omitted.

---

## ADR-011: Multi-Module Gradle Architecture

### Status
**Accepted**

### Context
Maintaining a complex Android automation system within a single monolithic `:app` module causes:
- Slow incremental build times due to full recompilation cycles.
- Weak architectural boundaries, permitting leaky abstractions between UI, database, engine, and network layers.
- Inability to enforce visibility rules or test modules in isolation.

### Decision
Decompose BAutoPilot into a **Multi-Module Gradle Architecture** using Gradle Version Catalogs (`libs.versions.toml`) and modular layer boundaries:

```
                      ┌───────────────┐
                      │     :app      │
                      └───────┬───────┘
                              │
       ┌──────────────────────┼──────────────────────┐
       ▼                      ▼                      ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│ :feature:    │      │ :feature:    │      │ :feature:    │
│ actions      │      │ execution    │      │ scheduler    │
└──────┬───────┘      └──────┬───────┘      └──────┬───────┘
       │                     │                     │
       └─────────────────────┼─────────────────────┘
                             ▼
              ┌─────────────────────────────┐
              │     :engine:automation      │
              └──────────────┬──────────────┘
                             │
              ┌──────────────┴──────────────┐
              ▼                             ▼
       ┌──────────────┐              ┌──────────────┐
       │ :engine:     │              │ :core:       │
       │ webview      │              │ root         │
       └──────┬───────┘              └──────────────┘
              │
       ┌──────┴──────────────────────┬──────────────────────┐
       ▼                             ▼                      ▼
┌──────────────┐              ┌──────────────┐       ┌──────────────┐
│ :core:       │              │ :core:       │       │ :core:       │
│ database     │              │ network      │       │ common/model │
└──────────────┘              └──────────────┘       └──────────────┘
```

- **`:app`**: Application entry point, Hilt dependency graph aggregation, top-level navigation setup.
- **`:feature:*`**: User-facing Compose UI modules (Action builder, Live Execution, Scheduler, Settings).
- **`:engine:automation`**: Domain orchestrator, recipe parser, step runner, execution state machine.
- **`:engine:webview`**: WebView configuration, script injection, DOM observer, AndroidView wrappers.
- **`:core:database`**: Room database, SQLCipher integration, DAOs, migrations.
- **`:core:network`**: OkHttp client, proxy health check, network monitoring.
- **`:core:root`**: `libsu` wrapper, root capability detection, shell execution.
- **`:core:model`**: Shared domain models, entities, enums.
- **`:core:common`**: Extensions, dispatchers, utility helpers.

### Consequences
- **Positive:**
  - Parallel compilation and aggressive Gradle build cache utilization, drastically cutting build times.
  - Enforced architectural separation: feature modules cannot directly depend on sibling features.
  - Core domain and engine logic can be unit-tested without loading UI or Android framework classes.
- **Negative:**
  - Initial configuration complexity and maintenance of build configuration across modules.
  - Requires disciplined dependency management to prevent circular module dependencies.

---

## ADR-012: AndroidX ProxyController API for WebView Proxying

### Status
**Accepted**

### Context
Automation workflows frequently require routing web traffic through external proxies (HTTP, HTTPS, SOCKS5) for geo-targeting, network simulation, or IP address management. Traditional approaches on Android have significant drawbacks:
- Setting system-wide Wi-Fi proxies requires user interaction or MDM/Device Owner privileges.
- Creating a local VPN service (`VpnService`) introduces significant latency, battery drain, and conflicts with existing VPNs.
- Root-level `iptables` redirection requires root access.

### Decision
Use the official **AndroidX WebKit `ProxyController` API** (`androidx.webkit.ProxyController`) to programmatically configure proxy rules for the System WebView:
- Implement `ProxyConfig.Builder` to define proxy URLs, bypass rules (e.g., `<-loopback>`), and fallback schemes.
- Apply configurations asynchronously via `ProxyController.getInstance().setProxyOverride()`.
- Clear proxy settings cleanly via `clearProxyOverride()` when automation sessions conclude.

### Consequences
- **Positive:**
  - Fully standard, officially supported AndroidX API operating on unrooted devices without special system permissions.
  - No need to initiate a full `VpnService` tunnel or route all device traffic.
  - Supports comprehensive rule sets including proxy failovers, scheme filtering, and domain bypass lists.
- **Negative:**
  - Proxy configuration applies globally to the entire WebView process within the app: different simultaneous WebViews cannot bind to distinct proxies within the same Android process.
  - Proxy authentication (username/password) must be handled via `WebViewClient.onReceivedHttpAuthRequest` callbacks rather than inline proxy URLs.

---

## ADR-013: Polymorphic JSON Configuration for Actions with Room TypeConverters

### Status
**Accepted**

### Context
BAutoPilot automation recipes consist of an ordered sequence of heterogeneous actions, for example:
- `ClickAction` (selector, clickType, timeout)
- `TextInputAction` (selector, textValue, clearExisting, simulatedTypingSpeed)
- `WaitConditionAction` (conditionType, selector, expectedState, timeout)
- `ScrollAction` (direction, amountPx, targetElement)
- `EvaluateJsAction` (scriptContent, returnResult)
- `ExtractDataAction` (selector, attributeName, variableName)
- `NavigateAction` (targetUrl, referer, timeout)

Representing these varying schemas in a purely relational SQL database would require either:
1. A bloated table with dozens of nullable columns (sparse table anti-pattern).
2. Complex multi-table polymorphic joins (`ActionEntity` + `ClickActionEntity`, etc.), complicating query performance, ordering, and migrations.

### Decision
Store action configurations as structured **JSON payloads** within the Room `ActionEntity`. Use **Kotlinx Serialization** (`kotlinx.serialization.json.Json`) with polymorphic class discriminators (`type: "click"`, `type: "input"`, etc.) registered via a Room `@TypeConverter`.

```kotlin
@Entity(tableName = "recipe_actions")
data class ActionEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val orderIndex: Int,
    val actionType: ActionType,
    val configJson: ActionConfig // Converted via ActionConfigTypeConverter
)
```

### Consequences
- **Positive:**
  - High flexibility: adding new action types requires creating a new configuration subclass without database schema migrations.
  - Native export and import of automation recipes as human-readable JSON files.
  - Compact table structure with high read/write performance.
- **Negative:**
  - Cannot easily perform arbitrary SQL queries filtering by deep nested JSON properties (e.g., selecting all actions with a specific CSS selector) without SQLite JSON1 extension syntax.
  - Modifications to existing action configuration fields require careful backward-compatible deserialization defaults (`@EncodeDefault`, default arguments).

---

## ADR-014: Offline-First, Client-Only Architecture (No External Backend)

### Status
**Accepted**

### Context
Many automation platforms rely on cloud backends for storing user scripts, managing credentials, dispatching execution jobs, and logging analytics. For BAutoPilot, a remote cloud architecture introduces critical liabilities:
- **Privacy & Security Risks:** Automation scripts frequently handle personal logins, authentication tokens, proprietary web workflows, and private data. Transmitting these to a central cloud server creates severe data liability (GDPR, CCPA compliance) and high risk during data breaches.
- **Operational Costs:** Cloud hosting, database clusters, and bandwidth incur recurring maintenance and server costs.
- **Offline Reliability:** Users must be able to automate local intranets, offline resources, or environments with intermittent connectivity.

### Decision
Architect BAutoPilot strictly as an **Offline-First, Client-Only application** with **No External Backend**:
1. All recipe storage, scheduling, execution, and log collection occur strictly on the local device.
2. The application initiates zero telemetry, tracking, or cloud synchronization calls.
3. User export/import of automation recipes is executed via local encrypted files or standard Android system share intents (`ACTION_SEND`, `ACTION_CREATE_DOCUMENT`).

### Consequences
- **Positive:**
  - Absolute user privacy: zero credentials, logs, or web payloads ever leave the device.
  - Zero recurring cloud infrastructure, hosting, or maintenance expenses.
  - Full operational capability in offline or firewalled intranet networks.
  - Immune to cloud service outages or server-side service disruptions.
- **Negative:**
  - No automatic multi-device synchronization: users must manually transfer exported recipes between devices.
  - Processing power and memory are strictly bounded by the host Android hardware.

---

## ADR-015: Minimum SDK 26 (Android 8.0 Oreo)

### Status
**Accepted**

### Context
Selecting a minimum Android SDK version requires balancing market device coverage against architectural complexity, developer velocity, and access to modern system APIs. Supporting legacy platforms (API 21–25, Android 5.0–7.1) introduces severe constraints:
- Notification Channels (`NotificationChannel`) were introduced in API 26; legacy versions require separate fallback notification code paths.
- Mandatory background execution restrictions and modern Foreground Service semantics were standardized starting in Android 8.0 (API 26).
- Core Java 8+ language APIs (e.g., `java.time.Instant`, `java.time.Duration`) natively require API 26+ or heavy desugaring libraries.
- Modern Android KeyStore cipher algorithms and secure hardware guarantees are significantly more reliable on API 26+.

Market statistics confirm that Android 8.0 (API 26) and above covers **over 95%** of active Android devices globally.

### Decision
Set the minimum supported SDK version to **`minSdk = 26` (Android 8.0 Oreo)** and target the latest stable SDK version (**`targetSdk = 35` / `compileSdk = 35`**).

### Consequences
- **Positive:**
  - Immediate native access to Notification Channels, eliminating backward-compatibility branching for background notifications.
  - Native support for modern `java.time` APIs without requiring desugaring build bloat.
  - Standardized background execution and foreground service contracts across all supported OS versions.
  - Access to modern Android KeyStore implementations for Room/SQLCipher master key derivation.
  - Streamlined QA and testing surface by excluding obsolete platform versions.
- **Negative:**
  - Drops support for legacy devices running Android 7.1 and lower (< 5% of global market share).

---

## ADR-016: Room Schema Migration v1 to v2 with Foreign Key Cascade for Scheduling

### Status
**Accepted**

### Context
Phase 3 introduces automated background scheduling requiring persistent storage of `automation_schedules`. The table requires a strict relationship to `automation_profiles` such that deleting an automation profile immediately cascades and purges all associated schedules, eliminating dangling orphaned tasks. At the same time, existing installed users (and automated tests) from Phase 2 have existing profiles and execution history that must not be lost or corrupted during an upgrade.

### Decision
Implement a deterministic Room manual migration `MIGRATION_1_2`:
1. Bump database version from `1` to `2`.
2. Add `automation_schedules` table with `id` (INTEGER PRIMARY KEY AUTOINCREMENT), `profile_id` (TEXT NOT NULL, FOREIGN KEY REFERENCES `automation_profiles(id)` ON DELETE CASCADE), `name`, `type`, `recurrence_rule`, `target_time_millis`, `repeat_interval_millis`, `max_duration_seconds`, `is_enabled`, `last_run_time_millis`, `next_run_time_millis`, `status`, `created_at`, `updated_at`.
3. Add performance and foreign key index `index_automation_schedules_profile_id` and composite query index `index_automation_schedules_next_run_status` on `(is_enabled, status, next_run_time_millis)`.
4. Validate the migration via automated Room integration test `DatabaseMigrationTest` and on-device runtime upgrade testing.

### Consequences
- **Positive:**
  - Zero data loss for existing Phase 2 profiles and execution records.
  - Foreign key cascade guarantees referential integrity: profile deletion automatically cleans up schedules.
  - Indexed queries for upcoming scheduled jobs execute in O(log N) time.
- **Negative:**
  - Requires maintaining explicit SQL DDL migration scripts across database schema bumps.

---

## ADR-017: Dual Background Engine: AlarmManager for Precision & WorkManager for Self-Healing Reconciliation

### Status
**Accepted**

### Context
Android background execution faces strict Doze and App Standby power management restrictions. `WorkManager` provides robust battery-conscious deferrable job execution with constraint awareness, but lacks exact second-precision triggering when device is in Doze. `AlarmManager` with `setExactAndAllowWhileIdle()` provides exact wall-clock triggering across Doze, but does not offer built-in persistence, retry mechanics, or automatic reboot rescheduling. Furthermore, if a process dies mid-execution, a schedule can remain stuck in a `STARTING` or `RUNNING` state.

### Decision
Adopt a hybrid, self-healing dual background architecture:
1. **Precision Dispatching (`AlarmManager`):**
   - Use `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, ...)` when `canScheduleExactAlarms()` is granted, with graceful fallback to `setAndAllowWhileIdle()`.
   - Distinct `PendingIntent` request codes derived from schedule IDs ensure no alarm collision.
   - Handled by `ScheduleReceiver` which coordinates with `AutomationCoordinator` to check concurrency limits before launching `AutomationService`.
2. **Persistence & Lifecycle Recovery (`BootCompletedReceiver`):**
   - Listen for `ACTION_BOOT_COMPLETED` and `ACTION_MY_PACKAGE_REPLACED`.
   - Re-arm all active upcoming alarms from Room database.
   - Reconcile stale executions stuck in `STARTING`/`RUNNING` to `CANCELLED`/`FAILED` and advance passed recurrence windows.
3. **Periodic Maintenance (`WorkManager`):**
   - Register a periodic 2-hour `ScheduleReconciliationWorker` as a safety net to detect missed alarms or orphaned executions.

### Consequences
- **Positive:**
  - Exact time execution when armed, surviving deep Doze mode.
  - Completely self-healing: crashes, force stops, or device reboots do not leave persistent zombie states.
  - Fully compliant with Android 14+ exact alarm policies and Android 15 foreground service limits.
- **Negative:**
  - Requires handling two distinct background subsystems (`AlarmManager` and `WorkManager`) and registering multiple receivers.

---
*Document maintained by BAutoPilot Core Architecture Team.*

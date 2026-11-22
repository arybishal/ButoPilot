# BAutoPilot Architecture Specification

## 1. Executive Summary & High-Level Architecture

BAutoPilot is an enterprise-grade Android automation and browser orchestration platform. The architecture is engineered around the principles of **Clean Architecture** combined with the **Model-View-ViewModel (MVVM)** presentation pattern, enforcing unidirectional data flow (UDF), strict domain isolation, and modular encapsulation across all layers.

The primary objective of the architecture is to decouple business logic and automation orchestration from the Android OS runtime, third-party frameworks, and user interface components. This separation allows deterministic unit testing, robust headless execution, zero-leak background execution, and resilience against OS-level process management.

```
+---------------------------------------------------------------------------------------+
|                                  PRESENTATION LAYER                                   |
|  [Jetpack Compose UI] <--- (StateFlow) --- [ViewModels] <--- (User Events / Intents)  |
+---------------------------------------------------------------------------------------+
                                           |
                                           | Invokes Use Cases
                                           v
+---------------------------------------------------------------------------------------+
|                                     DOMAIN LAYER                                      |
|            [Use Cases / Interactors] ---> [Domain Models & Business Rules]            |
|                                          |                                            |
|                               [Repository Interfaces]                                 |
+---------------------------------------------------------------------------------------+
                                           ^
                                           | Implements Interfaces
                                           |
+---------------------------------------------------------------------------------------+
|                                      DATA LAYER                                       |
|                              [Repository Implementations]                             |
|                                           |                                           |
|            +------------------------------+------------------------------+            |
|            v                                                             v            |
|   [Local Data Source]                                           [Remote Data Source]  |
|  (Room + SQLCipher DB)                                         (OkHttp / Web Engine)  |
+---------------------------------------------------------------------------------------+
```

### 1.1 Architectural Layers

#### Presentation Layer (`:feature:*`, `:app`)
- **Technology**: Jetpack Compose (Declarative UI), Material 3, AndroidX ViewModel.
- **Responsibilities**: Renders UI state, captures user interactions, delegates events to ViewModels, and observes immutable `StateFlow` streams.
- **Rules**: Zero knowledge of database entities, network DTOs, or repository implementations. Communicates exclusively with domain Use Cases or Domain Repositories via injected ViewModels.

#### Domain Layer (`:core:domain`)
- **Technology**: Pure Kotlin library module (Zero Android framework imports, `android.*` strictly prohibited).
- **Responsibilities**: Contains enterprise business models, use cases (interactors), domain-specific business validation rules, repository contracts, and domain event definitions.
- **Rules**: Outermost layers depend on the domain layer. The domain layer has zero outgoing dependencies on any other module in the system (the Dependency Inversion Principle).

#### Data Layer (`:core:data`, `:core:database`, `:core:network`)
- **Technology**: Room, SQLCipher, OkHttp, DataStore, AndroidX WebKit.
- **Responsibilities**: Implements repository interfaces defined in `:core:domain`. Coordinates multiple data sources (encrypted disk persistence, remote endpoints, headless browser engines, low-level system bridges). Handles entity-to-domain model mapping, caching, data synchronisation, and offline policies.
- **Rules**: Exposes domain models to the outside world; entity schemas and network models are internal to the data layer.

---

## 2. Multi-Module Project Structure

The project employs a fine-grained, decoupled multi-module architecture structured into three main tiers: **App / Root**, **Features**, and **Core / Integration**.

```
                           +-------------+
                           |    :app     |
                           +------+------+
                                  |
         +------------------------+------------------------+
         |                        |                        |
         v                        v                        v
+-----------------+      +-----------------+      +-----------------+
|:feature:profiles|      |:feature:automate|      | :feature:proxy  |  ... (Other Features)
+--------+--------+      +--------+--------+      +--------+--------+
         |                        |                        |
         +------------------------+------------------------+
                                  |
                                  v
                         +-----------------+
                         |   :core:domain  | <-------------------+
                         +--------+--------+                     |
                                  ^                              |
                                  | (Implements interfaces)      |
                         +--------+--------+                     |
                         |   :core:data    |                     |
                         +---+----+----+---+                     |
                             |    |    |                         |
              +--------------+    |    +---------------+         |
              v                   v                    v         |
     +-----------------+ +-----------------+ +-----------------+ |
     | :core:database  | |  :core:network  | |:integration:root| |
     +--------+--------+ +--------+--------+ +--------+--------+ |
              |                   |                    |         |
              +-------------------+--------------------+         |
                                  |                              |
                                  v                              |
                         +-----------------+                     |
                         |  :core:common   | --------------------+
                         +-----------------+
```

### 2.1 Module Registry and Responsibilities

| Module Name | Type | Responsibility |
| :--- | :--- | :--- |
| `:app` | Android Application | Composition root, Hilt dependency graph aggregation, Application class, top-level Compose Navigation host, system-level initialisers. |
| `:core:common` | Android/Kotlin Library | Shared primitives, custom Result wrappers, Dispatcher providers, datetime utilities, generic extensions, base classes. |
| `:core:domain` | Pure Kotlin Library | Domain business models, Use Cases, Repository interfaces, Domain exception hierarchies. Independent of Android SDK. |
| `:core:data` | Android Library | Repository implementations, data synchronisation logic, data source coordination, mapping between DB/Network and Domain models. |
| `:core:database` | Android Library | Encrypted Room Database, SQLCipher setup, DAOs, Database Entities, TypeConverters, schema migrations. |
| `:core:network` | Android Library | OkHttp client engine, proxy authenticators, interceptors (logging, headers, SSL pinning), connection pooling, network monitor. |
| `:feature:profiles` | Android Library | Browser profile management, fingerprint configurations (User-Agent, screen geometry, WebGL parameters), cookie stores. |
| `:feature:automation`| Android Library | Automation execution engine, action sequencer, script execution, DOM assertion engine, step-by-step runner. |
| `:feature:scheduler` | Android Library | Task scheduling interface, cron expression evaluator, recurring automation triggers, execution calendar. |
| `:feature:proxy` | Android Library | Proxy pool management (HTTP, SOCKS5), latency benchmarking, live rotation policies, authentication binding. |
| `:feature:diagnostics`| Android Library | Execution log capture, real-time performance metrics, crash dumps, DOM snapshot inspector, log exporter. |
| `:feature:settings` | Android Library | Application configurations, encryption key management, storage limits, theme preferences, developer options. |
| `:feature:backup` | Android Library | Encrypted archive export/import of profiles, task routines, and application configurations. |
| `:integration:root` | Android Library | Root shell interface (`libsu`), iptables redirection for transparent proxying, system-level input injection, daemon management. |

---

## 3. Dependency Direction & Boundary Enforcement

The architecture enforces strict compilation boundaries to preserve separation of concerns and prevent circular dependencies.

### 3.1 Inviolable Dependency Rules

1. **Inward-Only Dependencies**: Dependencies point strictly towards the business domain:
   $$\text{Features} \longrightarrow \text{Domain} \longleftarrow \text{Data}$$
2. **Feature Isolation**: No feature module may depend on any other feature module:
   $$\text{:feature:A} \;\not\longleftrightarrow\; \text{:feature:B}$$
   Cross-feature navigation and communication is coordinated via `:app` through deep links, navigation routes, or decoupled domain events.
3. **Domain Independence**: `:core:domain` must never depend on `:core:data`, `:core:database`, `:core:network`, `:feature:*`, or `:app`. It has zero dependencies on `android.*` packages.
4. **Data Isolation**: Feature modules cannot access `:core:database` or `:core:network` directly. All data access must pass through `:core:domain` interfaces implemented by `:core:data`.
5. **Integration Boundaries**: `:integration:root` is isolated and consumable only by `:core:data` or designated feature modules (e.g., `:feature:proxy` for iptables routing).

### 3.2 Module Dependency Matrix

| From \ To | `:app` | `:feature:*` | `:core:domain` | `:core:data` | `:core:database` | `:core:network` | `:core:common` | `:integration:root` |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **`:app`** | - | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| **`:feature:*`** | No | No | Yes | No | No | No | Yes | No |
| **`:core:domain`** | No | No | - | No | No | No | Yes | No |
| **`:core:data`** | No | No | Yes | - | Yes | Yes | Yes | Yes |
| **`:core:database`**| No | No | No | No | - | No | Yes | No |
| **`:core:network`** | No | No | No | No | No | - | Yes | No |
| **`:integration:root`**| No | No | No | No | No | No | Yes | - |
| **`:core:common`** | No | No | No | No | No | No | - | No |

---

## 4. Dependency Injection (Hilt / Dagger)

Dependency Injection is managed globally via Dagger Hilt. Hilt organizes the dependency graphs, lifecycle bindings, and scope boundaries.

```
                      +-----------------------+
                      |  @SingletonComponent  |
                      |  (App-wide Singletons)|
                      +-----------+-----------+
                                  |
         +------------------------+------------------------+
         |                        |                        |
         v                        v                        v
+--------------------+   +--------------------+   +--------------------+
|  @ServiceComponent |   | @ActivityComponent |   |  @ViewModelComponent|
| (AutomationService)|   |   (MainActivity)   |   |    (UI ViewModels) |
+--------------------+   +--------------------+   +--------------------+
```

### 4.1 Component Hierarchies & Scoping Rules

- **`@SingletonComponent`**:
  - Encrypted database instances (`BAutoPilotDatabase`).
  - Network clients (`OkHttpClient`, connection pools).
  - System-wide Dispatcher providers (`CoroutineDispatchers`).
  - Automation engine orchestrator and WebView pool manager.
  - Repository singletons (`ProfileRepositoryImpl`, `AutomationRepositoryImpl`).
- **`@ServiceComponent`**:
  - Service-scoped controllers tied to `AutomationForegroundService`.
  - Notification managers, wake-lock handlers, execution heartbeat listeners.
- **`@ViewModelComponent`**:
  - Injected Use Cases and presentation-layer StateFlow formatters.
  - Scoped to the lifecycle of individual Compose destinations.

### 4.2 Module Provisioning Specifications

- **`CoroutinesModule` (`:core:common`)**:
  - Provides `@IoDispatcher`, `@DefaultDispatcher`, and `@MainDispatcher` qualified coroutine contexts.
  - Binds an `@ApplicationScope` `CoroutineScope` running on a `SupervisorJob()`.
- **`DatabaseModule` (`:core:database`)**:
  - Configures and provides the SQLCipher `SupportFactory`.
  - Builds and provides `BAutoPilotDatabase` using the encrypted passphrase provider.
  - Exposes DAO singletons (`ProfileDao`, `TaskDao`, `ProxyDao`, `LogDao`).
- **`NetworkModule` (`:core:network`)**:
  - Provides base `OkHttpClient` with custom socket factories for SOCKS/HTTP proxy routing.
  - Injects timeout policies, DNS-over-HTTPS resolvers, and certificate pinner configurations.
- **`RepositoryModule` (`:core:data`)**:
  - Binds repository interfaces declared in `:core:domain` to concrete implementations in `:core:data` using `@Binds`.
- **`AutomationModule` (`:feature:automation`)**:
  - Binds the script execution pipeline, DOM evaluation bridges, and step evaluators.

---

## 5. Concurrency & Asynchronous Streams (Coroutines + Flow)

BAutoPilot operates under heavy asynchronous concurrency requirements, executing concurrent browser automation tasks, background proxy validation, real-time log ingestion, and database writes.

### 5.1 Coroutine Dispatcher Contract

All asynchronous operations must explicitly inject and bind to dedicated dispatchers via the `CoroutineDispatchers` abstraction:

```kotlin
interface CoroutineDispatchers {
    val main: CoroutineDispatcher      // UI updates and main-thread operations
    val io: CoroutineDispatcher        // Room DB, disk I/O, OkHttp socket traffic
    val default: CoroutineDispatcher   // Script parsing, JSON transforms, encryption
    val unconfined: CoroutineDispatcher// Testing utilities
}
```

Direct use of `Dispatchers.IO` or `Dispatchers.Default` in domain use cases or repositories is strictly prohibited to guarantee deterministic unit testability via `StandardTestDispatcher`.

### 5.2 Flow Architecture

```
[ Room / Data Sources ] 
        |
        | Cold Flow (emit on change)
        v
[ Repository Layer ] 
        |
        | transform / map / filter
        v
[ Use Case Layer ] 
        |
        | business rule orchestration
        v
[ ViewModel Layer ] 
        |
        | stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InitialState)
        v
[ StateFlow<UiState> ] 
        |
        | collectAsStateWithLifecycle()
        v
[ Compose Screen ]
```

- **Cold Flows**: Used for data observation from Room DAOs, streaming socket logs, and file I/O operations. Emission starts only when collected.
- **StateFlow**: Used for UI state representation in ViewModels. StateFlows are initialized with explicit states and read via `stateIn(scope, SharingStarted.WhileSubscribed(5_000), InitialValue)` to gracefully manage Android configuration changes while conserving system resources when the UI is in the background.
- **SharedFlow**: Used exclusively for one-shot, non-persisted events such as snackbars, transient toast notifications, UI navigation triggers, and biometric authorization prompts (`replay = 0`, `extraBufferCapacity = 1`).

### 5.3 Structured Concurrency & Supervision

- **Automation Execution**: Managed under a dedicated `Job` hierarchy with a `SupervisorJob`. If an individual sub-step or DOM inspection fails or times out, it does not cancel the entire automation coordinator unless explicitly configured by the step's failure policy.
- **Graceful Cancellation**: All long-running automation tasks cooperatively check `isActive` or use `yield()` / `ensureActive()`. Non-cancellable resource teardowns (e.g., releasing a WebView instance back to the pool, flushing encrypted audit logs to disk) are executed within `withContext(NonCancellable)`.

---

## 6. State Management & Persistence

### 6.1 Unidirectional Data Flow (UDF) Loop

The user interface adheres to a strict Unidirectional Data Flow:

```
                  +-----------------------------------------+
                  |                                         |
                  v                                         |
          +---------------+                         +---------------+
          |  Composable   | --- User Action ------> |   ViewModel   |
          |  Screen (UI)  |                         |               |
          +---------------+                         +---------------+
                  ^                                         |
                  |                                         v
                  |                                 +---------------+
                  +---- Collects StateFlow <------- | Domain UseCase|
                        (Immutable UiState)         | / Repository  |
                                                    +---------------+
```

1. The Composable Screen renders the current immutable `UiState`.
2. The user interacts with the UI, which emits a typed `UiEvent` (e.g., `ProfileUiEvent.UpdateProxyConfig`).
3. The ViewModel receives the event, triggers the corresponding Use Case, and dispatches asynchronous processing.
4. The Domain / Data layer processes the request, modifies the database, and emits new data.
5. The ViewModel transforms the incoming model into a new immutable `UiState` via `StateFlow`.
6. The Composable updates atomically based on state differential.

### 6.2 Single Source of Truth (SSOT)

Room acts as the Single Source of Truth across the application. ViewModels never maintain distinct in-memory stores that conflict with the database:
- Write operations (e.g., updating a profile, queuing a task) write directly to the database via Repositories.
- The UI observes Room queries via reactive Flows. The UI updates automatically as a consequence of database mutations.

### 6.3 Room + SQLCipher Encryption Architecture

Data security at rest is guaranteed via 256-bit AES encryption through SQLCipher:

```
+-------------------------------------------------------+
|                 BAutoPilotDatabase                    |
|   (Room abstraction over encrypted SQLite engine)     |
+-------------------------------------------------------+
                           |
                           v
+-------------------------------------------------------+
|             SQLCipher SupportFactory                  |
|  - AES-256-CBC Encryption                             |
|  - Passphrase injected from Android KeyStore          |
|  - Encrypted WAL (Write-Ahead Logging) enabled        |
+-------------------------------------------------------+
                           |
                           v
+-------------------------------------------------------+
|            Encrypted Disk File (bautopilot.db)        |
+-------------------------------------------------------+
```

- **Passphrase Generation & Storage**: The encryption passphrase is cryptographically generated on first launch using `SecureRandom`, encrypted via the Android KeyStore (`MasterKey` with AES-256 GCM), and persisted within `EncryptedSharedPreferences`.
- **Database Hardening**:
  - Write-Ahead Logging (WAL) is enabled for high-throughput concurrent reads and writes.
  - Page size set to 4096 bytes matching Android OS storage pages.
  - Database integrity checks (`PRAGMA integrity_check`) executed on application start before mounting the DAO layer.

---

## 7. Navigation Architecture (Compose Navigation)

Navigation is implemented using Jetpack Compose Navigation with strong type-safety.

```
                           +------------------------+
                           |   AppNavHost (:app)    |
                           +-----------+------------+
                                       |
         +-----------------------------+-----------------------------+
         |                             |                             |
         v                             v                             v
+------------------+         +-------------------+         +------------------+
| profilesGraph()  |         | automationGraph() |         | settingsGraph()  |
| (:feature:prof)  |         | (:feature:auto)   |         | (:feature:sett)  |
+------------------+         +-------------------+         +------------------+
```

### 7.1 Decoupled Feature Routing

To preserve feature module decoupling:
1. Each feature module exposes a navigation graph extension function on `NavGraphBuilder`:
   - `fun NavGraphBuilder.profilesGraph(onNavigateToAutomation: (profileId: String) -> Unit)`
   - `fun NavGraphBuilder.automationGraph(onBack: () -> Unit)`
2. Route definitions are modeled as `@Serializable` data classes or sealed objects located in `:core:common` or exposed as public navigation API contracts per feature.
3. The `:app` module integrates all feature graphs inside the central `AppNavHost` and coordinates cross-feature navigation callbacks. Feature modules never reference destination classes of other feature modules.

---

## 8. Headless Browser & WebView Architecture

BAutoPilot executes sophisticated web automations utilizing Android WebViews. Because the Android WebView subsystem is constrained by Android OS sandboxing, memory boundaries, and single-renderer architectures, a dedicated WebView engine architecture is implemented.

```
+---------------------------------------------------------------------------------------+
|                                 WEBVIEW POOL MANAGER                                  |
|                                                                                       |
|  +-------------------------+   Acquire    +-------------------------+                 |
|  |     Available Pool      | -----------> |       Active Pool       |                 |
|  |  [WebView 1] [WebView 2]|              |  [WebView 3 (Task #42)] |                 |
|  +-------------------------+              +-------------------------+                 |
|               ^                                        |                              |
|               | Cleanse / Reset Profile / Flush Cache  |                              |
|               +----------------------------------------+                              |
|                                Release                                                |
+---------------------------------------------------------------------------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
|                            ANDROID WEBVIEW ENGINE CORE                                |
|                                                                                       |
|   +------------------------------------+   +------------------------------------+     |
|   |   AndroidX WebKit ProfileStore     |   |    Multi-Process IPC Bridge        |     |
|   |  - Isolated Cookie Stores          |   |  - Process: :automation_browser    |     |
|   |  - Profile-specific WebStorage     |   |  - Isolated memory & renderer      |     |
|   |  - Partitioned HTTP Cache          |   |  - Death recipient & auto-restart  |     |
|   +------------------------------------+   +------------------------------------+     |
+---------------------------------------------------------------------------------------+
```

### 8.1 WebView Pool Management & Lifecycle

- **Pre-Warming**: WebViews incur high initialization latency (loading libwebviewchromium.so, GPU raster initialization). The `WebViewPoolManager` maintains a warm pool of pre-instantiated, detached WebViews initialized in a dedicated background UI context.
- **Lifecycle Cleanse Routine**: Before a WebView is acquired for a new automation task or returned to the pool:
  - `loadUrl("about:blank")` is invoked.
  - JavaScript interfaces are unregistered.
  - History is cleared (`clearHistory()`).
  - Cache, DOM storage, and session credentials are wiped or partitioned based on profile requirements.
  - Timers and JavaScript execution are paused (`pauseTimers()`) while the instance is idle in the pool.

### 8.2 Process Isolation & Sandbox

Running multiple automated browser sessions within the main application process creates severe instability:
- A single JavaScript out-of-memory error or native crash (`SIGSEGV` in Chromium) terminates the entire application.
- High memory usage by Blink/V8 triggers Android OS low-memory-killer (LMK).

**Architectural Solution**:
- The WebView automation execution engine runs inside a dedicated, isolated process specified in the Android Manifest:
  ```xml
  android:process=":automation_browser"
  ```
- The main application communicates with the browser automation process via Android AIDL / IPC or bounded local sockets.

### 8.3 Single Renderer Process Limitation & Mitigation

#### The Constraint
On Android, regardless of how many `WebView` instances an application instantiates, the underlying Chromium engine uses a **single shared renderer process** for the application's UID by default. If one WebView encounters an uncaught native crash or hangs due to heavy DOM loops, all WebViews in that process crash simultaneously (`onRenderProcessGone`).

#### Mitigation Strategies
1. **Multi-Process Subservicing**: When concurrent, mutually isolated automations are required, BAutoPilot spawns independent bound services running on distinct processes (`:browser_slot_0`, `:browser_slot_1`), each obtaining an independent Chromium renderer process.
2. **`onRenderProcessGone` Fault Isolation**: Every pooled WebView implements a robust `WebViewRenderProcessClient`. When `onRenderProcessGone` fires:
   - The crash is intercepted and categorized (`didCrash()` vs. memory killer termination).
   - The specific task is marked as failed with a recoverable `RenderProcessTerminatedException`.
   - The dead WebView instance is discarded, memory is purged, and a fresh instance is initialized.
3. **Partitioned Profiles with AndroidX WebKit `ProfileStore`**: On supported Android versions (API 29+ / AndroidX WebKit), BAutoPilot leverages `ProfileStore` to assign distinct browser profiles (`Profile.getName()`) to different WebViews. This guarantees hardware-isolated cookie jars, WebStorage (LocalStorage/IndexedDB), and HTTP cache partitions without requiring manual file-system hacking.

### 8.4 DOM Interaction & Script Bridge

- **Asynchronous Evaluation**: All DOM commands, assertions, and queries are executed via `WebViewCompat.evaluateJavascript` wrapped in suspend functions with configurable timeout mechanics.
- **Bi-directional Bridge**: High-frequency telemetry (DOM element mutations, network requests intercepted via `WebViewClient.shouldInterceptRequest`, console outputs) is streamed back to the automation engine via custom `@JavascriptInterface` channels and parsed asynchronously on `@DefaultDispatcher`.

---

## 9. Service Architecture & Background Execution

BAutoPilot must execute tasks uninterrupted for hours while the device screen is off, the app is backgrounded, or under aggressive OEM battery optimizations.

```
+---------------------------------------------------------------------------------------+
|                               BACKGROUND COORDINATION                                 |
+---------------------------------------------------------------------------------------+
                                           |
         +---------------------------------+---------------------------------+
         | (Immediate / Active Execution)                                    | (Scheduled / Deferred)
         v                                                                   v
+-----------------------------------------+         +-----------------------------------+
|       AutomationForegroundService       |         |            WorkManager            |
|  - android:foregroundServiceType=       |         |  - Periodic / One-Time Requests   |
|         "specialUse"                    |         |  - Constraints: Network, Idle     |
|  - Ongoing Persistent Notification      |         |  - Survives Device Reboots        |
|  - Partial WakeLock Acquisition         |         |  - Triggers Foreground Service    |
|  - Automation Engine Execution Loop     |         |    upon schedule activation       |
+-----------------------------------------+         +-----------------------------------+
```

### 9.1 Foreground Service (`specialUse` Type)

Starting with Android 14 (API level 34), foreground services must declare explicit service types. BAutoPilot uses the `specialUse` foreground service type:

```xml
<service
    android:name=".service.AutomationForegroundService"
    android:foregroundServiceType="specialUse"
    android:exported="false">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Long-running headless browser automation, proxy validation, and task orchestration." />
</service>
```

#### Service Operational Policies
- **Notification Lifecycle**: A persistent, low-priority notification is maintained with real-time task progress metrics (step number, current action, active profile, stop/pause action intents).
- **Power Management**:
  - `PowerManager.WakeLock` (`PARTIAL_WAKE_LOCK`) is acquired at service start and safely released in `onDestroy()` or upon task idle state.
  - Integrates battery optimization exemption requests (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) for unhindered execution on OEM skins.
- **Heartbeat & Watchdog**: An internal watchdog coroutine monitors task responsiveness. If a script blocks the thread or hangs beyond the configured threshold, the watchdog forces a task abort and safe state recovery.

### 9.2 WorkManager for Scheduling & Maintenance

- **Scheduled Automations**: Configured via `CoroutineWorker` implementations (`AutomationExecutionWorker`). When a scheduled execution window triggers, `AutomationExecutionWorker` evaluates constraints (e.g., Wi-Fi connected, battery > 20%) and delegates the active execution by launching `AutomationForegroundService`.
- **System Maintenance Jobs**:
  - `ProxyHealthCheckWorker`: Periodically pings and validates proxy endpoints, measuring latency and updating proxy statuses in Room.
  - `DatabaseMaintenanceWorker`: Runs encrypted database checkpoints, log pruning, and `VACUUM` operations during device charging and idle windows.
  - `BackupWorker`: Performs automated encrypted database exports to local or cloud-synced storage targets.

---

## 10. Data Flow & Execution Sequence Diagrams

### 10.1 User Action to Database Mutation and UI State Update

```
User          Compose UI       ViewModel         UseCase        Repository       Room DAO       StateFlow
 |                |                |                |                |              |               |
 |-[ Tap Toggle ]->|                |                |                |              |               |
 |                |--[ OnEvent ]-->|                |                |              |               |
 |                |                |--[ Execute ]-->|                |              |               |
 |                |                |                |--[ Mutate ]--->|              |               |
 |                |                |                |                |--[ Insert ]->|               |
 |                |                |                |                |   (Encrypt)  |--[ DB Write ] |
 |                |                |                |                |              |      |        |
 |                |                |                |                |              |<-[ Emit Flow]-|
 |                |                |                |<-[ Domain Model Flow ]--------|               |
 |                |                |<-[ Transform & map to UiState ]----------------|               |
 |                |                |-----------------[ Update Value ]------------------------------>|
 |                |<-[ Collect StateFlow Notification ]---------------------------------------------|
 |-[ Re-render ]->|                |                |                |              |               |
```

### 10.2 Automation Engine Execution Sequence

```
ForegroundService     TaskOrchestrator     WebViewPool     IsolatedWebView     DOM Bridge      Diagnostics
       |                      |                 |                 |                 |               |
       |--[ Start Task ]----->|                 |                 |                 |               |
       |                      |--[ Acquire ]--->|                 |                 |               |
       |                      |<-[ Return Wv ]--|                 |                 |               |
       |                      |                                   |                 |               |
       |                      |--[ Configure Proxy & Profile ]--->|                 |               |
       |                      |--[ Load Target URL ]------------->|                 |               |
       |                      |                                   |--[ OnLoadDone ]>|               |
       |                      |<-[ Page Ready Event ]-------------------------------|               |
       |                      |                                                     |               |
       |                      |--[ Execute Step: Evaluate DOM Selector ]----------->|               |
       |                      |                                                     |--[ Script ]-->|
       |                      |<-[ Selector Result / Element Bounds ]---------------|               |
       |                      |                                                                     |
       |                      |--[ Log Step Success & Latency ]------------------------------------>|
       |                      |                                                                     |
       |                      |--[ Release & Reset ]------------->|                                 |
       |                      |-----------------[ Recycle ]------>|                                 |
       |<-[ Task Complete ]---|                                                                     |
```

### 10.3 Scheduled Job Activation via WorkManager

```
Android OS          WorkManager       ExecutionWorker    ForegroundService    AutomationEngine
    |                      |                 |                   |                   |
    |-[ Alarm / Trigger ]->|                 |                   |                   |
    |                      |--[ StartWork ]->|                   |                   |
    |                      |                 |--[ Check Consts ]-|                   |
    |                      |                 |--[ StartService ]>|                   |
    |                      |                 |    (Foreground)   |--[ Launch Task ]->|
    |                      |                 |                   |                   |--[ Execute ]
    |                      |                 |<--[ Bind / Status ]-------------------|
    |                      |<--[ Result.success() ]              |                   |
```

### 10.4 Root-Privileged Transparent Proxy Routing (`:integration:root`)

```
FeatureProxy           Data Layer           RootBridge (:integration:root)      Linux Kernel (iptables)
     |                     |                              |                                |
     |--[ Enable Route ]-->|                              |                                |
     |                     |--[ Request Transparent Redir]|                                |
     |                     |                              |--[ su -c iptables-restore ]--->|
     |                     |                              |                                |--[ Redirect UID
     |                     |                              |                                |    Traffic to
     |                     |                              |<--[ Shell Return Code 0 ]------|    Local SOCKS ]
     |                     |<--[ Routing Confirmed ]------|                                |
     |<--[ Status: Active ]|
```

---

## 11. Error Handling & Domain Resilience

The architecture bans unchecked runtime exceptions across architectural boundaries. Uncaught errors must be converted into typed domain results.

### 11.1 Functional Result Hierarchy

A robust functional Result type models outcome states across all layers:

```kotlin
sealed interface AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>
    data class Error(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Error -> this
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onError(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Error) action(error)
    return this
}
```

### 11.2 Domain Exception & Error Taxonomies

Errors are modeled as exhaustive sealed class hierarchies rooted in `AppError`:

```
                           +-------------------+
                           |     AppError      |
                           +---------+---------+
                                     |
         +---------------------------+---------------------------+
         |                           |                           |
         v                           v                           v
+------------------+       +-------------------+       +--------------------+
| AutomationError  |       |   NetworkError    |       |   DatabaseError    |
+--------+---------+       +---------+---------+       +----------+---------+
         |                           |                            |
         |-- Timeout                 |-- ProxyConnectionRefused   |-- DiskFull
         |-- ElementNotFound         |-- DnsResolutionFailure     |-- EncryptionKeyCorrupt
         |-- ScriptExecutionFailed   |-- SslHandshakeFailed       |-- MigrationFailed
         |-- RenderProcessCrashed    |-- SocketTimeout            |-- ConstraintViolation
         |-- CaptchaDetected         |-- RateLimited              +--------------------+
         +------------------+        +-------------------+
```

### 11.3 Boundary Exception Translation Rules

- **Data to Domain Boundary**:
  - `SQLiteException` / `SQLCipherException` $\longrightarrow$ Translated into `DatabaseError.DatabaseCorruption` or `DatabaseError.DiskFull`.
  - `java.io.IOException` / `SocketTimeoutException` $\longrightarrow$ Translated into `NetworkError.SocketTimeout`.
  - Native WebView crashes (`onRenderProcessGone`) $\longrightarrow$ Translated into `AutomationError.RenderProcessCrashed`.
- **Domain to Presentation Boundary**:
  - The Presentation layer consumes `AppError` and determines user-facing representations. It does not inspect raw stack traces.
  - Errors are rendered into either a dedicated `ErrorState` composable or a transient UI effect (Snackbar / Dialog) depending on recoverability.

---

## 12. Technology Stack & Specifications

### 12.1 Core Technology Matrix

| Category | Technology | Specification / Version Target | Architectural Purpose |
| :--- | :--- | :--- | :--- |
| **Language** | Kotlin | 2.0+ (K2 Compiler enabled) | Primary language; strict null-safety, coroutines, sealed models. |
| **UI Framework** | Jetpack Compose | Modern BOM (Compiler 2.0+) | Modern, declarative, reactive user interface. |
| **Design System** | Material 3 | AndroidX Compose Material3 | Adaptive enterprise styling, dark theme, dynamic color. |
| **Architecture** | AndroidX Lifecycle | ViewModel 2.8+, Lifecycle-Runtime | Survives process reconfiguration; UDF state management. |
| **Dependency Injection** | Dagger Hilt | 2.51+ | Compile-time dependency injection and scoping. |
| **Local Persistence** | Room Database | 2.6+ | ORM, Single Source of Truth, reactive Flow queries. |
| **Database Encryption** | SQLCipher for Android | 4.5+ (Zetetic) | 256-bit AES encryption at rest for database and WAL files. |
| **Concurrency** | Kotlin Coroutines | 1.8+ | Non-blocking execution, structured concurrency. |
| **Reactive Streams** | Kotlin Flow | StateFlow, SharedFlow | Unidirectional reactive streaming and state propagation. |
| **Networking** | OkHttp | 4.12+ / 5.0 alpha | Low-level HTTP/2 & HTTP/3 engine, proxy chaining, TLS config. |
| **Background Processing** | AndroidX WorkManager | 2.9+ (HiltWorker integration) | Guaranteed execution for deferred and recurring schedules. |
| **Foreground Engine** | Android Core Service | Android 14+ (`specialUse`) | Uninterrupted foreground automation orchestration. |
| **Browser Subsystem** | AndroidX WebKit | 1.11+ (`WebViewCompat`) | Multi-profile management (`ProfileStore`), headless DOM bridge. |
| **Root Integration** | libsu | 6.0+ (TopJohnWu) | Superuser shell execution bridge, root detection, iptables control.|
| **Key Management** | Android KeyStore | AES-256 GCM MasterKey | Hardware-backed cryptographic key generation and storage. |
| **Serialization** | Kotlinx Serialization | 1.6+ (JSON format engine) | Strict, reflection-free JSON decoding/encoding for configs. |

### 12.2 Android Platform Compatibility Targets

- **Minimum SDK (`minSdk`)**: API 26 (Android 8.0 Oreo) - Guarantees baseline support for modern networking, background service policies, and WebKit capabilities.
- **Target SDK (`targetSdk`)**: API 34 / 35 (Android 14 / 15) - Enforces full compliance with foreground service types (`specialUse`), strict runtime permissions, predictive back gestures, and 16KB memory page alignment.
- **Architectural ABI Targets**: `arm64-v8a`, `armeabi-v7a`, `x86_64` (fully optimized for 64-bit real hardware and development emulators).

---

## 13. Quality Assurance & Architectural Verification

To prevent architectural drift and guarantee code quality across development cycles, the following structural assertions are enforced:

1. **Architecture Linter (Konsist / ArchUnit)**:
   - Validates that classes in `:core:domain` have no dependencies on `android.*`.
   - Asserts that all ViewModel classes expose only immutable `StateFlow` to composables.
   - Verifies that no feature module references any other feature module.
2. **Deterministic Test Isolation**:
   - Every Domain Use Case is covered by unit tests using `kotlinx-coroutines-test` and mock/fake repositories.
   - Repository implementations are verified using in-memory encrypted Room instances (`SupportFactory(passphrase)`).
   - WebView automations are tested against a local MockWebServer executing pre-compiled DOM fixtures.

---

## 14. Phase 3 Architecture: Background Execution & Scheduling

Phase 3 introduces an end-to-end background scheduling and execution system integrated seamlessly into the Clean Architecture structure:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PRESENTATION LAYER                              │
│   ScheduleDashboardScreen (Filter tabs, Switch, Actions)               │
│   ScheduleEditorScreen (Name, Profile Picker, Presets, Limits)         │
│   ScheduleViewModel (StateFlow<ScheduleDashboardState>)                │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                           DOMAIN LAYER                                 │
│   Domain Models: AutomationSchedule, ScheduleType, RecurrenceRule      │
│   ScheduleCalculator (Pure Kotlin, DST/Leap-year/Month-end clamping)   │
│   ScheduleValidator (Name, Existence, Future Time, Boundaries)         │
│   Repository Interface: ScheduleRepository                             │
└───────────────────────────────────▲────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                            DATA LAYER                                  │
│   ScheduleEntity (FK -> automation_profiles(id) ON DELETE CASCADE)     │
│   ScheduleDao (Reactive Flow queries, status transitions)              │
│   ScheduleRepositoryImpl (Room Data Source implementation)             │
│   AutoPilotDatabase (v2 schema, MIGRATION_1_2)                         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                       FEATURE / SYSTEM LAYER                           │
│   AutomationScheduler (AlarmManager RTC_WAKEUP, exact/inexact)         │
│   ScheduleReceiver (Handles com.autopilot.android.action.TRIGGER)       │
│   BootCompletedReceiver (BOOT_COMPLETED & MY_PACKAGE_REPLACED recovery)│
│   ScheduleReconciliationWorker (Periodic 2-hour WorkManager maintenance│
│   ScheduleNotificationManager (autopilot_schedule_channel alerts)      │
│   AutomationService (Foreground Service with specialUse type)          │
└────────────────────────────────────────────────────────────────────────┘
```

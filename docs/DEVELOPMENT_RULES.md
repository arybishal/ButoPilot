# BAutoPilot Development Rules

This document establishes the mandatory standards, architectural constraints, and engineering guidelines for the **BAutoPilot** Android project. Every contributor, team member, and automated workflow must adhere to these rules.

---

## Table of Contents
1. [Language & Platform Specifications](#1-language--platform-specifications)
2. [Architecture & Modularization](#2-architecture--modularization)
3. [Coding Standards & Code Quality](#3-coding-standards--code-quality)
4. [Concurrency & Threading](#4-concurrency--threading)
5. [Database & Persistence (Room)](#5-database--persistence-room)
6. [Git & Branching Workflow](#6-git--branching-workflow)
7. [Testing & Quality Assurance](#7-testing--quality-assurance)
8. [Security & Privacy](#8-security--privacy)
9. [Performance & Resource Optimization](#9-performance--resource-optimization)
10. [Documentation & Architectural Decisions](#10-documentation--architectural-decisions)

---

## 1. Language & Platform Specifications

- **Language:** Kotlin 2.x only.
  - **No Java:** Zero Java code permitted in new or refactored components. All source files must be `.kt`.
- **Target SDK:** `37`
- **Compile SDK:** `37`
- **Min SDK:** `26` (Android 8.0 Oreo)
- **Toolchain:** Kotlin JVM Target 17+ aligned with Android Gradle Plugin (AGP) and Kotlin 2.x compiler configurations (K2 compiler enabled).

---

## 2. Architecture & Modularization

The application strictly follows **Clean Architecture** with unidirectional data flow (UDF).

```
┌───────────────────────────────────────────────┐
│              Presentation Layer               │
│   Jetpack Compose UI  ◄──►  Hilt ViewModels   │
└───────────────────────┬───────────────────────┘
                        │ depends on
                        ▼
┌───────────────────────────────────────────────┐
│                 Domain Layer                  │
│       Pure Kotlin: Models & Use Cases         │
└───────────────────────▲───────────────────────┘
                        │ depends on
                        │
┌───────────────────────┴───────────────────────┐
│                  Data Layer                   │
│   Room DB, Network, DataStores, Repositories  │
└───────────────────────────────────────────────┘
```

- **Clean Architecture Boundaries:**
  - **Presentation Layer:** Jetpack Compose screens, components, and ViewModels. Depends strictly on Domain.
  - **Domain Layer:** Business models, repository interfaces, and use cases. **Pure Kotlin only:** Zero Android SDK dependencies (`android.*`, `Context`, Android resources).
  - **Data Layer:** Repository implementations, Room databases, network datasources, and local storage. Depends on Domain to fulfill repository interfaces.
- **Cross-Feature Dependencies:**
  - Cross-feature module dependencies are **strictly forbidden**.
  - Inter-feature communication must proceed via shared domain contracts, core navigation abstractions, or deep links.
- **Dependency Injection:**
  - Google **Hilt** is the mandatory DI framework across all layers.
  - Standard annotations required: `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `@Inject`, `@Module`, and `@InstallIn`.
- **Reactive State:**
  - Use `StateFlow` (for state holders) and `SharedFlow` (for one-off events).
  - **No LiveData:** `LiveData` is legacy and strictly prohibited.
- **Single Activity Architecture:**
  - Exactly one host Activity: `MainActivity`.
  - Screen transitions must use **Compose Navigation** with type-safe route definitions.

---

## 3. Coding Standards & Code Quality

- **Conventions:** Follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- **Static Analysis & Formatting:**
  - `ktlint` and `detekt` run on every build and CI invocation.
  - Code failing linting or detekt checks will be blocked from merging.
- **Strict Size Limits:**
  - **Function limit:** Maximum **30 lines** per function. Break down complex routines into cohesive helper functions.
  - **File limit:** Maximum **400 lines** per file (including imports and KDoc). Decompose large files into distinct modular components.
- **Documentation (KDoc):**
  - All public APIs, public interfaces, domain use cases, and repository contracts must include descriptive KDoc comments detailing parameters, return values, and behavior.
- **Resource Management:**
  - **No hardcoded strings:** All user-facing text must be declared in `strings.xml` and referenced via `stringResource()` or localized providers.
  - **No hardcoded colors/dimensions:** All styling must consume design tokens from the Compose `MaterialTheme` color scheme, typography, and shape definitions.

---

## 4. Concurrency & Threading

- **Coroutines Only:**
  - Raw `Thread`, `Handler`, `AsyncTask`, and `java.util.concurrent.Executors` are strictly prohibited.
- **Dispatcher Discipline:**
  - `Dispatchers.Main` / `Dispatchers.Main.immediate`: Exclusively for UI rendering and emitting state to Compose.
  - `Dispatchers.IO`: Mandatory for all disk I/O, Room transactions, file operations, and network requests.
  - `Dispatchers.Default`: Mandatory for CPU-intensive work (JSON serialization, large data transformations, cryptographic operations).
  - **Never block Main:** Blocking calls (e.g., `Thread.sleep()`, synchronous file/DB queries) on the Main dispatcher will cause immediate PR rejection.
- **Structured Concurrency:**
  - Coroutines must be launched within managed scopes (`viewModelScope`, `lifecycleScope`). Never use `GlobalScope`.
  - Root scopes and parallel tasks must employ `SupervisorJob` so that child task failures do not inadvertently cancel unrelated sibling coroutines.
  - Respect cooperative cancellation: check `isActive` or invoke `ensureActive()` / `yield()` in iterative background routines.

---

## 5. Database & Persistence (Room)

- **Access Pattern:**
  - All SQLite operations must be routed through Room Data Access Objects (`@Dao`).
  - No unvalidated raw SQLite queries in presentation or business logic.
- **Migrations:**
  - Explicit Room `Migration` definitions are required for every schema modification.
  - `fallbackToDestructiveMigration()` is **strictly prohibited in release builds**. Destructive fallbacks are only allowed in local test suites.
- **Reactive Streaming:**
  - DAO read queries must return Kotlin `Flow<T>` or `Flow<List<T>>` for continuous observable state updates.
- **Transactions:**
  - Multi-step mutations or atomic batch updates must use `@Transaction` or `RoomDatabase.withTransaction {}`.
- **Indexing:**
  - Every foreign key constraint (`@ForeignKey`) must have a corresponding index (`@Index`) declared on the child entity to avoid full table scans.

---

## 6. Git & Branching Workflow

- **Branch Naming Standard:**
  - `feature/<ticket-or-feature-name>`: New capabilities or functional additions.
  - `bugfix/<ticket-or-bug-name>`: Bug fixes against development branches.
  - `hotfix/<ticket-or-issue-name>`: Emergency patches intended for immediate release.
  - `docs/<description>`: Documentation additions and updates.
- **Commit Messages:**
  - Must follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:
    - `feat(<scope>): <short summary>`
    - `fix(<scope>): <short summary>`
    - `refactor(<scope>): <short summary>`
    - `test(<scope>): <short summary>`
    - `docs(<scope>): <short summary>`
    - `perf(<scope>): <short summary>`
    - `chore(<scope>): <short summary>`
- **Pull Request Rules:**
  - Direct pushes to `main` and protected release branches are forbidden.
  - Every change requires a Pull Request with at least one approving code review.
  - **Merge Strategy:** **Squash and merge** is mandatory to maintain a clean, linear git history.
- **Repository Hygiene (.gitignore):**
  - Binary artifacts, build outputs, keystores, and local environment files must never be checked into Git.
  - Verify that `build/`, `.gradle/`, `*.apk`, `*.aab`, `local.properties`, `*.jks`, `*.keystore`, and `.idea/` are ignored.

---

## 7. Testing & Quality Assurance

- **Coverage Mandate:**
  - All public functions in the **Domain** (use cases, business rules) and **Data** (repositories, data sources, mappers) layers must be covered by automated unit tests.
- **CI Gate:**
  - The automated CI pipeline executes:
    1. Static analysis (`ktlintCheck`, `detekt`)
    2. Unit test suites (`testDebugUnitTest`)
    3. Lint checks (`lintDebug`)
  - Any CI failure blocks PR merge.
- **PR Test Plan:**
  - Every Pull Request description must include a clear **Test Plan** specifying:
    - Automated tests added/modified.
    - Manual verification steps performed with device/OS details.
- **Test Double Strategy:**
  - **Fakes over Mocks:** Prefer in-memory fake implementations (e.g., `FakeUserRepository`) over mock libraries (`Mockito`/`MockK`).
  - Mocks should be reserved exclusively for external third-party SDK boundaries that cannot be faked cleanly.

---

## 8. Security & Privacy

- **No Secrets in Code:**
  - Zero API keys, private certificates, encryption secrets, or auth tokens in source control.
  - Inject secrets at build time via CI environment variables, Gradle properties, or Google Cloud Secret Manager.
- **No Credential Logging:**
  - Sensitive data (passwords, auth tokens, session cookies, PII) must never be logged to Logcat, Timber, or external analytics.
- **Encrypted Storage:**
  - All sensitive user data, auth tokens, and session credentials must be stored using AndroidX `EncryptedSharedPreferences` / `EncryptedFile` (Jetpack Security) backed by the Android Keystore.
- **Input Validation:**
  - Validate and sanitize all external inputs at application boundaries: user input fields, deep link URIs, Intent extras, WebView JavaScript bridges, and REST/WebSocket payloads.
- **No Dynamic / Eval Code Execution:**
  - Dynamic code evaluation (`eval()`, runtime DEX loading via `DexClassLoader` from external/network sources) is strictly prohibited.

---

## 9. Performance & Resource Optimization

- **Profile Before Optimizing:**
  - Premature optimization is discouraged. Base performance refactoring on profiling telemetry captured via Android Studio Profiler, Perfetto, or Macrobenchmark.
- **Lazy Initialization:**
  - Defer expensive object construction until needed using `lazy {}`, Compose `remember {}`, and deferred Hilt provider bindings.
- **WebView Management:**
  - When WebViews are required, use a managed **WebView pool** or lifecycle-aware recycling strategy to prevent memory leaks and eliminate repeated cold-start initialization latency.
- **Query Pagination:**
  - Large data sets from Room or network sources must be paginated using AndroidX `Paging 3` or cursor/limit-offset queries with Flow.
- **List Optimization (Compose & Views):**
  - In Compose `LazyColumn` and `LazyRow`, always provide stable and unique keys (`items(items, key = { it.id })`).
  - For any legacy `RecyclerView` use case, `DiffUtil` or `ListAdapter` is mandatory to minimize unnecessary recomposition/redraw cycles.

---

## 10. Documentation & Architectural Decisions

- **Architectural Synchronicity:**
  - Any PR that introduces, deprecates, or restructures architectural components must update relevant architecture diagrams and documentation in `/docs`.
- **Architecture Decision Records (ADR):**
  - Significant architectural decisions, structural patterns, or technology choices must be recorded in `docs/DECISIONS.md` using the ADR format (Context, Decision, Consequences, Status).
- **Changelog Maintenance:**
  - Record all public API additions, breaking contract changes, database schema upgrades, and milestone releases in `CHANGELOG.md`.

# BAutoPilot Test Strategy & Quality Assurance Framework

## 1. Executive Summary & Testing Pyramid

BAutoPilot is an advanced Android browser automation platform combining Jetpack Compose UI, Room persistence, background orchestration via WorkManager and Foreground Services, elevated/root shell execution capabilities, and embedded WebView automation.

To ensure deterministic reliability, zero flaky executions, and robust fault-tolerance, the testing strategy adopts an adapted **Testing Pyramid**:

```
                 / \
                /   \
               / E2E \       10% UI & End-to-End Tests
              / & UI  \      (Compose UI, Journey Verification, Screenshots)
             /---------\
            /           \
           / Integration \   20% Integration & Subsystem Tests
          /    Tests      \  (Room In-Memory, WorkManager, Services, Proxy Mocks)
         /-----------------\
        /                   \
       /     Unit Tests      \  70% Unit Tests
      /                       \ (JUnit 5, MockK, Domain Logic, Parsers, State Machines)
     /-------------------------\
```

### 1.1 Target Distribution & Metrics

| Tier | Distribution Target | Execution Scope | Primary Tools | Coverage Goal | Execution Speed |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Unit** | **70%** | Local JVM | JUnit 5, MockK, Coroutines Test, Turbine | ≥ 80% Core/Domain | ~Seconds |
| **Integration** | **20%** | Local JVM (Robolectric) & On-Device (`androidTest`) | Room In-Memory, `TestWorkerBuilder`, MockWebServer, Hilt | ≥ 70% Data/Engine | ~1–3 Minutes |
| **UI / E2E** | **10%** | Android Emulators / Physical Devices | Compose UI Test, Roborazzi / Paparazzi, Macrobenchmark | Critical Journeys | ~5–10 Minutes |

---

## 2. Unit Testing Strategy (70%)

Unit testing guarantees that domain logic, business rules, execution state machines, and mathematical/evaluative algorithms function correctly in complete isolation without launching Android system runtimes.

### 2.1 Tooling & Infrastructure
- **Test Framework**: JUnit 5 (`org.junit.jupiter:junit-jupiter`)
- **Mocking Library**: MockK (`io.mockk:mockk`)
- **Coroutines & Flows**: `kotlinx-coroutines-test` (`StandardTestDispatcher`, `runTest`), Turbine (`app.cash.turbine:turbine`)
- **Assertions**: Google Truth (`com.google.truth:truth`)

### 2.2 Core Coverage Target
All core domain models, parsers, and engines under `:core:domain`, `:core:model`, and `:engine:parser` must enforce **≥ 80% line and branch coverage** enforced via JaCoCo in CI.

### 2.3 Focus Areas & Reference Implementations

#### A. Action Parsing & Execution Step Deserialization
Verify JSON/DSL serialization and deserialization of automated steps (Click, Type, Wait, Scroll, EvaluateScript), including validation bounds and graceful error handling on malformed syntax.

```kotlin
@Test
fun `parseAction correctly deserializes click action with valid selector`() = runTest {
    val json = """
        {
            "type": "CLICK",
            "selector": "#submit-btn",
            "timeoutMs": 5000,
            "optional": false
        }
    """.trimIndent()

    val action = ActionParser.parse(json)

    assertThat(action).isInstanceOf(Action.Click::class.java)
    val clickAction = action as Action.Click
    assertThat(clickAction.selector).isEqualTo("#submit-btn")
    assertThat(clickAction.timeoutMs).isEqualTo(5000L)
    assertThat(clickAction.optional).isFalse()
}

@Test
fun `parseAction throws InvalidActionSchemaException on missing mandatory fields`() {
    val invalidJson = """{"type": "CLICK"}"""

    val exception = assertThrows<InvalidActionSchemaException> {
        ActionParser.parse(invalidJson)
    }
    assertThat(exception.message).contains("Missing mandatory selector")
}
```

#### B. Cron Parsing & Scheduling Math
Validate cron evaluation algorithms, leap-year calculations, timezone conversions, and next-run instant generation.

```kotlin
@ParameterizedTest
@CsvSource(
    "0 0 * * * , 2026-11-22T10:15:00Z, 2026-11-23T00:00:00Z",
    "*/15 * * * *, 2026-11-22T10:14:59Z, 2026-11-22T10:15:00Z",
    "0 0 29 2 * , 2026-11-22T00:00:00Z, 2028-02-29T00:00:00Z"
)
fun `evaluateNextExecution calculates correct UTC timestamp`(
    cronExpression: String,
    currentInstant: Instant,
    expectedNextInstant: Instant
) {
    val scheduler = CronEvaluator(ZoneOffset.UTC)
    val next = scheduler.computeNextExecution(cronExpression, currentInstant)
    assertThat(next).isEqualTo(expectedNextInstant)
}
```

#### C. Proxy Validation & Protocol Parsing
Ensure IP/Host parsing, Port range enforcement (1–65535), scheme verification (HTTP, HTTPS, SOCKS4, SOCKS5), and credentials extraction are robust.

```kotlin
@Test
fun `parseProxyConfig extracts credentials and endpoint correctly`() {
    val raw = "socks5://operator:s3cretToken@192.168.1.100:1080"
    val proxy = ProxyValidator.parseAndValidate(raw)

    assertThat(proxy.protocol).isEqualTo(ProxyProtocol.SOCKS5)
    assertThat(proxy.host).isEqualTo("192.168.1.100")
    assertThat(proxy.port).isEqualTo(1080)
    assertThat(proxy.auth?.username).isEqualTo("operator")
    assertThat(proxy.auth?.password).isEqualTo("s3cretToken")
}

@ParameterizedTest
@ValueSource(strings = [
    "http://127.0.0.1:99999",     // Out of range port
    "invalid-scheme://localhost:8080", // Unsupported protocol
    "http://:8080",              // Empty host
    ""                           // Empty string
])
fun `parseProxyConfig rejects invalid proxy strings`(invalidInput: String) {
    assertThrows<IllegalArgumentException> {
        ProxyValidator.parseAndValidate(invalidInput)
    }
}
```

#### D. Variable Interpolation & Dynamic Template Replacement
Support expressions like `{{profile.name}}`, `{{env.TIMESTAMP}}`, and variable indexing with error isolation.

```kotlin
@Test
fun `interpolateVariables substitutes known keys and escapes control sequences`() {
    val template = "Hello {{profile.user}}, run timestamp is {{sys.date}} with symbol {{missing|default_val}}"
    val context = mapOf(
        "profile.user" to "AlphaBot",
        "sys.date" to "2026-11-22"
    )

    val result = VariableInterpolator.interpolate(template, context)

    assertThat(result).isEqualTo("Hello AlphaBot, run timestamp is 2026-11-22 with symbol default_val")
}
```

#### E. State Machine & Lifecycle Reducer Testing
Verify execution engine transitions using Turbine on emitted Kotlin StateFlows.

```kotlin
@Test
fun `execution engine state machine transitions deterministically`() = runTest {
    val engine = AutomationStateMachine(initialState = ExecutionState.Idle)

    engine.state.test {
        assertThat(awaitItem()).isEqualTo(ExecutionState.Idle)

        engine.processEvent(ExecutionEvent.Initialize(taskId = "task_101"))
        assertThat(awaitItem()).isEqualTo(ExecutionState.Initializing(taskId = "task_101"))

        engine.processEvent(ExecutionEvent.StartExecution)
        assertThat(awaitItem()).isInstanceOf(ExecutionState.Running::class.java)

        engine.processEvent(ExecutionEvent.Complete(success = true))
        assertThat(awaitItem()).isInstanceOf(ExecutionState.Finished::class.java)
        
        cancelAndIgnoreRemainingEvents()
    }
}
```

---

## 3. Integration Testing Strategy (20%)

Integration testing verifies interactions between application layers, persistence engines, system frameworks, and third-party modules.

### 3.1 Room In-Memory Database Tests
Test relational database entities, Foreign Key constraints, cascade operations, and complex queries using in-memory Room SQLite instances.

```kotlin
@RunWith(AndroidJUnit4::class)
class AutomationProfileDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var profileDao: ProfileDao
    private lateinit var taskDao: TaskDao

    @Before
    fun initDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        profileDao = database.profileDao()
        taskDao = database.taskDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertProfile_andCascadeDeleteTasks() = runTest {
        val profile = ProfileEntity(id = "p1", name = "Test Profile", proxyId = null)
        profileDao.insert(profile)

        val task = TaskEntity(id = "t1", profileId = "p1", actionPayload = "{}")
        taskDao.insert(task)

        assertThat(taskDao.getTasksForProfile("p1")).hasSize(1)

        // Delete profile and check cascade constraint
        profileDao.delete(profile)
        assertThat(taskDao.getTasksForProfile("p1")).isEmpty()
    }
}
```

### 3.2 WorkManager Background Integration
Use `WorkManagerTestInitHelper` and `TestListenableWorkerBuilder` to validate asynchronous task orchestration and periodic execution.

```kotlin
@RunWith(AndroidJUnit4::class)
class ScheduledExecutionWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun scheduledWorker_executesSuccessfullyUnderValidConstraints() = runTest {
        val worker = TestListenableWorkerBuilder<ScheduledExecutionWorker>(context)
            .setInputData(workDataOf("SCHEDULE_ID" to "sched_42"))
            .build()

        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }
}
```

### 3.3 Service Lifecycle & Foreground Worker
Validate that the automation foreground service properly triggers notification creation, binds lifecycle listeners, responds to `STOP` actions, and releases wakelocks.

### 3.4 Network & Proxy Mocking
Use `okhttp3.mockwebserver.MockWebServer` to validate proxy handshakes, TLS renegotiation, authentication challenges (HTTP 407 Proxy Authentication Required), and network timeout recovery.

```kotlin
@Test
fun `proxy client handles 407 Proxy Authentication challenge`() = runTest {
    val server = MockWebServer()
    server.enqueue(
        MockResponse()
            .setResponseCode(407)
            .setHeader("Proxy-Authenticate", "Basic realm=\"Access to internal bot proxy\"")
    )
    server.enqueue(MockResponse().setResponseCode(200).setBody("AUTHENTICATED_RESPONSE"))
    server.start()

    val client = ProxyHttpClientFactory.create(
        proxyHost = server.hostName,
        proxyPort = server.port,
        username = "botUser",
        password = "secretPassword"
    )

    val response = client.get("http://example.com/check-ip")
    assertThat(response.body?.string()).isEqualTo("AUTHENTICATED_RESPONSE")
    server.shutdown()
}
```

### 3.5 Hilt Test Modules
Replace external dependencies with deterministic fakes using `@TestInstallIn`.

```kotlin
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [ProxyEngineModule::class]
)
abstract class FakeProxyEngineModule {
    @Binds
    @Singleton
    abstract fun bindProxyEngine(fake: FakeProxyEngine): ProxyEngine
}
```

---

## 4. UI & End-to-End Testing Strategy (10%)

UI and E2E tests guarantee user journeys remain seamless across dynamic Compose rendering, navigation graphs, and user interactions.

### 4.1 Key User Journeys
The E2E suite tests 3 critical workflows:

```mermaid
journey
    title Critical User Journeys
    section Journey 1: Execution Pipeline
      Create automation profile: 5: User
      Add action steps: 4: User
      Trigger execution: 5: User
      Inspect execution output: 5: User
    section Journey 2: Proxy Verification
      Configure proxy credentials: 4: User
      Test proxy latency: 3: User, System
      Assign proxy to profile: 5: User
    section Journey 3: Scheduling Management
      Create cron schedule: 4: User
      Verify next execution: 5: System
      Pause or cancel schedule: 5: User
```

#### Implementation: Journey 1 (Create Profile → Add Actions → Run → View Results)
```kotlin
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AutomationExecutionJourneyTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun createProfile_addActions_execute_andDisplaySuccessState() {
        // 1. Create Profile
        composeTestRule.onNodeWithContentDescription("Create Profile Button").performClick()
        composeTestRule.onNodeWithTag("ProfileNameInput").performTextInput("ShopBot")
        composeTestRule.onNodeWithText("Save").performClick()

        // 2. Add Actions
        composeTestRule.onNodeWithText("ShopBot").performClick()
        composeTestRule.onNodeWithContentDescription("Add Action").performClick()
        composeTestRule.onNodeWithText("Click Element").performClick()
        composeTestRule.onNodeWithTag("SelectorInput").performTextInput("#checkout")
        composeTestRule.onNodeWithText("Confirm Action").performClick()

        // 3. Run Automation
        composeTestRule.onNodeWithContentDescription("Run Automation").performClick()

        // 4. Verify Execution Results
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Execution Completed").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Execution Completed").assertIsDisplayed()
    }
}
```

### 4.2 Screenshot & Visual Regression Testing
- **Library**: Roborazzi / Paparazzi
- **Scenarios**:
  - Dark Mode and Light Mode theme conformance.
  - Multi-window and orientation changes (Portrait vs Landscape).
  - Accessibility font scaling (1.0x, 1.5x, 2.0x).
- **Execution**:
  `./gradlew recordRoborazziDebug` to record golden images;
  `./gradlew verifyRoborazziDebug` to verify in CI. Discrepancy threshold set to **< 0.1% pixel diff**.

---

## 5. WebView Automation Testing

Because BAutoPilot relies heavily on Android `WebView` to inject scripts and manipulate the DOM, tests must validate rendering and JavaScript bridges deterministically.

### 5.1 Local Sealed HTML Assets
Tests never make external internet calls. All DOM scenarios are driven through local HTML fixtures loaded via `file:///android_asset/test_pages/` or `androidx.webkit.WebViewAssetLoader`.

```
src/androidTest/assets/test_pages/
├── dom_actions.html        # Form elements, buttons, nested containers
├── dynamic_rendering.html  # React/Vue style virtual DOM & async mutations
├── shadow_dom.html         # Shadow roots and isolated web components
├── auth_challenge.html     # Basic/Form authentication mock forms
└── timeout_stress.html     # Heavy scripts and infinite loops
```

### 5.2 Action Verification against Known DOM
Verify that the `WebViewBridge` executes queries, injects clicks, and types text accurately:

```kotlin
@Test
fun webViewBridge_dispatchesClickToElementInDOM() = runTest {
    val scenario = ActivityScenario.launch(WebViewTestActivity::class.java)
    scenario.onActivity { activity ->
        val webView = activity.findViewById<WebView>(R.id.test_webview)
        val bridge = AutomationWebViewBridge(webView)

        activity.loadAssetUrl("file:///android_asset/test_pages/dom_actions.html")
        
        bridge.waitForElement("#target-button", timeoutMs = 3000)
        bridge.clickElement("#target-button")

        bridge.evaluateScript("document.getElementById('result-output').innerText") { result ->
            assertThat(result).isEqualTo("\"BUTTON_CLICKED\"")
        }
    }
}
```

### 5.3 JavaScript Execution & Return Value Serialization
- Verify Promises resolution in `evaluateJavascript`.
- Verify extraction of JSON payloads returned by DOM query scripts.

### 5.4 Timeout, Error & Recovery Handling
- **Dead-man Switches**: Ensure script execution times out after user-specified `timeoutMs`.
- **Page Load Failures**: Intercept `onReceivedError` and `onReceivedHttpError`, verifying that automation terminates with descriptive `AutomationError.PageLoadFailed`.
- **SSL Certificate Errors**: Validate that untrusted or self-signed test certificates trigger `onReceivedSslError` and abort execution by default.

---

## 6. Performance & Benchmark Testing

### 6.1 Startup Benchmarking (AndroidX Macrobenchmark)
Measure Cold and Warm app startup times across app updates and generate Baseline Profiles.

```kotlin
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCold() = benchmarkRule.measureRepeated(
        packageName = "com.bautopilot.app",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
        iterations = 5,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
    }
}
```
**Metric Target**: Time To Initial Display (TTID) **≤ 600ms**, Time To Full Display (TTFD) **≤ 1200ms**.

### 6.2 WebView Memory Profiling & Leak Detection
- **Memory Footprint**: Continuous monitoring of native heap and graphic buffers during multi-profile automation runs.
- **Cache Invalidation**: Explicit teardown verification (`WebView.clearCache(true)`, `WebView.destroy()`) to prevent `WebCore` thread and context leaks.

### 6.3 Battery & Wakelock Profiling
- Run `dumpsys batterystats` during headless background runs.
- Enforce strict timeouts on `WakeLock` acquisitions: every acquired `WakeLock` must have a defined maximum expiration (`wakeLock.acquire(MAX_DURATION_MS)`).

### 6.4 Room Query Performance
Benchmark full-text searches, execution log truncations, and bulk writes using AndroidX Microbenchmark. Queries taking **> 16ms** fail the benchmark.

---

## 7. Root & Shell Execution Testing

BAutoPilot supports privileged operations on rooted devices (e.g. system input injection `input tap`, iptables traffic routing, and private directory management).

### 7.1 Architecture: Mockable Shell Layer
Elevated commands are never executed directly via raw runtime calls in business logic. They run through an injectable abstraction:

```kotlin
interface ShellExecutor {
    suspend fun execute(command: String, asRoot: Boolean = false): ShellResult
    suspend fun isRootAvailable(): Boolean
}
```

### 7.2 Rooted Emulator Setup
- Dedicated CI target: Android Virtual Device with `google_apis` (or `default`) image, ABI `x86_64`, with root enabled via `adb root`.
- Verification of binary availability via `which su`.

### 7.3 Shell Injection & Parameter Sanitization Tests
Unit tests ensure that user-supplied input is sanitized against command chaining (`&&`, `;`, `|`, `` ` ``, `$()`).

```kotlin
@Test
fun `shellExecutor rejects unescaped shell injection attempts`() = runTest {
    val maliciousInput = "profile1; rm -rf /data/data/*"
    
    assertThrows<IllegalArgumentException> {
        ShellCommandBuilder.buildInputTap(x = 100, y = 200, label = maliciousInput)
    }
}
```

### 7.4 Graceful Fallback Verification
Ensure that when `isRootAvailable()` returns `false` or when `su` execution throws `PermissionDenied`:
1. The app does not crash.
2. The automation engine falls back to non-root alternatives:
   - Input injection falls back to `AccessibilityService`.
   - Network routing falls back to Android `VpnService` / local proxy.
3. Diagnostic notification alerts the user to missing elevated capabilities.

---

## 8. CI/CD Pipeline & Static Analysis

The Continuous Integration pipeline is configured via GitHub Actions to deliver rapid feedback and guard code quality.

### 8.1 Pipeline Stages

```mermaid
flowchart LR
    A[PR Opened / Push] --> B[Stage 1: Lint & Detekt]
    B --> C[Stage 2: Unit Tests & JaCoCo]
    C --> D[Stage 3: Assemble APKs]
    D --> E[Stage 4: Instrumented & Emulator Tests]
    E --> F[PR Checks Passed & Mergeable]
```

### 8.2 GitHub Actions Workflow Specification (`.github/workflows/ci.yml`)

```yaml
name: CI Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  static-analysis:
    name: Lint & Detekt
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - name: Setup Gradle Cache
        uses: gradle/actions/setup-gradle@v3

      - name: Run Detekt
        run: ./gradlew detekt

      - name: Run Android Lint
        run: ./gradlew lintDebug

  unit-tests:
    name: Unit Tests & Coverage
    runs-on: ubuntu-latest
    needs: static-analysis
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - name: Setup Gradle Cache
        uses: gradle/actions/setup-gradle@v3

      - name: Run Unit Tests
        run: ./gradlew testDebugUnitTest jacocoTestReport

      - name: Verify Code Coverage Gate (80%)
        run: ./gradlew jacocoTestCoverageVerification

      - name: Upload JaCoCo Coverage Report
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: app/build/reports/jacoco/jacocoTestReport/html/

  build-apks:
    name: Assemble Debug & Test APKs
    runs-on: ubuntu-latest
    needs: unit-tests
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - name: Setup Gradle Cache
        uses: gradle/actions/setup-gradle@v3

      - name: Assemble Debug APKs
        run: ./gradlew assembleDebug assembleDebugAndroidTest

      - name: Store APK Artifacts
        uses: actions/upload-artifact@v4
        with:
          name: app-apks
          path: |
            app/build/outputs/apk/debug/*.apk
            app/build/outputs/apk/androidTest/debug/*.apk

  instrumented-tests:
    name: Rooted Emulator Tests
    runs-on: macos-13 # Metal/Hardware virtualization support
    needs: build-apks
    timeout-minutes: 30
    strategy:
      matrix:
        api-level: [ 33 ]
        target: [ default ]
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Download Built APKs
        uses: actions/download-artifact@v4
        with:
          name: app-apks
          path: app/build/outputs/apk/

      - name: Run Instrumented Tests via AVD
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: ${{ matrix.api-level }}
          target: ${{ matrix.target }}
          arch: x86_64
          profile: pixel_6
          script: |
            adb devices
            adb root
            ./gradlew connectedDebugAndroidTest
```

### 8.3 Quality Gates & Static Analysis Rules
- **Detekt**:
  - Max cyclomatic complexity: **12**.
  - No wild-card imports.
  - Ban on `GlobalScope` usage and untracked coroutine dispatchers.
- **JaCoCo Coverage Gate**:
  - Minimum line coverage for `:core:domain`: **80%**.
  - Minimum branch coverage for `:engine:parser`: **85%**.

---

## 9. Test Data Management & Fixtures

Maintaining consistent, deterministic test state prevents cross-test contamination and ensures readability.

### 9.1 Object Mother & Factory Functions
Avoid manual instantiation in test files; utilize factory functions with default parameters:

```kotlin
object TestFixtures {

    fun createProfile(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test Automation Profile",
        proxyId: String? = null,
        userAgent: String = "Mozilla/5.0 (Linux; Android 13; Pixel 7)",
        isRootEnabled: Boolean = false
    ): Profile = Profile(
        id = id,
        name = name,
        proxyId = proxyId,
        userAgent = userAgent,
        isRootEnabled = isRootEnabled,
        createdAt = Instant.parse("2026-11-22T00:00:00Z")
    )

    fun createActionStep(
        id: String = UUID.randomUUID().toString(),
        order: Int = 1,
        action: Action = Action.Click(selector = "#btn-ok", timeoutMs = 2000L)
    ): ActionStep = ActionStep(
        id = id,
        order = order,
        action = action
    )

    fun createProxyConfig(
        id: String = "proxy_1",
        host: String = "127.0.0.1",
        port: Int = 8080,
        protocol: ProxyProtocol = ProxyProtocol.HTTP
    ): ProxyConfig = ProxyConfig(
        id = id,
        host = host,
        port = port,
        protocol = protocol,
        auth = null
    )
}
```

### 9.2 Hermetic Asset Files (`androidTest/assets`)
All assets are checked into version control under `src/androidTest/assets/` to ensure offline reproducibility:
- `dom_actions.html`: Deterministic test bed containing inputs, buttons, checkboxes, alerts, and target nodes with data-testid attributes.
- `dynamic_dom.html`: Triggers DOM modifications with `setTimeout` to test explicit and implicit wait strategies.
- `auth_form.html`: Standard HTTP basic and form authentication fields.

---

## 10. Summary & Engineering Governance

1. **Test-First Mindset**: All bug fixes must be preceded by a failing unit or integration test reproducing the issue.
2. **Deterministic Stability**: Flaky tests are treated as production P1 defects; any test failing intermittently must be quarantined immediately and resolved.
3. **No External Network Dependencies**: All network operations must point to `MockWebServer` or sealed local assets.
4. **Enforced Coverage Thresholds**: PRs reducing coverage under the 80% domain gate will be blocked automatically by GitHub Actions.

---

## 11. Phase 3 Test Suite & Quality Verification

In Phase 3 (Background Execution & Scheduling), the test suite was expanded with 14 unit and migration tests, bringing total test count to **32 unit tests passing (100%)**:

### 11.1 Phase 3 Automated Unit Tests
- **`ScheduleCalculatorTest` (7 tests)**:
  - Validates one-time future calculation.
  - Validates past one-time schedule rejection (`null`).
  - Validates daily recurrence advancing to next day at same hour/minute.
  - Validates weekday recurrence advancing from Friday to Monday.
  - Validates weekly recurrence advancing by exactly 7 days.
  - Validates monthly recurrence handling month-end boundary clamping (Jan 31 -> Feb 28/29).
  - Validates custom interval calculation.
- **`ScheduleValidatorTest` (6 tests)**:
  - Validates valid schedule passing validation.
  - Validates rejection of blank schedule name.
  - Validates rejection of non-existent profile ID.
  - Validates rejection of one-time schedule set in the past.
  - Validates rejection of invalid timezone ID strings.
  - Validates rejection of negative duration or interval boundaries.
- **`DatabaseMigrationTest` (1 test)**:
  - Validates `MIGRATION_1_2` DDL execution: table creation, indexes, and foreign key cascade rule.

### 11.2 Phase 3 LDPlayer Runtime Verification
- Verified on LDPlayer 9 (`emulator-5554`, Android 9 / API 28).
- 10 screenshot artifacts captured and stored under `docs/testing/phase-3-ldplayer/screenshots/`.
- Validated exact alarm triggering, notification posting, background foreground service start, recurrence, toggle disable/cancel, process death recovery, and Phase 2 regression integrity.

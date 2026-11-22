# Product Requirements Document (PRD)

# AutoPilot: General-Purpose Android Automation Engine

* **Document Version:** 1.0.0  
* **Document Status:** Approved for Implementation  
* **Target OS:** Android 8.0 (API Level 26) through Android 15 (API Level 35+)  
* **Architecture:** Kotlin Native / Android Jetpack / Chromium WebView / libsu  
* **Date:** November 2026  

---

## 1. Product Vision & Goals

### 1.1 Executive Summary
**AutoPilot** is an on-device, high-performance Android automation platform designed to execute complex, scheduled, and headless/visible web and application workflows directly on mobile hardware. By coupling an isolated multi-instance Chromium-based WebView engine with an extensible action orchestration system, fine-grained proxy/VPN routing, and optional root-level system instrumentation, AutoPilot bridges the gap between fragile UI accessibility scripts and heavyweight server-side headless browser farms.

### 1.2 Problem Statement
Current Android automation tools suffer from distinct architectural limitations:
* **Accessibility-based tools** (e.g., Tasker, AutoInput, MacroDroid) interact solely with the UI accessibility hierarchy. They are fragile, break with minor UI redesigns, cannot inspect deep web DOM structures, suffer high latency, and lack cookie/profile isolation.
* **Cloud-based headless browser fleets** (e.g., Selenium, Puppeteer, Playwright running on servers) do not run natively on real mobile hardware, struggle to replicate mobile network/carrier characteristics, and fail mobile device integrity audits.
* **Existing Android web automation tools** lack robust enterprise features such as true profile sandboxing (separate local storage, IndexedDB, cookies, and network stacks per profile), SOCKS5/HTTP proxy failovers, cron-grade background task execution, and battery-aware scheduling.

### 1.3 Strategic Goals
1. **Zero-Friction Local Execution:** Provide a 100% on-device automation engine capable of running unattended workflows 24/7 without requiring tethering to a PC or reliance on cloud runners.
2. **Deterministic Profile Isolation:** Guarantee complete data sandboxing across arbitrary numbers of independent browser profiles, preventing data, cookie, or session bleed.
3. **Resilient Action Engine:** Deliver an expressive, programmable DOM-level and network-aware action system with sub-50ms step latency and automatic fault recovery.
4. **Adaptive Environmental Routing:** Route network traffic per profile through HTTP/HTTPS/SOCKS5 proxies or localized VPN tunnels with strict DNS-leak prevention.
5. **Tiered Capability Spectrum:** Function completely out-of-the-box on standard unrooted devices, while gracefully scaling to unlock system-level capabilities (transparent routing, deep process freeze, system certificate trust) when root permissions (Magisk/libsu) are detected.

---

## 2. Target Users & Personas

### 2.1 User Segmentation

| Persona | Primary Needs | Technical Proficiency | Typical Workflows |
| :--- | :--- | :--- | :--- |
| **QA / Test Engineers** | Automated regression testing of responsive web apps on real Android hardware; network throttling emulation; multi-account concurrency. | High (familiar with CSS selectors, XPath, JSON assertions, test harnesses). | Running 50 end-to-end checkout simulations across 5 isolated profiles with network throttling and screenshot assertions. |
| **Power Users & Automators** | Routine automation (daily check-ins, price monitoring, personal data aggregation); background scheduling without active screen wake. | Intermediate (comfortable with graphical task builders, cron syntax, basic regex). | Waking up at 07:00 AM, rotating through three accounts to scrape energy usage dashboards, extracting CSV metrics, and saving local backups. |
| **Developers & Researchers** | Scriptable web scraping, custom JavaScript injection, headless data extraction, network traffic capture (HAR export), device property emulation. | Advanced (Kotlin, JavaScript, HTTP/REST, networking, reverse engineering). | Injecting custom instrumentation scripts to monitor DOM mutations on dynamic Single Page Applications (SPAs) over authenticated SOCKS5 proxies. |

### 2.2 Detailed User Personas

#### Persona A: Elena – Lead Mobile QA Engineer
* **Objective:** Validate an enterprise web application's behavior across multiple user privilege tiers on real Android hardware before major releases.
* **Pain Points:** Cloud device farms are expensive and exhibit unpredictable latency; existing Android UI automation tests fail when DOM nodes render off-screen; testing different localized proxy IPs requires manual network switching.
* **AutoPilot Fit:** Elena creates automated test suites using AutoPilot’s action sequences. Profiles allow her to run concurrent Admin, Operator, and Customer sessions with distinct credentials and proxies, asserting DOM states and collecting full-page screenshots upon failure.

#### Persona B: Marcus – Developer & System Automator
* **Objective:** Aggregate real-time local public transit alert data and synchronize status updates to an offline SQLite database.
* **Pain Points:** Target services lack public APIs and enforce complex multi-step cookie validations; standard cron solutions on Android are killed aggressively by OEM battery managers (e.g., Samsung One UI, Xiaomi HyperOS).
* **AutoPilot Fit:** Marcus writes a headless task scheduled via cron, leveraging WorkManager and foreground services. The task injects extraction scripts, parses the resulting DOM text into structured JSON variables, and stores the output locally without waking the screen.

---

## 3. Core Requirements

```
+-----------------------------------------------------------------------------------+
|                                 AutoPilot App Core                                |
+-----------------------------------------+-----------------------------------------+
|              Presentation               |              Orchestration              |
|  - Jetpack Compose UI                   |  - Cron & WorkManager Scheduler         |
|  - Workflow Builder & Visual Editor     |  - Action Engine & Flow Control         |
|  - Diagnostics & Log Viewer             |  - Background & Foreground Services     |
+-----------------------------------------+-----------------------------------------+
|                               Core Subsystems                                     |
|  +--------------------+  +----------------------+  +---------------------------+  |
|  | Profile Sandboxing |  | Chromium Web Engine  |  | Network Management        |  |
|  | - Cookie/Storage   |  | - Headless/Visible   |  | - Per-Profile HTTP/SOCKS5 |  |
|  | - Fingerprint Mask |  | - Custom Interceptor |  | - VpnService Integration  |  |
|  | - Credentials DB   |  | - JS Bridge / DOM    |  | - DNS Leak Protection     |  |
|  +--------------------+  +----------------------+  +---------------------------+  |
+-----------------------------------------+-----------------------------------------+
|                           System & Storage Layer                                  |
|  - SQLCipher Encrypted Database         - libsu Root Abstraction (Optional)       |
|  - Secure Backup / Restore Engine       - Android Keystore & Biometric Auth       |
+-----------------------------------------------------------------------------------+
```

### 3.1 Profile Management
AutoPilot must support multiple, fully isolated profiles. Each profile represents an independent browser container with its own persistence layer and identity configuration.

1. **Storage Sandboxing:**
   * Each profile must maintain an isolated directory for:
     * HTTP Cookies (`CookieManager` partition or isolated data directory).
     * `localStorage` and `sessionStorage`.
     * `IndexedDB` databases.
     * Web SQL and Application Cache.
   * Clearing data in Profile A must have zero side-effects on Profile B.
2. **Identity & Environment Emulation:**
   * Custom User-Agent string configuration per profile.
   * Configurable Client Hints (`Sec-CH-UA`, `Sec-CH-UA-Mobile`, `Sec-CH-UA-Platform`).
   * Configurable Viewport dimensions, device pixel ratio (DPR), and screen orientation.
   * Language/Locale header overrides (`Accept-Language`).
   * Timezone and Geolocation spoofing via DOM JavaScript override hooks.
3. **Credential Storage:**
   * Secure credential vault per profile storing pre-configured form logins, basic auth tokens, and session metadata.
   * Cryptographic protection backed by Android Keystore and SQLCipher.

### 3.2 Browser Engine (WebView-based)
The browsing runtime leverages the system-installed Chromium WebView, enhanced with custom control bridges.

1. **Execution Modes:**
   * **Visible Mode:** Renders inside an in-app interactive container for visual debugging, human intervention, or real-time workflow inspection.
   * **Headless / Virtual Display Mode:** Runs the WebView detached from the active window hierarchy or within an off-screen surface buffer, allowing completely unobtrusive background execution.
2. **Client Customization:**
   * Custom `WebViewClient` intercepting navigation, resource loading, SSL error handling, and HTTP status codes.
   * Custom `WebChromeClient` handling JavaScript alerts, confirms, file uploads, console logs, and progress tracking.
3. **Lifecycle & State Management:**
   * Multi-instance capability: Ability to instantiate and run multiple isolated WebViews sequentially or concurrently (subject to device RAM limits).
   * Aggressive resource reclamation: Full memory teardown and cache flush upon profile deactivation or workflow completion.

### 3.3 Task Scheduling
AutoPilot requires enterprise-grade scheduling capable of surviving device restarts and complying with Android's battery-saving constraints.

1. **Scheduling Engine:**
   * Standard 5-field cron syntax (`minute hour day-of-month month day-of-week`).
   * One-shot timers (e.g., "Run once at 14:30 on 2026-12-01").
   * Recurring intervals (e.g., "Every 15 minutes", "Every 2 hours").
2. **Execution Framework:**
   * **AndroidX WorkManager:** Handles flexible, deferred, and opportunistic background execution respecting device battery, network state, and idle constraints.
   * **AlarmManager (`setExactAndAllowWhileIdle`):** Handles precise, time-critical task triggers where execution jitter must be under 5 seconds.
   * **Foreground Service Mode:** Utilizes sticky foreground services with a persistent status bar notification, wakelocks, and battery optimization whitelist (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) for continuous, uninterrupted task sequences.
3. **Trigger Event Sources:**
   * Time-based (cron/interval).
   * System events: `BOOT_COMPLETED`, network transition (Wi-Fi connected/disconnected), battery charging state change.
   * Local IPC / Intent triggers: Allows external apps or shell commands (`am startservice`) to trigger workflows with custom payload parameters.

### 3.4 Action System
The core workflow engine executes declarative, ordered action pipelines. The engine supports branching, loops, variables, and error recovery.

```
[Start Task] ---> (Action: navigate) ---> [Wait DOM Ready]
                        |
                        v
              (Action: wait_for_element)
                        |
              +---------+---------+
              | (Element Found)   | (Timeout / Error)
              v                   v
      (Action: fill)       [Catch Handler] ---> (Action: screenshot)
              |                                        |
              v                                        v
      (Action: click)                          [Abort / Notify]
              |
              v
     (Action: extract) ---> [Store to Variable: "user_balance"]
              |
              v
     (Action: js_execute) ---> [Transform Data]
              |
              v
         [End Task]
```

#### Supported Action Types:
1. `navigate`:
   * Parameters: `url` (string), `timeoutMs` (integer), `waitUntil` (`load`, `domcontentloaded`, `networkidle0`).
   * Navigates the WebView to the target endpoint.
2. `click`:
   * Parameters: `selector` (CSS or XPath), `clickType` (`simulated_touch`, `dom_dispatch`), `delayMs` (post-click delay), `coordinates` (optional {x, y}).
   * Resolves the element, scrolls into view if required, and dispatches the click.
3. `fill`:
   * Parameters: `selector` (CSS or XPath), `value` (string or template variable), `clearFirst` (boolean), `simulateKeystrokes` (boolean, with randomized delay per character).
   * Types values into input, textarea, or contenteditable targets.
4. `extract`:
   * Parameters: `selector` (CSS or XPath), `targetAttribute` (`innerText`, `textContent`, `innerHTML`, `value`, or custom HTML attribute), `outputVariable` (string), `regex` (optional capture group).
   * Extracts data from the DOM into a workflow runtime variable.
5. `wait`:
   * Parameters: `waitType` (`duration`, `element_visible`, `element_hidden`, `js_condition`), `parameter` (ms duration, selector, or JS expression), `timeoutMs` (integer).
   * Pauses pipeline execution until the condition resolves or timeouts.
6. `scroll`:
   * Parameters: `target` (`top`, `bottom`, `element`, `delta`), `deltaY` (integer), `selector` (optional), `smooth` (boolean).
   * Scrolls the page or a target container element.
7. `screenshot`:
   * Parameters: `captureType` (`viewport`, `full_page`, `element`), `selector` (optional), `quality` (1-100 JPEG/PNG), `savePath` (string), `variableName` (optional Base64 output).
   * Captures visual state into storage for auditing or diagnostics.
8. `js_execute`:
   * Parameters: `script` (string), `args` (array of variables), `outputVariable` (optional), `async` (boolean), `timeoutMs` (integer).
   * Injects and evaluates custom JavaScript in the context of the active page.
9. **Control Flow Primitives:**
   * `condition` (`if/else`): Evaluates variable comparisons or DOM states to branch execution.
   * `loop` (`for_each`, `while`): Iterates over extracted arrays or repeats actions until a condition is met.
   * `call_subroutine`: Calls a reusable sub-workflow with isolated or inherited variable scope.
   * `try_catch`: Encapsulates error-prone actions, providing rollback or recovery paths.

### 3.5 Proxy Support
AutoPilot must support granular, per-profile network proxy configurations to allow localized network testing and traffic routing.

1. **Protocol Support:**
   * HTTP and HTTPS proxies.
   * SOCKS4, SOCKS5, and SOCKS5 with UDP relay support.
2. **Authentication:**
   * Username / Password authentication for HTTP Basic/Digest and SOCKS5 user/pass auth.
3. **Engine-Level Routing:**
   * Dynamic proxy binding directly to the Android Chromium WebView using `androidx.webkit.ProxyController` (on supported WebView versions).
   * Fallback to internal embedded local loopback tunnel: An in-process forwarding client that bridges WebView network sockets through authenticated remote SOCKS5 endpoints.
4. **Resilience & Security:**
   * Remote DNS resolution: In SOCKS5 mode, DNS queries must resolve on the remote proxy host to prevent client-side DNS leakage.
   * Proxy health probes: Automatic pre-run ping and latency checks before workflow start, with configurable failover pool support.

### 3.6 VPN Integration
To provide full-device or application-level tunneling when per-WebView proxy hooks are insufficient or when third-party services bypass standard WebView sockets:

1. **Android VpnService Engine:**
   * Built-in client capable of establishing local TUN interfaces using standard WireGuard or OpenVPN configurations.
2. **Split Tunneling:**
   * Route only AutoPilot traffic (or specifically targeted child processes) through the VPN tunnel, leaving the rest of the host device on normal network routes.
3. **Kill Switch:**
   * Optional setting: Immediately block all outgoing network requests if the VPN tunnel drops unexpectedly during a workflow run, preventing data leaks.

### 3.7 Root Features (Optional Magisk / libsu Integration)
AutoPilot is designed to operate seamlessly on non-rooted devices. However, when root access is granted via Magisk/KernelSU/APatch using `libsu`, AutoPilot unlocks advanced system-level capabilities:

1. **Transparent Global / App-Level Proxying:**
   * Directly configure `iptables` and `nftables` rules to redirect all TCP/UDP traffic from specific Android UIDs through a local proxy redirector (e.g., `redsocks` or `tun2socks`), eliminating proxy leaks across any network stack.
2. **Custom System Certificate Authority Injection:**
   * Install custom root CAs directly into `/system/etc/security/cacerts/` or dynamic overlayfs mounts. This allows debugging, inspecting, and automating HTTPS sites that enforce strict system-trust stores or target apps without modifying `network_security_config.xml`.
3. **Deep App & Process Lifecycle Control:**
   * Send `SIGSTOP` / `SIGCONT` (freeze/unfreeze) or `am force-stop` to background system bloatware during high-load automation runs to preserve CPU and RAM.
4. **System Property Spoofing:**
   * Dynamic modification of `getprop` values (e.g., `ro.build.version.release`, `ro.product.model`) via `resetprop` for advanced testing environments.
5. **Direct File System Access:**
   * Backup and restore deep app data directories (`/data/data/<package_name>/`) when coordinating with hybrid apps.

### 3.8 Diagnostics & Observability
Unattended automation requires rich diagnostic output to identify why a workflow failed or degraded.

1. **Structured Logging:**
   * Log levels: `VERBOSE`, `DEBUG`, `INFO`, `WARN`, `ERROR`.
   * Contextual metadata per log line: `timestamp`, `profileId`, `workflowId`, `actionIndex`, `actionType`, `durationMs`.
   * Live in-app log viewer with real-time text search, severity filtering, and profile filtering.
2. **Session Recording & Visual Tracing:**
   * **Action Timeline:** Visual step-by-step breadcrumb view displaying execution status, duration, and error codes.
   * **Failure Snapshots:** Automatic capture of the visible WebView surface and page DOM HTML at the exact moment an action throws a timeout or assertion error.
   * **Network HAR (HTTP Archive) Capture:** Optional capture of HTTP request/response headers, status codes, and latency metrics for web testing.
3. **Export Capabilities:**
   * Export diagnostic logs and traces as a zipped bundle (`.apdiag`) containing structured JSON, raw logs, and captured images.

### 3.9 Data Management (Backup & Restore)
1. **Encrypted Archive Format:**
   * Backup package format (`.apbackup`): Standard ZIP container containing AES-GCM-256 encrypted JSON schemas for profiles, workflows, tasks, and credentials.
2. **Export Options:**
   * Full backup (Profiles, Settings, Workflows, Schedules, Credentials).
   * Workflow-only export: Enables sharing automation scripts with other users while stripping sensitive profile data, proxy credentials, and authentication cookies.
3. **Import & Conflict Resolution:**
   * Schema versioning check: Migration engine to handle backward compatibility across AutoPilot releases.
   * Collision strategies: `Overwrite Existing`, `Keep Both (Rename)`, or `Skip Existing`.
   * Integrity verification: SHA-256 checksums embedded in the backup manifest to detect corruption or tampering.

---

## 4. Non-Functional Requirements (NFRs)

### 4.1 Performance
* **NFR-P1 (Cold Start):** The application UI must render the home dashboard within **1.5 seconds** on a standard mid-range device (e.g., Snapdragon 600-series or equivalent, 4GB RAM).
* **NFR-P2 (Action Dispatch Latency):** The action engine must parse and dispatch an action to the WebView runtime within **≤50 milliseconds** of the preceding action's completion.
* **NFR-P3 (Memory Envelope):** An active WebView automation instance running in headless mode must maintain a memory footprint of **≤120 MB** RSS (excluding heavy multimedia site buffers). Memory must return to baseline (≤40 MB) within 5 seconds of workflow completion.
* **NFR-P4 (Idle Resource Usage):** When no tasks are actively running, background services/schedulers must consume **0% CPU** and wake only on scheduled alarms.

### 4.2 Reliability & Fault Tolerance
* **NFR-R1 (Crash-Free Execution):** The background service and action execution engine must maintain a crash-free rate of **≥99.5%** across continuous 72-hour stress runs.
* **NFR-R2 (Process Recovery):** If the OS terminates the application process due to extreme memory pressure (Low Memory Killer), the task state must be persisted in SQLite. The scheduler must detect the interrupted state upon next boot/wake and execute configured failure policies (retry, abort, or alert).
* **NFR-R3 (Network Resilience):** Actions failing due to transient network drops (`ERR_INTERNET_DISCONNECTED`, socket timeout) must support configurable retry policies (e.g., exponential backoff up to 3 retries) before terminating the workflow.

### 4.3 Security & Privacy
* **NFR-S1 (Zero External Telemetry):** AutoPilot must not transmit analytics, logs, user workflows, profile data, or credentials to any remote server. 100% of data remains on-device.
* **NFR-S2 (At-Rest Encryption):** All stored credentials, proxies, session cookies, and sensitive workflow variables must be encrypted using **AES-256-GCM**. Encryption keys are derived via Android Keystore system.
* **NFR-S3 (Authentication & Lockout):** The app must offer optional Biometric (Fingerprint/Face Unlock via `BiometricPrompt`) and PIN app-lock, required upon app launch or credential viewing.
* **NFR-S4 (Memory Sanitization):** Plaintext credentials must be purged from memory buffers immediately after injection into the DOM input field.

### 4.4 Battery & Resource Efficiency
* **NFR-B1 (Doze Mode Compliance):** Workflows marked as "Non-Critical" must be scheduled using WorkManager with standard battery and network constraints, executing during system maintenance windows to preserve battery.
* **NFR-B2 (Wakelock Hygiene):** Partial wakelocks acquired during critical foreground executions must enforce a hard timeout guardrail (default: maximum 15 minutes) to prevent accidental infinite battery drains.
* **NFR-B3 (Power Consumption Target):** 1 hour of continuous foreground background automation (e.g., running 1 task every 5 minutes) must consume **<2.5%** of a standard 4000mAh battery capacity.

### 4.5 Compatibility & Portability
* **NFR-C1 (OS Range):** Fully functional on Android 8.0 (API Level 26) through Android 15 (API Level 35+).
* **NFR-C2 (OEM Compatibility):** Robust execution across divergent Android flavors (AOSP, Google Pixel, Samsung One UI, Xiaomi HyperOS/MIUI, OnePlus OxygenOS). The app must include an in-app setup guide to instruct users on disabling vendor-specific aggressive battery kill systems (e.g., "Don't Kill My App" guidelines).
* **NFR-C3 (WebView Flexibility):** Compatible with standard Android System WebView, Google Chrome, Bromite, and Vanadium WebView providers.

---

## 5. User Stories & Acceptance Criteria

### Domain 1: Profile Sandboxing & Management

#### US-01: Independent Profile Creation & Data Isolation
* **As a** mobile test engineer,  
* **I want to** create distinct browser profiles with isolated storage and cookie jars,  
* **So that** I can test multi-user roles without session tokens cross-contaminating.
* **Acceptance Criteria:**
  * **Given** an existing Profile A with an active session cookie on `example.com`,
  * **When** I create a new Profile B and navigate to `example.com`,
  * **Then** the page must load in an unauthenticated state with zero cookies from Profile A.
  * **And** clearing Profile B's data via the profile manager must leave Profile A's session cookies and localStorage completely intact.

#### US-02: Profile Identity & User-Agent Emulation
* **As a** web developer,  
* **I want to** override User-Agent, Client Hints, and Viewport resolution per profile,  
* **So that** I can simulate desktop, tablet, and distinct mobile devices during automated runs.
* **Acceptance Criteria:**
  * **Given** a profile configured with a custom Desktop Chrome User-Agent and a viewport of 1920x1080,
  * **When** a workflow runs a `navigate` action to `https://httpbin.org/user-agent`,
  * **Then** the extracted response must exactly match the custom desktop string.
  * **And** evaluation of `window.innerWidth` and `window.innerHeight` via JavaScript must return 1920 and 1080 respectively.

#### US-03: Secure Credential Auto-Injection
* **As a** power user,  
* **I want to** associate encrypted usernames and passwords with a profile,  
* **So that** my login workflows can dynamically inject credentials without hardcoding them in plain text inside scripts.
* **Acceptance Criteria:**
  * **Given** a profile with stored encrypted credentials for `service_login`,
  * **When** a workflow executes a `fill` action referencing `{{profile.credentials.service_login.password}}`,
  * **Then** the decrypted password must be typed into the target input field.
  * **And** the plaintext password must never be printed to raw application logs or visible trace outputs.

---

### Domain 2: Action System & Workflow Execution

#### US-04: Declarative DOM Click and Type Actions
* **As a** tester,  
* **I want to** define click and fill actions targeting CSS and XPath selectors,  
* **So that** I can automate form completion on responsive web pages.
* **Acceptance Criteria:**
  * **Given** a page with a visible input `#username` and button `//button[@type='submit']`,
  * **When** the workflow executes `fill` on `#username` with "testuser" and `click` on the XPath,
  * **Then** the input must contain "testuser" and the submit event must trigger.
  * **And** if an element is off-screen, the engine must automatically scroll the element into view prior to dispatching the click.

#### US-05: Dynamic Data Extraction into Variables
* **As a** data automator,  
* **I want to** extract inner text or attribute values from DOM nodes into named variables,  
* **So that** I can use the values in subsequent actions or export them.
* **Acceptance Criteria:**
  * **Given** a page element `<span id="order-id">#98765</span>`,
  * **When** an `extract` action targets `#order-id` with regex `\d+` and output variable `order_num`,
  * **Then** the runtime variable `order_num` must equal `"98765"`.
  * **And** this variable must be available for interpolation in subsequent steps (e.g., `fill` or `js_execute`).

#### US-06: JavaScript Evaluation with Asynchronous Promises
* **As a** developer,  
* **I want to** execute arbitrary JavaScript (including async promises) within the page context,  
* **So that** I can manipulate dynamic SPAs and extract computed client-side state.
* **Acceptance Criteria:**
  * **Given** an action `js_execute` with `async: true` executing `return new Promise(resolve => setTimeout(() => resolve(document.title), 200));`,
  * **When** the script executes,
  * **Then** the engine must suspend action progression until the promise resolves.
  * **And** store the resolved title string into the specified output variable.
  * **And** raise a timeout error if the promise does not resolve within the configured `timeoutMs`.

#### US-07: Resilient Conditional Branching and Loops
* **As a** workflow designer,  
* **I want to** create `if/else` conditions and retry loops based on element presence or variable values,  
* **So that** my automations can handle unpredictable popups or paginated tables.
* **Acceptance Criteria:**
  * **Given** a conditional block checking if element `.newsletter-modal` is visible within 3 seconds,
  * **When** the modal appears,
  * **Then** the engine executes the child branch containing a `click` on `.close-btn`.
  * **When** the modal does not appear,
  * **Then** the engine skips the child branch without throwing an error and proceeds to the next sequence.

#### US-08: Full-Page and Element Screenshot Capture
* **As a** QA engineer,  
* **I want to** capture full-page or element-specific screenshots during execution,  
* **So that** I can visually audit rendering output across test stages.
* **Acceptance Criteria:**
  * **Given** a web page taller than the physical screen height,
  * **When** an action `screenshot` with `captureType: full_page` executes,
  * **Then** the engine must stitch or scroll-render the complete DOM surface into a single PNG/JPEG file.
  * **And** save the image file to the designated profile artifacts folder with an absolute file link in logs.

---

### Domain 3: Task Scheduling & Background Execution

#### US-09: Precision Cron-Like Task Scheduling
* **As a** power user,  
* **I want to** schedule workflows using standard cron syntax,  
* **So that** my routines execute automatically at exact times without manual triggers.
* **Acceptance Criteria:**
  * **Given** a task configured with cron `0 8 * * 1-5` (Every weekday at 08:00 AM),
  * **When** the device clock reaches 08:00 AM on a Monday,
  * **Then** the system must launch the assigned workflow in the background.
  * **And** record the execution trigger timestamp in the task history.

#### US-10: Persistent Execution via Foreground Service
* **As an** automator running long-duration workflows (10+ minutes),  
* **I want to** run tasks under an Android Foreground Service with a persistent notification,  
* **So that** the operating system does not kill the automation process midway through.
* **Acceptance Criteria:**
  * **Given** a workflow configured with `run_in_foreground_service: true`,
  * **When** the workflow begins,
  * **Then** a persistent status notification must appear in the Android notification drawer showing current progress.
  * **And** the process must hold a partial wakelock, preventing the CPU from sleeping until the workflow reaches termination or timeout.

#### US-11: System Reboot Persistence
* **As a** power user,  
* **I want to** have my scheduled tasks re-register automatically after my phone reboots,  
* **So that** I do not have to manually reopen the app after a system restart.
* **Acceptance Criteria:**
  * **Given** active scheduled tasks,
  * **When** the device reboots and broadcasts `ACTION_BOOT_COMPLETED`,
  * **Then** AutoPilot's broadcast receiver must awaken, query the SQLite task database, and reschedule all active alarms/workers.

---

### Domain 4: Network, Proxy & VPN Routing

#### US-12: Per-Profile SOCKS5/HTTP Proxy Assignment
* **As a** tester,  
* **I want to** assign a dedicated SOCKS5 proxy with authentication to a specific profile,  
* **So that** all web requests for that profile route through a designated regional gateway.
* **Acceptance Criteria:**
  * **Given** Profile A configured with SOCKS5 proxy `proxy.example.com:1080` (user/pass authenticated),
  * **When** a workflow assigned to Profile A navigates to `https://api.ipify.org`,
  * **Then** the response IP must match the external IP of the SOCKS5 proxy.
  * **And** Profile B (set to direct connection) running concurrently must show the device's native carrier/Wi-Fi IP.

#### US-13: DNS Leak Prevention
* **As a** privacy-conscious researcher,  
* **I want to** ensure all DNS queries are resolved through the configured SOCKS5 proxy,  
* **So that** my local network's DNS server does not log my automation traffic.
* **Acceptance Criteria:**
  * **Given** a profile configured with an authenticated SOCKS5 proxy and "Prevent DNS Leaks" enabled,
  * **When** navigating to a new domain,
  * **Then** the domain name resolution must occur remotely on the proxy server.
  * **And** zero local UDP port 53 packets must be transmitted over the device's default network interface.

#### US-14: Built-in VPN Tunnel Split Routing
* **As a** power user,  
* **I want to** route all AutoPilot traffic through a WireGuard VPN tunnel while leaving other device apps untouched,  
* **So that** my background workflows run over an encrypted tunnel without disrupting my personal browsing.
* **Acceptance Criteria:**
  * **Given** a valid WireGuard configuration imported into AutoPilot's VPN manager,
  * **When** a task initiates with VPN routing enabled,
  * **Then** AutoPilot must establish a local `VpnService` tunnel binding only AutoPilot's Android UID.
  * **And** other applications (e.g., Spotify, Chrome) continue to route through the primary network interface.

---

### Domain 5: Diagnostics, Root & Data Management

#### US-15: Visual Action Tracing & Failure Snapshots
* **As a** developer debugging a failed workflow,  
* **I want to** view a timeline of executed steps with a snapshot of the DOM and screen on failure,  
* **So that** I can immediately pinpoint why a selector was not found.
* **Acceptance Criteria:**
  * **Given** an action step `click` on `.submit-btn` that times out after 10 seconds,
  * **When** the error triggers,
  * **Then** the engine must record the failure state in the session diagnostic log.
  * **And** capture and save a JPEG screenshot and the full HTML DOM markup as an artifact linked in the run report.

#### US-16: Encrypted Configuration Backup & Sanitized Export
* **As an** automator,  
* **I want to** export my workflows to an encrypted file, with the option to strip private credentials,  
* **So that** I can safely share my automation logic with colleagues.
* **Acceptance Criteria:**
  * **Given** a set of workflows containing stored passwords,
  * **When** I choose "Export (Sanitized)",
  * **Then** the resulting `.apbackup` JSON package must contain all action sequences, selectors, and logic,
  * **And** all password values and private proxy strings must be completely excised or replaced with placeholder tokens.

#### US-17: Root-Level System CA Certificate Trust (Optional)
* **As a** security QA engineer with a rooted device (Magisk),  
* **I want to** install a custom debugging CA certificate into the Android system trust store,  
* **So that** AutoPilot's WebView can automate internal enterprise testing environments with private SSL certificates without security warnings.
* **Acceptance Criteria:**
  * **Given** a device with confirmed root access (`libsu` granted),
  * **When** the user imports a custom PEM certificate and selects "Install to System Store",
  * **Then** AutoPilot must write the hashed certificate to `/system/etc/security/cacerts/` (or via Magisk overlay).
  * **And** subsequent WebView navigations to sites signed by that CA must load cleanly without `SSL_UNTRUSTED` warnings.

#### US-18: Non-Root Graceful Capability Degradation
* **As a** user on an unrooted production phone,  
* **I want** the application to function reliably without prompting for or failing due to lack of root,  
* **So that** all core web automation capabilities remain fully accessible.
* **Acceptance Criteria:**
  * **Given** an unrooted Android device (no `su` binary present),
  * **When** AutoPilot is installed and launched,
  * **Then** the app must initialize without errors, hiding or disabling root-dependent features (iptables redirection, system certs).
  * **And** all core Profile, Browser, Scheduling, and Proxy features must operate at 100% capacity.

---

## 6. Out of Scope

To ensure legal compliance, platform security, and technical focus, the following capabilities are explicitly out of scope for the AutoPilot project:

1. **Ad Fraud & Click Fraud Mechanics:**
   * No automated ad-clicking engines, hidden iframe impression generators, or evasion algorithms specifically designed to simulate false advertising revenue or defraud monetization networks.
2. **Automated CAPTCHA Solving / Circumvention:**
   * No built-in bypasses, OCR solving modules, or automated integrations with third-party CAPTCHA solving farms (e.g., 2Captcha, Anti-Captcha) targeting reCAPTCHA, Cloudflare Turnstile, Arkose Labs, or hCaptcha.
   * *Defined System Behavior:* When a CAPTCHA challenge is detected, AutoPilot provides clean hooks to:
     * Pause the workflow and notify the user via a system notification for manual completion.
     * Abort the workflow cleanly with a structured `CHALLENGE_DETECTED` status code.
3. **Security Circumvention & Exploit Delivery:**
   * No capabilities designed to exploit Android vulnerabilities, bypass OEM lockouts, evade Mobile Device Management (MDM) enrollment, or disable SELinux enforcing modes.
   * No tools for dumping raw credentials from other third-party apps or circumventing Android's application sandbox.
4. **Targeting Native Banking or Authentication Gateways:**
   * No automated hooks designed to extract two-factor authentication SMS codes without explicit user consent, or bypass biometric authentication challenges in third-party native banking applications.
5. **Botnet / Distributed C2 Architecture:**
   * AutoPilot is strictly a local client tool. It contains no Command and Control (C2) listeners, no remote code execution backdoors, and no peer-to-peer task distribution mechanisms that could turn host devices into distributed botnets.

---

## 7. Success Metrics & Key Performance Indicators (KPIs)

| Metric | Target | Measurement Method |
| :--- | :--- | :--- |
| **Workflow Completion Reliability** | **≥ 99.0%** | Ratio of scheduled workflows completed without unexpected runtime crash or unhandled exception across 1,000 test runs. |
| **Action Execution Overhead** | **≤ 50 ms** | Average latency measured between the end of step *N* and the dispatch of step *N+1* in a standardized 20-step synthetic test. |
| **Schedule Trigger Accuracy** | **± 2 seconds** | Discrepancy between scheduled wall-clock time and actual service invocation time for high-priority AlarmManager tasks. |
| **Profile Isolation Guarantee** | **100% (Zero Leakage)** | Automated security audit verifying that session cookies and localStorage keys are never accessible across disparate profile instances. |
| **Crash-Free User Sessions** | **≥ 99.5%** | Percentage of daily sessions without an uncaught fatal exception (logged via local crash log ledger). |
| **Battery Drain Index** | **< 2.5% per hour** | Measured on a reference 4000mAh device running active 1-minute interval headless tasks over Wi-Fi. |
| **Diagnostics Completeness** | **100%** | Every failed workflow run must produce a structured error record containing step index, error code, and failure screenshot. |

---

## 8. Assumptions and Dependencies

### 8.1 Technical Assumptions
1. **Modern Chromium WebView Availability:** It is assumed the target device has a reasonably up-to-date Chromium WebView provider (version 90+), regularly updated via the Google Play Store or OEM system updates.
2. **Network Availability:** Background automation tasks that require internet access assume active cellular or Wi-Fi connectivity. WorkManager network constraints (`NetworkType.CONNECTED`) will defer tasks when offline.
3. **Storage Availability:** Sufficient local storage space (minimum 250 MB free) is available for storing profile caches, offline SQLite databases, and diagnostic image artifacts.
4. **OEM Background Processing Guidelines:** While AutoPilot implements foreground services and battery optimization exceptions, extreme OEM vendor skins (e.g., aggressive OEM battery management) may still terminate processes unless the user manually grants background permissions as prompted during onboarding.

### 8.2 Architectural & Library Dependencies

```
+-------------------------------------------------------------------------------+
|                             AutoPilot Technology Stack                        |
+-------------------+-----------------------------------------------------------+
| Layer             | Selected Technology / Dependency                          |
+-------------------+-----------------------------------------------------------+
| Language & Core   | Kotlin 2.0+ / Coroutines / Flow                           |
| Architecture      | Jetpack Architecture Components (MVI / Clean Arch)        |
| UI Framework      | Jetpack Compose / Material 3                              |
| Web Engine        | AndroidX WebKit (`androidx.webkit:webkit:1.12.0+`)         |
| Scheduling        | AndroidX WorkManager (`androidx.work:work-runtime-ktx`)   |
| Database          | Room with SQLCipher (`net.zetetic:android-database-sqlcipher`) |
| Network & HTTP    | OkHttp 4.12+ (Proxy tunneling & health probes)            |
| VPN Engine        | WireGuard Android Library (`com.wireguard.android:tunnel`)|
| Root Abstraction  | libsu Core & Shell (`com.github.topjohnwu.libsu:core:5.2+`)|
| Serialization     | Kotlinx Serialization (JSON)                              |
| Cryptography      | AndroidX Security Crypto (Android Keystore integration)   |
+-------------------+-----------------------------------------------------------+
```

### 8.3 Required Android Permissions
AutoPilot requires the following permissions declared in `AndroidManifest.xml`:
* `android.permission.INTERNET`: Core web navigation and proxy connections.
* `android.permission.ACCESS_NETWORK_STATE`: Detecting Wi-Fi/Cellular connectivity to govern scheduler constraints.
* `android.permission.FOREGROUND_SERVICE` & `android.permission.FOREGROUND_SERVICE_DATA_SYNC`: Continuous background execution without OS kill.
* `android.permission.POST_NOTIFICATIONS`: Alerting users of task completion, failure, or interactive manual intervention prompts.
* `android.permission.SCHEDULE_EXACT_ALARM`: Sub-second precision scheduling via AlarmManager.
* `android.permission.RECEIVE_BOOT_COMPLETED`: Rescheduling persistent alarms and tasks after device restart.
* `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: Prompting user to whitelist AutoPilot from system battery throttling.
* `android.permission.WAKE_LOCK`: Holding partial CPU wakelock during active workflow execution.

### 8.4 Risk Management Matrix

| Risk Identified | Severity | Probability | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| **Aggressive OEM Process Killing** | High | High | Implement standard Foreground Service with persistent notification; guide users through vendor-specific settings (e.g., autostart permission, battery unrestricted mode) during first-run setup. |
| **System WebView Deprecation / Breakage** | Medium | Low | Restrict WebView API usage to standardized AndroidX WebKit wrapper interfaces; implement defensive exception handling around proprietary vendor WebView features. |
| **DNS Leaks on Proxied Workflows** | High | Medium | Enforce SOCKS5 remote hostname resolution (`socks5h://`); provide automated pre-flight DNS leak verification test during profile configuration. |
| **Excessive Storage Consumption** | Low | High | Implement automatic rolling retention policies for diagnostic artifacts (e.g., auto-delete screenshots older than 7 days or exceeding 500 MB total disk quota). |
| **Target Website DOM Instability** | Medium | High | Support fallback multi-selector strategies (e.g., primary CSS selector with secondary XPath and text fallback); provide configurable retry delays. |

---

## 9. Document Approval & Sign-Off

* **Lead Systems Architect:** *Approved*
* **Product Manager:** *Approved*
* **Engineering Lead:** *Approved*
* **Security & Compliance Officer:** *Approved*

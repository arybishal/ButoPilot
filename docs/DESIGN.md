# BAutoPilot - UI/UX Design Specification
**Version:** 1.0.0  
**Target Platform:** Android 14+ (API Level 26–35 compatibility)  
**UI Toolkit:** Jetpack Compose + Material Design 3 (Material You)  
**Author:** Product & UX Engineering Architecture  
**Status:** Approved Specification  

---

## Executive Summary & System Overview

**BAutoPilot** is a native Android browser automation and multi-profile orchestration platform. It empowers power users, QA engineers, and automated task orchestrators to configure isolated browser profiles, construct sequential web automation workflows (DOM interaction, form filling, script evaluation, network assertion), route traffic through proxy pools, and execute background tasks reliably via Android WorkManager and foreground execution services.

### Core UX Principles
1. **At-a-Glance Operational Clarity:** Complex automation states (running, paused, scheduled, failing) are immediately comprehensible through standardized semantic colors, haptics, and animated status tokens.
2. **Predictable Precision:** Orchestration workflows must feel surgical. Fine-grained control over selectors, timings, cookies, and proxies requires clear visual hierarchies, strict input validation, and non-destructive defaults.
3. **Adaptive Form Factor:** The interface seamlessly scales from compact single-hand phone usage to multi-column split-pane tablet, foldable, and desktop (ChromeOS/DeX) workspaces.
4. **Resilient Background Feedback:** Users are never left wondering what a headless or foreground automation process is executing; real-time telemetry, notifications, and timeline playback offer total visibility.

---

## 1. Design System: Material Design 3 & Material You

BAutoPilot is built upon Google's **Material Design 3 (Material You)** design language, utilizing dynamic color theming derived from Android 12+ wallpaper extraction, while providing a deeply calibrated static fallback palette for consistent branding across legacy runtimes.

### 1.1 Color Palette & Tonal System

The app utilizes a dual-engine color system:
* **Dynamic Color (Android 12+ / API 31+):** `dynamicLightColorScheme(context)` and `dynamicDarkColorScheme(context)` dynamically generate tonal palettes matching user device preferences.
* **Baseline Fallback Color Scheme:** A modern technical palette centered around **Deep Tech Cyan** (`#006874`) and **Electric Slate** (`#4A6267`), signaling automation reliability and security.

#### Baseline Color Tokens

| Token Name | Light Mode (Hex) | Dark Mode (Hex) | Role / Usage |
| :--- | :--- | :--- | :--- |
| `primary` | `#006874` | `#4FD8EB` | Primary buttons, active tab indicators, key accents |
| `onPrimary` | `#FFFFFF` | `#00363D` | Text/icons on top of primary color |
| `primaryContainer` | `#97F0FF` | `#004F58` | Floating action badges, highlighted card containers |
| `onPrimaryContainer` | `#001F24` | `#97F0FF` | Text/icons on primary container |
| `secondary` | `#4A6267` | `#B1CBD0` | Secondary controls, inactive chips, auxiliary icons |
| `onSecondary` | `#FFFFFF` | `#1C3438` | Text/icons on secondary background |
| `secondaryContainer` | `#CCE8ED` | `#334B4F` | Filter chip surfaces, segmented buttons |
| `onSecondaryContainer` | `#051F23` | `#CCE8ED` | Text on secondary containers |
| `tertiary` | `#525E7D` | `#BAC6EA` | Workflow actions, proxy connection highlights |
| `onTertiary` | `#FFFFFF` | `#24304D` | Text on tertiary background |
| `tertiaryContainer` | `#DAE2FF` | `#3B4664` | Code blocks, selector pill tags |
| `onTertiaryContainer` | `#0E1B37` | `#DAE2FF` | Text on tertiary container |
| `error` | `#BA1A1A` | `#FFB4AB` | Critical failure states, abort buttons, error chips |
| `onError` | `#FFFFFF` | `#690005` | Text on error surfaces |
| `errorContainer` | `#FFDAD6` | `#93000A` | Error banner backgrounds, failed log highlight |
| `onErrorContainer` | `#410002` | `#FFDAD6` | Text on error container |
| `background` | `#FAFDFD` | `#191C1D` | Global window background |
| `onBackground` | `#191C1D` | `#E1E3E3` | Primary body typography |
| `surface` | `#FAFDFD` | `#191C1D` | Navigation bars, top bars, un-elevated sheets |
| `onSurface` | `#191C1D` | `#E1E3E3` | Standard headings and titles |
| `surfaceVariant` | `#DBE4E6` | `#3F484A` | Card borders, dividers, disabled controls |
| `onSurfaceVariant` | `#3F484A` | `#BFC8CA` | Subtitles, helper text, timestamps |
| `outline` | `#6F797B` | `#899294` | Text field outlines, subtle borders |
| `outlineVariant` | `#BFC8CA` | `#3F484A` | Non-critical dividers, timeline connector lines |

#### Surface Elevation (Tonal Elevation)
In accordance with Material 3, drop shadows are minimized in favor of **Surface Tint Overlays**. Surfaces at elevation levels 1–5 gain increasing blends of `primary` color over `surface`:
* **Level 0 (0dp):** Plain surface `#FAFDFD` / `#191C1D` (Canvas background)
* **Level 1 (1dp):** 5% primary tint (Standard Cards, Inactive Lists)
* **Level 2 (3dp):** 8% primary tint (Top App Bars on scroll, Bottom App Bars)
* **Level 3 (6dp):** 11% primary tint (Modal Bottom Sheets, Floating Action Menus)
* **Level 4 (8dp):** 12% primary tint (Navigation Drawers, Search Panels)
* **Level 5 (12dp):** 14% primary tint (Dialogs, Prominent Popups)

---

### 1.2 Typography System

The typography scale utilizes **Roboto** as primary body/display and **Roboto Mono / Jetpack Mono** for automation scripts, CSS selectors, XPath strings, and logs.

| Style Role | Font Family | Weight | Size (sp) | Line Height (sp) | Tracking | Usage |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `Display Large` | Sans-Serif | Regular (400) | 57 | 64 | -0.25 | Splash / Onboarding intro figures |
| `Display Medium` | Sans-Serif | Regular (400) | 45 | 52 | 0.0 | Empty state key headlines |
| `Display Small` | Sans-Serif | Regular (400) | 36 | 44 | 0.0 | Metric stat callouts on Dashboard |
| `Headline Large` | Sans-Serif | Regular (400) | 32 | 40 | 0.0 | Top-level screen titles (collapsing) |
| `Headline Medium` | Sans-Serif | Medium (500) | 28 | 36 | 0.0 | Profile name in detail view |
| `Headline Small` | Sans-Serif | Medium (500) | 24 | 32 | 0.0 | Modal and bottom sheet titles |
| `Title Large` | Sans-Serif | SemiBold (600) | 22 | 28 | 0.0 | Card headers, section dividers |
| `Title Medium` | Sans-Serif | Medium (500) | 16 | 24 | +0.15 | Sub-section headers, list item titles |
| `Title Small` | Sans-Serif | SemiBold (600) | 14 | 20 | +0.1 | Step numbers, chip labels, tabs |
| `Body Large` | Sans-Serif | Regular (400) | 16 | 24 | +0.5 | Primary descriptions, user inputs |
| `Body Medium` | Sans-Serif | Regular (400) | 14 | 20 | +0.25 | Secondary descriptions, table cells |
| `Body Small` | Sans-Serif | Regular (400) | 12 | 16 | +0.4 | Timestamps, metadata, hints |
| `Label Large` | Sans-Serif | Medium (500) | 14 | 20 | +0.1 | Buttons, floating action text |
| `Label Medium` | Sans-Serif | Medium (500) | 12 | 16 | +0.5 | Badges, small pills, tab badges |
| `Label Small` | Sans-Serif | Medium (500) | 11 | 16 | +0.5 | Tiny status indicators, micro labels |
| `Code Medium` | Monospace | Regular (400) | 13 | 18 | 0.0 | Selectors, XPath, JS snippets |
| `Code Small` | Monospace | Regular (400) | 11 | 14 | 0.0 | Diagnostics raw log stream |

---

### 1.3 Spacing & Grid System (4dp Baseline)

All layout dimensions, paddings, and margins conform to an integer multiple of the **4dp base unit**:

```
 0dp   2dp   4dp   8dp   12dp   16dp   20dp   24dp   32dp   40dp   48dp   64dp
[none][xxs] [xs]   [s]   [m]    [l]    [xl]   [xxl]  [3xl]  [4xl]  [5xl]  [huge]
```

* **Micro-Alignment (`2dp` / `4dp`):** Inner badge padding, radio/checkbox offsets, inline icon gaps.
* **Component-Interior (`8dp` / `12dp`):** Padding inside buttons, card headers, segmented buttons, chip spacing.
* **Screen Gutters (`16dp`):** Standard outer horizontal margin for compact screens (phones).
* **Section Separation (`24dp` / `32dp`):** Vertical space separating distinct logical groups on cards and forms.
* **Touch Target Enclosure (`48dp`):** Absolute minimum width and height for any interactive element.

#### Shape System (Corner Radii)
* **Extra Small (`4dp`):** Code inline tags, status indicator pills.
* **Small (`8dp`):** Action step items, text fields, notification banners.
* **Medium (`12dp`):** Standard content cards, dropdown menus, modal dialogs.
* **Large (`16dp`):** Bottom sheet top corners, expanded profile cards.
* **Extra Large (`28dp`):** Floating Action Buttons (M3 square-round FAB), Search bar pill.
* **Full (`9999dp`):** Filter chips, avatar circles, icon buttons.

---

## 2. Screen Inventory & UX Specifications

```
                             ┌───────────────────────────────┐
                             │       BAutoPilot Main         │
                             └──────────────┬────────────────┘
                                            │
        ┌──────────────┬────────────────────┼───────────────────┬──────────────┐
        ▼              ▼                    ▼                   ▼              ▼
  ┌───────────┐  ┌───────────┐        ┌───────────┐       ┌───────────┐  ┌───────────┐
  │ Dashboard │  │ Profiles  │        │ Scheduler │       │Diagnostics│  │ Settings  │
  └─────┬─────┘  └─────┬─────┘        └─────┬─────┘       └─────┬─────┘  └───────────┘
        │              │                    │                   │
        │        ┌─────┴──────────────┐     │                   │
        │        ▼                    ▼     │                   │
        │  ┌───────────┐       ┌──────────┐ │                   │
        │  │Profile Det│       │Action Ed.│ │                   │
        │  │(5 Tabs)   │       └──────────┘ │                   │
        │  └─────┬─────┘                    │                   │
        │        │                          │                   │
        ▼        ▼                          ▼                   ▼
  ┌───────────────────────────────────────────────────────────────┐
  │           Running Session Monitor & Live HUD / Preview        │
  └───────────────────────────────────────────────────────────────┘
```

---

### 2.1 Dashboard Screen

The central control center displaying active automations, quick metrics, operational status, and rapid controls.

```
+-----------------------------------------------------------+
| [=] BAutoPilot                             (Sync) [Avatar]|
+-----------------------------------------------------------+
| OVERVIEW METRICS                                          |
| +-------------------+ +-------------------+               |
| | 3 ACTIVE RUNS     | | 98.4% SUCCESS     |               |
| | (2 Profiles)      | | Past 24h (120)    |               |
| +-------------------+ +-------------------+               |
| +-------------------+ +-------------------+               |
| | 14 SCHEDULED      | | 4 PROXIES UP      |               |
| | Next in 12m       | | 1 Flagged (Slow)  |               |
| +-------------------+ +-------------------+               |
+-----------------------------------------------------------+
| LIVE RUNNING SESSIONS                         [View All >]|
| +-------------------------------------------------------+ |
| | [*] Scraping "MarketPrices" (Step 4/12)       [00:32] | |
| | Target: https://target.store/search                   | |
| | [=============>               ] 35%                   | |
| | [Pause] [Abort]                            [Live HUD] | |
| +-------------------------------------------------------+ |
+-----------------------------------------------------------+
| RECENT WORKFLOW ACTIVITY                                  |
| (v) Daily Login & Token Sync            COMPLETED  08:14  |
| (x) Checkout Bot #2 (Timeout Step 6)    ERROR      07:55  |
| (v) Cloudflare Session Keep-Alive       COMPLETED  06:00  |
+-----------------------------------------------------------+
|                                        [ + Quick Launch ] |
+-----------------------------------------------------------+
| [Home]    [Profiles]    [Scheduler]   [Logs]   [Settings] |
+-----------------------------------------------------------+
```

#### Key Dashboard Components
1. **Top App Bar (`CenterAlignedTopAppBar`):** App title with live heartbeat indicator dot (green when background service is healthy).
2. **Telemetry Summary Cards:** 2x2 grid of elevated cards displaying active executions, rolling 24h success rate, upcoming scheduled events, and proxy health.
3. **Live Execution Card Carousel:** Shows real-time running sessions with inline animated progress bar, elapsed timer, current URL, and quick abort/pause buttons.
4. **Recent Activity Feed:** Grouped list with status pills, timestamp, and one-tap navigation to the Diagnostic Execution Log.
5. **Quick Launch Extended FAB:** Expands to reveal "New Profile" or "Instant Action Run".

---

### 2.2 Profile Detail Screen

The detailed workbench for a specific browser profile. Hosts 5 specialized configuration tabs underneath a persistent profile header.

```
+-----------------------------------------------------------+
| [<] Amazon Checkout Bot                (Fav) [Delete] [:] |
+-----------------------------------------------------------+
| STATUS: IDLE    | Last Run: 2h ago    | Total Runs: 142   |
| [ Run Now ]           [ Clone ]             [ Export ]    |
+-----------------------------------------------------------+
| [ General ] [ Actions ] [ Schedule ] [ Proxy ] [ Cookies ]|
+-----------------------------------------------------------+
| (Active Tab: Actions)                                     |
| Reorderable Sequence (6 steps defined)                    |
|                                                           |
| [=] 1. NavigateTo                                         |
|     URL: https://amazon.com/dp/B08N5WRWNW                 |
|     Timeout: 15000ms                    [Test] [Delete]   |
|                                                           |
| [=] 2. WaitForElement                                     |
|     Selector: #add-to-cart-button                         |
|     State: Visible | Timeout: 5000ms    [Test] [Delete]   |
|                                                           |
| [=] 3. ClickElement                                       |
|     Selector: #add-to-cart-button                         |
|     Delay: 350ms (Human Emulation)      [Test] [Delete]   |
|                                                           |
|                                        [ + Add Action ]   |
+-----------------------------------------------------------+
```

#### Profile Detail Tab Breakdown

#### Tab 1: General
* **Profile Metadata:** Name, description, colored icon/emoji tag.
* **Browser Fingerprint & Emulation:**
  * **User-Agent Selector:** Dropdown presets (Chrome 122 Mobile, Chrome 122 Desktop, Safari iOS 17, Custom String input).
  * **Viewport Dimensions:** Width x Height in dp, Pixel Ratio (1x, 2x, 3x).
  * **Hardware & OS Flags:** Canvas Fingerprint Spoofing (Toggle), WebGL Vendor Spoofing, WebRTC IP Leak Shield (Toggle: Disable UDP / Spoof ICE candidates).
  * **Geolocation Override:** Manual Latitude/Longitude input or "Match Proxy IP Location" checkbox.

#### Tab 2: Actions (Workflow Sequence)
* **Reorderable LazyColumn:** Actions can be reordered via drag-handles (`Modifier.dragAndDrop`).
* **Step Header:** Step number, action type icon, custom step label.
* **Action Summary:** Brief display of target selector or URL with inline syntax highlighting.
* **Action Utilities:** Inline "Test Step" button (executes solely this action in headless sandbox and confirms DOM response), Duplicate, and Delete.
* **Add Action FAB / Bottom Button:** Spawns the Action Editor Modal Sheet.

#### Tab 3: Schedule
* **Scheduler Toggle:** Enable/disable background execution for this profile.
* **Cron / Interval Configurator:**
  * Interval Mode: "Every X Minutes / Hours / Days".
  * Exact Time Mode: Specific clock time with day-of-week checkboxes.
  * Advanced Cron Mode: Direct cron string editor (`0 8 * * 1-5`) with human-readable translation ("At 08:00 AM, Monday through Friday").
* **Execution Constraints (WorkManager):**
  * Require Unmetered Wi-Fi.
  * Require Device Charging.
  * Require Battery Not Low (> 20%).
* **Next 5 Scheduled Runs Preview:** List showing upcoming timestamps.

#### Tab 4: Proxy
* **Connection Mode:** Segmented Button: `[ Direct ]` | `[ Profile Specific ]` | `[ From Pool ]`.
* **Profile-Specific Proxy Form:**
  * Protocol selector: `HTTP`, `HTTPS`, `SOCKS5`.
  * Host/IP, Port, Username, Password (masked with visibility toggle).
* **Latency & Health Checker:** "Test Connection" button with live ping indicator (`42ms` in Green, or `Timeout` in Red).
* **Rotation Settings:** "Rotate IP on each execution" (requires rotating proxy gateway or pool selection).

#### Tab 5: Cookies & Storage
* **Cookie Jar Manager:**
  * Active cookie count badge.
  * Search/Filter bar by domain (e.g., `amazon.com`).
  * Cookie table list: Name, Value (truncated with copy button), Domain, Path, Expiry, HttpOnly/Secure flags.
* **Management Controls:**
  * [Import Cookies (Netscape / JSON format)]
  * [Export Cookies]
  * [Clear Session Cookies]
  * [Wipe Entire Profile Storage (LocalStorage + Cookies + Cache)]

---

### 2.3 Action Editor (Sub-Screen / Modal Sheet)

A dedicated, high-precision form for creating or modifying a single automation step.

```
+-----------------------------------------------------------+
| [X] Edit Action: Step 3                      [ Save Step ]|
+-----------------------------------------------------------+
| ACTION TYPE                                               |
| [ (o) Click Element ]  [ Input Text ]   [ Navigate URL ]  |
| [ Scroll Page       ]  [ Evaluate JS]   [ Assert State ]  |
+-----------------------------------------------------------+
| SELECTOR SPECIFICATION                                    |
| Strategy: [ CSS Selector                v ]               |
| Expression:                                               |
| | #nav-cart-dropdown > div.action-btn                    |
| [ Auto-Inspect in WebView ]           [ Test Selector ]   |
| (v) Valid DOM element resolved (1 node found)             |
+-----------------------------------------------------------+
| EXECUTION TUNING                                          |
| Human Emulation:                                          |
| [x] Randomize Click Coordinates (+/- 3px)                 |
| [x] Pre-click delay: [ 250 ] ms                           |
| Timeout: [ 5000 ] ms                                      |
+-----------------------------------------------------------+
| ERROR HANDLING POLICY                                     |
| On Failure:                                               |
| ( ) Abort Session   (o) Retry (3x)   ( ) Ignore & Proceed |
+-----------------------------------------------------------+
```

#### Supported Action Types
1. **Navigate:** Load URL, wait for `DOMContentLoaded`, `NetworkIdle`, or specific redirect.
2. **Click Element:** Normal tap, double-click, long-press, coordinate-offset click.
3. **Input Text:** Human typing emulation (variable keystroke delay 30ms–120ms), clear field before typing, append mode.
4. **Wait:** Fixed delay, wait for element visibility, wait for element absence, wait for custom JS evaluation.
5. **Scroll:** Scroll to element, scroll by pixels (X, Y), scroll to bottom/top.
6. **Evaluate JavaScript:** Custom JS code editor with syntax highlighting, parameter injection, and return-value extraction.
7. **Extract / Scrape:** Grab text content, inner HTML, or element attribute and assign to session variable.
8. **Assert State:** Check text matches regex, element exists, or URL contains substring.

---

### 2.4 Schedule Manager

Global administration of all scheduled automation jobs across all profiles.

```
+-----------------------------------------------------------+
| Schedule Manager                                          |
+-----------------------------------------------------------+
| Master Scheduler: [ ON  ]       [ Filter: All Profiles v ]|
+-----------------------------------------------------------+
| UPCOMING TODAY (3)                                        |
| +-------------------------------------------------------+ |
| | 12:00 PM  (In 15m)       Profile: CryptoPriceCheck    | |
| | Daily at 12:00           Network: Any | Battery: Any  | |
| | [ Run Now ]                              [ Pause ]    | |
| +-------------------------------------------------------+ |
| | 02:30 PM  (In 2h 45m)    Profile: ForumAutoBump       | |
| | Every 4 Hours            Wi-Fi Only                   | |
| | [ Run Now ]                              [ Pause ]    | |
| +-------------------------------------------------------+ |
|                                                           |
| TOMORROW (2)                                              |
| | 08:00 AM                 Profile: StockInventorySync  | |
+-----------------------------------------------------------+
|                                      [ + New Schedule ]   |
+-----------------------------------------------------------+
```

---

### 2.5 Proxy Settings (Global Pool)

```
+-----------------------------------------------------------+
| Proxy Settings & Pool                                     |
+-----------------------------------------------------------+
| [ + Add Single ]       [ Bulk Import ]      [ Test All ]  |
+-----------------------------------------------------------+
| ACTIVE PROXIES (4 Online, 1 Failed)                       |
| +-------------------------------------------------------+ |
| | (v) US-East (Residential)               42ms [ACTIVE] | |
| | 192.168.1.100:8080 (SOCKS5)  * 0 Failures             | |
| | Assigned to: 3 Profiles                               | |
| +-------------------------------------------------------+ |
| | (x) Frankfurt Datacenter             TIMEOUT [ERROR]  | |
| | 45.12.33.19:3128 (HTTP)      * 4 Failures             | |
| | Assigned to: None                                     | |
| +-------------------------------------------------------+ |
| FAILOVER CONFIGURATION                                    |
| [x] Automatically blacklist proxy after 3 timeouts        |
| [x] Fallback to next healthy proxy in pool                |
+-----------------------------------------------------------+
```

---

### 2.6 Diagnostics (Log Viewer & Timeline)

A high-performance log and telemetry visualizer capable of streaming hundreds of events per second with zero UI frame drops.

```
+-----------------------------------------------------------+
| Diagnostics: Session #842               [ Share ] [ Clear]|
+-----------------------------------------------------------+
| Profile: Amazon Checkout Bot | Run time: 14.2s            |
| Filter: [ All ] [ Info ] [ Warn ] [ Error (1) ]   [Search]|
+-----------------------------------------------------------+
| TIMELINE VIEW                                             |
| [Step 1: Nav]--->[Step 2: Wait]--->[Step 3: Click]--x     |
| 0.0s             2.1s              4.8s            8.9s   |
+-----------------------------------------------------------+
| LOG STREAM                                                |
| 08:14:02.102 [INFO] Engine initialized. WebView v122.0.4  |
| 08:14:02.340 [NET]  Proxy connected: SOCKS5 192.168.1.100 |
| 08:14:03.110 [DOM]  Navigated to amazon.com               |
| 08:14:05.420 [DOM]  Found element #add-to-cart-button     |
| 08:14:08.912 [ERR]  Step 4 Timed out waiting for #modal   |
|                      Screenshot captured: [shot_842.png]  |
+-----------------------------------------------------------+
| [ Jump to Bottom ]                    [ View Error Shot ] |
+-----------------------------------------------------------+
```

---

### 2.7 Settings Screen

Standard preferences categorized cleanly with Material 3 section headings:
* **Automation Engine Preferences:**
  * Background execution mode (Foreground Service with persistent notification vs Background WorkManager).
  * WebKit / WebView Hardware Acceleration toggle.
  * Headless Rendering vs Visible Surface rendering.
  * Global Script Execution Timeout (default: 30 seconds).
* **Storage & Cleanup:**
  * Clear WebView Cache and Service Workers.
  * Purge historical execution logs (> 7 days).
  * Export Full Configuration (Encrypted Backup JSON).
  * Import Configuration.
* **Appearance & Theme:**
  * Dynamic Color (Material You switch).
  * Theme Mode: System Default / Dark Mode / Light Mode.
* **About & Diagnostics:**
  * App Version, WebView Chromium Core Version, Open Source Licenses.

---

### 2.8 WebView Preview & Inspector

An interactive live browser surface designed for debugging and recording action steps.
* **Interactive Top Bar:** URL Bar, Reload, Stop, Forward, Back, and Desktop/Mobile mode toggle.
* **DOM Inspector Overlay:** Tap to highlight any HTML element. Tapping an element pauses rendering, renders an outline overlay around the bounding box, and displays a bottom sheet with suggested CSS Selectors, XPath, Tag name, Class names, and Inner Text.
* **Console Drawer:** Collapsible bottom drawer showing real-time JavaScript `console.log`, `console.error`, and network requests.

---

### 2.9 Running Session Monitor & Live HUD

A high-tech heads-up display presented when an automation workflow is running.

```
+-----------------------------------------------------------+
| LIVE SESSION: Amazon Checkout Bot         [ Min ] [ Abort]|
+-----------------------------------------------------------+
|                                                           |
|  +-----------------------------------------------------+  |
|  | [ LIVE WEBVIEW PREVIEW CANVAS ]                     |  |
|  |                                                     |  |
|  |  [ Amazon Storefront View ]                         |  |
|  |                                                     |  |
|  +-----------------------------------------------------+  |
|                                                           |
| CURRENT STEP: 3 of 6 (ClickElement)                       |
| Targeting: #add-to-cart-button                            |
| [===========================>             ] 50%           |
+-----------------------------------------------------------+
| TELEMETRY STATS                                           |
| CPU: 12% | RAM: 142MB | Network: 34KB/s | Elapsed: 00:18  |
+-----------------------------------------------------------+
| CONTROLS                                                  |
| [ Pause Execution ]     [ Step Next ]     [ Snapshot ]    |
+-----------------------------------------------------------+
```

---

## 3. Navigation Architecture

BAutoPilot employs a standard **Material 3 Adaptive Navigation System** combined with type-safe Jetpack Navigation Compose.

```
                        TOP APP BAR (Contextual / Dynamic)
+-------------------------------------------------------------------------+
|                                                                         |
|                                                                         |
|                           PRIMARY CONTENT AREA                          |
|                       (NavHost - Screen Transitions)                    |
|                                                                         |
|                                                                         |
+-------------------------------------------------------------------------+
    NAVIGATION BAR (Phones) / NAVIGATION RAIL (Tablets & Landscape)
```

### 3.1 Primary Navigation Destinations
The app features five primary top-level destinations pinned to the bottom navigation bar on phones and leading navigation rail on tablets:

| Destination | Route ID | Icon (Unselected) | Icon (Selected) | Badge Support |
| :--- | :--- | :--- | :--- | :--- |
| **Dashboard** | `dashboard` | `Icons.Outlined.Dashboard` | `Icons.Filled.Dashboard` | Dynamic count of active runs |
| **Profiles** | `profiles` | `Icons.Outlined.Layers` | `Icons.Filled.Layers` | None |
| **Scheduler** | `scheduler` | `Icons.Outlined.Schedule` | `Icons.Filled.Schedule` | Upcoming count (< 1h) |
| **Diagnostics** | `diagnostics` | `Icons.Outlined.BugReport` | `Icons.Filled.BugReport` | Error badge if runs failed |
| **Settings** | `settings` | `Icons.Outlined.Settings` | `Icons.Filled.Settings` | None |

### 3.2 Stack Navigation & Screen Transitions
Sub-destinations push onto the navigation stack with smooth, motion-spec transitions:
* **Level 1 to Level 2 (e.g., Profiles -> Profile Detail):** Material Shared Axis (X-Axis horizontal slide with 300ms easing `FastOutSlowInEasing`).
* **Level 2 to Sub-Editor (e.g., Profile Detail -> Action Editor):** Shared Axis (Z-Axis / Vertical slide up).
* **Top-Level Bar Switches:** Fade-through transition (150ms crossfade) with no horizontal sliding to avoid disorientation.
* **Predictive Back Gesture (Android 14+ / API 34+):** Full support for edge-to-edge predictive back swipe with smooth scale-down of the exiting screen and reveal of the underlying parent destination.

### 3.3 Floating Action Button (FAB) Architecture
* **Dashboard:** Extended FAB labeled `"Run Profile"` with `Icons.Default.PlayArrow`. Shrinks to icon-only on downward list scroll.
* **Profiles Screen:** Standard Square-Round FAB (`28dp` shape radius) with `Icons.Default.Add` for creating a new profile.
* **Action List (Profile Detail):** Small FAB or extended bottom-sheet trigger for adding actions to the workflow.

---

## 4. Interaction Patterns

### 4.1 Swipe Actions
* **Swipe-to-Dismiss / Delete:** Swiping list items (Profiles, Actions, Proxies) from right to left exposes a destructive red container (`colorScheme.errorContainer`) containing a white trash can icon.
* **Haptic Confirmation:** A crisp tactile click (`HapticFeedbackType.LongPress`) fires the moment the swipe crosses the 40% dismiss threshold.
* **Reversible Action:** Swiping an item away does not immediately wipe data; a `Snackbar` appears for 5 seconds with an `UNDO` action before permanent commitment.
* **Swipe-to-Run (Left to Right):** Swiping a profile card from left to right triggers an instant background run with a green confirmation container and play icon.

### 4.2 Long-Press & Contextual Action Bar (CAB)
* **Multi-Selection Mode:** Long-pressing any profile or proxy card triggers selection mode.
* **CAB Header:** The Top App Bar animates into a contextual bar displaying the count of selected items (e.g., `"3 Selected"`), accompanied by actions:
  * `Run Batch` (Play icon)
  * `Assign Proxy Pool` (Network icon)
  * `Duplicate` (Copy icon)
  * `Delete` (Trash icon)
* **Haptics:** Heavy haptic pulse on entry into selection mode.

### 4.3 Pull-to-Refresh
* Built using Material 3 `PullToRefreshBox`.
* Available on Dashboard, Proxy Manager, and Diagnostics.
* Pulling downward displays an elevated circular spinner with `primary` color stroke.
* Triggers network latency re-checks, WorkManager status refreshes, and log index sync.

### 4.4 Bottom Sheets (`ModalBottomSheet`)
Used extensively for non-modal quick editing:
* **Action Type Picker:** Categorized grid of action types with icons and descriptions.
* **Profile Filter & Tag Sheet:** Filter profiles by status, tag, or proxy.
* **Quick Run Parameter Sheet:** Override proxy or headless mode before kicking off a one-off execution.
* **Drag Handle:** Every bottom sheet includes a standard centered M3 drag handle (`width 32dp, height 4dp, rounded full`).

### 4.5 Destructive Action Dialogs
Destructive actions (deleting profiles, purging databases, killing active sessions) require explicit confirmation via a standardized M3 `AlertDialog`:
* **Icon:** Centered `Icons.Default.Warning` in `error` color.
* **Headline:** Short, unambiguous question (e.g., *"Delete 'Amazon Checkout'?"*).
* **Supporting Text:** Concrete explanation of consequences (e.g., *"This will permanently remove this profile, its 6 actions, and all stored session cookies. This action cannot be undone."*).
* **Confirm Button:** Filled button styled with `colorScheme.error` and label *"Delete"*.
* **Dismiss Button:** Outlined or Text button with label *"Cancel"*.

---

## 5. Accessibility (a11y) & Inclusive Design

BAutoPilot is engineered to meet **WCAG 2.1 Level AAA** contrast standards and comply fully with Google's Android Accessibility Guidelines.

### 5.1 Touch Targets & Hit Boxes
* **48dp Minimum Constraint:** Every interactive icon button, chip, tab, checkbox, and switch enforces a minimum touch target bounding box of `48dp x 48dp` using `Modifier.minimumInteractiveComponentSize()`.
* **Visual vs. Touch Bounds:** Small 24dp action icons (e.g., trash can, drag handle) maintain 24dp visual bounds but provide 48dp padded clickable boundaries.
* **Spacing Cushion:** Minimum `8dp` buffer maintained between adjacent clickable targets to eliminate mis-taps.

### 5.2 Content Descriptions & Semantic Properties
* **Zero Missing Labels:** Every `IconButton`, `Icon`, and custom clickable composite defines an explicit, localized `contentDescription`.
* **Action Oriented Verbs:** Content descriptions state action and target (e.g., `contentDescription = "Run Amazon Checkout profile immediately"` instead of `"Play button"`).
* **Decorative Elements:** Non-informative background graphics and divider lines explicitly declare `contentDescription = null`.
* **Custom Composites:** Custom UI elements (such as timeline nodes or reorderable steps) utilize `Modifier.semantics`:
  ```kotlin
  Modifier.semantics {
      role = Role.Button
      stateDescription = if (isRunning) "Running step 3 of 6" else "Idle"
      contentDescription = "Step 3: Click Add To Cart button"
  }
  ```

### 5.3 Screen Reader (TalkBack) Navigation & Live Regions
* **Semantic Grouping (`mergeDescendants = true`):** Complex cards (e.g., Profile item with name, last run time, and status badge) merge internal textual elements into a single cohesive announcement for TalkBack focus.
* **Live Regions for Telemetry:** The Running Session HUD and Diagnostics log stream declare `LiveRegionMode.Polite` for status updates, ensuring users listening through TalkBack receive timely audio notifications of step progression without interrupting ongoing speech.
* **Focus Traversal Ordering:** Complex forms (Action Editor) specify explicit `isTraversalGroup = true` and `traversalIndex` order so logical focus moves top-to-bottom, left-to-right.

### 5.4 Typography Scaling & High Contrast
* **Non-Truncating Fluid Text:** All text elements use scalable pixels (`sp`). Layouts support Android OS system font scaling up to **200%** without text clipping, overlap, or broken button containers.
* **Contrast Compliance:** All text tokens meet minimum contrast ratios against their respective backgrounds:
  * Normal text (< 18sp): Minimum 4.5:1 (Targeting 7:1 for core UI).
  * Large text (>= 18sp or bold >= 14sp): Minimum 3:1.
  * Active status badges: High contrast borders included when rendered on complex surfaces.

---

## 6. Responsive & Adaptive Layouts

BAutoPilot adapts dynamically across all screen form factors using Jetpack WindowManager's `WindowSizeClass` specifications.

```
       COMPACT (< 600dp)                MEDIUM (600dp - 839dp)              EXPANDED (>= 840dp)
      +-------------------+             +---+---------------+         +---+-----------+---------------+
      |  Top App Bar      |             | R | Top Bar       |         | N | Top Bar   | Detail Pane   |
      +-------------------+             | a |---------------+         | a |-----------|               |
      |                   |             | i | Content Area  |         | v | List Pane | (Profile,     |
      |  Single Column    |             | l | (2 Columns)   |         |   |           |  Actions,     |
      |  Content Feed     |             |   |               |         | D | (Profiles |  Live Preview)|
      |                   |             |   |               |         | r |  Feed)    |               |
      +-------------------+             +---+---------------+         +---+-----------+---------------+
      | Bottom Nav Bar    |                                           (Permanent Nav  (Dual-Pane
      +-------------------+                                            Drawer)         Scaffold)
```

### 6.1 Layout Strategy by Window Size Class

#### 1. Compact Width (< 600dp) — Phones in Portrait
* **Navigation:** Bottom `NavigationBar` with 5 items.
* **Content:** Single-column vertical scroll (`LazyColumn`).
* **Sub-views:** Full-screen stack transitions.
* **Action Editor:** Bottom sheet or dedicated full screen.

#### 2. Medium Width (600dp – 839dp) — Foldables Unfolded, Small Tablets, Phones in Landscape
* **Navigation:** Left-aligned `NavigationRail` replacing the bottom bar.
* **Content Grid:** Dashboard metrics and profile cards render in a 2-column adaptive grid (`GridCells.Adaptive(minSize = 280.dp)`).
* **Modal Dialogs:** Centered M3 dialogs with maximum 560dp width constraints.

#### 3. Expanded Width (>= 840dp) — Large Tablets, ChromeOS, DeX Desktop Mode
* **Navigation:** Persistent `PermanentNavigationDrawer` with labels and iconography.
* **List-Detail Split Pane (`SupportingPaneScaffold` / `TwoPane`):**
  * **Left Pane (380dp fixed width):** Profile list, filters, search, and status summary.
  * **Right Pane (Fill remaining width):** Active Profile Detail with all 5 tabs and action sequence.
* **Simultaneous Live Debugging:** While a session runs, the left pane displays step execution progress, while the right pane renders the Live Interactive WebView Canvas side-by-side.

### 6.2 Foldable Posture Awareness
* **Tabletop Mode (Half-Folded):**
  * When device hinge angle is detected between 80° and 100°:
  * **Top Display Half:** Live headless/visible browser viewport playback.
  * **Bottom Display Half (Sub-surface):** Diagnostic controls, live telemetry meters, Pause/Abort buttons, and step execution log stream.

---

## 7. Status Indicators & Execution State System

System state is represented through a consistent status token model across the entire application.

### 7.1 Master Execution State Matrix

| State Name | Semantic Meaning | Color Token (Light) | Color Token (Dark) | Icon Token | Animation / Motion |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `IDLE` | Profile configured, waiting for trigger | `#4A6267` (Secondary) | `#B1CBD0` | `Icons.Outlined.CheckCircle` | None (Static) |
| `SCHEDULED` | Armed with active background timer/cron | `#006874` (Primary) | `#4FD8EB` | `Icons.Outlined.Alarm` | Clock hand subtle tick |
| `RUNNING` | Browser session active, executing steps | `#00875A` (Success Green)| `#36B37E` | `Icons.Filled.PlayArrow` | Breathing glow pulse (1.5s loop)|
| `PAUSED` | User paused or breakpoint hit | `#B78103` (Warning Amber)| `#FFAB00` | `Icons.Filled.Pause` | Slow blink (2.0s loop) |
| `ERROR` | Step timed out, crash, or proxy dead | `#BA1A1A` (Error Red) | `#FFB4AB` | `Icons.Filled.Error` | Micro-shake on entrance (300ms) |
| `COMPLETED` | Execution ended with all assertions met | `#006874` (Primary Cyan)| `#4FD8EB` | `Icons.Filled.CheckCircle` | One-shot scale bounce checkmark |

### 7.2 Status UI Representations

#### 1. Status Pill / Badge
```
+-------------------+
|  [*] RUNNING      |   (8dp height dot with 1.5s alpha breathing cycle)
+-------------------+
```
* Container shape: `RoundedCornerShape(8.dp)`.
* Padding: `horizontal = 8.dp, vertical = 4.dp`.
* Typography: `labelSmall` (Medium 500, uppercase).

#### 2. Card Header Indicator
A 4dp thick vertical indicator strip pinned to the leading edge of every profile card, providing instant peripheral recognition of profile status without reading text.

#### 3. Pulse Animation Specification (Jetpack Compose)
```kotlin
val infiniteTransition = rememberInfiniteTransition(label = "running_pulse")
val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.4f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
    ),
    label = "alpha"
)
```

---

## 8. Notification Design & Background Operations

Because BAutoPilot performs complex browser automation while the application may be backgrounded or the device locked, notifications serve as a primary operational interface.

### 8.1 Android Notification Channels

| Channel ID | User-Visible Name | Importance | Sound / Vibrate | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| `bautopilot_foreground_service` | Running Automations | `IMPORTANCE_LOW` | Silent / No vibration | Persistent foreground service anchor |
| `bautopilot_execution_results` | Workflow Results | `IMPORTANCE_DEFAULT`| Default notification sound | Successful workflow completion notifications |
| `bautopilot_alerts_errors` | Automation Alerts | `IMPORTANCE_HIGH` | Urgent vibration + sound | Critical failures, step timeouts, proxy crashes |

---

### 8.2 Notification UX Wireframes

#### 1. Foreground Running Service Notification
Persistent notification required by Android OS for long-running headless/visible background browser sessions.

```
+-----------------------------------------------------------+
| (B) BAutoPilot * Running Automation                       |
| Profile: "Amazon Checkout Bot" (Step 3/6)                 |
| Submitting order form...                                  |
| [========================>            ] 50%               |
|                                                           |
| [ PAUSE ]              [ ABORT ]             [ LIVE HUD ] |
+-----------------------------------------------------------+
```
* **Icon:** App monochrome outline vector icon.
* **Title:** Profile name and current step index.
* **Content:** Description of active step (e.g., *"Clicking #confirm-btn"*).
* **Progress:** Determinate progress bar showing completed steps percentage.
* **Actions:**
  * `[PAUSE]` / `[RESUME]`: BroadcastIntent to pause session execution.
  * `[ABORT]`: Immediate destructive termination of the underlying WebView instance.
  * `[LIVE HUD]`: Deep-links directly into the full-screen Running Session Monitor.

#### 2. Workflow Completion Notification
```
+-----------------------------------------------------------+
| (B) BAutoPilot * Workflow Completed                 08:14 |
| "Daily Token Refresh" finished successfully in 8.4s       |
| 2 Session Cookies stored * 0 Warnings                     |
|                                                           |
| [ VIEW DIAGNOSTICS ]                  [ DISMISS ]         |
+-----------------------------------------------------------+
```

#### 3. Execution Failure Alert Notification
```
+-----------------------------------------------------------+
| (!) BAutoPilot * Automation Failed                  07:55 |
| "Amazon Checkout Bot" halted at Step 4                    |
| TimeoutException: Element '#checkout' not found (5000ms)  |
|                                                           |
| [ RETRY RUN ]         [ VIEW ERROR SCREENSHOT ]           |
+-----------------------------------------------------------+
```
* **BigTextStyle:** Expands to show the exact selector, error message, and proxy IP used during failure.
* **Action - View Screenshot:** One-tap intent to open the cached PNG captured by the engine at the instant of failure.

---

## 9. Empty States, Error States & Onboarding

### 9.1 Empty State Design Framework
Every screen features a dedicated, encouraging empty state that educates the user on the feature value and provides a frictionless, direct call-to-action (CTA).

```
+-----------------------------------------------------------+
|                                                           |
|                       [ VECTOR ICON ]                     |
|                       (Spacious 64dp)                     |
|                                                           |
|                   No Automation Profiles Yet              |
|                     (Headline Small, 24sp)                |
|                                                           |
|         Create your first browser profile to automate     |
|         repetitive form filling, monitor page changes,    |
|         or orchestrate headless scraping workflows.       |
|                     (Body Medium, 14sp)                   |
|                                                           |
|                 +-----------------------------+           |
|                 |    + Create First Profile   |           |
|                 +-----------------------------+           |
|                                                           |
|               [ Or Explore Pre-built Templates ]          |
|                                                           |
+-----------------------------------------------------------+
```

#### Screen-by-Screen Empty State Inventory

| Screen | Empty State Condition | Illustrated Vector Icon | Title & Explanatory Copy | Primary CTA Button |
| :--- | :--- | :--- | :--- | :--- |
| **Dashboard** | Fresh install / No activity | `Icons.Outlined.RocketLaunch` | *"All Engines Ready"*\nLaunch a workflow or configure your first profile to see live operational stats. | `"Create Profile"` |
| **Profiles** | No profiles stored | `Icons.Outlined.Layers` | *"No Profiles Found"*\nProfiles isolate browser cookies, user agents, and action sequences. | `"New Profile"` |
| **Actions Tab** | Profile has 0 steps | `Icons.Outlined.PlayCircleOutline`| *"Workflow is Empty"*\nAdd sequential steps like Navigate, Click, or Type to begin scripting. | `"Add First Action"` |
| **Scheduler** | No scheduled jobs | `Icons.Outlined.AlarmOn` | *"No Scheduled Tasks"*\nConfigure interval triggers or cron rules to automate background execution. | `"Schedule a Run"` |
| **Proxy Pool** | No custom proxies | `Icons.Outlined.Public` | *"Direct Connection Active"*\nNo proxy servers in pool. Add HTTP or SOCKS5 proxies to mask requests. | `"Add Proxy"` |
| **Diagnostics** | No logs recorded | `Icons.Outlined.Terminal` | *"Clean Diagnostic Slate"*\nLogs, DOM assertion outputs, and network captures appear here during execution. | `"Launch Test Run"` |

---

### 9.2 First-Run Onboarding Flow

When launched for the first time, BAutoPilot displays an interactive 3-step introductory walkthrough with smooth page indicators.

```
+-----------------------------------------------------------+
|  [ Skip ]                                     Step 1 of 3 |
+-----------------------------------------------------------+
|                                                           |
|                   +-------------------+                   |
|                   |   [ ARTWORK 1 ]   |                   |
|                   |   Multi-Profile   |                   |
|                   |     Isolation     |                   |
|                   +-------------------+                   |
|                                                           |
|               Isolated Browser Profiles                   |
|                                                           |
|   Each profile maintains its own sandboxed cookies,       |
|   localStorage, proxy route, and simulated device         |
|   fingerprint without cross-contamination.                |
|                                                           |
|                          (o) ( ) ( )                      |
|                                                           |
|                 +-----------------------------+           |
|                 |          Continue           |           |
|                 +-----------------------------+           |
+-----------------------------------------------------------+
```

#### Onboarding Slides
1. **Slide 1: Isolated Sandboxing**
   * Visual: Multi-layered shield and browser windows.
   * Concept: Separate identity, cookies, and fingerprint for every automation task.
2. **Slide 2: Visual Action Builder**
   * Visual: Connected action blocks (Navigate -> Wait -> Click).
   * Concept: Script complex web interactions visually without writing low-level code.
3. **Slide 3: Autonomous Background Reliability**
   * Visual: Android battery and clock gear symbol.
   * Concept: Unattended WorkManager scheduling and foreground worker reliability with instant failure notifications.
   * CTA: *"Get Started"* (Requests POST_NOTIFICATIONS permission and Battery Optimization Exemption via standard OS dialogs).

---

### 9.3 Pre-built Quick-Start Templates
To lower the barrier to entry, clicking *"Explore Pre-built Templates"* in the empty state opens a bottom sheet offering instant profile blueprints:
* **Template 1: Health & Uptime Checker:** Navigates to a target URL, asserts element exists, checks HTTP status, logs response time.
* **Template 2: Daily Login & Token Refresh:** Navigates to login page, types credentials from secure vault, clicks submit, extracts auth token cookie.
* **Template 3: E-Commerce Stock Monitor:** Refreshes item page every 15 minutes, checks for "In Stock" button, fires high-priority alert when available.
* **Template 4: Cloudflare / Captcha Bypasser Sandbox:** Opens interactive WebView with humanized mouse movement delays and real browser user agents.

---

## 10. Design Specification Verification & Implementation Checklist

* [x] **Material 3 Alignment:** Dynamic color theming with full tonal palettes, Surface Tint Elevation (levels 0–5), and complete type scale mapped to `sp`.
* [x] **4dp Grid Enforcement:** All components, paddings, and margins conform to the standard 4dp/8dp layout scale.
* [x] **Touch Target Compliance:** Minimum `48dp x 48dp` interactive hitboxes guaranteed across all platforms.
* [x] **Adaptive Layouts:** Fully specified Compact (Phone), Medium (Foldable/Landscape), and Expanded (Tablet/Desktop) Window Size Classes with list-detail two-pane support.
* [x] **Complete Screen Inventory:** All 9 core screens documented with detailed wireframes, UI hierarchies, and field-level validation rules.
* [x] **Interaction Standards:** Swipe actions, long-press contextual action bars, pull-to-refresh, modal bottom sheets, and destructive warning dialogs specified.
* [x] **Comprehensive Status System:** IDLE, RUNNING, ERROR, SCHEDULED, PAUSED, and COMPLETED states formalized with colors, icons, and motion.
* [x] **Notification Engine:** Multi-channel notification architecture with foreground progress controls, failure alerts, and action buttons.
* [x] **Accessibility (a11y):** Screen reader TalkBack traversal, live regions, semantic merging, and 200% font scale responsiveness.
* [x] **Empty States & Onboarding:** Frictionless zero-data guidance, onboarding wizard, and pre-built profile templates.

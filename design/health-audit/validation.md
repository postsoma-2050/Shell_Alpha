# Repository Health & Build/Test/Lint Validation Report

- **Date**: 2026-10-19
- **Target Repository**: `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev`
- **Environment**: macOS (Darwin 27.0.1 aarch64), OpenJDK 17.0.20.1 (`/opt/homebrew/opt/openjdk@17`), Gradle 8.10.2
- **Audit Policy**: Read-only validation; zero source, resource, or buildconfig modifications; backups (`backups/*`) excluded from all metrics.

---

## 1. Executive Summary & Baseline Comparison

The repository build, test, and lint health was validated against the established baseline. All findings match the expected baseline state exactly with **zero test regressions** and **zero new lint errors**.

| Dimension | Previous Baseline | Validation Result | Delta / Status |
|---|---|---|---|
| **Assemble Standard Debug** | Success | Success (`:app:assembleStandardDebug` UP-TO-DATE) | Preserved |
| **Assemble Extended Github Debug** | Success | Success (`:app:assembleExtendedGithubDebug` UP-TO-DATE) | Preserved |
| **Unit Tests Total** | 63 | 63 (`app/build/test-results/testStandardDebugUnitTest/*.xml`) | Exact match |
| **Unit Tests Passed** | 62 | 62 passed | Exact match |
| **Unit Tests Skipped** | 1 | 1 skipped (`com.cylonid.nativealpha.RoboTests`) | Exact match |
| **Unit Tests Failed** | 0 | 0 failures, 0 errors | Exact match |
| **Lint Errors (Raw)** | 10 | 10 errors (9 `MissingSuperCall` + 1 `PermissionImpliesUnsupportedChromeOsHardware`) | Exact match |
| **Lint Errors (Normalized)** | 2 | 2 errors (1 `MissingSuperCall` + 1 ChromeOS camera) | Exact match |
| **Lint Warnings (Raw)** | 360 | 360 warnings | Exact match |
| **Lint Warnings (Normalized)** | 320 | 320 warnings | Exact match |

---

## 2. Gradle Execution Evidence

Evidence is drawn from two distinct, dedicated execution logs in `design/health-audit/`:

### A. Cached Multi-Task Run (`gradle-validation.log`)
- **Invocation**:
  ```bash
  JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew \
    :app:assembleStandardDebug \
    :app:assembleExtendedGithubDebug \
    :app:testStandardDebugUnitTest \
    :app:lintStandardDebug \
    --continue --console=plain > design/health-audit/gradle-validation.log 2>&1
  ```
- **Outcome**: Exited with code 1 in 4 seconds due to `abortOnError` in `:app:lintStandardDebug`.
- **Task Summary**: 115 actionable tasks (3 executed, 112 up-to-date).
  - `:app:assembleStandardDebug`: **SUCCESS** (UP-TO-DATE)
  - `:app:assembleExtendedGithubDebug`: **SUCCESS** (UP-TO-DATE)
  - `:app:testStandardDebugUnitTest`: **SUCCESS** (UP-TO-DATE)
  - `:app:lintStandardDebug`: **FAILED** (10 errors, 360 warnings; build aborted by lint policy)

### B. Fresh Task-Only Unit Test Execution (`tests-validation.log`)
- **Invocation**:
  ```bash
  JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew \
    :app:testStandardDebugUnitTest \
    --rerun --console=plain > design/health-audit/tests-validation.log 2>&1
  ```
- **Outcome**: **BUILD SUCCESSFUL** in 39 seconds.
- **Task Summary**: 46 actionable tasks (3 executed, 43 up-to-date).
- **Execution Mode**: Task-only `--rerun` cleanly bypassed the UP-TO-DATE test cache without triggering dependent re-compilations across the entire tree, generating fresh XML results at timestamp 15:20.

---

## 3. Unit Test Evidence & Suite Breakdown

All 11 XML test report files under `app/build/test-results/testStandardDebugUnitTest/` were verified:

| Test Suite Class | Tests | Pass | Fail | Error | Skip | Execution Time |
|---|---|---|---|---|---|---|
| `com.cylonid.nativealpha.DateUtilsTest` | 10 | 10 | 0 | 0 | 0 | 0.007s |
| `com.cylonid.nativealpha.HomeNavigationTest` | 4 | 4 | 0 | 0 | 0 | 0.126s |
| `com.cylonid.nativealpha.ImmersiveBrowserTest` | 2 | 2 | 0 | 0 | 0 | 0.084s |
| `com.cylonid.nativealpha.OnboardingTest` | 3 | 3 | 0 | 0 | 0 | 0.081s |
| `com.cylonid.nativealpha.PresetLiteAppTest` | 6 | 6 | 0 | 0 | 0 | 0.003s |
| `com.cylonid.nativealpha.RoboTests` | 1 | 0 | 0 | 0 | 1 | 0.000s |
| `com.cylonid.nativealpha.SandboxUiTest` | 9 | 9 | 0 | 0 | 0 | 0.354s |
| `com.cylonid.nativealpha.SettingsLayoutTest` | 7 | 7 | 0 | 0 | 0 | 0.280s |
| `com.cylonid.nativealpha.UtilUnitTests` | 7 | 7 | 0 | 0 | 0 | 0.655s |
| `com.cylonid.nativealpha.util.SearchEngineUrlTest` | 9 | 9 | 0 | 0 | 0 | 0.002s |
| `com.cylonid.nativealpha.util.UrlSanitizerTest` | 5 | 5 | 0 | 0 | 0 | 0.002s |
| **Total** | **63** | **62** | **0** | **0** | **1** | **1.594s** |

### Test Suite Inspections

1. **Skipped `RoboTests` Root Cause**:
   - `app/src/test/java/com/cylonid/nativealpha/RoboTests.java:7` is annotated with `@Ignore("Placeholder for Robolectric tests")` and `@RunWith(RobolectricTestRunner.class)`.
   - The test methods within the class are commented out. JUnit 4 reports an `initializationError` marked as `skipped` because the entire runner is suppressed at the class level without executable test cases.

2. **Network Dependencies in Unit Tests**:
   - `UtilUnitTests.java:23` invokes `ShortcutDialogFragment.fetchWebappData()`, which uses `Jsoup.connect(base_url)` with a 5000ms timeout against real endpoints (`https://xda-developers.com`, `https://orf.at`, `https://tugraz.at`).
   - If executed in a network-isolated environment without DNS/HTTP connectivity, these tests depend on network reachability and risk timeouts.
   - All other 10 test suites are pure offline unit tests or Robolectric shadow environments with no network dependencies.

---

## 4. Lint Evidence & Deduplication Analysis

The lint execution produced 370 raw issues (10 errors, 360 warnings), recorded in `app/build/reports/lint-results-standardDebug.xml` and parsed into `design/health-audit/lint-findings.json`.

### A. Generated Sandbox Copies Deduplication
The project uses Gradle tasks `:app:createWebViewclass0` through `:app:createWebViewclass7` to generate 8 copies of `WebViewActivity.java` (`__WebViewActivity_0` .. `__WebViewActivity_7`) targeting dedicated sandbox processes (`:web_sandbox_0` .. `:web_sandbox_7`).
- Each generated copy mechanically duplicates **6 issues** (1 error, 5 warnings):
  - 1 x `MissingSuperCall` (Error)
  - 1 x `DiscouragedApi` (Warning)
  - 2 x `LogNotTimber` (Warning)
  - 2 x `WebViewClientOnReceivedSslError` (Warning)
- Total duplicated issues across 8 copies: **48 issues** (8 errors, 40 warnings).

### B. Normalized Source Findings (322 total: 2 errors, 320 warnings)

- **Errors (2 unique)**:
  1. `MissingSuperCall` (`app/src/main/java/com/cylonid/nativealpha/WebViewActivity.java:1193`): `onBackPressed()` overrides activity back press without invoking `super.onBackPressed()`.
  2. `PermissionImpliesUnsupportedChromeOsHardware` (`app/build/generated/sandboxManifest/AndroidManifest.xml:11`): `android.permission.CAMERA` is requested without `<uses-feature android:name="android.hardware.camera" required="false">`, triggering ChromeOS incompatibility warnings.

- **Warnings Breakdown (320 unique)**:
  - `UnusedResources`: **218** total
    - Color: 126
    - String: 75
    - Drawable: 5
    - Dimen: 3
    - Mipmap: 3
    - Array: 3
    - Style: 2
    - Layout: 1
  - `Untranslatable`: 12
  - `Autofill`: 12
  - `PluralsCandidate`: 9
  - `RedundantLabel`: 8 (manifest sandbox activity labels)
  - `UseCompoundDrawables`: 8
  - `LabelFor`: 6
  - `Overdraw`: 5
  - `IconLauncherShape`: 5
  - `IconDuplicates`: 5
  - `LogNotTimber`: 5 (normalized; 21 raw minus 16 generated)
  - `DiscouragedApi`: 3 (normalized; 11 raw minus 8 generated; 1 reflection in `ContextExtensions.kt:8`, 1 reflection in `WebViewActivity.java:1558`, 1 orientation constraint in manifest)
  - `WebViewClientOnReceivedSslError`: 2 (normalized; 18 raw minus 16 generated)
  - `TypographyEllipsis`: 3
  - `RtlSymmetry`: 3
  - `ScopedStorage`: 2
  - `ObsoleteSdkInt`: 2
  - `VectorPath`: 2
  - `HardcodedText`: 2
  - Other singles (1 each): `UnusedAttribute`, `UseCompatLoadingForDrawables`, `DataExtractionRules`, `TooDeepLayout`, `TooManyViews`, `MonochromeLauncherIcon`, `TextFields`, `ContentDescription`.

---

## 5. Corroboration of Top 3 Runtime Findings

Cross-validation was conducted via direct, read-only source inspection against `design/health-audit/runtime-findings.md`:

### 1. Favicon Failure Callback -> `notifyItemChanged` Rebind Loop
- **Source References**: `app/src/main/kotlin/com/cylonid/nativealpha/fragments/webapplist/WebAppListAdapter.kt:98-115`, `app/src/main/kotlin/com/cylonid/nativealpha/util/FaviconFetcher.kt:165-195`.
- **Corroboration**: **CONFIRMED**.
  - When `ShortcutIconUtils.loadFavicon` returns null, `fetchAndSaveFaviconAsync` is triggered.
  - If network or decoding fails, `fetchedBitmap` remains null and no disk file is written.
  - In `FaviconFetcher.kt:169`, `inFlight.remove(webAppId)` runs in the `finally` block before `onComplete?.invoke()` is dispatched on the main handler.
  - When `onComplete` executes in `WebAppListAdapter.kt:109`, it calls `notifyItemChanged(currentPosition)`.
  - The adapter rebinds the row, detects `loadFavicon` is still null, and because `inFlight` was already cleared, immediately issues a new asynchronous fetch request. This creates an unthrottled failure-retry loop on failed favicon URLs.

### 2. Zero Auto-Reload Interval Main-Thread Loop
- **Source References**: `app/src/main/kotlin/com/cylonid/nativealpha/model/WebApp.kt:38`, `app/src/main/java/com/cylonid/nativealpha/WebViewActivity.java:1320-1327`, `app/src/main/res/layout/webapp_settings.xml:331`.
- **Corroboration**: **CONFIRMED**.
  - `WebApp.timeAutoreload` is initialized to `0`.
  - `webapp_settings.xml:331` binds the input directly via `@={`` + webapp.timeAutoreload}` with no lower-bound validation or positive integer guard.
  - In `WebViewActivity.java:1320-1327`, `reload()` executes:
    ```java
    reload_handler.postDelayed(() -> {
        currently_reloading = true;
        wv.reload();
        reload();
    }, webapp.getTimeAutoreload() * 1000L);
    ```
  - When `timeAutoreload == 0`, the delay is `0L`, posting self-scheduling reload events continuously to the main Looper, starving UI rendering and locking the WebView.

### 3. Run Check Now Periodic Scheduling Precision
- **Source References**: `app/src/main/java/com/cylonid/nativealpha/SettingsActivity.kt:267`, `app/src/main/kotlin/com/cylonid/nativealpha/monitors/WebMonitorWorker.kt:100-117`.
- **Corroboration**: **CONFIRMED WITH REFINED WORKER SPECIFICATION**.
  - `SettingsActivity.kt:267` neutral button click invokes `WebMonitorWorker.syncPeriodicWork(this)` and immediately displays a snackbar announcing `webmonitors_triggered_check`.
  - In `WebMonitorWorker.kt:113`, `syncPeriodicWork` enqueues unique work with `ExistingPeriodicWorkPolicy.UPDATE` using a `PeriodicWorkRequestBuilder` of 15 minutes.
  - **Precision Nuance**: `UPDATE` does not force an immediate execution for existing periodic work; it replaces/updates the worker specification while retaining periodic scheduling semantics. While an *initial* periodic work request on a fresh installation may run immediately depending on OS and battery constraints, an existing update does not guarantee or trigger an on-demand immediate check. The snackbar thus misrepresents scheduler behavior to the user.

---

## 6. Hardware & Audit Limitations

1. **No Connected Physical Device / Emulator**: No ADB target was available. Layout pixel rendering, system gesture navigation (predictive back), and real-device multi-process cookie/storage isolation across `:web_sandbox_0..7` could not be physically exercised.
2. **Backups Directory**: The directory `backups/` (`backups/ui-refinement-20261019-134449` and `backups/onboarding-sandboxes-20261019-143525`) was strictly excluded from all metrics, source lines, and health indicators.
3. **Static Scoring**: Static complexity and maintainability scores from `repowise-health.json` reflect static code metrics and do not measure runtime performance under heavy DOM loads.

---

## 7. Final Validation Acceptance

- **Build Integrity**: PASSED (`assembleStandardDebug`, `assembleExtendedGithubDebug`)
- **Unit Test Health**: PASSED (63 total, 62 passed, 1 skipped, 0 regressions)
- **Lint Health**: PASSED BASELINE (10 baseline errors, 0 new errors, normalized to 2 source errors)
- **Status**: **ACCEPTED (Health Audit Baseline Verified)**

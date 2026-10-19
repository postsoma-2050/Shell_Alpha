# ShellAlpha Onboarding, Sandboxes & Navigation QA Report

Date: 2026-10-19
Project: NativeAlphaForAndroid-dev (ShellAlpha v1.5.2)

---

## 1. Build, Test, and Lint Verification

- **Gradle Build**:
  - `assembleStandardDebug`: **SUCCESS** (universal APK output: `app/build/outputs/apk/standard/debug/ShellAlpha-standard-universal-debug.apk`)
  - `assembleExtendedGithubDebug`: **SUCCESS**
- **Unit Tests (`testStandardDebugUnitTest`)**:
  - **Total**: 63 tests
  - **Passed**: 62 passed
  - **Failures**: 0 failures
  - **Skipped**: 1 skipped (`RoboTests.java` legacy test)
  - Key suites:
    - `OnboardingTest`: 3 tests passed (Dynamic versioning, existing user migration without re-prompting EULA, Chinese language rendering).
    - `SandboxUiTest`: 9 tests passed (Management mode, narrow-screen 48dp touch targets, Chinese resource names, container selection & flag synchronization, Default clearing flag, switch persistence, cancellation without live mutation, legacy/invalid route fallback, settings activity metadata preservation).
    - `HomeNavigationTest`: 4 tests passed (Hamburger open, back press lifecycle callback, SandboxActivity intent target, compact/wide Chinese labels and IDs).
- **Lint Audit (`lintStandardDebug`)**:
  - 10 baseline errors (9 `MissingSuperCall` in `WebViewActivity` and its 8 generated process copies, 1 `PermissionImpliesUnsupportedChromeOsHardware` in manifest).
  - **0 new lint errors** introduced across all modules.

---

## 2. Reference Similarities (Hermit Alignment)

- **Main Navigation Drawer**:
  - Brand header containing ShellAlpha foreground logo and headline.
  - Selected navigation pill styling using `colorPrimaryContainer` with rounded corners.
  - Consistent 56dp item height and 48dp minimum touch targets.
- **Sandboxes Experience**:
  - Large title layout with expandable rounded cards for Default and 8 color containers.
  - Expandable content showing assigned Lite Apps without cross-process clearing or fake incognito claims.
  - Clicking assigned Lite App opens `WebAppSettingsActivity`.

---

## 3. Sandboxes Capability & Boundary (8 Containers)

- **Containers Count**: Strictly Default (`NO_CONTAINER = -1`) plus 8 distinct colored containers (0..7: Blue, Green, Orange, Purple, Teal, Pink, Amber, Indigo).
- **Process Isolation**: Containers 0..7 route to independent Android processes (`:web_sandbox_0` .. `:web_sandbox_7`) with `setDataDirectorySuffix`.
- **Default Exit**: Selecting Default resets `containerId = -1` and `isUseContainer = false`. When switching from a container to Default in `WebViewActivity`, process migration is triggered via ProcessPhoenix and safely returns immediately.
- **Metadata Protection**:
  - `WebApp(other: WebApp)` copy constructor explicitly preserves `order`, `isActiveEntry`, and `lastMonitorHash`.
  - Live object is not mutated until user confirms selection.
  - Invalid container IDs are sanitized to `NO_CONTAINER`.

---

## 4. Onboarding Behavior (New vs. Existing Users)

- **New Users**:
  - Prompts with dynamic version (`BuildConfig.VERSION_NAME` = 1.5.2).
  - Displays feature breakdown, data boundary explanation, upstream GPL v3 attribution (crediting cylonid and NativeAlpha), and the full EULA disclaimer.
  - Action button: "I understand, continue" / "了解并继续".
  - On confirm: records `eulaAccepted = true`, `lastShownUpdate = BuildConfig.VERSION_CODE`, and `shellAlphaOnboardingRevision = 1`.
- **Existing Users**:
  - When `eulaAccepted` is already true, the EULA card is hidden and the button reads "Continue" / "继续".
  - After confirming current onboarding revision, subsequent launches skip that revision; future notice revisions may be shown.

---

## 5. Artifact & Dist Verification

- **Dist APK**: `dist/ShellAlpha-1.5.2-hermit-sandbox-debug.apk`
- **Source**: `app/build/outputs/apk/standard/debug/ShellAlpha-standard-universal-debug.apk`
- **SHA256**: `8559edfe2499bf619572eac9aeaf993091c1ccccb14980e4e39ec00da467832b`
- **Size**: 26,064,589 bytes
- **AAPT Inspection**:
  - `resourceConfigurations`: Only `en` and `zh-rCN`.
  - Manifest includes `com.cylonid.nativealpha.SandboxActivity`.
  - Manifest includes 8 sandboxed activities `.__WebViewActivity_0` .. `.__WebViewActivity_7` targeting `:web_sandbox_0` .. `:web_sandbox_7`.
- **Backup Snapshot**: `backups/onboarding-sandboxes-20261019-143525/`



---

## 6. Validation Limits

- **Hardware & Device State**: No ADB-connected device or running emulator was available in the environment.
- **Verification Scope**: Robolectric unit tests and static AAPT/Manifest inspection verified XML layouts, component IDs, localization strings, lifecycle callbacks, and intent routing.
- **Unverified on Real Hardware**: Physical screen pixel rendering, native system predictive-back gestures, and real-device multi-process Cookie/storage data directory isolation and switching have not been physically verified on hardware.

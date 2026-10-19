# Runtime Findings

Date: 2026-10-19  
Scope: repository health, dead/no-op behavior, and only the runtime paths for Web Monitor scheduling and Run Check Now, favicon retries, periodic reload, unconnected settings, and WebApp identity allocation.  
Method: read-only source tracing of production code, resources, call sites, getter names, and Data Binding expressions. No source or Gradle changes were made. Backups, build output, and generated `__WebViewActivity_0..7` copies were excluded. Reflection and dynamic binding were treated as live.

## Priority summary

| Priority | Type | Finding | Certainty |
|---|---|---|---|
| P1 | Bug | Run Check Now updates periodic work but does not force a fresh check | Confirmed |
| P1 | Bug | A failed favicon fetch can immediately retry when its row is rebound | Confirmed trigger and loop path |
| P1 | Bug | Restored non-contiguous IDs break identity lookup and index-based routing; adding an app can also collide | Confirmed trigger, routing crash, and collision path |
| P2 | Bug | Zero auto-reload intervals create a main-thread reload loop | Confirmed |
| P2 | Bug | “Clear cookies after usage” is persisted but never applied | Confirmed field state and absent lifecycle consumer; residual limitation below |

## Bugs

### 1. Run Check Now does not force an immediate check

**Evidence**

- `app/src/main/java/com/cylonid/nativealpha/SettingsActivity.kt:267` wires the neutral dialog button to `WebMonitorWorker.syncPeriodicWork(this)` and then reports `webmonitors_triggered_check`.
- `app/src/main/kotlin/com/cylonid/nativealpha/monitors/WebMonitorWorker.kt:100` implements `syncPeriodicWork` only to inspect whether monitors exist and enqueue or cancel unique periodic work.
- `app/src/main/kotlin/com/cylonid/nativealpha/monitors/WebMonitorWorker.kt:109` builds a 15-minute periodic request.
- `app/src/main/kotlin/com/cylonid/nativealpha/monitors/WebMonitorWorker.kt:113` applies `ExistingPeriodicWorkPolicy.UPDATE`; this is scheduling policy, not a one-time work enqueue or direct call to `monitor`.
- The actual network fetch and hash comparison are in `app/src/main/kotlin/com/cylonid/nativealpha/monitors/WebMonitorWorker.kt:29` through `:57`.

**Trigger:** Open Global Settings → Web Monitors and select **Run Check Now**.

**Consequence:** The button enqueues or updates a periodic schedule and immediately displays a success message. If periodic work already exists, this does not force a fresh execution for the button press. A newly created periodic request may be eligible to run immediately; therefore it would be incorrect to claim that the first execution must wait 15 minutes. Actual execution remains subject to WorkManager scheduling, with no guaranteed upper bound from this code alone.

**Certainty:** Confirmed from the click handler through the scheduler implementation. The missing explicit one-time execution path and unconditional success message are deterministic; whether work happens to execute immediately is scheduler-dependent. The exact next Worker execution time is scheduler-dependent.

**Why it matters:** This is a direct behavior mismatch on an explicitly named user action, and it masks whether monitoring is working.

### 2. Favicon failures can immediately re-enter the network pipeline

**Evidence**

- `app/src/main/kotlin/com/cylonid/nativealpha/fragments/webapplist/WebAppListAdapter.kt:98` checks only for a cached favicon.
- `app/src/main/kotlin/com/cylonid/nativealpha/fragments/webapplist/WebAppListAdapter.kt:101` starts a fetch whenever the cache lookup misses.
- `app/src/main/kotlin/com/cylonid/nativealpha/fragments/webapplist/WebAppListAdapter.kt:103` supplies an `onComplete` callback that rebinds the row.
- `app/src/main/kotlin/com/cylonid/nativealpha/fragments/webapplist/WebAppListAdapter.kt:109` calls `notifyItemChanged`, returning the row to `onBindViewHolder`.
- `app/src/main/kotlin/com/cylonid/nativealpha/util/FaviconFetcher.kt:31` prevents only overlapping requests for the same ID.
- `app/src/main/kotlin/com/cylonid/nativealpha/util/FaviconFetcher.kt:169` removes the in-flight marker before the completion callback is posted.
- `app/src/main/kotlin/com/cylonid/nativealpha/util/FaviconFetcher.kt:192` invokes the callback after the marker has been removed.
- `app/src/main/kotlin/com/cylonid/nativealpha/util/FaviconFetcher.kt:172` saves only when a bitmap was obtained; a failed fetch creates no negative cache or retry state.
- `app/src/main/java/com/cylonid/nativealpha/MainActivity.kt:349` refreshes the list after adding a site, while `:352` also starts an explicit fetch for the new site. A first bind can therefore initiate the adapter fetch too; the in-flight set coalesces those overlapping calls, but the completion rebind can start the next attempt.

**Trigger:** Bind a WebApp without a saved favicon whose HTML and fallback providers fail, or whose downloaded bytes cannot be decoded. The same rebind path is reached when an automatic fetch completes or the list is refreshed.

**Consequence:** Each completion removes the in-flight guard, rebinds the row, finds no cached favicon, and starts another fetch. The row can repeatedly issue the page request plus manifest, candidate, Google, DuckDuckGo, Apple-touch, and `/favicon.ico` attempts. The fixed four-thread executor can stay occupied with retries, delaying favicon work for other apps and adding avoidable network and parse work.

**Certainty:** Confirmed for the adapter-driven failure path. The rebind and second attempt follow directly from the cited state transitions. Whether the loop continues indefinitely depends on the row remaining bound and the app remaining active; the fetch executor has no delay or backoff.

**Why it matters:** This turns a normal missing-icon case into repeated network and CPU work. It is also a duplicate-fetch design defect around creation, although the in-flight set does coalesce truly simultaneous requests.

### 3. Restored non-contiguous identities break lookup, routing, and allocation

**Evidence**

- `app/src/main/java/com/cylonid/nativealpha/MainActivity.kt:341` constructs a new WebApp using `DataManager.getInstance().incrementedID`.
- `app/src/main/java/com/cylonid/nativealpha/model/DataManager.java:252` implements `getIncrementedID()` as `getWebsites().size()`.
- `app/src/main/java/com/cylonid/nativealpha/model/DataManager.java:247` appends the new WebApp without checking for an existing ID.
- `app/src/main/java/com/cylonid/nativealpha/model/DataManager.java:427` accepts a valid JSON backup by schema and checksum.
- `app/src/main/java/com/cylonid/nativealpha/model/DataManager.java:491` whitelists `WEBSITEDATA` and `MAX_ID`, then writes their supplied values without validating the relationship between WebApp IDs, list positions, and the maximum ID.
- `app/src/main/java/com/cylonid/nativealpha/model/DataManager.java:187` loads `MAX_ID` from that same backup.
- `app/src/main/java/com/cylonid/nativealpha/model/DataManager.java:252` still ignores the persisted maximum and allocates the current list size.
- `app/src/main/java/com/cylonid/nativealpha/model/DataManager.java:282` resolves a WebApp by the first matching ID.
- `app/src/main/java/com/cylonid/nativealpha/model/DataManager.java:292` falls back to using an ID as a list index when no exact ID match exists.
- `app/src/main/java/com/cylonid/nativealpha/model/DataManager.java:308` replaces by ID and therefore targets the first matching entry.
- `app/src/main/java/com/cylonid/nativealpha/model/DataManager.java:550` and `:562` use the WebApp ID as a list index for predecessor/successor navigation.
- `app/src/main/kotlin/com/cylonid/nativealpha/util/ShortcutIconUtils.kt:97` stores favicon files by WebApp ID, so two entries with the same ID share `webapp_<id>.png`.
- `app/src/main/java/com/cylonid/nativealpha/model/DataManager.java:84` declares a persisted `max_assigned_ID`, but production code never updates it; `:252` ignores it.

**Trigger:** Restore a valid JSON backup containing two entries with IDs `0` and `2`.

**Consequence:** Three-finger predecessor/successor navigation treats ID `2` as list index 2 and throws `IndexOutOfBoundsException`. If an app with an unknown ID is requested, the lookup fallback at `app/src/main/java/com/cylonid/nativealpha/model/DataManager.java:292` can return the entry at that list index instead. If the user then adds an app, size-based allocation assigns ID `2` again, so ID lookup, replacement, shortcut routing, and the ID-keyed favicon file can resolve to the wrong entry.

**Certainty:** Confirmed for the restored-ID sequence above. The backup schema/checksum check does not guarantee contiguous IDs, and no ID normalization occurs. Ordinary create/delete/create in the current UI does not reuse an ID because deletion is a soft delete and the list remains append-only; that earlier interpretation was too broad.

**Why it matters:** WebApp IDs are used as durable identity keys and as list indexes. A schema-valid backup with non-contiguous IDs can cause a routing crash, wrong-entry updates, and shared favicon state.

### 4. Zero auto-reload interval creates a main-thread reload loop

**Evidence**

- `app/src/main/res/layout/webapp_settings.xml:324` exposes an editable numeric interval with no lower bound or validation.
- `app/src/main/res/layout/webapp_settings.xml:331` binds that text directly to `webapp.timeAutoreload`.
- `app/src/main/kotlin/com/cylonid/nativealpha/model/WebApp.kt:38` defaults the interval to `0`.
- `app/src/main/java/com/cylonid/nativealpha/WebViewActivity.java:1291` starts reload scheduling whenever `isAutoreload` is enabled.
- `app/src/main/java/com/cylonid/nativealpha/WebViewActivity.java:1320` schedules the next reload with `webapp.getTimeAutoreload() * 1000L`.
- `app/src/main/java/com/cylonid/nativealpha/WebViewActivity.java:1321` recursively posts the next reload from the delayed callback.

**Trigger:** Enable **Refresh page regularly** and leave the interval at its default `0`, or enter `0`.

**Consequence:** Every callback immediately posts another delayed callback with a zero delay and calls `wv.reload()`. The Handler runs on the main looper, so this can continuously restart page loads and consume UI-thread and WebView resources until the activity is paused or the setting is changed.

**Certainty:** Confirmed from the default value, unchecked input, multiplication, and recursive `postDelayed` path. No minimum-interval guard exists in production code.

**Why it matters:** A user can enter this state through the normal UI without malformed data.

### 5. “Clear cookies after usage” has no runtime effect

**Evidence**

- `app/src/main/res/values/strings.xml:20` defines the user-facing option as **Clear cookies after usage**.
- `app/src/main/java/com/cylonid/nativealpha/SettingsActivity.kt:200` exposes that option in the Privacy dialog.
- `app/src/main/java/com/cylonid/nativealpha/SettingsActivity.kt:210` stores the choice in `settings.clear_cookies`.
- `app/src/main/java/com/cylonid/nativealpha/model/GlobalSettings.kt:15` persists the field.
- `app/src/main/java/com/cylonid/nativealpha/WebViewActivity.java:1300` is the lifecycle cleanup path. It clears cache at `:1311`, but never reads `clear_cookies` or removes cookies.
- The other `removeAllCookies` calls in `app/src/main/java/com/cylonid/nativealpha/SettingsActivity.kt:229` and `:324` are the explicit **clear now** action and backup import; neither applies the after-usage setting.

**Trigger:** Enable Privacy → **Clear cookies after usage**, use a Lite App, then leave or pause it.

**Consequence:** The current source does not enforce the saved option. The UI promises cleanup, but the WebView lifecycle path that clears cache on pause has no matching cookie removal.

**Certainty:** Confirmed in this source tree: the field is write-only and the only cookie removal calls are unrelated one-shot operations. I did not execute the app, so persistence of the actual cookie store after a pause remains a runtime verification gap rather than a separately demonstrated failure.

**Why it matters:** This is a privacy toggle whose state is disconnected from its advertised behavior.

## Inefficiency

### Monitor scheduling performs repeated state I/O

`WebMonitorWorker.syncPeriodicWork` reloads all WebApp data at `app/src/main/kotlin/com/cylonid/nativealpha/monitors/WebMonitorWorker.kt:101`, and it is called from several normal entry points:

- `app/src/main/java/com/cylonid/nativealpha/MainActivity.kt:75` on every resume.
- `app/src/main/java/com/cylonid/nativealpha/WebViewActivity.java:614` after every monitor switch change.
- `app/src/main/java/com/cylonid/nativealpha/WebAppSettingsActivity.kt:107` after every settings save.
- `app/src/main/java/com/cylonid/nativealpha/SettingsActivity.kt:326` after import.

Each call also enqueues the same unique periodic work with `UPDATE` when any monitor is active (`app/src/main/kotlin/com/cylonid/nativealpha/monitors/WebMonitorWorker.kt:109`). This is unnecessary scheduler churn and a full JSON/shared-preferences reload on routine UI returns. The behavior is not incorrect, but it adds avoidable work and makes the scheduling entry point harder to distinguish from an immediate-check action.

**Certainty:** Confirmed; severity is low to moderate because the schedule is unique and WorkManager coalesces it.

## No-op note

The persisted `GlobalSettings.swipeGestureMode` and `WebApp.isAllowMediaPlaybackInBackground` fields have no consumer after checking the Kotlin property names, their generated JVM-style getter spellings, layout expressions, resources, and runtime paths. `swipeGestureMode` appears only in `app/src/main/java/com/cylonid/nativealpha/model/GlobalSettings.kt:18` and a hidden legacy spinner at `app/src/main/res/layout/global_settings.xml:454`; the spinner has no listener or binding. The media-playback field appears only at `app/src/main/kotlin/com/cylonid/nativealpha/model/WebApp.kt:56` and in copy logic at `:130`; its localized string is unused by layouts, and no WebView media setting reads it.

Getter casing changes the conclusion for two fields. Kotlin property `toolbarPosition` is consumed through generated Java `getToolbarPosition()` at `app/src/main/java/com/cylonid/nativealpha/WebViewActivity.java:740`, and `alwaysShowSoftwareButtons` is consumed through `getAlwaysShowSoftwareButtons()` at `:267`. Both affect runtime behavior and are not dead fields.

`toolbarPosition` has no configured UI control: the hidden spinner at `app/src/main/res/layout/global_settings.xml:453` has no listener or Data Binding expression in `SettingsActivity`. It is a live runtime field with an unconnected legacy UI slot, rather than an unused runtime field.

## Exclusions and limits

- Generated `__WebViewActivity_0..7` copies were excluded because Gradle generates the process entry points from `WebViewActivity`.
- Backups, build output, and historical research reports were not used as evidence for current runtime behavior.
- Reflection and Data Binding were treated as live. Getter names were checked explicitly.
- This was a static, read-only audit. No Gradle build or test run was performed, and no application files were modified.
- The monitor findings describe scheduling and fetch behavior visible in source. WorkManager's exact delivery timing remains subject to Android scheduling constraints and is not asserted beyond the 15-minute periodic request.

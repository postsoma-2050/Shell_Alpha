# Dead-code and disabled-path audit

Audit date: 2026-10-19  
Repository: `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev`  
Mode: read-only; no source files, dependencies, resources, or generated activities were deleted.

## Scope and evidence base

- The clean RepoWise dead-code run contains 7 records and every record has `safe_to_delete: false`: `design/health-audit/repowise-dead-code.json`. `design/health-audit/repowise-health.json` scores 69 files after exclusions at average health 8.3. Of these, 61 are maintained Java/Kotlin production or test files; the remaining files are assets, documentation, and the wrapper script.
- The earlier raw run contained 80 records, including snapshots. `.repowiseIgnore:2-12` now excludes backups, `dist`, audit output, Gradle/Kotlin caches, build trees, and the generated sandbox activity copies. The clean result therefore retains the seven maintained-file records.
- References were checked across source, tests, Android XML, the manifest, Gradle declarations, and asset names. Android XML, databinding, manifest classes, reflection, and generated process entry points were treated as possible live references.
- The eight `app/src/main/java/com/cylonid/nativealpha/__WebViewActivity_*` copies are live. `WebViewLauncher.kt:45-53` loads the base class or a sandbox class with `Class.forName`, and `app/build.gradle:177-221` generates those classes and their process manifest entries. They are excluded from quality scoring only and are not dead-code candidates.
- The repository has no `.git` directory, so this audit cannot attribute the current state to a commit or compare it with prior revisions.
- No Gradle task was run for this audit. Existing artifacts show a successful unit-test run (`design/health-audit/tests-validation.log`) and a lint run with 10 errors and 360 warnings (`design/health-audit/gradle-validation.log`). The leading lint failure is in the live WebView activity and is outside this dead-code scope.

## Strongest findings

### 1. Confirmed: the old HTML news pages and their stylesheet are unreachable

Evidence:

- `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/assets/news/latestUpdate_en.html:5`
- `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/assets/news/latestUpdate_de.html:5`
- `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/assets/news/news.css:1`
- The live activity is declared at `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/AndroidManifest_original.xml:92-98`.
- `NewsActivity` inflates the native databinding layout at `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/kotlin/com/cylonid/nativealpha/activities/NewsActivity.kt:19-20`; that layout is `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/res/layout/news_activity.xml:1-75`.
- Repository-wide asset-name and `loadUrl` searches found no loader for `latestUpdate_en.html`, `latestUpdate_de.html`, or `news.css`. `news.css` is referenced only by those two HTML files. The sole packaged-asset URL in the WebView implementation is the error page at `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/java/com/cylonid/nativealpha/WebViewActivity.java:1689`.

Status: **confirmed dead in the current app.** The HTML files are packaged as assets but have no current runtime path from the live news/onboarding screen.

Deletion risk: **low.** They may retain historical or marketing value, but deleting the two HTML files and the stylesheet together would not remove the live native news screen.

### 2. Confirmed: the global-settings legacy compatibility views and listeners are a disabled path

Evidence:

- `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/res/layout/global_settings.xml:443-466` contains a `0dp` by `0dp`, `gone` “Compatibility hidden legacy views” container.
- `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/java/com/cylonid/nativealpha/SettingsActivity.kt:94-115` attaches listeners to hidden buttons from that container.
- The live settings controls are the visible paths at `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/java/com/cylonid/nativealpha/SettingsActivity.kt:44-92`.
- Repository searches found no other source or test use of the hidden IDs. The controls without a compatibility listener are `textSearchEngineUrl`, `spinnerToolbarPosition`, `spinnerSwipeGestureMode`, `switchGlobalCache`, `switchGlobalMultiTouchReload`, `switchGlobalMultiTouch`, `switchGlobalMultiTouch3Fingers`, `switchShowProgressbar`, and `switchAlwaysShowSoftwareButtons`. The hidden buttons are referenced only by the compatibility listeners.

Status: **confirmed dead UI path in this repository.** Removing only the hidden views and their listeners would not remove the backing settings or browser behavior. Those values remain loaded by `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/java/com/cylonid/nativealpha/model/DataManager.java:224-231` and consumed in the live WebView at `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/java/com/cylonid/nativealpha/WebViewActivity.java:267` and `:740`.

Deletion risk: **low to medium.** In-repo tests only inflate and measure `GlobalSettingsBinding`; they do not use the hidden IDs (`app/src/test/java/com/cylonid/nativealpha/SettingsLayoutTest.java:44-55`). External instrumentation or compatibility tooling could still look up the hidden view IDs, which is the reason to remove the UI block as a deliberate compatibility change rather than as an isolated ID cleanup.

### 3. Confirmed: `ResourceImageGetter` and `Context.resIdByName` are an unused pair

Evidence:

- `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/java/com/cylonid/nativealpha/util/ResourceImageGetter.kt:7-15`
- `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/kotlin/com/cylonid/nativealpha/util/ContextExtensions.kt:6-11`
- `ResourceImageGetter` calls `context.resIdByName(...)` at line 10, which is its only reference.
- Searches found no importer or caller for `ResourceImageGetter` or `resIdByName` elsewhere in main source, tests, XML, or manifest.
- Existing lint also identifies deprecated drawable loading in `ResourceImageGetter.kt:11`.

Status: **confirmed unused as a pair.** Neither symbol has an independent live caller.

Deletion risk: **low.** Remove both together; deleting only one leaves a new compiler or lint cleanup issue.

### 4. Confirmed: `LocUtils` is unused

Evidence:

- `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/java/com/cylonid/nativealpha/util/LocUtils.kt:5-10`
- No importer or caller references `LocUtils`.
- The similarly named, live implementation is `LocaleUtils`; it is used for the error-page locale at `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/java/com/cylonid/nativealpha/WebViewActivity.java:1688`.

Status: **confirmed unused.** This is not the live locale helper despite the similar name.

Deletion risk: **low.** Search for the exact `LocUtils` name before removal.

### 5. Confirmed: `MovableFloatingActionButton` is unused source

Evidence:

- `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/java/com/cylonid/nativealpha/lib/MovableFloatingActionButton.java:12-102`
- No source import, constructor call, or custom XML tag references the class.
- The only repository name match is the documentation entry at `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/README.md:108`.

Status: **confirmed unused source class.** The README entry is stale documentation, not a runtime reference.

Deletion risk: **low.** Update the README if the class is removed.

### 6. Confirmed: `AboutActivity.showPayPal` is unused

Evidence:

- `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/java/com/cylonid/nativealpha/AboutActivity.kt:128-139`
- No source, test, XML, or reflection call invokes `showPayPal`.
- The visible donation path uses `showLiberaPay` at `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/java/com/cylonid/nativealpha/AboutActivity.kt:59` and `:113-126`.
- The PayPal string is otherwise consumed only by this function; base and translated resources appear at `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/res/values/strings.xml:81`, `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/res/values-es/strings.xml:68`, and `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/res/values-it/strings.xml:67`.

Status: **confirmed unused in the current source.**

Deletion risk: **low to medium.** The code has no current caller, but product owners may intend to restore PayPal as a donation option. If removed, remove the associated translated strings in the same change.

### 7. Confirmed: two XML resources have no static or reflective consumer

Evidence:

- `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/res/drawable/baseline_drag_indicator_24.xml:1`
- `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/src/main/res/layout/context_menu_title.xml:2`
- Existing lint reports both as unused at `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/design/health-audit/lint-findings.json:1333` and `:2835`.
- Searches found no `R.drawable`/`R.layout` reference, XML reference, or layout inflater name reference. Dynamic `getIdentifier` calls are limited to string resources in the WebView implementation.

Status: **confirmed unused in the maintained source.** Lint can miss dynamic resource access, so the names were checked manually against source and XML.

Deletion risk: **low.** External tooling could have a non-code expectation for these XML files, but the repository itself has none.

### 8. Candidate: the two direct Navigation dependencies may be removable

Evidence:

- `/Users/grangerfdad/Desktop/NativeAlphaForAndroid-dev/app/build.gradle:121-122` directly declares `androidx.navigation:navigation-fragment:2.8.9` and `androidx.navigation:navigation-ui:2.8.9`.
- Searches found no Navigation component import, `NavHostFragment`, `NavController`, navigation graph, or `NavigationUI` use in app source, XML, tests, or the manifest.

Status: **candidate, not confirmed.** A dependency graph was not run, as required by the audit constraints, so transitive use cannot be ruled out from direct declarations alone.

Deletion risk: **medium until dependency analysis is available.** This is a suitable follow-up for a Gradle dependency-insight check, but it should not be deleted from this static evidence alone.

## RepoWise six-name disposition

| Exact RepoWise name | Disposition | Evidence and reason |
|---|---|---|
| `gradlew` | **Reject** | Standard executable build entrypoint. It is executable and the wrapper JAR and properties are present under `gradle/wrapper/`; “no importers” is expected for a launcher script. |
| `MovableFloatingActionButton` | **Validate** | Confirmed unused source class. The only name match is documentation at `README.md:108`; no source or XML uses it. |
| `LocUtils` | **Validate** | Confirmed unused. No callers; the live error-page helper is the distinct `LocaleUtils` used by `WebViewActivity.java:1688`. |
| `ResourceImageGetter` | **Validate** | Confirmed unused. Remove with `Context.resIdByName`, which exists only to serve it. |
| `ColorUtils` | **Reject** | False positive. `AboutActivity.kt:10` imports the project’s `ColorUtils.getColorResFromThemeAttr`, and `AboutActivity.kt:77` calls it. `ColorPalette.kt:5` separately uses `androidx.core.graphics.ColorUtils`; that is unrelated. |
| `ContextExtensions.resIdByName` | **Validate** | Confirmed unused outside `ResourceImageGetter`; no independent caller. Remove as part of the helper-pair cleanup. |

## Android-specific limits

The findings above account for databinding, XML resource references, manifest-declared activities, and class-name reflection. The main deliberate exclusion is the generated sandbox activity family: although it dominates raw graph output, it is generated, manifest-registered, and loaded by name. Future removals should still rerun reference searches after any databinding or manifest generation change.

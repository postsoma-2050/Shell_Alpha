# Fullscreen interaction repair

Scope: Lite App fullscreen and recovery controls. YouTube ad filtering is deferred.

## Delegation
- Pasteur (Gemini 3.8 Flash): shortcut fullscreen switch, usable recovery control, corrected frameless text. Resource files only.
- Rawls (Gemini 3.8 Flash): Hermit behavior cross-check and independent unit regression tests. New test file only.
- Main agent: WebViewActivity behavior integration, content constraints, device test, build and acceptance.

## Behavior contract
- Fullscreen hides Android status/navigation bars. Frameless hides the app toolbar. These remain independent as in Hermit's settings_lite_app_behavior.xml.
- Enable both for hidden system bars and hidden browser toolbar.
- Frameless exposes a 48dp recovery control outside the website area. This deliberately reserves space; it is not a claim of a completely chrome-free viewport.
- System swipe can temporarily reveal Android bars. Android controls this behavior.
- Keyboard and display-cutout safe areas remain protected.

## Acceptance
- Standard debug APK + Android test APK assembled successfully; installed with `--user 0` on Pixel 7a (33081JEHN21726).
- `./gradlew testStandardDebugUnitTest`: 230 tests, 0 failures, 0 errors, 1 skipped (includes 8 new fullscreen unit cases).
- `FullscreenInteractionTest`: OK (1 test), real device Android 17. Checks hidden status/navigation bars, hidden toolbar, positive recovery bounds, no website/recovery overlap, real input tap opening settings, mode independence, and restored bars/toolbar.
- Test used a temporary about:blank Lite App and removed only that record afterwards. It did not clear user data or login sessions.
- First device run failed due to window coordinates used for screen injection; corrected to getLocationOnScreen and reran. First new unit run failed due to omitted state initialization; corrected test setup and reran. These failed runs were not counted as successful validation.
- Screenshot fullscreen-recovery.png shows the recovery tap opened the drawer with fullscreen active. Opening the drawer is expected to cover part of the webpage.
- No assertion of whole-app compatibility, every website's touch behavior, or battery certification.

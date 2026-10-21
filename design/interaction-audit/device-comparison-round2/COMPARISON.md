# Pixel 7a: Hermit / ShellAlpha comparison (2026-10-20)

Device: 33081JEHN21726. Same device, installed apps, actual UI interaction. Reference screenshots in this directory are captured this round. Existing accounts and saved apps were not cleared. Current website/session/style settings differ between apps, so screenshots do not establish pixel-for-pixel viewport equivalence or measured performance superiority.

| Area | Observed difference / finding | Action |
|---|---|---|
| Home return | Reproduced this round: type yahoo.com/news/, cancel add dialog (saved app uses a different host), tap saved Yahoo result, tap browser Home: keyboard automatically reopens. Hermit home observed without keyboard | Hume: clear search focus/IME on departure; preserve deliberate user search |
| Quick settings | Hermit uses three-column action tiles; ShellAlpha uses six large two-column tiles, pushing privacy controls below | Carver: retain labels/two columns but reduce minimum tile height |
| Sandbox access | ShellAlpha badge and gear measured 32dp targets; picker opens directly from badge | Carver: enlarge targets to 48dp |
| Fullscreen | Hermit saved PostSoma has both fullscreen and frameless enabled, no persistent native footer; ShellAlpha has separate fullscreen switch and reserved recovery footer | Prior system bar fix retained; persistent-footer-free presentation remains a known gap |
| Website touch | ShellAlpha PostSoma '从一个好奇开始' real tap scrolled to the four-tab card section | Passed this interaction; not a blanket website-compatibility result |
| Page tools | Hermit quick panel offers Copy URL and Find in page; ShellAlpha lacks equivalent direct quick entries | Follow-up, not implemented this round |
| Hidden settings access | Two lower-right swipes did not reliably open Hermit saved frameless app; Yahoo framed-mode Settings opens its drawer | Do not infer universal gesture success from one configuration |

## Delegation
- Carver, Gemini 3.8 Flash: browser panel resource changes + layout regression tests.
- Hume, Gemini 3.8 Flash: home search/IME focus repair + regression tests.
- Main: connected-device comparison, real web-button input regression, integration/build/install and acceptance.

## Verification
Home return fix installed and manually verified: typing Yahoo, opening saved Yahoo, and pressing browser Home retains query and returns without the IME. Final integrated build succeeded. Standard Debug + Android test APKs installed using --user 0.

- testStandardDebugUnitTest: 233 tests, 0 failures, 0 errors, 1 skipped.
- Pixel 7a FullscreenInteractionTest: OK (1 test). Added actual injected touch on a fixed-bottom HTML button and asserted its DOM text changed to Tapped; system-bar hide/restore and recovery-control touch assertions also passed.
- Manual final panel inspection: desktop tile 196px -> 142px; sandbox badge/gear height 71px -> 107px (32dp -> 48dp at current density). Adblock switch y1456 -> y1330, 126px higher (~56dp). These are XML UI bounds on the same device, not general device-independent performance measurements.
- Actual tap on enlarged sandbox gear opened Choose sandbox; dismissed without changing sandbox.
- Final panel labels were visible without clipping at the device's current font/display size. Larger accessibility font sizes and every website were not exhaustively checked.
- Home focus unit fixture was seeded with a saved site to exercise actual launch rather than the Add dialog; regression suite passed after this fixture correction. YouTube ad filtering is out of scope.

Yahoo header/search regions rendered within web content below native toolbar in both apps in this run. Different feed articles/session configuration were observed; they are not rendering regressions. Typing a host that differs from the saved host can offer Add Web App even with a filtered match; no URL equivalence change made this round.

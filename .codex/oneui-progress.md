# Lawnchair OneUI Progress

Branch:
feature/oneui-enhancements

Last verified HEAD:
TBD

Global:
10%

Modules:
- M1 Large Folders: 0% — not started
- M2 Pixel Search: 0% — not started
- M3 One UI Finder: 80% — implemented, CI pending
- M4 Dock Glass: 0% — not started
- M5 Widget Grid Snap: 0% — not started

Completed:
- Verified feature branch matches 16-dev at 155ccd1ee49e29e839ca603072777a9b7ef1e52c before work.
- Created remote progress checkpoint 91293ff90e22981d7afef50210198204180d3d28.
- Implemented One UI Finder gesture option using explicit component com.sec.android.app.launcher/.search.SearchActivity with safe fallback.

Current:
- Commit M3 and continue to widget grid snapping discovery.

Next:
- Discover widget placement/resize paths for M5.

Architecture decisions:
- Remote GitHub only; no local checkout or local build.
- One UI Finder is a normal GestureHandlerConfig.Simple action exposed through existing gesture selector.
- Finder launch verifies resolveActivity and catches launch/security/runtime failures.

Files currently relevant:
- lawnchair/src/app/lawnchair/gestures/config/GestureHandlerConfig.kt
- lawnchair/src/app/lawnchair/gestures/config/GestureHandlerOption.kt
- lawnchair/src/app/lawnchair/gestures/config/GestureHandlerOptions.kt
- lawnchair/src/app/lawnchair/gestures/handlers/OpenOneUiFinderGestureHandler.kt
- lawnchair/res/values/strings.xml

Validation:
- M3 code prepared; remote CI pending.

Known blockers:
- None
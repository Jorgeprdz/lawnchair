# Lawnchair OneUI Progress

Branch:
feature/oneui-enhancements

Last verified HEAD:
a7c22c87b71d682a98560df39d3ac14791c4e016

Global:
17%

Modules:
- M1 Large Folders: 0% - no committed implementation found
- M2 Pixel Search: 0% - not started
- M3 One UI Finder: 85% - implemented and registered; CI/device validation pending
- M4 Dock Glass: 0% - not started
- M5 Widget Grid Snap: 70% - drop/migration snapping implemented; CI/device validation pending
- M6 Widget Stacks: 0% - not started; means multiple real widgets in one persistent swipable stack

Completed:
- Recovered remote-only state on 2026-09-10.
- Verified feature branch is 6 commits ahead of 16-dev and 0 behind.
- Verified 16-dev HEAD and merge-base: 155ccd1ee49e29e839ca603072777a9b7ef1e52c.
- Verified feature HEAD before recovery checkpoint: a7c22c87b71d682a98560df39d3ac14791c4e016.
- M3 One UI Finder gesture committed in 88df5140c318d69df1980e3bea569d2fa009515b.
- M5 widget span snapping and grid migration preservation committed through 183ddc7378e71997282862ca1a590eb54d6bea01 / a7c22c87b71d682a98560df39d3ac14791c4e016.
- No M1 files are present in the branch diff; transient large-folder work did not survive remotely.

Current:
- Inspect M5 compile risk and validate gesture registration paths.

Next:
- Stabilize M3/M5 with remote CI if possible, then begin M6 model discovery.

Architecture decisions:
- Remote GitHub only; no clone, pull, fetch, ZIP download, local checkout, or local build.
- One UI Finder uses explicit component com.sec.android.app.launcher/.search.SearchActivity and fails safely.
- Widget snapping stays within CellLayout spans, GridOccupancy, provider spans, and resizeMode.
- M6 must be a persistent widget-stack container, not overlapping independent widgets.

Relevant files:
- lawnchair/src/app/lawnchair/gestures/config/GestureHandlerConfig.kt
- lawnchair/src/app/lawnchair/gestures/config/GestureHandlerOption.kt
- lawnchair/src/app/lawnchair/gestures/config/GestureHandlerOptions.kt
- lawnchair/src/app/lawnchair/gestures/handlers/OpenOneUiFinderGestureHandler.kt
- lawnchair/res/values/strings.xml
- src/com/android/launcher3/Workspace.java
- src/com/android/launcher3/model/GridSizeMigrationDBController.java
- src/com/android/launcher3/model/GridSizeMigrationLogic.kt

Validation:
- No GitHub Actions runs found yet for feature/oneui-enhancements or HEAD a7c22c87b71d682a98560df39d3ac14791c4e016.

Known blockers:
- None

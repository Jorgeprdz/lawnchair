# Lawnchair OneUI Progress

Branch:
feature/oneui-enhancements

Last verified HEAD:
a3e7649cae81303ee00888b256e317b68b9ae143

Global:
18%

Modules:
- M1 Large Folders: 0% - no committed implementation found
- M2 Pixel Search: 0% - not started
- M3 One UI Finder: 85% - implemented and registered; CI/device validation pending
- M4 Dock Glass: 0% - not started
- M5 Widget Grid Snap: 75% - drop/migration snapping implemented; provider clamp improved; CI/device validation pending
- M6 Widget Stacks: 0% - not started; means multiple real widgets in one persistent swipable stack

Completed:
- Recovered remote-only state on 2026-09-10.
- Verified feature branch was 6 commits ahead of 16-dev and 0 behind before recovery checkpoint.
- Verified 16-dev HEAD and merge-base: 155ccd1ee49e29e839ca603072777a9b7ef1e52c.
- Verified feature HEAD before recovery checkpoint: a7c22c87b71d682a98560df39d3ac14791c4e016.
- M3 One UI Finder gesture committed in 88df5140c318d69df1980e3bea569d2fa009515b.
- M5 widget span snapping and grid migration preservation committed through a7c22c87b71d682a98560df39d3ac14791c4e016.
- M5 bound-widget snap now clamps with provider min/max spans and fixed-axis resizeMode in a3e7649cae81303ee00888b256e317b68b9ae143.
- No M1 files are present in the branch diff; transient large-folder work did not survive remotely.

Current:
- Begin M6 Widget Stacks architecture discovery.

Next:
- Inspect Launcher3 model/database/widget binding paths for a minimal persistent widget-stack container.

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
- No GitHub Actions runs found yet for feature/oneui-enhancements; CI push filter only matches *-dev branches, PR, dispatch, or workflow_call.

Known blockers:
- None

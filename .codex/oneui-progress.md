# Lawnchair OneUI Progress

Branch:
feature/oneui-enhancements

Last verified HEAD:
183ddc7378e71997282862ca1a590eb54d6bea01

Global:
18%

Modules:
- M1 Large Folders: 0% — not started
- M2 Pixel Search: 0% — not started
- M3 One UI Finder: 80% — implemented, CI pending
- M4 Dock Glass: 0% — not started
- M5 Widget Grid Snap: 70% — implemented, CI pending

Completed:
- Verified feature branch started from 16-dev at 155ccd1ee49e29e839ca603072777a9b7ef1e52c.
- Implemented One UI Finder gesture action in 88df5140c318d69df1980e3bea569d2fa009515b.
- Implemented widget span snapping on widget drop and improved grid migration span preservation in dc5955bf6677e731df2b0de8083a2338ccf72183.
- Restored Workspace.java after GitHub blob upload issue in 183ddc7378e71997282862ca1a590eb54d6bea01.

Current:
- Discover folder architecture for M1.

Next:
- Implement persisted large folder state and 2x2 footprint.

Architecture decisions:
- Remote GitHub only; no local checkout or local build.
- One UI Finder is a normal GestureHandlerConfig.Simple action exposed through existing gesture selector.
- Widget snapping stays within CellLayout spans, GridOccupancy, provider min/max spans, and resizeMode.

Files currently relevant:
- src/com/android/launcher3/Workspace.java
- src/com/android/launcher3/model/GridSizeMigrationLogic.kt
- src/com/android/launcher3/model/GridSizeMigrationDBController.java
- lawnchair/src/app/lawnchair/gestures/config/GestureHandlerConfig.kt
- lawnchair/src/app/lawnchair/gestures/handlers/OpenOneUiFinderGestureHandler.kt

Validation:
- Remote diff verified after M5 fix; CI pending.

Known blockers:
- None

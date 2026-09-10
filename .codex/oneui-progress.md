# Lawnchair OneUI Progress

Branch:
feature/oneui-enhancements

HEAD (code audited before this checkpoint):
a78a0c9e1e918aa0bba5b4304ff4460ff5823d84

Global:
25% (equal-weight estimate across six modules; not acceptance completion)

Modules:
- M1 Large Folders: 0% — no committed implementation
- M2 Pixel Search: 0% — not started
- M3 One UI Finder: 80% — selector/config/explicit launch present; validation pending
- M4 Dock Glass: 0% — not started
- M5 Widget Grid Snap: 70% — safe pre-reorder constraints and proportional migration; CI/device checks pending
- M6 Widget Stacks: 0% — no committed model or rank normalization

Completed:
- Recovery: original feature HEAD 5c25056, 9 ahead/0 behind base 155ccd1ee49e29e839ca603072777a9b7ef1e52c.
- No M1/M6 implementation survived remotely.
- Finder availability checks and launches guarded against platform exceptions (3a154b7).
- M5 applies constraints before CellLayout reserves cells, including drag previews (d73d45d).
- Rejected external placements do not persist invalid cells; existing force-resize preference retained.
- Proportional widget span/center preferences added to Kotlin standard migration (a78a0c9).
- Existing saved target-grid layouts are preserved; provider constraints bound preferred spans.
- Five Android regression cases added in WidgetGridScalingTest.kt; not yet executed.
- CI registration recovered via Actions permissions API; feature push trigger added.
- Draft PR #1 exists for review/CI; never merge.

Current:
- Monitor CI for a78a0c9 and complete M5 migration safety before M6 implementation.

Next:
- Inspect any remote compile failure and fix; instrumented tests need a configured runner.
- M5: cover strictly-taller shortcut and legacy Java migration proportional behavior.
- M5: audit impossible provider minima/removal paths to prevent widget disappearance.
- M6: extend CollectionInfo + loader/host/migration; current processWidget rejects nested containers.
- Rank normalization must be integrated after all stack members load, not added blindly.

Architecture decisions:
- Remote GitHub is durable state; no local clone or full local build.
- Only feature/oneui-enhancements may change; never merge into 16-dev.
- Use existing CellLayout placement, spans and AppWidget host.
- Stack model must persist membership/rank and own one workspace footprint.
- DefaultLauncher stack model/view studied; files are GPLv3 despite repository Apache metadata.
- Lawnchair LICENSE.txt is Apache-2.0; no DefaultLauncher source copied.
- PR LawnchairLauncher/lawnchair#7029 is preview-only; does not supply large-folder footprint.
- Stack discovery: CollectionInfo, WorkspaceData.kt, LoaderCursor, ItemInflater, ModelWriter/LauncherWidgetHolder.
- Preserve existing functionality and provider resize constraints.

Relevant files:
- lawnchair/src/app/lawnchair/gestures/config/GestureHandlerConfig.kt
- lawnchair/src/app/lawnchair/gestures/config/GestureHandlerOption.kt
- lawnchair/src/app/lawnchair/gestures/config/GestureHandlerOptions.kt
- lawnchair/src/app/lawnchair/gestures/handlers/OpenOneUiFinderGestureHandler.kt
- src/com/android/launcher3/Workspace.java
- src/com/android/launcher3/model/GridSizeMigrationDBController.java
- src/com/android/launcher3/model/GridSizeMigrationLogic.kt
- src/com/android/launcher3/model/WorkspaceItemProcessor.kt
- .github/workflows/ci.yml

Validation:
- d73d45d: eight isolated Java constraint scenarios passed; not an Android build.
- CI 34423066055 (d73d45d): spotlessCheck passed; three APK builds running at checkpoint.
- CI 34423420226 (a78a0c9): push build running; duplicate PR run should be cancelled.
- CI builds NightlyRelease/GithubDebug/PlayDebug; does not execute multivalent Android tests.
- All runtime/device acceptance checks remain pending; no verified APK yet.

Blockers:
- No remote access blocker. Android interaction and instrumented-test execution pending.
- User prompt ends in APK section after “build”; remaining APK instructions not received.
- WIP: no transient feature edits; all described source committed.

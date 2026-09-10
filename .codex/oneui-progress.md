# Lawnchair OneUI Progress

Branch:
feature/oneui-enhancements

HEAD (code audited before this checkpoint):
488c0c7152c5fdfd85b7d258ff2af267b109e243

Global:
28% (rounded equal-weight estimate across seven modules; not acceptance completion)

Modules:
- M1 Large Folders: 0% — no committed implementation
- M2 Pixel Search: 0% — not started
- M3 One UI Finder: 90% — complete CI passed; Samsung/missing-component device checks pending
- M4 Dock Glass: 0% — not started
- M5 Widget Grid Snap: 70% — safe pre-reorder constraints and proportional migration; CI/device checks pending
- M6 Widget Stacks: 35% — creation action, native picker/editor connected; CI/resize/lifecycle validation pending
- M7 Max Icons / Max Folders: 0% — required annex; individual workspace-only 1x1/2x2 items

Completed:
- Recovery: original feature HEAD 5c25056, 9 ahead/0 behind base 155ccd1ee49e29e839ca603072777a9b7ef1e52c.
- No M1/M6 implementation survived recovery; M6 foundations reconstructed afterward.
- Finder availability checks and launches guarded against platform exceptions (3a154b7).
- M5 applies constraints before CellLayout reserves cells, including drag previews (d73d45d).
- Rejected external placements do not persist invalid cells; existing force-resize preference retained.
- Proportional widget span/center preferences added to Kotlin standard migration (a78a0c9).
- Existing saved target-grid layouts are preserved; provider constraints bound preferred spans.
- Five Android regression cases added in WidgetGridScalingTest.kt; not yet executed.
- CI registration recovered via Actions permissions API; feature push trigger added.
- Draft PR #1 exists for review/CI; never merge.
- M6 model/type12/rank tests: 07309a0; OPTIONS stores active member row ID.
- M6 nested loading and rank normalization: 59c25cf; broken providers retained.
- M6 PagedView/PageIndicatorDots/real host views and async host attachment: 3aa9890.
- M6 atomic create/delete: 7f2734c; migration copies members and remaps active row: df78685.
- M6 context fix: 8909ad2; selector 50bfffb/editor 294f23b; creation uses native widget resize frame.

Current:
- Grid Apply preflight guards fixed/unavailable widget dimensions, including stack members.

Next:
- Inspect any remote compile failure and fix; instrumented tests need a configured runner.
- M5: cover strictly-taller shortcut and legacy Java migration proportional behavior.
- M5: audit impossible provider minima/removal paths to prevent widget disappearance.
- M6: validate picker recreation/drag routes, model callback membership and cancellation.
- M6: validate editor/add/remove/resize/provider interactions; restore/legacy migration failure paths pending.

Architecture decisions:
- Remote GitHub is durable state; no local clone or full local build.
- Only feature/oneui-enhancements may change; never merge into 16-dev.
- Order: M3 -> M5 -> M6 -> M1 + M7 together -> M4 -> M2; finish current M6 unit first.
- M7: per-app contextual maximize/restore, real 2x2 CellLayout occupancy, intentional persisted state.
- M1/M7: ONE shared 1x1 <-> 2x2 placement/reorder/nearest-space/persistence/restore mechanism.
- Max Icons: implement on current WorkspaceItemInfo/BubbleTextView; no further broad drop-in search.
- AOSP span/model/binding paths guide occupancy; DefaultLauncher GPL files remain reference only.
- M7 folders MUST share M1 canonical state/3x3 direct-launch preview; no duplicate flags/systems.
- M7: native popup, nearest safe placement/refusal, same item identity, adaptive/themed icons/badges.
- M7: workspace only, no Hotseat/global scaling/Nothing mode; preserve drag, labels, a11y, animations.
- M7: restart/reboot/restore/orientation/grid persistence; use M5 migration; do not apply to stacks.
- Nothing is UX inspiration only; no proprietary assets/code; inspect open-source file licenses.
- Future normal/final/BACKUP-STOP reports include M7 and global across all seven modules.
- Use existing CellLayout placement, spans and AppWidget host.
- Stack model must persist membership/rank and own one workspace footprint.
- DefaultLauncher stack model/view studied; files are GPLv3 despite repository Apache metadata.
- Lawnchair LICENSE.txt is Apache-2.0; no DefaultLauncher source copied.
- PR LawnchairLauncher/lawnchair#7029 is preview-only; does not supply large-folder footprint.
- Stack discovery: CollectionInfo, WorkspaceData.kt, LoaderCursor, ItemInflater, ModelWriter/LauncherWidgetHolder.
- Stack parent owns geometry; loader reapplies parent spans to every member after restart/migration.

Relevant files:
- lawnchair/src/app/lawnchair/gestures/config/GestureHandlerConfig.kt
- lawnchair/src/app/lawnchair/gestures/config/GestureHandlerOption.kt
- lawnchair/src/app/lawnchair/gestures/config/GestureHandlerOptions.kt
- lawnchair/src/app/lawnchair/gestures/handlers/OpenOneUiFinderGestureHandler.kt
- src/com/android/launcher3/Workspace.java
- src/com/android/launcher3/model/GridSizeMigrationDBController.java
- src/com/android/launcher3/model/GridSizeMigrationLogic.kt
- src/com/android/launcher3/model/WorkspaceItemProcessor.kt
- src/com/android/launcher3/model/LoaderCursor.java
- src/com/android/launcher3/model/data/WidgetStackInfo.kt
- src/com/android/launcher3/widget/WidgetStackView.kt
- src/com/android/launcher3/util/ItemInflater.kt
- src/com/android/launcher3/Launcher.java
- .github/workflows/ci.yml

Validation:
- d73d45d: eight isolated Java constraint scenarios passed; not an Android build.
- CI 34423066055 (d73d45d): all three APK builds and spotlessCheck PASSED.
- CI a78a0c9 (34423420226) and M6 loader 59c25cf (34423937406): all APKs/style PASSED.
- M6 migration CI 34424966519 and host restore CI 34426161207: all APK builds/style PASSED.
- Selector CI 34426603559 type errors fixed ff78c06; latest picker/editor/creation CI pending.
- CI builds NightlyRelease/GithubDebug/PlayDebug; does not execute multivalent Android tests.
- APK artifacts exist for d73d45d/a78a0c9; all runtime/device acceptance checks remain pending.

Blockers:
- No remote access blocker. Android interaction and instrumented-test execution pending.
- One-member stacks remain valid; standalone conversion awaits safe atomic footprint replacement.
- WIP: no transient feature edits; all described source committed.

# Lawnchair OneUI Progress

Branch: feature/oneui-enhancements
HEAD (audited before checkpoint): b7223668d2f64d805c6b4af460ba7da17567259d
Global: 29% — equal-weight estimate across seven modules; not acceptance completion.

Modules:
- M1 Large Folders: 0% — no committed implementation.
- M2 Pixel Search: 0% — not started; real provider discovery/hosting required.
- M3 One UI Finder: 90% — CI passed; actual Samsung/missing-component tests pending.
- M4 Dock Glass: 0% — Off/Solid/Blur/Crystal not started.
- M5 Widget Grid Snap: 70% — safe pre-reorder constraints/proportional standard migration.
- M6 Widget Stacks: 45% — model, host, picker/editor/create, resize/lifecycle/migration foundations.
- M7 Max Icons / Max Folders: 0% — registered; workspace-only real 1x1/2x2 items.

Completed:
- No M1/M6 implementation survived original recovery; M6 reconstructed afterward.
- Finder launch/availability exceptions guarded 3a154b7; native gesture registration retained.
- M5 constraints precede CellLayout occupancy d73d45d; proportional Kotlin migration a78a0c9.
- M6 type12/model/rank 07309a0; nested loader 59c25cf; native host/paging/indicator 3aa9890.
- M6 atomic create/delete 7f2734c; member migration/active-ID remapping df78685.
- M6 host lookup/reinflation/rebinding: dde9893, 59f20b3, d6c793c.
- Native picker/config routing 50bfffb; editor 294f23b; creation action 07075ae.
- Atomic configured-member insertion c8c70b2; picker destination recreation 71c3076.
- Resize through CellLayout 0ad6862 (access fix 488c0c7); provider retention 399ea1b.
- Shared M5/stack proportional migration f09d4bc; grid preference preflight fbf5412.
- Draft PR #1 exists for review/CI; NEVER merge.

Current:
- M6 geometry/removal CI green; gesture ownership, accessibility and first-provider checks committed.
- Binding-cancel ID fallback 453a92f + regression f0e6b1d; resize/recreation 6dd63d6; taps/swipes cabc6b3.

Next:
- Monitor fast CI/emulator 34433342940 (9cc7574); binding test now in launcher package for ActivityCodes.
- Verify explicit AVD paths; first corrected setup did not run because test compilation failed.
- Run latest interaction tests after compile; finish provider failure, vertical scroll, picker/editor and migration acceptance.
- M5/M6 impossible restore-grid failure paths remain pending; then M1+M7, M4, M2.

Architecture decisions:
- GitHub durable state; no clone/full checkout or local full Android build. Remote CI only.
- Only feature/oneui-enhancements changes; never modify/merge 16-dev.
- Order M3 -> M5 -> M6 -> M1+M7 -> M4 -> M2; no interruption of coherent M6 units.
- Stack parent owns footprint; real widget rows use parent ID as container, rank is ordering.
- OPTIONS stores active member database row ID; migration remaps it with children.
- Existing LauncherWidgetHolder/AppWidgetHost, PagedView, PageIndicatorDots and ComposeBottomSheet.
- Editor uses existing drag/a11y reorder controls and widget picker; no second host.
- Configure before inserting a member; transaction failure/stale load reports host-ID cleanup.
- One remaining member stays a valid stack pending safe atomic standalone conversion.
- Loader reapplies parent spans to members; provider disappearance retains removable pending page.
- M1/M7 share ONE 1x1<->2x2 occupancy/reorder/nearest-place/persist/restore mechanism.
- Max Folders use M1 canonical state and 3x3/up-to-9 direct-launch preview; no duplicate flags.
- Max Icons use WorkspaceItemInfo/BubbleTextView; retain identity, theme/badges/drag/a11y.
- M7 workspace only; no global scaling/Hotseat/Nothing mode; no further broad drop-in search.
- M7 uses M5 migration; stack resizing remains M6. Nothing is UX inspiration only.
- DefaultLauncher model/view files are GPLv3 despite Apache metadata: reference only, NOT copied.
- Check licenses per file; Lawnchair LICENSE.txt Apache-2.0. PR7029 is preview-only reference.

Relevant files:
- .github/workflows/ci.yml; lawnchair/src/app/lawnchair/gestures/handlers/OpenOneUiFinderGestureHandler.kt
- src/com/android/launcher3/{Workspace.java,Launcher.java,CellLayout.java,AppWidgetResizeFrame.java}
- src/com/android/launcher3/model/{ModelWriter.java,WorkspaceItemProcessor.kt,LoaderCursor.java}
- src/com/android/launcher3/model/{GridSizeMigrationLogic.kt,GridSizeMigrationDBController.java,DbEntry.kt}
- src/com/android/launcher3/model/data/WidgetStackInfo.kt; model/PackageUpdatedTask.java
- src/com/android/launcher3/widget/{WidgetStackView.kt,WidgetStackController.kt,WidgetGridPreflight.kt}
- src/com/android/launcher3/widget/{BaseWidgetSheet.java,PendingAppWidgetHostView.java,picker/WidgetsFullSheet.java}
- src/com/android/launcher3/util/{ItemInflater.kt,LauncherBindableItemsContainer.kt}
- lawnchair/src/app/lawnchair/widgetstack/WidgetStackEditor.kt; tests/oneui/; .github/scripts/oneui-emulator-smoke.sh
- lawnchair/src/app/lawnchair/ui/preferences/destinations/HomeScreenGridPreferences.kt

Validation:
- 5 grid scaling + 5 stack model + 3 migration cases in multivalentTests remain unexecuted.
- Three APKs/style green: 1960ee0/34427352339, f84b4c7/34429849107, 2e6eba2/34429976188.
- b0aad3d/34430802520, 1bc97b0/34431464980, 6dd63d6/34431706677: APKs/instrumentation/style compiled.
- First emulator failed before APK install: Unknown AVD lawnchair-oneui; cancelled stalled run for logs.
- Diagnostics: Lawnchair-OneUI-Emulator-Smoke, run34430802520, artifact10134948008; no device test passed.
- Explicit ANDROID_USER_HOME/ANDROID_AVD_HOME + AVD existence checks dc9c8cd; bounded ADB/setup waits.
- dc9c8cd/34432524467 failed AndroidTest compilation: ActivityCodes constant package access; test moved.
- Feature pushes: GithubDebug + instrumentation/style; duplicate feature PR jobs skipped (f70e137).
- Manual oneui-fast=true gives quick validation; default manual run retains all 3 APK variants.
- oneui-apk-run reuses successful APKs only after source/build checks (6 guard cases passed).
- Gradle cache defaults read-only off default branch; feature cache writes enabled b722366 (caching already true).

Final emulator/screenshots requirements (after all modules and successful CI):
- Use remote emulator if available; install actual APK, launch without crash, set home when permitted.
- Run available instrumentation/UI tests; exercise icons/folders/max items, widgets/resize/stacks,
  paging/reorder/remove/add, migration, dock modes and restart/process recreation; capture failure logcat.
- Report compile, emulator and remaining physical-device validation SEPARATELY.
- Never claim Samsung Finder/Pixel Search device validation without actual package/provider present.
- Capture REAL ADB PNGs: 01-home.png, 02-large-folder.png, 03-large-folder-open.png,
  04-dock-off.png, 05-dock-solid.png, 06-dock-blur.png, 07-dock-crystal.png, 08-widget-grid.png,
  09-widget-stack-page1.png, 10-widget-stack-page2.png, 11-widget-stack-editor.png,
  12-max-icon.png, 13-max-folder.png; Pixel Search/Finder screenshots ONLY if genuinely available.
- Create/use /sdcard/Download/Lawnchair-OneUI/; verify every captured file exists and is valid PNG.
- Copy emulator captures to CI host; publish artifact Lawnchair-OneUI-Screenshots.
- Download/extract to user's /sdcard/Download/Lawnchair-OneUI/ if supported; else give exact run/artifact.
- Final screenshot report: count, filenames, artifact, workflow run ID, Download path, omissions/reasons.

Blockers:
- No remote access blocker. All described code committed; no transient WIP files.
- Runtime/device acceptance pending. Global percentages include all seven modules; CTX unavailable.

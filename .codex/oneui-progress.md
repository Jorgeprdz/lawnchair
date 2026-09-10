# Lawnchair OneUI Progress

Branch: feature/oneui-enhancements
HEAD (audited before checkpoint): ac3a2031d3ad44e85a7cad19211fcbf18225904b
Global: 49% — equal-weight estimate across seven modules; not acceptance completion.

Modules:
- M1 Large Folders: 35% — shared 2x2 placement, persisted state, 3x3 direct preview and native action.
- M2 Pixel Search: 35% — installed provider discovery, native QSB host, tracked binding/config IDs, fallback.
- M3 One UI Finder: 90% — CI passed; actual Samsung/missing-component tests pending.
- M4 Dock Glass: 35% — four persisted modes, native geometry, optional region blur and crystal styling.
- M5 Widget Grid Snap: 70% — safe pre-reorder constraints/proportional standard migration.
- M6 Widget Stacks: 45% — model, host, picker/editor/create, resize/lifecycle/migration foundations.
- M7 Max Icons / Max Folders: 30% — native Max Icon action/rendering; shared folder state/placement.

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
- M2 real QSB hosting/lifecycle; M1 preview app install-state handling and touch cancellation reviewed.
- Max drag preserves spans; native migrations retain 2x2 or restore 1x1 on grids smaller than 2x2.

Next:
- USER UPDATE: defer new builds/emulator until all implementation is finished; review diffs and commit normally.
- M6 active-page hook fbdbc36 plus generation guard for queued rebind callbacks; provider-label failure safe.
- Final regression/capture tests committed: max items, dock modes, stack activity/process recreation; start final CI next.
- M5/M6 guard 58731d2 cancels incompatible grid changes before migration; source-grid reload needs testing.

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
- M4 uses AOSP BackgroundBlurDrawable when accessible/enabled, otherwise translucent fallback; no wallpaper capture.
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
- util/WorkspaceItemSize.java; graphics/DockGlassBackground.java; Hotseat.java; qsb/QsbContainerView.java
- lawnchair/src/app/lawnchair/widgetstack/WidgetStackEditor.kt; tests/oneui/; .github/scripts/oneui-emulator-smoke.sh
- lawnchair: HomeScreenGridPreferences.kt; DockPreferences.kt; qsb/PixelSearchQsbFragment.kt

Validation:
- 5 grid scaling + 5 stack model + 3 migration cases in multivalentTests remain unexecuted.
- Three APKs/style green: 1960ee0/34427352339, f84b4c7/34429849107, 2e6eba2/34429976188.
- b0aad3d/34430802520, 1bc97b0/34431464980, 6dd63d6/34431706677: APKs/instrumentation/style compiled.
- AVD path issue fixed: API35 boot/install/startup confirmed; 1 of 2 instrumentation tests passes.
- Latest paging diagnostics: Lawnchair-OneUI-Emulator-Smoke, run34435384391, artifact10136032142.
- Explicit ANDROID_USER_HOME/ANDROID_AVD_HOME + AVD existence checks dc9c8cd; bounded ADB/setup waits.
- 51b25f6/34434279638 compile/style green; emulator34434641844: host regression PASS, real widget tap PASS; first swipe timed out.
- Automatic feature push CI paused at user request; manual full build/style/tests remain available for final validation.
- Do not dispatch intermediate builds/emulators; final manual run retains all 3 APK variants and validation.
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
- Create/use /storage/emulated/0/Download/lawnchair/; verify every captured file exists and is valid PNG.
- Copy emulator captures to CI host; publish artifact Lawnchair-OneUI-Screenshots.
- Download/extract to user's /storage/emulated/0/Download/lawnchair/ if supported; else give exact run/artifact.
- Final screenshot report: count, filenames, artifact, workflow run ID, Download path, omissions/reasons.

Blockers:
- Download/lawnchair created; five recent emulator artifacts checked: no PNGs existed. Remote access works.
- Runtime/device acceptance pending. Global percentages include all seven modules; CTX unavailable.

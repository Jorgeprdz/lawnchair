# Lawnchair OneUI Progress

Branch: feature/oneui-enhancements
HEAD (audited before checkpoint): 4acde682a04d62ce4b50dd2a437752b3410d89cd
Global: 73% — equal-weight estimate across seven modules; not acceptance completion.

Modules:
- M1 Large Folders: 75% — shared 2x2 placement, persisted state, 3x3 direct preview and native action.
- M2 Pixel Search: 60% — installed provider discovery, native QSB host, tracked binding/config IDs, fallback.
- M3 One UI Finder: 90% — CI passed; actual Samsung/missing-component tests pending.
- M4 Dock Glass: 65% — four persisted modes, native geometry, optional region blur and crystal styling.
- M5 Widget Grid Snap: 70% — safe pre-reorder constraints/proportional standard migration.
- M6 Widget Stacks: 75% — model, host, picker/editor/create, resize/lifecycle/migration foundations.
- M7 Max Icons / Max Folders: 75% — native Max Icon action/rendering; shared folder state/placement.

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
- All module implementations compile: 34439279055 three APKs passed; style failed then fixed 7d0c233.
- Max drag preserves spans; native migrations retain 2x2 or restore 1x1 on grids smaller than 2x2.

Next:
- Final validation phase: implementations compiled; run emulator, fix failures and collect actual screenshots.
- M6 active-page hook fbdbc36 plus generation guard for queued rebind callbacks; provider-label failure safe.
- Full CI 34439748126/c340706: all 3 APKs/style GREEN; emulator failed: test thread/schema/context/null-tag errors; screenshot ack fixed.
- CI34440973789: compile/style GREEN; dock modes + 2 SQLite migrations + hidden host + stack force-stop restore PASS.
- CI34441287074: 7/8 tests PASS + process-death PASS; only Max icon fixture wrong-thread failure (fixed04d433a).
- Stack real taps/vertical scroll/paging/resize/reorder/remove/reload PASS; absent Finder/Pixel fallback PASS.
- CI34441778019: Max toggle/occupancy/10-member folder/recreation PASS; 7/8 tests; widget tap intermittent.
- CI34442569058/4acde68 testing stable tap bounds; newer rounded-square visual fix needs validation/screenshots.
- Replace canonical02-large-folder.png/13-max-folder.png with verified rounded-square emulator captures.

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
- USER visual correction: Large/Max Folder uses rounded SQUARE, corner radius16% of measured preview edge.
- Drawing/clipping/reveal share native RoundedSquare delegate; normal folder theme and 3x3 geometry unchanged.
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
- Original 13 multivalent cases remain unexecuted; Android tests now include SQLite Max migration and workspace persistence.
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
- Download all produced PNGs to /storage/emulated/0/Download/lawnchair, including valid partial runs. 25 PNG files downloaded/CRC-verified, covering all13 required views; canonical names use best/latest captures.
- Screenshot artifacts: run34441778019/10138414543 (Max/dock) +34441287074/10138255813 (stack); older versions retained.
- Runtime/device acceptance pending. Global percentages include all seven modules; CTX unavailable.

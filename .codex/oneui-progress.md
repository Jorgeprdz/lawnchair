# Lawnchair OneUI Progress

Branch: feature/oneui-enhancements
HEAD (audited before checkpoint): 51bc0d00afd7914fc7aeb8babbec9a82046318ec
Base 16-dev: 155ccd1ee49e29e839ca603072777a9b7ef1e52c — unchanged; no merge.
Global: 73% — equal-weight estimate across seven modules, not acceptance completion.

Modules:
- M1 Large Folders: 75% — persisted 2x2, safe placement, 3x3 direct preview; rounded-square correction validating.
- M2 Pixel Search: 60% — real provider discovery/native QSB host/lifecycle; absent-provider fallback tested.
- M3 One UI Finder: 90% — native gesture action and safe missing-component test; Samsung validation pending.
- M4 Dock Glass: 65% — four persisted modes and geometry/recreation tested; true blur/device coverage pending.
- M5 Widget Grid Snap: 70% — native provider constraints, proportional migration and grid preflight.
- M6 Widget Stacks: 75% — persistent real hosts, picker/editor, paging, resize, reorder/remove and process restore.
- M7 Max Icons / Max Folders: 75% — per-item Max Icon; SAME folder model/placement as M1.

Completed:
- M3 explicit Samsung component; native configurable gesture and guarded platform failures.
- M5 uses existing CellLayout/reorder/migrations; rejects impossible provider minima before destructive migration.
- M6 persistent parent/member rows, native widget host, picker/config routing and bottom-sheet editor.
- M6 hidden/recreated host lookup, active-page persistence, generation-guarded rebind and individual ID cleanup.
- M1/M7 share safe 1x1<->2x2 placement, persisted flags/spans, native actions and grid migration.
- M1 9-icon direct preview with all contents accessible; Max/normal restoration preserves identity/metadata.
- M4 Off/Solid/Blur/Crystal; M2 real Pixel Search replaces Google option with native search fallback.

Current:
- Final validation phase: source implemented; fix real failures, run remote CI/emulator, capture actual UI.
- USER visual correction f275b49: Large/Max Folder must be a ROUNDED SQUARE, never circular.
- Native RoundedSquare corner radius is 16% of measured preview edge; drawing/clipping/reveal share shape.
- 2x2 occupancy, persistence, 3x3 preview and normal-folder theme unchanged by visual correction.
- CI34442888473/f275b49 GREEN: compile/style, all8 tests and process-death restore; 13 actual screenshots.
- e278912 fixes negative label padding; CI34443803289 all3 APK/style GREEN; label-bound test failed.
- HOME-category harness6f60a5b passed full emulator CI34443608218.
- Current CI34446579147/b7c3855 validates settled icon-centered taps and durable screenshot requests.
- 418396f initializes preview rule before spring animation; shares square shape and all9 preview members.
- CI34445531657 all3 APK GREEN; spring crash absent; Max touch/Crystal capture failed.
- Capture requests now durable shell files (logcat pressure dropped07); wait settled workspace before app taps.
- CI34445977242: light wallpaper setup PASS;10 real light-background captures downloaded; Max tap still failed.

Next:
- Inspect current CI failures if any; preserve validation; repeat until coherent acceptance checks pass.
- Download every newly produced PNG, including valid partial runs, to user's requested directory below.
- Visually verify new square folder and replace canonical02/13 screenshots; preserve prior versions with run prefix.
- Finish remaining provider/drag/grid/orientation coverage as available; report physical-device gaps honestly.
- USER authorizes final verified APK download and automatic installation on phone; report Android confirmation if required.

Architecture decisions:
- GitHub is durable state; only this feature branch; no clone/full checkout/local full build, force push or merge.
- Stack type12 parent owns ONE CellLayout footprint; real widget rows use parent ID as container.
- Rank orders members; OPTIONS stores active member DATABASE ROW ID; migration remaps parent/member/active IDs.
- Existing LauncherWidgetHolder/AppWidgetHost, PagedView, PageIndicatorDots and ComposeBottomSheet reused.
- Configure before atomic member insertion; cancellation/stale destination cleans tracked AppWidget IDs.
- One remaining member stays valid stack until safe atomic standalone conversion is implemented.
- Missing provider retains removable pending page; loader propagates parent spans to all members.
- M1 canonical FolderInfo.FLAG_LARGE_FOLDER; M7 folder uses SAME flag, never a second Max Folder system.
- WorkspaceItemInfo.FLAG_MAX_ICON distinguishes intentional 2x2; shared CellLayout resize handles both types.
- Max is workspace-only; icon/theme/badges/identity retained; no global DeviceProfile scaling or Nothing mode.
- M4 native AOSP BackgroundBlurDrawable where accessible/enabled; otherwise translucent fallback, no fake blur claim.
- Pixel package rk.android.app.pixelsearch; discover actual installed provider, never invent component.
- Pixel reuses QSB host1026 with bound/pending/configured IDs and backup restore mapping; no independent host.
- DefaultLauncher GPL-specific files ARCHITECTURAL REFERENCE ONLY; no GPL/proprietary source/assets copied.

Relevant files:
- .github/workflows/ci.yml; .github/scripts/oneui-{emulator-smoke.sh,screenshots.py}; tests/oneui/
- src/com/android/launcher3/{Workspace.java,CellLayout.java,Launcher.java,Hotseat.java,AppWidgetResizeFrame.java}
- model/{ModelWriter.java,WorkspaceItemProcessor.kt,GridSizeMigrationDBController.java,GridSizeMigrationLogic.kt,DbEntry.kt}
- model/data/{WidgetStackInfo.kt,FolderInfo.java,WorkspaceItemInfo.java}; util/WorkspaceItemSize.java
- widget/{WidgetStackView.kt,WidgetStackController.kt,WidgetGridPreflight.kt,WidgetGridMigrationGuard.kt}
- folder/{FolderIcon.java,PreviewBackground.java,PreviewItemManager.java,FolderAnimationManager.java}; BubbleTextView.java
- graphics/DockGlassBackground.java; qsb/QsbContainerView.java; AppWidgetsRestoredReceiver.java
- lawnchair: widgetstack/WidgetStackEditor.kt; qsb/PixelSearchQsbFragment.kt; preferences2/PreferenceManager2.kt

Validation:
- All3 APK variants + AndroidTest/style GREEN: CI34443803289/e278912, including square shape and label-padding fix.
- CI34441287074: 7/8 tests PASS + process-death PASS; only Max fixture failed (fixed04d433a).
- M6 real tap/vertical scroll/horizontal paging/resize/reorder/remove/model reload PASS there; hidden host regression PASS.
- Missing Finder/Pixel fallback tests PASS; this does NOT validate genuine Samsung/Pixel providers.
- CI34441778019: Max toggle/occupancy/10-member folder/open/recreation/restore PASS; dock and SQLite migrations PASS.
- Intermittent test focus/taps investigated: stable bounds4acde68 and actual HOME intent6f60a5b; retain real assertions.
- API35 boots/installs/starts actual launcher; force-stop/restore test PASS across two instrumentation invocations.
- Original13 multivalent cases unexecuted; current Android suite includes real SQLite Max migration tests.
- Manual final CI: oneui-emulator=true; oneui-fast=true for Debug/test/style, false for all3 APK variants.
- Automatic feature push builds paused at user request; do not disable checks or treat compile alone as100%.

Screenshots:
- USER destination on emulator AND user's Android: /storage/emulated/0/Download/lawnchair/
- REAL ADB screencap/pull only; PNG signature/IHDR checked in CI, full CRC/zlib integrity checked locally.
- 104 PNG files downloaded, covering13 views; canonical02/13 visually verified square; label recapture pending.
- Latest light screenshots: CI34445977242/artifact10139936029; main10 updated; canonical02/03/13 still older.
- Required01-home,02-large-folder,03-large-folder-open,04-dock-off,05-dock-solid,06-dock-blur,07-dock-crystal.png;
- 08-widget-grid,09-widget-stack-page1,10-widget-stack-page2,11-widget-stack-editor,12-max-icon,13-max-folder.png.
- Square shape confirmed visually; label-padding fix e278912 needs refreshed screenshot/glyph-bound confirmation.
- Pixel/Finder screenshots ONLY if real providers present; stock emulator lacks them; never fabricate images.
- Final report: compile/emulator/physical validation separately; screenshot count/names/artifact/run/path/omissions.

Blockers:
- ADB serial emulator-5554 is actual Samsung SM-S931B/API36 (ro.kernel.qemu=0); read-only access works.
- Phone has app.lawnchair.nightly; Finder resolves; actual Pixel provider rk.android.app.pixelsearch/.SearchWidget.
- No phone installation/settings changes yet; final installation authorized. CI emulator remains separate.
- CTX unavailable; report no available metric, never invent percentage; no manual /compact.

# Lawnchair OneUI Progress

Branch: feature/oneui-enhancements
HEAD (audited before checkpoint): 52ddec651a2ba354cd9e9f8ed920c940bc0a3234
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
- USER: conserve quota; ONE CI at a time, fast Debug/test path until regressions pass; no scope expansion.
- USER authorizes final APK download/install on phone; preserve existing Nightly data, never uninstall to bypass signature.
- 52ddec6 fixes nullable SurfaceControl found by new GNC test; CI34450404326 running.
- CI34449083380 compile/style GREEN; attempt1 ADB transport lost; attempt2 GNC null surface crash.
- Earlier CI34446579147: 7/8 PASS; Max second launch readiness timeout. Exact diagnostic added f145252.
- Rounded-square folder visually confirmed; label-spacing correction still needs complete Max scenario/recapture.
- Light wallpaper setup PASS; canonical10 screenshots updated; canonical02/03/13 still older.

Next:
- Inspect current CI failures if any; preserve validation; repeat until coherent acceptance checks pass.
- Download every newly produced PNG, including valid partial runs, to user's requested directory below.
- Visually verify new square folder and replace canonical02/13 screenshots; preserve prior versions with run prefix.
- Finish remaining provider/drag/grid/orientation coverage as available; report physical-device gaps honestly.
- USER authorizes final verified APK download and automatic installation on phone; report Android confirmation if required.

Bugfixes:
- 0ddd824: GNC surface layout/valid bounds before handoff; no translation springs on real workspace icon.
- Restore visibility on surface loss/finish/3s callback timeout; isolate stale finish callbacks per contract.
- 7bb4ad7 invalidates old finish before new layout;52ddec6 waits for nullable surface creation.
- Samsung physical gesture reproduction still pending; do not claim fixed solely from code.

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
- M6 real tap/vertical scroll/horizontal paging/resize/reorder/remove/model reload PASS there; hidden host regression PASS.
- Missing Finder/Pixel fallback tests PASS; this does NOT validate genuine Samsung/Pixel providers.
- Intermittent test focus/taps investigated: stable bounds4acde68 and actual HOME intent6f60a5b; retain real assertions.
- API35 boots/installs/starts actual launcher; force-stop/restore test PASS across two instrumentation invocations.
- Original13 multivalent cases unexecuted; current Android suite includes real SQLite Max migration tests.
- Manual final CI: oneui-emulator=true; oneui-fast=true for Debug/test/style, false for all3 APK variants.
- Automatic feature push builds paused at user request; do not disable checks or treat compile alone as100%.

Screenshots:
- USER destination on emulator AND user's Android: /storage/emulated/0/Download/lawnchair/
- REAL ADB screencap/pull only; PNG signature/IHDR checked in CI, full CRC/zlib integrity checked locally.
- 120 PNG files downloaded, covering13 views; canonical02/13 visually verified square; label recapture pending.
- Latest light screenshots: CI34445977242/artifact10139936029; main10 updated; canonical02/03/13 still older.
- Required01-home,02-large-folder,03-large-folder-open,04-dock-off,05-dock-solid,06-dock-blur,07-dock-crystal.png;
- 08-widget-grid,09-widget-stack-page1,10-widget-stack-page2,11-widget-stack-editor,12-max-icon,13-max-folder.png.
- Square shape confirmed visually; label-padding fix e278912 needs refreshed screenshot/glyph-bound confirmation.
- Pixel/Finder screenshots ONLY if real providers present; stock emulator lacks them; never fabricate images.
- Final report: compile/emulator/physical validation separately; screenshot count/names/artifact/run/path/omissions.

Blockers:
- ADB serial emulator-5554 is actual Samsung SM-S931B/API36 (ro.kernel.qemu=0); read-only access works.
- Phone default HOME app.lawnchair.nightly 16.Dev.(#5074); Finder resolves; Pixel provider .SearchWidget exists.
- No phone installation/settings changes yet; final installation authorized. CI emulator remains separate.

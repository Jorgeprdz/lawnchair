# Lawnchair OneUI Progress

Branch: feature/oneui-enhancements
HEAD (audited before checkpoint): 3f97017e193b2b3381403e172edaae60b2d15068
Base 16-dev: 155ccd1ee49e29e839ca603072777a9b7ef1e52c — unchanged; no merge.
Global: 76% — equal-weight estimate across seven modules, not acceptance completion.

Modules:
- M1 Large Folders: 85% — persisted 2x2, safe placement, 3x3 direct preview; rounded square/label/direct launch/recreation validated.
- M2 Pixel Search: 60% — real provider discovery/native QSB host/lifecycle; absent-provider fallback tested.
- M3 One UI Finder: 90% — native gesture action and safe missing-component test; Samsung validation pending.
- M4 Dock Glass: 65% — four persisted modes and geometry/recreation tested; true blur/device coverage pending.
- M5 Widget Grid Snap: 70% — native provider constraints, proportional migration and grid preflight.
- M6 Widget Stacks: 80% — persistent real hosts, picker/editor, paging, resize, reorder/remove and process restore.
- M7 Max Icons / Max Folders: 85% — per-item Max Icon; SAME folder model/placement as M1.

Completed:
- M3 explicit Samsung component; native configurable gesture and guarded platform failures.
- M5 uses existing CellLayout/reorder/migrations; rejects impossible provider minima before destructive migration.
- M6 persistent parent/member rows, native widget host, picker/config routing and bottom-sheet editor.
- M6 hidden/recreated host lookup, active-page persistence, generation-guarded rebind and individual ID cleanup.
- M1/M7 share safe 1x1<->2x2 placement, persisted flags/spans, native actions and grid migration.
- M1 9-icon direct preview with all contents accessible; Max/normal restoration preserves identity/metadata.
- M4 Off/Solid/Blur/Crystal; M2 real Pixel Search replaces Google option with native search fallback.

Current:
- USER: conserve quota; do NOT repeat passing tests; no further work on home-return bug unless requested.
- CI34453991685 GREEN: Debug APK/AndroidTest/style and ONLY pending Max recreation regression.
- CI34452779043: all13 light screenshots; labels/direct launches/folder open PASS; old harness recreate failed.
- Actual current-activity recreation fixed3f97017; isolated regression PASS; previous approved cases not repeated.
- APK downloaded and ADB-installed successfully on Samsung SM-S931B/API36; cold launch OK (2216ms), PID3109.
- Installed app.lawnchair.debug separately; original Nightly signature differs; Nightly remains default HOME.

Next:
- User can choose Debug as HOME to validate actual Samsung gesture return; do not replace their default silently.
- Physical real Pixel hosting/Finder gesture, drag/grid/orientation/provider-failure/true-blur checks remain pending.
- No further broad test suites or extra builds without a new failure/change that needs them.

Bugfixes:
- 0ddd824/7bb4ad7/52ddec6 GNC: valid laid-out surface before hiding icon, no springs on original icon.
- Guard missing surface; restore on finish/loss/3s timeout; isolate stale finish callbacks before next layout.
- Native completion/lost/stale callback test PASS in34450404326 and later; Samsung gesture still unvalidated.
- User says Lawnchair has experimental fix; do not spend further cycles on this bug.

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
- CI34453991685 (3f97017) GREEN: Debug compilation, AndroidTest/style, isolated Max recreation/restore PASS.
- CI34452779043 (707dfb0): 8/9 whole cases PASS; Max all UI checks passed before obsolete harness recreate.
- M6 real tap/vertical scroll/horizontal paging/resize/reorder/remove/model reload and process death PASS.
- Missing Finder/Pixel fallback, SQLite Max migrations, hidden widget host and GNC callback tests PASS.
- Validation accumulated across targeted runs; do NOT claim a final all9 suite rerun or all7 modules100%.
- Latest all3 build before GNC:34445531657; latest chosen installed artifact is verified GithubDebug.
- Final phone startup checked; actual provider integration, Samsung gesture and hardware blur still pending.
- APK: /storage/emulated/0/Download/lawnchair/Lawnchair.16.Dev.(#230).github.debug.apk
- APK artifact10142869416/assembleLawnWithQuickstepGithubDebug, workflow34453991685.
- APK SHA256 be8b5f524456aec33aadf21772315a4b3bce11b6a8779b49d4e0e4030e91ae6e; apksigner verified.

Screenshots:
- USER destination on emulator AND user's Android: /storage/emulated/0/Download/lawnchair/
- REAL ADB screencap/pull only; PNG signature/IHDR checked in CI, full CRC/zlib integrity checked locally.
- 152 PNG files downloaded, covering13 views; canonical13 refreshed; square and label visually verified.
- Latest canonical13: CI34452779043/artifact10142579904, Lawnchair-OneUI-Screenshots; all downloaded.
- Required01-home,02-large-folder,03-large-folder-open,04-dock-off,05-dock-solid,06-dock-blur,07-dock-crystal.png;
- 08-widget-grid,09-widget-stack-page1,10-widget-stack-page2,11-widget-stack-editor,12-max-icon,13-max-folder.png.
- 707dfb0 clamps negative grid padding; square shape and separated full label confirmed visually and by test.
- Pixel/Finder screenshots ONLY if real providers present; stock emulator lacks them; never fabricate images.
- Final report: compile/emulator/physical validation separately; screenshot count/names/artifact/run/path/omissions.

Blockers:
- ADB serial emulator-5554 is actual Samsung SM-S931B/API36 (ro.kernel.qemu=0); read-only access works.
- Phone default HOME app.lawnchair.nightly 16.Dev.(#5074); Finder resolves; Pixel provider .SearchWidget exists.
- Phone Nightly signer747c3645 differs from CI Nightly signer07f6de48; direct update impossible, NEVER uninstall.
- Debug installed and starts; actual HOME gesture validation requires choosing it as HOME (Nightly preserved).

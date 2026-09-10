# Lawnchair OneUI Progress

Branch:
feature/oneui-enhancements

HEAD (code audited before this checkpoint):
5c25056dc25b15e8b2505e20d5c5790ca57ce22c

Global:
23% (equal-weight estimate across six modules; not acceptance completion)

Modules:
- M1 Large Folders: 0% — no committed implementation
- M2 Pixel Search: 0% — not started
- M3 One UI Finder: 80% — selector/config/explicit launch present; validation pending
- M4 Dock Glass: 0% — not started
- M5 Widget Grid Snap: 60% — migration/drop code present; unsafe post-placement clamp needs correction
- M6 Widget Stacks: 0% — no committed model or rank normalization

Completed:
- Remote recovery verified 9 commits ahead, 0 behind before this checkpoint.
- Base and merge-base: 155ccd1ee49e29e839ca603072777a9b7ef1e52c.
- Inspected all feature code diffs and existing CI workflow.
- M1/M6 transient work did not survive: no WidgetStack files in remote tree.
- M3 action is registered and catches launch failures.
- M5 migration searches vacant spans instead of always using minimum size.

Current:
- Validate M3, repair M5 placement safety, establish remote CI.

Next:
- Move widget constraints before CellLayout placement; never enlarge its reserved result.
- Check Finder availability handling, then run existing CI on feature branch.
- Continue M6 architecture discovery only after M5 safety correction.

Architecture decisions:
- Remote GitHub is durable state; no local clone or full local build.
- Only feature/oneui-enhancements may change; never merge into 16-dev.
- Use existing CellLayout placement, spans and AppWidget host.
- Stack model must persist membership/rank and own one workspace footprint.
- Inspect DefaultLauncher/Lawnchair PR 7029 and stack references before design/reuse.
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
- No feature Actions runs; Actions enabled but workflow registry is empty.
- CI dispatch by ci.yml returned HTTP 404: workflow not found on default branch.
- Existing CI builds LawnWithQuickstep NightlyRelease/GithubDebug/PlayDebug and spotlessCheck.
- Device interaction/persistence/provider validation remains pending for all modules.

Blockers:
- Remote CI workflow registration unresolved; source writes being verified by this checkpoint.
- User prompt ends in APK section after “build”; remaining APK instructions not received.
- WIP: none committed; no transient feature edits.

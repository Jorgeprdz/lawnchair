# OneUI Next Nightly Refresh Implementation Plan

> **For agentic workers:** Execute this plan task-by-task with tests and review at each checkpoint.

**Goal:** Bring `feature/oneui-next` onto current Lawnchair 16 development while preserving One UI features, refreshing themed icons on night-mode changes, refining Blur/Frosty, and shipping an Actions APK.

**Architecture:** Merge official upstream `16-dev` to preserve fork history, then use application configuration callbacks, icon freshness identity, and the existing model reload path for event-driven refresh. Keep the existing One UI glass drawable pipeline and platform blur APIs, sharing the Normal/Frosty material parameters without adding another renderer. Use the existing OneUI build workflow for the installable artifact.

**Tech Stack:** Android Launcher3, Kotlin/Java, Android Gradle Plugin, GitHub Actions.

**Spec:** User request in this conversation; upstream base `LawnchairLauncher/lawnchair@494399a69904fb6f3d1201d2878a6c1bfb884d64`.

## Global Constraints

- Preserve all existing One UI Next functionality and `applicationId`.
- Do not remove One UI, widget-stack, Crystal, Finder, or blur behavior to make builds pass.
- Avoid dependency upgrades beyond those already present in official upstream.
- Keep UI-mode updates event-driven; do not poll or restart Lawnchair.
- Keep blur work cached and bounded; do not add per-frame bitmap captures or heavy dependencies.
- Report runtime-only acceptance checks as unverified unless exercised on an emulator/device.

## Review Focus

- Night-mode changes that do not alter icon preferences must invalidate launcher icon state and caches.
- Existing-folder, hotseat, and All Apps icons must be rebound through the model callback path.
- Light/Dark drawable resources and cached icon-map state must be resolved after configuration changes.
- Blur drawable must rebuild on uiMode changes while preserving Crystal as its separate renderer.
- Samsung and non-Samsung fallback paths must remain safe when platform blur is unavailable.

---

### Task 1: Merge current official development base

**Files:** `src/com/android/launcher3/Workspace.java` (semantic conflict only).

- [x] Create and publish `backup/oneui-next-pre-nightly-update` at the original branch head.
- [x] Merge upstream `16-dev` and retain the One UI guard that keeps widgets, stacks, and Max items out of the hotseat.
- [x] Commit the merge as `Sync OneUI Next with latest Lawnchair 16-dev`.

### Task 2: Refresh themed icons on night-mode changes

**Files:** `src/com/android/launcher3/graphics/ThemeManager.kt`, `lawnchair/src/app/lawnchair/icons/LawnchairThemeManager.kt`, `lawnchair/src/app/lawnchair/icons/LawnchairIconProvider.kt`, focused tests.

- [x] Add a focused test for LIGHT↔DARK identity change and unrelated configuration changes.
- [x] Register an application `ComponentCallbacks` listener in `ThemeManager`; notify theme listeners only when `UI_MODE_NIGHT_MASK` changes and unregister through the singleton lifecycle.
- [x] Include the current night-mode mask in icon-state equality and `LawnchairIconProvider.getStateForApp`; invalidate cached themed icon maps on the same transition. Track the Lawnchair Light/Dark preference as part of its icon state as well.
- [x] Route theme refresh through icon-pool and in-memory cache invalidation before `LauncherModel.forceReload()`; persistent icon rows use the changed freshness ID.

### Task 3: Refine Normal and Frosty glass profiles

**Files:** `src/com/android/launcher3/graphics/OneUiGlassBackground.java`, `src/com/android/launcher3/graphics/SamsungGlassBlur.java`, focused tests.

- [x] Add focused tests for Normal/Frosty distinction and light/dark profile tuning.
- [x] Make Normal use a lower tint and platform blur with no extra frosted haze layers; retain a restrained translucent fallback.
- [x] Give Frosty a stronger blur, adaptive low-opacity haze, and subtle highlight; retain Crystal's renderer unchanged.
- [x] Include night mode in drawable delegate and Samsung blur state cache keys and refresh the Hotseat surface when the mode changes.

### Task 4: Verify and deliver APK through Actions

**Files:** `.github/workflows/oneui-build.yml` and any minimum build fixes.

- [ ] Run unit tests, `assembleLawnWithQuickstepGithubDebug`, and compile OneUI instrumentation tests on Actions. Local ARM64 runner cannot start AGP's x86-64 AAPT2 binary.
- [ ] Upload the installable debug APK under `Lawnchair-OneUI-Next-Nightly` without rewriting the app's applicationId in CI.
- [ ] Push the completed commits to `feature/oneui-next`, monitor the new run for its exact SHA, download the artifact, and report APK name, artifact URL, and SHA-256.

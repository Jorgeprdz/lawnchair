# Crystal Wallpaper Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Crystal's recursive per-draw launcher capture with one generation-aware cache of the real static wallpaper, then render the dock and folders from that cache without black frames or heavyweight draw-time allocation.

**Architecture:** `Launcher` owns one `OneUiWallpaperBackdropRepository`. An Android source decodes the permitted static system wallpaper on a background executor; a pure generation store publishes immutable snapshots atomically and retains pinned generations during folder animations. Crystal renderers consume explicit surface roles, map screen bounds into the cached wallpaper, and reuse shader/effect resources; live wallpapers select Samsung native blur plus a contour overlay instead of pretending to have sampled pixels.

**Tech Stack:** Android Views/Canvas, `WallpaperManager`, `ParcelFileDescriptor`, `BitmapFactory`, AGSL `RuntimeShader` on API 33+, Samsung `SemBlurInfo` reflection, JUnit 4/Truth host tests, AndroidX instrumentation, Gradle, GitHub Actions, ADB on Samsung SM-S931B Android 16.

**Spec:** `docs/superpowers/specs/2026-09-20-crystal-wallpaper-cache-design.md`

## Global Constraints

- Remote source of truth is `https://github.com/Jorgeprdz/lawnchair.git`; implement only on `feature/crystal-wallpaper-cache`, based on remote commit `a5f76a90bcf00f308658c91260e9f63e6b320ce9` plus the committed spec.
- Do not import or cherry-pick unpushed work from another checkout.
- Do not modify `DockPreferences.kt` or `FolderPreferences.kt`; Settings previews remain outside scope.
- Do not change package name, application ID, signing configuration, or launcher role.
- Do not use `PixelCopy`, `MediaProjection`, root, a new privileged service, `Workspace.draw()`, `DragLayer.draw()`, `Hotseat.draw()`, or `View.getRootView().draw()` as a wallpaper source.
- `WallpaperManager.getWallpaperFile(FLAG_SYSTEM)` is the primary static source and may run only after confirmed wallpaper access; `getDrawable()` is not the Android 14+ primary path.
- Static wallpaper decode and file I/O run off the main thread; snapshot publication, pin operations, and renderer reads run on the main thread.
- A failed, stale, or superseded load never clears a valid snapshot and never produces a black frame.
- Steady-state `draw()` creates no `Bitmap`, `Canvas`, `RuntimeShader`, `RenderEffect`, `BitmapShader`, `Paint`, `Path`, `Rect`, `RectF`, or `Matrix`.
- Keep current plus in-flight wallpaper bitmap memory below 24 MiB.
- Static wallpaper gets real refraction; live wallpaper gets Samsung compositor blur plus contour overlay, or the sober non-black fallback when Samsung blur is unavailable.
- Normal Lawnchair dock icons, interaction, QSB, accessibility, paging, and layout remain unchanged and render above Crystal.
- Push every completed task commit to `origin/feature/crystal-wallpaper-cache`.
- Final APK path is `/storage/emulated/0/Download/Lawnchair-OneUI-Crystal-Wallpaper-Cache.apk`.

## Review Focus

- Permission is revoked while a replacement loads: keep the last valid snapshot and select sober fallback only when no valid snapshot exists; covered by Task 4 repository tests and Task 7 instrumentation.
- Two asynchronous decodes finish out of order: only the latest requested generation may publish; covered by Task 1 store tests and Task 4 repository tests.
- Wallpaper changes from static to live or live to static without changing orientation: identity comparison must invalidate and choose the correct material; covered by Task 3 identity tests and Task 6 policy tests.
- Folder animation is cancelled rather than completed: its snapshot pin must close exactly once and a retired bitmap may then be released; covered by Task 1 pin tests and Task 6 folder-state instrumentation.
- An extreme panoramic or smaller-than-display wallpaper reaches a dock at a screen edge: transform padding clamps without stretching or out-of-bounds samples; covered by Task 2 geometry tests.

---

### Task 1: Branch CI, Host-Test Wiring, and Atomic Generation Store

**Files:**
- Modify: `.github/workflows/oneui-build.yml`
- Modify: `build.gradle`
- Create: `src/com/android/launcher3/graphics/WallpaperSnapshotStore.java`
- Create: `tests/crystal/src/com/android/launcher3/graphics/WallpaperSnapshotStoreTest.java`

**Interfaces:**
- Consumes: no feature interfaces.
- Produces: `WallpaperSnapshotStore<T>`, `requestGeneration()`, `publish(long, T)`, `fail(long)`, `current()`, `pinCurrent()`, and `SnapshotPin<T>.close()` for Task 4.

- [ ] **Step 1: Make the feature branch buildable by GitHub Actions and wire pure host tests**

Add `feature/crystal-wallpaper-cache` to `.github/workflows/oneui-build.yml` under `on.push.branches`. Change the normalization push target from the hard-coded `feature/oneui-next` to `HEAD:${GITHUB_REF_NAME}` so this workflow cannot mutate a different branch.

Add this source set and dependencies in `build.gradle`:

```groovy
sourceSets {
    test {
        java.srcDirs = ['tests/crystal/src']
    }
}

dependencies {
    testImplementation libs.junit
    testImplementation libs.google.truth
}
```

Run:

```bash
git submodule update --init --recursive
grep -n "feature/crystal-wallpaper-cache" .github/workflows/oneui-build.yml
./gradlew tasks --all | grep testLawnWithQuickstepGithubDebugUnitTest
```

Expected: the branch appears in the workflow and the variant unit-test task exists.

- [ ] **Step 2: Write failing generation and pin tests**

Create tests with these behaviors:

```java
@Test
public void stalePublishCannotReplaceLatestGeneration() {
    WallpaperSnapshotStore<String> store = new WallpaperSnapshotStore<>(released::add);
    long first = store.requestGeneration();
    long second = store.requestGeneration();

    assertThat(store.publish(first, "old")).isFalse();
    assertThat(store.publish(second, "new")).isTrue();
    assertThat(store.current()).isEqualTo("new");
    assertThat(released).containsExactly("old");
}

@Test
public void failedReloadKeepsCurrentSnapshot() {
    WallpaperSnapshotStore<String> store = new WallpaperSnapshotStore<>(released::add);
    long first = store.requestGeneration();
    store.publish(first, "current");
    long failed = store.requestGeneration();

    store.fail(failed);

    assertThat(store.current()).isEqualTo("current");
}

@Test
public void retiredSnapshotWaitsForCancelledAnimationPin() {
    WallpaperSnapshotStore<String> store = new WallpaperSnapshotStore<>(released::add);
    long first = store.requestGeneration();
    store.publish(first, "pinned");
    WallpaperSnapshotStore.SnapshotPin<String> pin = store.pinCurrent();
    long second = store.requestGeneration();
    store.publish(second, "replacement");

    assertThat(released).isEmpty();
    pin.close();
    pin.close();

    assertThat(released).containsExactly("pinned");
}
```

- [ ] **Step 3: Run the tests and verify RED**

Run:

```bash
./gradlew testLawnWithQuickstepGithubDebugUnitTest \
  --tests com.android.launcher3.graphics.WallpaperSnapshotStoreTest --console=plain
```

Expected: compilation fails because `WallpaperSnapshotStore` does not exist.

- [ ] **Step 4: Implement the minimal generic store**

Implement a main-thread-confined store with this public surface:

```java
public final class WallpaperSnapshotStore<T> {
    public interface Releaser<T> { void release(T value); }

    public static final class SnapshotPin<T> implements AutoCloseable {
        public T value();
        @Override public void close();
    }

    public WallpaperSnapshotStore(Releaser<T> releaser);
    public long requestGeneration();
    public long requestedGeneration();
    public boolean publish(long generation, T candidate);
    public void fail(long generation);
    @Nullable public T current();
    @Nullable public SnapshotPin<T> pinCurrent();
    public void clear();
}
```

Keep an identity-based pin count per published value. `publish()` releases a stale candidate immediately, retires the replaced value, and releases the retired value only when its count becomes zero. `SnapshotPin.close()` is idempotent.

- [ ] **Step 5: Run GREEN and the variant test suite**

Run:

```bash
./gradlew testLawnWithQuickstepGithubDebugUnitTest \
  --tests com.android.launcher3.graphics.WallpaperSnapshotStoreTest --console=plain
./gradlew testLawnWithQuickstepGithubDebugUnitTest --console=plain
```

Expected: both commands pass.

- [ ] **Step 6: Commit and push**

```bash
git add .github/workflows/oneui-build.yml build.gradle \
  src/com/android/launcher3/graphics/WallpaperSnapshotStore.java \
  tests/crystal/src/com/android/launcher3/graphics/WallpaperSnapshotStoreTest.java
git commit -m "test: define atomic Crystal wallpaper generations"
git push origin feature/crystal-wallpaper-cache
```

### Task 2: Wallpaper Crop, Offset, and Lens Mathematics

**Files:**
- Create: `src/com/android/launcher3/graphics/WallpaperBackdropTransform.java`
- Create: `src/com/android/launcher3/graphics/OneUiCrystalOptics.java`
- Create: `tests/crystal/src/com/android/launcher3/graphics/WallpaperBackdropTransformTest.java`
- Create: `tests/crystal/src/com/android/launcher3/graphics/OneUiCrystalOpticsTest.java`

**Interfaces:**
- Consumes: immutable dimensions and offsets supplied by Task 3 snapshots.
- Produces: `WallpaperBackdropTransform.map(Input)` returning reusable numeric mapping data; `OneUiCrystalOptics.edgeWeight(...)` and role-scaled optical values for Task 5.

- [ ] **Step 1: Write failing mapping tests**

Use a pure Java `Input` value containing source width/height, bitmap width/height, display width/height, surface screen bounds, horizontal/vertical offsets, optical padding, and RTL. Assert these cases:

```java
@Test
public void centeredPortraitDockMapsIntoCenterCroppedWallpaper() {
    Input input = new Input(2160, 2400, 1080, 1200, 1080, 2400,
            120, 2120, 960, 220, 0.5f, 0.5f, 48, false);

    Result result = WallpaperBackdropTransform.map(input);

    assertThat(result.isValid()).isTrue();
    assertThat(result.left()).isAtLeast(0f);
    assertThat(result.right()).isAtMost(1080f);
    assertThat(result.top()).isLessThan(result.bottom());
}

@Test
public void panoramicWallpaperMovesWithPageOffset() {
    Result left = WallpaperBackdropTransform.map(panoramicInput(0f, false));
    Result right = WallpaperBackdropTransform.map(panoramicInput(1f, false));
    Result rtl = WallpaperBackdropTransform.map(panoramicInput(1f, true));

    assertThat(right.left()).isGreaterThan(left.left());
    assertThat(rtl.left()).isWithin(0.01f).of(left.left());
}

@Test
public void edgePaddingClampsWithoutCollapsingSampleWidth() {
    Result result = WallpaperBackdropTransform.map(edgeInputWithPadding(96));
    assertThat(result.left()).isEqualTo(0f);
    assertThat(result.right() - result.left()).isGreaterThan(1f);
}
```

- [ ] **Step 2: Write failing optical-band tests**

```java
@Test
public void edgeBandRefractsMoreThanStableCenter() {
    float edge = OneUiCrystalOptics.edgeWeight(-2f, 14f);
    float center = OneUiCrystalOptics.edgeWeight(-80f, 14f);
    assertThat(edge).isGreaterThan(center);
}

@Test
public void zeroPercentStillHasSubtleOpticalMaterial() {
    OneUiCrystalOptics.Values values = OneUiCrystalOptics.forIntensity(0, 3f);
    assertThat(values.refractionPx()).isGreaterThan(0f);
    assertThat(values.tintAlpha()).isGreaterThan(0f);
}
```

- [ ] **Step 3: Run RED**

```bash
./gradlew testLawnWithQuickstepGithubDebugUnitTest \
  --tests 'com.android.launcher3.graphics.WallpaperBackdropTransformTest' \
  --tests 'com.android.launcher3.graphics.OneUiCrystalOpticsTest' --console=plain
```

Expected: compilation fails because the transform and optics classes do not exist.

- [ ] **Step 4: Implement pure allocation-free math**

Implement `WallpaperBackdropTransform.map(Input)` with center-crop scale:

```java
float scale = Math.max(displayWidth / (float) sourceWidth,
        displayHeight / (float) sourceHeight);
float scaledWidth = sourceWidth * scale;
float overflowX = Math.max(0f, scaledWidth - displayWidth);
float visibleLeft = overflowX * clamp01(effectiveHorizontalOffset);
```

Map screen-space surface coordinates through the visible crop, convert to decoded-bitmap coordinates, expand by optical padding, and clamp each edge. RTL uses `1f - horizontalOffset`.

Implement `OneUiCrystalOptics.Values` as immutable floats calculated only when intensity/density changes. Use a smoothstep-style edge band whose center approaches zero but whose contour remains nonzero at intensity zero.

- [ ] **Step 5: Run GREEN**

```bash
./gradlew testLawnWithQuickstepGithubDebugUnitTest \
  --tests 'com.android.launcher3.graphics.WallpaperBackdropTransformTest' \
  --tests 'com.android.launcher3.graphics.OneUiCrystalOpticsTest' --console=plain
```

Expected: all mapping and optics tests pass.

- [ ] **Step 6: Commit and push**

```bash
git add src/com/android/launcher3/graphics/WallpaperBackdropTransform.java \
  src/com/android/launcher3/graphics/OneUiCrystalOptics.java \
  tests/crystal/src/com/android/launcher3/graphics/WallpaperBackdropTransformTest.java \
  tests/crystal/src/com/android/launcher3/graphics/OneUiCrystalOpticsTest.java
git commit -m "feat: add Crystal wallpaper mapping and optics math"
git push origin feature/crystal-wallpaper-cache
```

### Task 3: Real Static-Wallpaper Source and Immutable Snapshot

**Files:**
- Create: `src/com/android/launcher3/graphics/WallpaperIdentity.java`
- Create: `src/com/android/launcher3/graphics/WallpaperBackdropSnapshot.java`
- Create: `src/com/android/launcher3/graphics/WallpaperBackdropSource.java`
- Create: `src/com/android/launcher3/graphics/WallpaperDecodePlan.java`
- Create: `src/com/android/launcher3/graphics/AndroidWallpaperBackdropSource.java`
- Create: `tests/crystal/src/com/android/launcher3/graphics/WallpaperIdentityTest.java`
- Create: `tests/crystal/src/com/android/launcher3/graphics/WallpaperDecodePlanTest.java`
- Create: `tests/oneui/src/com/android/launcher3/OneUiWallpaperBackdropSourceTest.java`

**Interfaces:**
- Consumes: `FileAccessManager.wallpaperAccessState`, Android `WallpaperManager`, display dimensions, and the 24 MiB combined budget.
- Produces: `WallpaperBackdropSource.readIdentity()`, `load(LoadRequest)`, immutable `WallpaperBackdropSnapshot`, and typed `LoadResult` for Task 4.

- [ ] **Step 1: Write failing identity and decode-plan tests**

```java
@Test
public void liveComponentParticipatesInIdentity() {
    WallpaperIdentity staticId = new WallpaperIdentity(41, null, 0, 0);
    WallpaperIdentity liveId = new WallpaperIdentity(41, "pkg/.Wallpaper", 0, 0);
    assertThat(staticId).isNotEqualTo(liveId);
}

@Test
public void decodePlanKeepsCurrentAndCandidateBelowBudget() {
    WallpaperDecodePlan plan = WallpaperDecodePlan.forBounds(
            12000, 6000, 1080, 2400, 24L * 1024L * 1024L);
    long twoBitmaps = 2L * plan.width() * plan.height() * 4L;
    assertThat(twoBitmaps).isAtMost(24L * 1024L * 1024L);
    assertThat(plan.sampleSize()).isAtLeast(1);
}

@Test
public void corruptBoundsReturnUnavailablePlan() {
    assertThat(WallpaperDecodePlan.forBounds(0, -1, 1080, 2400,
            24L * 1024L * 1024L).isValid()).isFalse();
}
```

- [ ] **Step 2: Write the failing device source test**

The instrumentation test must skip with an explicit assumption only when the current wallpaper is live. For a static wallpaper with granted access:

```java
@Test
public void loadCurrentStaticWallpaperReturnsRealOpaquePixels() throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    AndroidWallpaperBackdropSource source = new AndroidWallpaperBackdropSource(context);
    WallpaperIdentity identity = source.readIdentity();
    Assume.assumeFalse(identity.isLive());

    WallpaperBackdropSource.LoadResult result = source.load(
            new WallpaperBackdropSource.LoadRequest(identity, 1080, 2400,
                    24L * 1024L * 1024L, 1));

    assertThat(result.status()).isEqualTo(LoadStatus.SUCCESS);
    assertThat(result.snapshot().bitmap().getWidth()).isGreaterThan(1);
    assertThat(hasAtLeastOneNonBlackOpaqueSample(result.snapshot().bitmap())).isTrue();
}
```

- [ ] **Step 3: Run RED**

```bash
./gradlew testLawnWithQuickstepGithubDebugUnitTest \
  --tests 'com.android.launcher3.graphics.WallpaperIdentityTest' \
  --tests 'com.android.launcher3.graphics.WallpaperDecodePlanTest' --console=plain
```

Expected: compilation fails on missing production classes.

- [ ] **Step 4: Implement the source contract and decode path**

Use these exact contracts:

```java
public interface WallpaperBackdropSource {
    WallpaperIdentity readIdentity();
    LoadResult load(LoadRequest request);

    enum LoadStatus { SUCCESS, LIVE_WALLPAPER, ACCESS_DENIED, UNAVAILABLE, OUT_OF_MEMORY }
}
```

`AndroidWallpaperBackdropSource.load()` must:

1. Return `LIVE_WALLPAPER` before opening a file when `getWallpaperInfo()` is non-null.
2. Require `FileAccessState.Full`.
3. Call `getWallpaperFile(FLAG_SYSTEM)` twice: bounds decode first, sampled decode second.
4. Use `BitmapFactory.Options.inSampleSize` from `WallpaperDecodePlan` and `inMutable = false`.
5. Reject null, recycled, zero-sized, or identity-mismatched candidates.
6. Catch `SecurityException`, `IOException`, `IllegalArgumentException`, and `OutOfMemoryError` into their typed statuses.
7. Never call `getDrawable()`, `PixelCopy`, or a launcher view.

`WallpaperBackdropSnapshot` stores the immutable bitmap, generation, identity, source/decoded
dimensions, scale, display geometry, offsets at decode time, orientation, and creation uptime.
Current offsets remain repository mapping state; mapping changes never manufacture another owner
for the same bitmap and never trigger a decode.

- [ ] **Step 5: Run host GREEN, assemble instrumentation, and run the device source test**

```bash
./gradlew testLawnWithQuickstepGithubDebugUnitTest \
  --tests 'com.android.launcher3.graphics.WallpaperIdentityTest' \
  --tests 'com.android.launcher3.graphics.WallpaperDecodePlanTest' --console=plain
./gradlew installLawnWithQuickstepGithubDebug \
  installLawnWithQuickstepGithubDebugAndroidTest --console=plain
adb shell appops set app.lawnchair.debug MANAGE_EXTERNAL_STORAGE allow
adb shell am instrument -w -r \
  -e class com.android.launcher3.OneUiWallpaperBackdropSourceTest \
  app.lawnchair.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: host tests pass; on the S25 static wallpaper, the instrumentation test reports `OK (1 test)` and a real non-black bitmap.

- [ ] **Step 6: Commit and push**

```bash
git add src/com/android/launcher3/graphics/WallpaperIdentity.java \
  src/com/android/launcher3/graphics/WallpaperBackdropSnapshot.java \
  src/com/android/launcher3/graphics/WallpaperBackdropSource.java \
  src/com/android/launcher3/graphics/WallpaperDecodePlan.java \
  src/com/android/launcher3/graphics/AndroidWallpaperBackdropSource.java \
  tests/crystal/src/com/android/launcher3/graphics/WallpaperIdentityTest.java \
  tests/crystal/src/com/android/launcher3/graphics/WallpaperDecodePlanTest.java \
  tests/oneui/src/com/android/launcher3/OneUiWallpaperBackdropSourceTest.java
git commit -m "feat: load immutable static wallpaper snapshots"
git push origin feature/crystal-wallpaper-cache
```

### Task 4: Launcher-Scoped Repository and Change Signals

**Files:**
- Create: `src/com/android/launcher3/graphics/OneUiWallpaperBackdropRepository.java`
- Create: `tests/crystal/src/com/android/launcher3/graphics/OneUiWallpaperBackdropRepositoryTest.java`
- Modify: `src/com/android/launcher3/Launcher.java`
- Modify: `src/com/android/launcher3/Workspace.java`

**Interfaces:**
- Consumes: `WallpaperBackdropSource` from Task 3 and `WallpaperSnapshotStore<WallpaperBackdropSnapshot>` from Task 1.
- Produces: `start()`, `onResume()`, `updateMapping(...)`, `currentMapping()`, `currentSnapshot()`, `pinCurrentSnapshot()`, `addListener(Runnable)`, `removeListener(Runnable)`, `isLiveWallpaper()`, and `close()` for Tasks 5–6.

- [ ] **Step 1: Write failing repository tests with a controllable fake source/executor**

Cover these concrete sequences:

```java
@Test
public void oldSnapshotStaysVisibleUntilNewestDecodePublishes() {
    repository.start();
    fakeIo.runNextSuccess(snapshot("first"));
    long first = repository.currentSnapshot().generation();

    repository.onWallpaperChangedSignal();

    assertThat(repository.currentSnapshot().generation()).isEqualTo(first);
    fakeIo.runNextSuccess(snapshot("second"));
    assertThat(repository.currentSnapshot().generation()).isGreaterThan(first);
}

@Test
public void revokedPermissionDuringReloadKeepsPreviousSnapshot() {
    publishInitial();
    repository.onWallpaperChangedSignal();
    fakeIo.runNextFailure(LoadStatus.ACCESS_DENIED);
    assertThat(repository.currentSnapshot()).isSameInstanceAs(initial);
}

@Test
public void resumeWithEqualIdentityDoesNotDecodeAgain() {
    publishInitial();
    repository.onResume();
    assertThat(source.loadCount()).isEqualTo(1);
}

@Test
public void colorAndBroadcastSignalsCoalesceBeforeIoStarts() {
    repository.onWallpaperChangedSignal();
    repository.onColorsChanged();
    assertThat(fakeIo.pendingCount()).isEqualTo(1);
}
```

Also verify out-of-order completion, listener notification exactly once, close unregister semantics, and metadata-only offset updates reuse the bitmap.

- [ ] **Step 2: Run RED**

```bash
./gradlew testLawnWithQuickstepGithubDebugUnitTest \
  --tests com.android.launcher3.graphics.OneUiWallpaperBackdropRepositoryTest --console=plain
```

Expected: compilation fails because the repository does not exist.

- [ ] **Step 3: Implement repository state and Android signal adapters**

Construct the production repository as:

```java
public OneUiWallpaperBackdropRepository(
        Context context,
        WallpaperBackdropSource source,
        Executor ioExecutor,
        Executor mainExecutor) { ... }
```

Use `SimpleBroadcastReceiver` for `ACTION_WALLPAPER_CHANGED` and the existing
`WallpaperManagerCompat.OnColorsChangedListener`. Coalesce invalidations while an IO task has not
started; when a decode is already running, record the newest requested generation and schedule one
replacement load after completion. Every completion rechecks both generation and identity before
calling `WallpaperSnapshotStore.publish()`.

Listeners are weak references. On successful publication, copy live listener references, invoke
each exactly once on the main thread, then prune cleared references. `close()` unregisters both
Android listeners, clears callbacks, and retires snapshots.

- [ ] **Step 4: Wire one repository to Launcher lifecycle and workspace mapping**

In `Launcher`:

```java
private OneUiWallpaperBackdropRepository mWallpaperBackdropRepository;

public OneUiWallpaperBackdropRepository getWallpaperBackdropRepository() {
    return mWallpaperBackdropRepository;
}
```

Create/start it after Launcher views are initialized, call `onResume()` after `super.onResume()`,
  and call `close()` before `super.onDestroy()` completes. On configuration/device-profile changes,
send current display size and orientation.

In `Workspace.computeScroll()`, after synchronizing system wallpaper offsets, send
`getWallpaperOffsetForCenterPage()` to `updateMapping(...)` only when the float changes. Expose
`currentMapping()` as an immutable value read by the renderer. Mapping updates replace only that
mapping value, never the snapshot or bitmap, and notify consumers only when the effective sample
transform changes.

- [ ] **Step 5: Run GREEN and lifecycle regressions**

```bash
./gradlew testLawnWithQuickstepGithubDebugUnitTest \
  --tests com.android.launcher3.graphics.OneUiWallpaperBackdropRepositoryTest --console=plain
./gradlew testLawnWithQuickstepGithubDebugUnitTest --console=plain
```

Expected: repository tests and the full variant unit suite pass.

- [ ] **Step 6: Commit and push**

```bash
git add src/com/android/launcher3/graphics/OneUiWallpaperBackdropRepository.java \
  src/com/android/launcher3/Launcher.java src/com/android/launcher3/Workspace.java \
  tests/crystal/src/com/android/launcher3/graphics/OneUiWallpaperBackdropRepositoryTest.java
git commit -m "feat: add generation-aware wallpaper backdrop repository"
git push origin feature/crystal-wallpaper-cache
```

### Task 5: Cached Crystal Renderer Without Launcher Capture

**Files:**
- Create: `src/com/android/launcher3/graphics/OneUiCrystalSurfaceRole.java`
- Rewrite: `src/com/android/launcher3/graphics/OneUiCrystalRenderer.java`
- Modify: `src/com/android/launcher3/graphics/OneUiGlassBackground.java`
- Create: `tests/crystal/src/com/android/launcher3/graphics/OneUiCrystalRendererContractTest.java`
- Create: `tests/oneui/src/com/android/launcher3/graphics/OneUiCrystalRendererDeviceTest.java`

**Interfaces:**
- Consumes: repository, snapshot, transform, and optics APIs from Tasks 1–4.
- Produces: `OneUiCrystalRenderer.create(View, OneUiCrystalSurfaceRole, int, float, int)` and allocation counters visible only to tests; role-aware factory methods in `OneUiGlassBackground` for Task 6.

- [ ] **Step 1: Write the failing renderer contract test**

Add a source-contract test that reads `OneUiCrystalRenderer.java` and asserts the rejected APIs are absent:

```java
@Test
public void rendererCannotCaptureLauncherTreeOrReadWallpaperDirectly() throws Exception {
    String source = Files.readString(Path.of(
            "src/com/android/launcher3/graphics/OneUiCrystalRenderer.java"));
    assertThat(source).doesNotContain("root.draw(");
    assertThat(source).doesNotContain("getRootView().draw(");
    assertThat(source).doesNotContain("WallpaperManager");
    assertThat(source).doesNotContain("Bitmap.createBitmap");
    assertThat(source).doesNotContain("new Canvas(");
}
```

Add pure assertions that `OneUiCrystalSurfaceRole.DOCK` uses capsule optics and that folder roles
do not change the snapshot source.

- [ ] **Step 2: Write the failing device resource-reuse test**

Provide package-private counters `getShaderBuildCountForTesting()` and
`getBitmapShaderBuildCountForTesting()`. Inject a test snapshot, draw the same bounds 20 times,
and assert both counters remain unchanged after the first draw. Change generation once and assert
each increases exactly once.

Use a package-private factory with an explicit repository so the device test does not alter global
Launcher state:

```java
static OneUiCrystalRenderer createForTesting(View host, OneUiCrystalSurfaceRole role, int color,
        float cornerRadius, int intensityPercent,
        OneUiWallpaperBackdropRepository repository)
```

- [ ] **Step 3: Run RED**

```bash
./gradlew testLawnWithQuickstepGithubDebugUnitTest \
  --tests com.android.launcher3.graphics.OneUiCrystalRendererContractTest --console=plain
```

Expected: the source-contract test fails because the current renderer contains `root.draw`,
`WallpaperManager`, `Bitmap.createBitmap`, and `new Canvas`.

- [ ] **Step 4: Replace capture fields and draw path**

Delete `mBackdrop`, `mWallpaper`, the thread-local recursion guard, `captureBackdrop()`,
`drawWallpaperUnderlay()`, `recycleBackdrop()`, and all host/root capture coordinates.

The replacement renderer keeps these resources as fields:

```java
private final Paint mShaderPaint;
private final Paint mFallbackPaint;
private final Paint mOverlayPaint;
private final Path mClipPath;
private final Matrix mBitmapMatrix;
private final RectF mSurfaceRect;
private final RectF mSampleRect;
private @Nullable BitmapShader mBitmapShader;
private @Nullable Api33State mApi33State;
private long mBoundGeneration = Long.MIN_VALUE;
```

`Api33State` is a nested class that owns `RuntimeShader`, isolating API-33 symbols from class
verification on API 26–32.

At draw time resolve `Launcher` through `ActivityContext.lookupContext(host.getContext())`, read
its repository snapshot once, and rebuild cached resources only when generation, bounds, role,
density, color, corner radius, or intensity changes. Use `getLocationOnScreen()` and
`WallpaperBackdropTransform` to align the bitmap shader.

Change the AGSL shader so `topGlow` is removed. Use the rounded-rectangle SDF edge weight for
normal-directed displacement and restrained RGB dispersion. The center displacement is at most
25% of the edge displacement. The rim uses the SDF contour and a directional light vector, not a
full-width rectangle.

API 26–32 draws the aligned bitmap through the clipped rounded rectangle with a small matrix
expansion plus contour overlays. Missing snapshot draws only the sober non-black material.

- [ ] **Step 5: Run GREEN and device renderer tests**

```bash
./gradlew testLawnWithQuickstepGithubDebugUnitTest \
  --tests com.android.launcher3.graphics.OneUiCrystalRendererContractTest --console=plain
./gradlew installLawnWithQuickstepGithubDebug \
  installLawnWithQuickstepGithubDebugAndroidTest --console=plain
adb shell am instrument -w -r \
  -e class com.android.launcher3.graphics.OneUiCrystalRendererDeviceTest \
  app.lawnchair.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: forbidden APIs are absent and repeated draws reuse all counted resources.

- [ ] **Step 6: Commit and push**

```bash
git add src/com/android/launcher3/graphics/OneUiCrystalSurfaceRole.java \
  src/com/android/launcher3/graphics/OneUiCrystalRenderer.java \
  src/com/android/launcher3/graphics/OneUiGlassBackground.java \
  tests/crystal/src/com/android/launcher3/graphics/OneUiCrystalRendererContractTest.java \
  tests/oneui/src/com/android/launcher3/graphics/OneUiCrystalRendererDeviceTest.java
git commit -m "feat: render Crystal from cached wallpaper snapshots"
git push origin feature/crystal-wallpaper-cache
```

### Task 6: Dock/Folder Roles, Live Policy, and Animation Pins

**Files:**
- Modify: `src/com/android/launcher3/Hotseat.java`
- Modify: `src/com/android/launcher3/folder/OneUiFolder.java`
- Modify: `src/com/android/launcher3/folder/OneUiFolderIcon.java`
- Modify: `src/com/android/launcher3/folder/OneUiPreviewBackground.java`
- Modify: `lawnchair/src/app/lawnchair/oneui/OneUiFolderCrystalDrawable.kt`
- Modify: `src/com/android/launcher3/graphics/OneUiGlassBackground.java`
- Create: `src/com/android/launcher3/graphics/OneUiCrystalSourcePolicy.java`
- Create: `tests/crystal/src/com/android/launcher3/graphics/OneUiCrystalSourcePolicyTest.java`
- Create: `tests/oneui/src/com/android/launcher3/OneUiCrystalSurfaceIntegrationTest.java`

**Interfaces:**
- Consumes: explicit renderer roles and repository live/static state.
- Produces: stable dock/folder call sites, static/live material selection, and folder animation pin lifecycle.

- [ ] **Step 1: Write failing source-policy tests**

```java
@Test
public void staticWallpaperWaitsForRealSnapshotWithoutSamsungBlur() {
    Decision decision = OneUiCrystalSourcePolicy.choose(
            true, false, false, true);
    assertThat(decision).isEqualTo(Decision.CACHED_OR_PENDING_STATIC);
}

@Test
public void liveWallpaperUsesSamsungBackdropWhenAvailable() {
    Decision decision = OneUiCrystalSourcePolicy.choose(
            true, true, false, true);
    assertThat(decision).isEqualTo(Decision.SAMSUNG_LIVE_BACKDROP);
}

@Test
public void missingPermissionWithoutOldSnapshotUsesSoberFallback() {
    Decision decision = OneUiCrystalSourcePolicy.choose(
            false, false, false, true);
    assertThat(decision).isEqualTo(Decision.SOBER_FALLBACK);
}
```

Add a test that a valid old static snapshot wins while a replacement is pending.

The production signature is:

```java
static Decision choose(boolean hasWallpaperAccess, boolean liveWallpaper,
        boolean hasValidStaticSnapshot, boolean samsungBlurAvailable)
```

- [ ] **Step 2: Write failing integration tests for hierarchy and pins**

Inflate Hotseat and a folder icon on the device and assert:

- the glass `View` is inserted below `mIconsContainer` and QSB;
- the glass view is non-clickable, non-focusable, and hidden from accessibility;
- `DOCK`, `OPEN_FOLDER`, `FOLDER_ICON`, and `FOLDER_DRAWABLE` reach distinct renderer roles;
- folder `STATE_ANIMATING` acquires one pin;
- transition to `STATE_OPEN`, `STATE_CLOSED`, cancellation, and detach each release it exactly once.

- [ ] **Step 3: Run RED**

```bash
./gradlew testLawnWithQuickstepGithubDebugUnitTest \
  --tests com.android.launcher3.graphics.OneUiCrystalSourcePolicyTest --console=plain
```

Expected: compilation fails because `OneUiCrystalSourcePolicy` does not exist.

- [ ] **Step 4: Implement policy and role-specific factories**

Expose these methods from `OneUiGlassBackground`:

```java
createDock(...)
createDockOverlay(...)
createOpenFolder(...)
createOpenFolderOverlay(...)
createFolderIcon(...)
createFolderIconOverlay(...)
createFolderDrawable(...)
setAnimationRunning(@Nullable Drawable drawable, boolean running)
```

For static or pending static wallpaper, clear `SamsungGlassBlur` and use the cached Crystal
renderer. For live wallpaper, apply Samsung blur and use the contour-only overlay. For missing
access with no old snapshot, skip Samsung unless it is a live wallpaper and draw sober fallback.

`OneUiFolder` registers an `OnFolderStateChangedListener`; it calls
`setAnimationRunning(mGlass, state == STATE_ANIMATING)`. On detach it forces `false`, removes the
listener, and clears native blur. The renderer acquires one repository pin on the transition to
running and closes it idempotently on every exit path.

Do not change Settings Compose files. The Kotlin drawable uses `createFolderDrawable` and keeps
its existing unattached fallback.

- [ ] **Step 5: Run GREEN, full unit tests, and integration instrumentation**

```bash
./gradlew testLawnWithQuickstepGithubDebugUnitTest --console=plain
./gradlew installLawnWithQuickstepGithubDebug \
  installLawnWithQuickstepGithubDebugAndroidTest --console=plain
adb shell am instrument -w -r \
  -e class com.android.launcher3.OneUiCrystalSurfaceIntegrationTest \
  app.lawnchair.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: all tests pass; no call site uses the generic ambiguous folder role.

- [ ] **Step 6: Commit and push**

```bash
git add src/com/android/launcher3/Hotseat.java \
  src/com/android/launcher3/folder/OneUiFolder.java \
  src/com/android/launcher3/folder/OneUiFolderIcon.java \
  src/com/android/launcher3/folder/OneUiPreviewBackground.java \
  lawnchair/src/app/lawnchair/oneui/OneUiFolderCrystalDrawable.kt \
  src/com/android/launcher3/graphics/OneUiGlassBackground.java \
  src/com/android/launcher3/graphics/OneUiCrystalSourcePolicy.java \
  tests/crystal/src/com/android/launcher3/graphics/OneUiCrystalSourcePolicyTest.java \
  tests/oneui/src/com/android/launcher3/OneUiCrystalSurfaceIntegrationTest.java
git commit -m "feat: bind Crystal surfaces to cached wallpaper policy"
git push origin feature/crystal-wallpaper-cache
```

### Task 7: Failure Paths, Memory Budget, and Device Wallpaper Swap

**Files:**
- Modify: `tests/crystal/src/com/android/launcher3/graphics/OneUiWallpaperBackdropRepositoryTest.java`
- Modify: `tests/crystal/src/com/android/launcher3/graphics/WallpaperBackdropTransformTest.java`
- Modify: `tests/oneui/src/com/android/launcher3/OneUiWallpaperBackdropSourceTest.java`
- Create: `tests/oneui/src/com/android/launcher3/OneUiCrystalWallpaperSwapTest.java`
- Modify production files only when a new failing regression requires the minimal fix.

**Interfaces:**
- Consumes: completed repository/source/renderer stack.
- Produces: verified behavior for permission loss, corrupt input, allocation failure, lifecycle replacement, live transition, and combined memory budget.

- [ ] **Step 1: Add failing regression tests for all fallback classes**

Add explicit tests for:

- `SecurityException` from both file opens;
- null `ParcelFileDescriptor`;
- corrupt bounds and corrupt second decode;
- `OutOfMemoryError` while a current snapshot exists;
- orientation change during an in-flight decode;
- static-to-live and live-to-static identity changes;
- 24 MiB combined budget at 1080×2400 and a synthetic 1440×3200 display;
- software `Canvas` selecting bitmap/fallback rendering without `RuntimeShader`;
- extreme panorama and source smaller than display.

Each test asserts either preservation of the prior real snapshot or a non-black typed fallback.

- [ ] **Step 2: Run RED and implement only the observed gaps**

```bash
./gradlew testLawnWithQuickstepGithubDebugUnitTest --console=plain
```

Expected: each newly added regression that covers a missing branch fails for that branch before
its minimal production fix; a compile error or unrelated setup error does not count as RED.

- [ ] **Step 3: Run full automated GREEN**

```bash
./gradlew spotlessCheck --console=plain
./gradlew testLawnWithQuickstepGithubDebugUnitTest --console=plain
./gradlew assembleLawnWithQuickstepGithubDebug \
  assembleLawnWithQuickstepGithubDebugAndroidTest --console=plain
./gradlew installLawnWithQuickstepGithubDebug \
  installLawnWithQuickstepGithubDebugAndroidTest --console=plain
adb shell appops set app.lawnchair.debug MANAGE_EXTERNAL_STORAGE allow
adb shell am instrument -w -r \
  -e class com.android.launcher3.OneUiWallpaperBackdropSourceTest,com.android.launcher3.graphics.OneUiCrystalRendererDeviceTest,com.android.launcher3.OneUiCrystalSurfaceIntegrationTest,com.android.launcher3.OneUiCrystalWallpaperSwapTest \
  app.lawnchair.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: formatting, host suite, APK compilation, and OneUI instrumentation all pass.

- [ ] **Step 4: Perform reversible wallpaper-swap validation on the S25**

The instrumentation test first opens and retains the original static wallpaper stream, installs a
generated high-contrast fixture using `WallpaperManager.setBitmap`, waits for exactly one newer
repository generation, asserts non-black continuity, and restores the original in `finally` using
`setStream`. It records generation IDs and sampled checksums to instrumentation output without
writing the wallpaper pixels to logs.

Run:

```bash
adb shell am instrument -w -r \
  -e class com.android.launcher3.OneUiCrystalWallpaperSwapTest \
  app.lawnchair.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: swap and restoration pass; the original wallpaper is visible again afterward.

- [ ] **Step 5: Commit and push**

```bash
git add tests/crystal/src/com/android/launcher3/graphics \
  tests/oneui/src/com/android/launcher3/OneUiWallpaperBackdropSourceTest.java \
  tests/oneui/src/com/android/launcher3/OneUiCrystalWallpaperSwapTest.java \
  src/com/android/launcher3/graphics src/com/android/launcher3/Launcher.java \
  src/com/android/launcher3/Workspace.java
git commit -m "test: cover Crystal wallpaper failure and swap paths"
git push origin feature/crystal-wallpaper-cache
```

### Task 8: Exact-SHA GitHub Build, S25 Visual/Performance Gate, and APK Delivery

**Files:**
- Create locally: `.superpowers/sdd/2026-09-20-crystal-wallpaper-cache/verification.md`
- Store temporary captures beside that record and do not commit them unless the user later requests repository evidence assets.

**Interfaces:**
- Consumes: final pushed branch SHA and GitHub Actions artifact.
- Produces: verified installed APK, visual/performance evidence, final report, and copied APK with digest.

- [ ] **Step 1: Run final local verification and push a clean final SHA**

```bash
git diff --check
git status --short
./gradlew spotlessCheck testLawnWithQuickstepGithubDebugUnitTest \
  assembleLawnWithQuickstepGithubDebug --console=plain
git rev-parse HEAD
git push origin feature/crystal-wallpaper-cache
```

Expected: no uncommitted production/test changes, all commands pass, and local HEAD equals
`origin/feature/crystal-wallpaper-cache`.

- [ ] **Step 2: Identify the exact GitHub Actions run and download only its artifact**

```bash
FINAL_SHA="$(git rev-parse HEAD)"
RUN_JSON="$(gh run list --repo Jorgeprdz/lawnchair \
  --branch feature/crystal-wallpaper-cache --commit "$FINAL_SHA" \
  --workflow oneui-build.yml --limit 1 --json databaseId,headSha,status,conclusion)"
test "$(printf '%s' "$RUN_JSON" | jq -r '.[0].headSha')" = "$FINAL_SHA"
RUN_ID="$(printf '%s' "$RUN_JSON" | jq -r '.[0].databaseId')"
test -n "$RUN_ID"
gh run watch --repo Jorgeprdz/lawnchair "$RUN_ID" --exit-status
gh run download --repo Jorgeprdz/lawnchair "$RUN_ID" \
  --name Lawnchair-OneUI-Separate-Debug --dir .superpowers/sdd/2026-09-20-crystal-wallpaper-cache/artifact
```

Expected: the selected run's `headSha` equals `FINAL_SHA` and its conclusion is `success`; never
download an artifact from a different SHA.

- [ ] **Step 3: Verify package, certificate, install compatibility, and source identity**

```bash
APK="$(find .superpowers/sdd/2026-09-20-crystal-wallpaper-cache/artifact -name '*.apk' | head -n1)"
"$ANDROID_HOME/build-tools/37.0.0/aapt2" dump badging "$APK" | \
  grep "package: name='app.lawnchair.oneui.debug'"
"$ANDROID_HOME/build-tools/37.0.0/apksigner" verify --print-certs "$APK"
adb install -r "$APK"
adb shell dumpsys package app.lawnchair.oneui.debug | grep -E 'versionName|versionCode'
```

Expected: package is `app.lawnchair.oneui.debug`, certificate SHA-256 is
`b19449e782c304983e6a1686bae0089f25c62d33e8ed1be10931f12eb7a82eca`, update install succeeds,
and the downloaded artifact belongs to the Actions run whose `headSha` equals the final commit.

- [ ] **Step 4: Capture dock visual gates**

Set a high-detail static wallpaper fixture, return to HOME, and save screenshots with the dock over
light, dark, and detailed regions. Preserve Lawnchair's normal icons and exclude any ReSukiSU
selector content.

```bash
adb shell screencap -p /sdcard/crystal-dock-detail.png
adb pull /sdcard/crystal-dock-detail.png \
  .superpowers/sdd/2026-09-20-crystal-wallpaper-cache/
```

Inspect original-resolution captures. Reject the build if the dock is opaque, shows a black fill,
contains icons/text from behind inside its source texture, or has a continuous horizontal top
line. Accept only when recognizable wallpaper detail bends near the capsule contour and the dock
icons remain sharp.

- [ ] **Step 5: Measure 120 Hz animation performance**

Clear metrics, record five folder open/close cycles and normal dock page interaction, then dump
frame stats:

```bash
adb shell dumpsys gfxinfo app.lawnchair.oneui.debug reset
adb shell screenrecord --time-limit 20 /sdcard/crystal-cache-validation.mp4
adb shell dumpsys gfxinfo app.lawnchair.oneui.debug framestats > \
  .superpowers/sdd/2026-09-20-crystal-wallpaper-cache/gfxinfo.txt
adb pull /sdcard/crystal-cache-validation.mp4 \
  .superpowers/sdd/2026-09-20-crystal-wallpaper-cache/
```

Calculate total/janky frames and p50/p90/p95 UI durations. Record GPU p95 when available. Reject
recursive imagery, black transitions, repeated wallpaper decode during animations, or regression
toward the rejected p95 of 109 ms. Include measured values rather than claiming smoothness by eye.

- [ ] **Step 6: Write the local verification record without changing the final SHA**

The markdown record must contain final SHA, Actions run ID/URL, artifact name, package, certificate
digest, device/build identity, automated test commands/results, screenshot filenames, wallpaper
generation swap result, frame statistics, visual verdict, APK byte size, and APK SHA-256.

```bash
git status --short
test "$(git rev-parse HEAD)" = "$FINAL_SHA"
test "$(git rev-parse origin/feature/crystal-wallpaper-cache)" = "$FINAL_SHA"
```

Expected: the repository remains clean and the verified branch SHA has not changed after artifact
download, installation, screenshots, or metrics collection.

- [ ] **Step 7: Copy the exact validated APK to Download and verify the copy**

```bash
FINAL_APK=/storage/emulated/0/Download/Lawnchair-OneUI-Crystal-Wallpaper-Cache.apk
cp "$APK" "$FINAL_APK"
ls -l "$FINAL_APK"
sha256sum "$APK" "$FINAL_APK"
```

Expected: both SHA-256 values match byte-for-byte. Report the final Git commit SHA, Actions run ID,
APK path, byte size, and SHA-256 to the user.

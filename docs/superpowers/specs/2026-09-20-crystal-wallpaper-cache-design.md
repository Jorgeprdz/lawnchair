# Crystal Wallpaper Cache Design

**Date:** 2026-09-20  
**Branch:** `feature/crystal-wallpaper-cache`  
**Remote baseline:** `origin/feature/oneui-next` at `a5f76a90bcf00f308658c91260e9f63e6b320ce9`

## Goal

Replace Crystal's per-draw launcher-tree capture with one launcher-scoped, generation-aware
wallpaper source. The dock and folder Crystal renderers must sample an immutable snapshot of the
real static home wallpaper without drawing `Workspace`, `DragLayer`, `Hotseat`, icons, folders, or
the Crystal surface into that snapshot.

The dock must look like a floating rounded glass capsule whose lens refracts the wallpaper near
its contour. Lawnchair's normal dock icons remain sharp above the material. The ReSukiSU screenshot
is a material reference only; its selected-tab bubble, labels, and navigation icons are not part of
this feature.

## Evidence and Platform Boundary

The rejected renderer calls `WallpaperManager.getDrawable()` and then `root.draw()` from
`OneUiCrystalRenderer.draw()`. That makes the mutable bitmap a per-frame launcher capture rather
than a wallpaper cache and produced recursive content, black frames, and 89.29% jank on the target
S25.

Android 14 and later restrict direct wallpaper access unless the caller has
`MANAGE_EXTERNAL_STORAGE` or `READ_WALLPAPER_INTERNAL`. The GitHub variant declares
`MANAGE_EXTERNAL_STORAGE`, and the target package `app.lawnchair.oneui.debug` has the corresponding
app-op set to `allow`. Therefore the supported real-pixel path for this branch is:

1. Require confirmed wallpaper access before attempting a static decode.
2. Open `WallpaperManager.getWallpaperFile(FLAG_SYSTEM)` on a background executor.
3. Decode an intentionally downscaled immutable bitmap.
4. Publish it only after validation succeeds.

`WallpaperManager.getDrawable()` is not the primary source. It may not be called on Android 14+
without the same explicit access check. `PixelCopy` is not a wallpaper source: it copies the
launcher's window surface and can reintroduce app content. Root, `MediaProjection`, and new
privileged services are outside scope.

Static wallpaper is the full Crystal path. A live wallpaper does not expose its frames through
`WallpaperManager`; live wallpaper therefore uses Samsung's native compositor blur when available
and the existing sober translucent fallback otherwise. The UI must not label that fallback as
real refractive Crystal internally or in diagnostics.

## Scope

### Included

- A launcher-scoped wallpaper repository shared by dock and folder Crystal consumers.
- Static wallpaper decoding, downscaling, identity tracking, validation, and atomic publication.
- Invalidation from `ACTION_WALLPAPER_CHANGED`, wallpaper color changes, resume-time identity
  checks, display geometry, orientation, and wallpaper offset changes.
- Deterministic mapping from screen-space Crystal bounds to wallpaper snapshot coordinates.
- A cached dock/folder renderer that allocates no bitmap, shader, `RenderEffect`, `Paint`, or
  `Canvas` during steady-state draws.
- Native Samsung live-wallpaper fallback and a non-black software fallback.
- Host-side unit tests plus device validation on the S25.

### Excluded

- Settings previews and edits to `DockPreferences.kt` or `FolderPreferences.kt`.
- `MediaProjection`, root services, signature permissions, or hidden filesystem access.
- Capturing launcher views to approximate the wallpaper.
- Copying ReSukiSU navigation content or its selected-app indicator.
- Shipping a fake tinted capsule as successful wallpaper refraction.
- Changing the package name, application ID, signing configuration, or existing launcher role.

## Components

### `WallpaperBackdropSource`

A narrow source interface isolates Android wallpaper I/O from repository state. Its production
implementation reports:

- the current `WallpaperIdentity`;
- whether the wallpaper is static or live;
- whether real-pixel access is currently granted;
- a decoded, downscaled static wallpaper candidate or a typed failure.

Tests use an in-memory implementation. Consumers never call `WallpaperManager` directly.

### `WallpaperIdentity`

An immutable value containing the system wallpaper ID, live component name when present, display
ID, and user identifier available to the launcher process. Equality is the resume-time change
check. A transition between static and live is always a change even if the integer wallpaper ID is
unchanged.

### `WallpaperBackdropSnapshot`

An immutable published value containing:

- decoded bitmap;
- monotonically increasing generation;
- source dimensions and decoded dimensions;
- decode scale;
- visible display width and height;
- wallpaper horizontal and vertical offsets;
- orientation;
- wallpaper identity;
- static/live classification;
- creation uptime.

Only static snapshots contain real pixels. The bitmap is never mutated after publication. Snapshot
retirement is repository-owned; renderers do not recycle it.

### `OneUiWallpaperBackdropRepository`

One instance belongs to the active Launcher. It owns:

- `currentSnapshot`;
- the in-flight generation and candidate;
- the latest requested generation;
- the last observed identity and geometry;
- decode executor and main-thread publication callback;
- weak invalidation listeners for active Crystal surfaces.

The repository coalesces duplicate invalidations. Every accepted invalidation increments the
requested generation. A decode result is publishable only when its generation still equals the
latest requested generation and its identity still equals the current source identity.

Publication is atomic on the main thread:

1. Retain the old `currentSnapshot`.
2. Assign the fully built candidate.
3. Notify consumers once.
4. Mark the old snapshot retired.
5. Recycle its bitmap only when its animation pin count reaches zero.

All repository publication, renderer reads, pin acquisition, and pin release happen on the main
thread. A normal draw therefore cannot overlap publication. A folder animation obtains a
`SnapshotPin` before its first frame and closes that pin after its final or cancelled frame. A
retired snapshot with an open pin remains valid; closing the last pin recycles it. Dock draws do
not retain pins between frames.

A failed or stale candidate never clears a valid current snapshot. If no valid snapshot has ever
been published, consumers receive an explicit unavailable state rather than a black bitmap.

### `WallpaperBackdropTransform`

A pure, allocation-free geometry helper maps screen-space bounds to bitmap sample coordinates. It
accounts for display size, wallpaper size, center crop, horizontal wallpaper offset, vertical
offset, orientation, optical padding, and decode scale. It clamps padded bounds without stretching
the last pixel across the entire lens.

Transform inputs are value objects so host tests can cover portrait, landscape, wide wallpaper,
single-page, multi-page, RTL, and edge-clamped cases without Android views.

### Crystal renderer integration

`OneUiCrystalRenderer` receives an explicit surface role and repository reference. It no longer
owns a wallpaper drawable, capture bitmap, capture canvas, or root-view recursion guard. A draw:

1. Reads one snapshot reference.
2. Resolves the already-cached transform for the current bounds and generation.
3. Samples the snapshot through a cached `BitmapShader` or API-appropriate effect.
4. Applies edge-weighted lens displacement, soft blur, restrained dispersion, cold tint, contour
   rim, and exterior shadow.
5. Returns without requesting asynchronous work.

Shader/effect objects are rebuilt only when generation, bounds, density, role, or optical config
changes. Paints, paths, matrices, and rectangles are persistent fields.

The rounded-rectangle signed-distance function drives the lens band. Displacement is strongest
near the contour and decays toward a stable center. The rim follows the entire capsule contour;
there is no full-width top gradient. Intensity zero retains a subtle nonzero material.

## Lifecycle and Invalidation

The repository starts and stops with Launcher attachment. It registers:

- a dynamically registered, non-exported `ACTION_WALLPAPER_CHANGED` receiver;
- the existing `WallpaperManagerCompat` color-change listener;
- a resume-time identity check invoked by Launcher lifecycle;
- geometry and wallpaper-offset updates from their existing owners.

All three wallpaper signals flow through one coalescing method. Color changes are treated as a
change signal, not as the wallpaper identity. Resume compares the full `WallpaperIdentity` and
invalidates only when it differs.

Static wallpaper rebuilds occur only for wallpaper identity, geometry, orientation, or relevant
offset changes. `View.getDrawingTime()`, ordinary invalidation, icon movement, folder animation,
and dock drawing do not rebuild the snapshot.

During folder open/close animation, consumers pin the current generation. Publication may finish
in the background, but the pinned consumer changes generation only after its animation ends. Dock
rendering is not blocked by folder animation and may accept the new generation at its next stable
draw.

## Memory and Threading

- File open and bitmap decode run off the main thread.
- Publication and listener notification run on the main thread.
- Decode uses bounds-first sampling and a pixel budget derived from the display.
- The repository targets at most one downscaled full-wallpaper bitmap plus one in-flight candidate.
- Combined live bitmap memory must remain below 24 MiB on the target display.
- Superseded candidates are discarded before publication.
- Allocation failure preserves the current snapshot and reports an unavailable candidate.
- Detach unregisters listeners and schedules safe bitmap retirement.

## Fallback Policy

Fallback selection is explicit:

1. Valid static snapshot: full cached refractive Crystal.
2. Static wallpaper reload in progress: last valid snapshot.
3. Live wallpaper on supported Samsung firmware: `SamsungGlassBlur` plus contour-only Crystal
   overlay that does not claim sampled refraction.
4. Missing permission, decode failure, software canvas, or unsupported platform: sober translucent
   rounded material using the existing source color, never black.

No fallback calls `root.draw()`, `DragLayer.draw()`, `Workspace.draw()`, or `PixelCopy`.

## Testing

Host-side tests must prove:

- generation reuse across repeated draws;
- drawing time does not invalidate the repository;
- each wallpaper signal reaches the single invalidation path;
- resume identity equality avoids unnecessary decode;
- stale generation results cannot replace a newer request;
- old snapshot remains visible during reload and failure;
- atomic publication never exposes a partially written bitmap;
- dock and folder roles cannot request launcher-tree capture;
- crop and offset mapping for portrait, landscape, RTL, and multiple pages;
- geometry/orientation invalidation;
- renderer resource reuse across steady-state draws;
- non-black fallback behavior;
- edge-band weighting exceeds center displacement;
- Crystal intensity zero remains optically nonzero;
- live wallpaper selects the Samsung/native fallback rather than a fake static snapshot.

Device validation on the S25 must:

1. Confirm the decoded source matches the visible static wallpaper and contains no launcher icons.
2. Capture dock screenshots over light, dark, and high-detail wallpaper regions.
3. Change the wallpaper while Lawnchair is backgrounded, resume, and confirm one generation swap
   without a black frame.
4. Open and close folders repeatedly while recording `gfxinfo` at 120 Hz.
5. Confirm no recursive imagery and no continuous horizontal highlight.
6. Record metrics and images against the exact GitHub Actions commit SHA.

## Delivery and Acceptance

Implementation is accepted only when:

- all changes are committed and pushed to `origin/feature/crystal-wallpaper-cache`;
- GitHub Actions passes for the exact final SHA;
- the installed APK comes from that SHA;
- real static-wallpaper pixels are visibly refracted in the dock;
- icons remain sharp and interactive above the material;
- no launcher content enters the sampled texture;
- wallpaper changes refresh without restarting Launcher;
- steady-state draw performs no heavyweight graphics allocation;
- no black frame, recursive feedback, or full-width highlight appears;
- device performance is measured and documented rather than inferred from unit tests.

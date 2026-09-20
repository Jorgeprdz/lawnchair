/*
 * Copyright (C) 2026 The Lawnchair Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.launcher3.graphics;

import android.view.View;

import com.android.launcher3.Launcher;
import com.android.launcher3.views.ActivityContext;

/** Selects the only permitted backdrop source for a Crystal surface. */
public final class OneUiCrystalSourcePolicy {
    public enum Decision {
        CACHED_OR_PENDING_STATIC,
        SAMSUNG_LIVE_BACKDROP,
        SOBER_FALLBACK
    }

    private OneUiCrystalSourcePolicy() { }

    public static Decision choose(boolean hasWallpaperAccess, boolean liveWallpaper,
            boolean hasValidStaticSnapshot, boolean samsungBlurAvailable) {
        if (hasValidStaticSnapshot) return Decision.CACHED_OR_PENDING_STATIC;
        if (liveWallpaper) {
            return hasWallpaperAccess && samsungBlurAvailable
                    ? Decision.SAMSUNG_LIVE_BACKDROP
                    : Decision.SOBER_FALLBACK;
        }
        return hasWallpaperAccess
                ? Decision.CACHED_OR_PENDING_STATIC
                : Decision.SOBER_FALLBACK;
    }

    public static Decision chooseForView(View view, boolean samsungBlurAvailable) {
        OneUiWallpaperBackdropRepository repository = repositoryFor(view);
        return choose(
                repository != null,
                repository != null && repository.isLiveWallpaper(),
                repository != null && repository.currentSnapshot() != null,
                samsungBlurAvailable);
    }

    private static OneUiWallpaperBackdropRepository repositoryFor(View view) {
        if (view == null) return null;
        ActivityContext context = ActivityContext.lookupContextNoThrow(view.getContext());
        return context instanceof Launcher
                ? ((Launcher) context).getWallpaperBackdropRepository() : null;
    }
}

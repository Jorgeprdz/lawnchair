/*
 * Copyright (C) 2026 The Lawnchair Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.launcher3.graphics;

/** Optical role of a Crystal surface. Every role samples the shared wallpaper repository. */
public enum OneUiCrystalSurfaceRole {
    DOCK(true, 1.0f),
    DOCK_OVERLAY(true, 0.92f),
    OPEN_FOLDER(false, 0.72f),
    OPEN_FOLDER_OVERLAY(false, 0.64f),
    FOLDER_ICON(false, 0.55f),
    FOLDER_ICON_OVERLAY(false, 0.50f),
    FOLDER_DRAWABLE(false, 0.60f);

    public enum BackdropSource { WALLPAPER_REPOSITORY }

    private final boolean mCapsuleOptics;
    private final float mRefractionScale;

    OneUiCrystalSurfaceRole(boolean capsuleOptics, float refractionScale) {
        mCapsuleOptics = capsuleOptics;
        mRefractionScale = refractionScale;
    }

    public boolean usesCapsuleOptics() { return mCapsuleOptics; }
    public float refractionScale() { return mRefractionScale; }
    public BackdropSource backdropSource() { return BackdropSource.WALLPAPER_REPOSITORY; }
    public boolean allowsLauncherTreeCapture() { return false; }
}

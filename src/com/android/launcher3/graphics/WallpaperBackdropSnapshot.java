/*
 * Copyright (C) 2026 The Lawnchair Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.android.launcher3.graphics;

import android.graphics.Bitmap;

import java.util.Objects;

/** Immutable, generation-tagged ownership unit for one decoded wallpaper bitmap. */
public final class WallpaperBackdropSnapshot {
    private final Bitmap mBitmap;
    private final long mWallpaperGeneration;
    private final WallpaperIdentity mIdentity;
    private final int mSourceWidth;
    private final int mSourceHeight;
    private final int mDecodedWidth;
    private final int mDecodedHeight;
    private final float mScale;
    private final float mHorizontalOffsetAtDecode;
    private final float mVerticalOffsetAtDecode;
    private final int mOrientation;
    private final int mDisplayWidth;
    private final int mDisplayHeight;
    private final long mCreationUptimeMillis;

    public WallpaperBackdropSnapshot(
            Bitmap bitmap,
            long wallpaperGeneration,
            WallpaperIdentity identity,
            int sourceWidth,
            int sourceHeight,
            float horizontalOffsetAtDecode,
            float verticalOffsetAtDecode,
            int orientation,
            int displayWidth,
            int displayHeight,
            long creationUptimeMillis) {
        mBitmap = Objects.requireNonNull(bitmap);
        if (bitmap.isMutable() || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            throw new IllegalArgumentException("Wallpaper bitmap must be live, non-empty, and immutable");
        }
        mWallpaperGeneration = wallpaperGeneration;
        mIdentity = Objects.requireNonNull(identity);
        mSourceWidth = sourceWidth;
        mSourceHeight = sourceHeight;
        mDecodedWidth = bitmap.getWidth();
        mDecodedHeight = bitmap.getHeight();
        mScale = Math.min(
                mDecodedWidth / (float) Math.max(1, sourceWidth),
                mDecodedHeight / (float) Math.max(1, sourceHeight));
        mHorizontalOffsetAtDecode = horizontalOffsetAtDecode;
        mVerticalOffsetAtDecode = verticalOffsetAtDecode;
        mOrientation = orientation;
        mDisplayWidth = displayWidth;
        mDisplayHeight = displayHeight;
        mCreationUptimeMillis = creationUptimeMillis;
    }

    public Bitmap bitmap() { return mBitmap; }
    public long wallpaperGeneration() { return mWallpaperGeneration; }
    public WallpaperIdentity identity() { return mIdentity; }
    public int sourceWidth() { return mSourceWidth; }
    public int sourceHeight() { return mSourceHeight; }
    public int decodedWidth() { return mDecodedWidth; }
    public int decodedHeight() { return mDecodedHeight; }
    public float scale() { return mScale; }
    public float horizontalOffsetAtDecode() { return mHorizontalOffsetAtDecode; }
    public float verticalOffsetAtDecode() { return mVerticalOffsetAtDecode; }
    public int orientation() { return mOrientation; }
    public int displayWidth() { return mDisplayWidth; }
    public int displayHeight() { return mDisplayHeight; }
    public long creationUptimeMillis() { return mCreationUptimeMillis; }

    public void recycle() {
        if (!mBitmap.isRecycled()) {
            mBitmap.recycle();
        }
    }
}

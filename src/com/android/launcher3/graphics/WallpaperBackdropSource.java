/*
 * Copyright (C) 2026 The Lawnchair Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.android.launcher3.graphics;

import androidx.annotation.Nullable;

/** Sole abstraction allowed to create wallpaper backdrop snapshots. */
public interface WallpaperBackdropSource {
    WallpaperIdentity readIdentity();
    LoadResult load(LoadRequest request);

    enum LoadStatus {
        SUCCESS,
        LIVE_WALLPAPER,
        ACCESS_DENIED,
        UNAVAILABLE,
        OUT_OF_MEMORY
    }

    final class LoadRequest {
        private final WallpaperIdentity mIdentity;
        private final int mDisplayWidth;
        private final int mDisplayHeight;
        private final long mCombinedBudgetBytes;
        private final long mGeneration;
        private final float mHorizontalOffset;
        private final float mVerticalOffset;
        private final int mOrientation;

        public LoadRequest(
                WallpaperIdentity identity,
                int displayWidth,
                int displayHeight,
                long combinedBudgetBytes,
                long generation) {
            this(identity, displayWidth, displayHeight, combinedBudgetBytes, generation, 0.5f, 0.5f, 0);
        }

        public LoadRequest(
                WallpaperIdentity identity,
                int displayWidth,
                int displayHeight,
                long combinedBudgetBytes,
                long generation,
                float horizontalOffset,
                float verticalOffset,
                int orientation) {
            mIdentity = identity;
            mDisplayWidth = displayWidth;
            mDisplayHeight = displayHeight;
            mCombinedBudgetBytes = combinedBudgetBytes;
            mGeneration = generation;
            mHorizontalOffset = horizontalOffset;
            mVerticalOffset = verticalOffset;
            mOrientation = orientation;
        }

        public WallpaperIdentity identity() { return mIdentity; }
        public int displayWidth() { return mDisplayWidth; }
        public int displayHeight() { return mDisplayHeight; }
        public long combinedBudgetBytes() { return mCombinedBudgetBytes; }
        public long generation() { return mGeneration; }
        public float horizontalOffset() { return mHorizontalOffset; }
        public float verticalOffset() { return mVerticalOffset; }
        public int orientation() { return mOrientation; }
    }

    final class LoadResult {
        private final LoadStatus mStatus;
        @Nullable private final WallpaperBackdropSnapshot mSnapshot;

        private LoadResult(LoadStatus status, @Nullable WallpaperBackdropSnapshot snapshot) {
            mStatus = status;
            mSnapshot = snapshot;
        }

        public static LoadResult success(WallpaperBackdropSnapshot snapshot) {
            return new LoadResult(LoadStatus.SUCCESS, snapshot);
        }

        public static LoadResult failure(LoadStatus status) {
            if (status == LoadStatus.SUCCESS) {
                throw new IllegalArgumentException("SUCCESS requires a snapshot");
            }
            return new LoadResult(status, null);
        }

        public LoadStatus status() { return mStatus; }
        @Nullable public WallpaperBackdropSnapshot snapshot() { return mSnapshot; }
    }
}

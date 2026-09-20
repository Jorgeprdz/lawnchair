/*
 * Copyright (C) 2026 The Lawnchair Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.android.launcher3.graphics;

import androidx.annotation.Nullable;

import java.util.Objects;

/** Stable fields available without decoding the wallpaper. */
public final class WallpaperIdentity {
    private final int mWallpaperId;
    @Nullable private final String mLiveComponent;
    private final long mSourceLength;
    private final long mSourceModifiedTime;

    public WallpaperIdentity(
            int wallpaperId,
            @Nullable String liveComponent,
            long sourceLength,
            long sourceModifiedTime) {
        mWallpaperId = wallpaperId;
        mLiveComponent = liveComponent;
        mSourceLength = sourceLength;
        mSourceModifiedTime = sourceModifiedTime;
    }

    public int wallpaperId() {
        return mWallpaperId;
    }

    @Nullable
    public String liveComponent() {
        return mLiveComponent;
    }

    public long sourceLength() {
        return mSourceLength;
    }

    public long sourceModifiedTime() {
        return mSourceModifiedTime;
    }

    public boolean isLive() {
        return mLiveComponent != null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WallpaperIdentity)) {
            return false;
        }
        WallpaperIdentity identity = (WallpaperIdentity) other;
        return mWallpaperId == identity.mWallpaperId
                && mSourceLength == identity.mSourceLength
                && mSourceModifiedTime == identity.mSourceModifiedTime
                && Objects.equals(mLiveComponent, identity.mLiveComponent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mWallpaperId, mLiveComponent, mSourceLength, mSourceModifiedTime);
    }

    @Override
    public String toString() {
        return "WallpaperIdentity{id=" + mWallpaperId + ", live=" + mLiveComponent
                + ", length=" + mSourceLength + ", modified=" + mSourceModifiedTime + "}";
    }
}

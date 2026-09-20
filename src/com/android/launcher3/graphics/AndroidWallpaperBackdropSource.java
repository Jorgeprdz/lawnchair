/*
 * Copyright (C) 2026 The Lawnchair Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.android.launcher3.graphics;

import static android.app.WallpaperManager.FLAG_SYSTEM;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import app.lawnchair.util.FileAccessManager;
import app.lawnchair.util.FileAccessState;

import java.io.IOException;

/** Reads static system wallpaper pixels without drawing any launcher view. */
public final class AndroidWallpaperBackdropSource implements WallpaperBackdropSource {
    private final WallpaperManager mWallpaperManager;
    private final FileAccessManager mFileAccessManager;

    public AndroidWallpaperBackdropSource(Context context) {
        Context applicationContext = context.getApplicationContext();
        mWallpaperManager = WallpaperManager.getInstance(applicationContext);
        mFileAccessManager = FileAccessManager.getInstance(applicationContext);
    }

    @Override
    public WallpaperIdentity readIdentity() {
        WallpaperInfo info = mWallpaperManager.getWallpaperInfo();
        String component = info == null
                ? null
                : new ComponentName(info.getPackageName(), info.getServiceName())
                        .flattenToShortString();
        return new WallpaperIdentity(
                mWallpaperManager.getWallpaperId(FLAG_SYSTEM), component, 0L, 0L);
    }

    @Override
    public LoadResult load(LoadRequest request) {
        Bitmap candidate = null;
        try {
            WallpaperIdentity before = readIdentity();
            if (before.isLive()) {
                return LoadResult.failure(LoadStatus.LIVE_WALLPAPER);
            }
            if (!before.equals(request.identity())) {
                return LoadResult.failure(LoadStatus.UNAVAILABLE);
            }

            mFileAccessManager.refresh();
            if (mFileAccessManager.getWallpaperAccessState().getValue()
                    != FileAccessState.Full.INSTANCE) {
                return LoadResult.failure(LoadStatus.ACCESS_DENIED);
            }

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (ParcelFileDescriptor descriptor = mWallpaperManager.getWallpaperFile(FLAG_SYSTEM)) {
                if (descriptor == null) {
                    return LoadResult.failure(LoadStatus.UNAVAILABLE);
                }
                BitmapFactory.decodeFileDescriptor(descriptor.getFileDescriptor(), null, bounds);
            }

            WallpaperDecodePlan plan = WallpaperDecodePlan.forBounds(
                    bounds.outWidth,
                    bounds.outHeight,
                    request.displayWidth(),
                    request.displayHeight(),
                    request.combinedBudgetBytes());
            if (!plan.isValid()) {
                return LoadResult.failure(LoadStatus.UNAVAILABLE);
            }

            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inSampleSize = plan.sampleSize();
            decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
            decode.inMutable = false;
            try (ParcelFileDescriptor descriptor = mWallpaperManager.getWallpaperFile(FLAG_SYSTEM)) {
                if (descriptor == null) {
                    return LoadResult.failure(LoadStatus.UNAVAILABLE);
                }
                candidate = BitmapFactory.decodeFileDescriptor(
                        descriptor.getFileDescriptor(), null, decode);
            }
            if (candidate == null
                    || candidate.isRecycled()
                    || candidate.isMutable()
                    || candidate.getWidth() <= 0
                    || candidate.getHeight() <= 0
                    || !request.identity().equals(readIdentity())) {
                recycle(candidate);
                return LoadResult.failure(LoadStatus.UNAVAILABLE);
            }

            WallpaperBackdropSnapshot snapshot = new WallpaperBackdropSnapshot(
                    candidate,
                    request.generation(),
                    request.identity(),
                    bounds.outWidth,
                    bounds.outHeight,
                    request.horizontalOffset(),
                    request.verticalOffset(),
                    request.orientation(),
                    request.displayWidth(),
                    request.displayHeight(),
                    SystemClock.uptimeMillis());
            candidate = null;
            return LoadResult.success(snapshot);
        } catch (SecurityException exception) {
            recycle(candidate);
            return LoadResult.failure(LoadStatus.ACCESS_DENIED);
        } catch (OutOfMemoryError error) {
            recycle(candidate);
            return LoadResult.failure(LoadStatus.OUT_OF_MEMORY);
        } catch (IOException | IllegalArgumentException exception) {
            recycle(candidate);
            return LoadResult.failure(LoadStatus.UNAVAILABLE);
        }
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}

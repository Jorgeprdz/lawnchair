/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package com.android.launcher3.graphics;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * A view-tree representation of the current wallpaper, used as the GPU backdrop source for dock
 * glass. Android's wallpaper is a separate surface and cannot be sampled by a normal View renderer.
 * This view draws one cached WallpaperManager drawable at launcher-root coordinates; the glass
 * renderer can then sample it without capturing the dock icons or allocating a screenshot per frame.
 */
public final class WallpaperBackdropView extends View {

    private final WallpaperManager mWallpaperManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final WallpaperManager.OnColorsChangedListener mColorsChangedListener =
            (colors, which) -> {
                mWallpaper = null;
                invalidate();
                if (mInvalidationTarget != null) mInvalidationTarget.invalidate();
            };
    private Drawable mWallpaper;
    private @Nullable View mInvalidationTarget;
    private boolean mListenerRegistered;
    private final int[] mViewLocation = new int[2];
    private final int[] mRootLocation = new int[2];

    public WallpaperBackdropView(Context context) {
        super(context);
        mWallpaperManager = context.getSystemService(WallpaperManager.class);
        setWillNotDraw(false);
    }

    public void setInvalidationTarget(@Nullable View target) {
        mInvalidationTarget = target;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateWallpaperListener();
        mWallpaper = null;
        invalidate();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (changedView == this) {
            updateWallpaperListener();
            if (visibility == VISIBLE) {
                mWallpaper = null;
                invalidate();
            }
        }
    }

    private void updateWallpaperListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
                && isAttachedToWindow() && isShown() && !mListenerRegistered
                && mWallpaperManager != null) {
            try {
                mWallpaperManager.addOnColorsChangedListener(
                        mColorsChangedListener, mMainHandler);
                mListenerRegistered = true;
            } catch (RuntimeException | LinkageError ignored) {
                // The cached wallpaper remains usable without change notifications.
            }
        } else if ((!isAttachedToWindow() || !isShown())
                && mListenerRegistered && mWallpaperManager != null) {
            try {
                mWallpaperManager.removeOnColorsChangedListener(mColorsChangedListener);
            } catch (RuntimeException | LinkageError ignored) {
                // Ignore teardown races.
            }
            mListenerRegistered = false;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        updateWallpaperListener();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mWallpaperManager == null) return;
        if (mWallpaper == null) {
            try {
                mWallpaper = mWallpaperManager.getDrawable();
            } catch (RuntimeException | LinkageError ignored) {
                return;
            }
        }
        if (mWallpaper == null) return;

        View root = getRootView();
        if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        getLocationInWindow(mViewLocation);
        root.getLocationInWindow(mRootLocation);
        int left = mViewLocation[0] - mRootLocation[0];
        int top = mViewLocation[1] - mRootLocation[1];

        int save = canvas.save();
        canvas.translate(-left, -top);
        mWallpaper.setBounds(0, 0, root.getWidth(), root.getHeight());
        mWallpaper.draw(canvas);
        canvas.restoreToCount(save);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (mInvalidationTarget != null) mInvalidationTarget.invalidate();
    }
}

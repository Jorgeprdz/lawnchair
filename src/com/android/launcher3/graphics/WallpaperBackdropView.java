/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package com.android.launcher3.graphics;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.Nullable;

/**
 * A local mirror of the wallpaper drawable used as the GPU blur source for dock glass. Android's
 * wallpaper is a separate surface and cannot be sampled by a normal View renderer; on devices with
 * layered/live wallpapers this mirrors the drawable exposed by WallpaperManager, not the system's
 * live composed surface. The cached drawable is drawn at launcher-root coordinates, so the dock
 * blur does not capture app icons or allocate a screenshot per frame.
 */
public final class WallpaperBackdropView extends View {

    private final WallpaperManager mWallpaperManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private Drawable mWallpaper;
    private @Nullable View mInvalidationTarget;
    private boolean mListenerRegistered;
    private float mBlurRadiusPx;
    private float mCornerRadiusPx;
    private int mInsetLeft;
    private int mInsetTop;
    private int mInsetRight;
    private int mInsetBottom;
    private final int[] mViewLocation = new int[2];
    private final int[] mRootLocation = new int[2];
    private final ViewOutlineProvider mGlassOutlineProvider = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            int right = Math.max(mInsetLeft, view.getWidth() - mInsetRight);
            int bottom = Math.max(mInsetTop, view.getHeight() - mInsetBottom);
            outline.setRoundRect(mInsetLeft, mInsetTop, right, bottom, mCornerRadiusPx);
        }
    };
    private final WallpaperManager.OnColorsChangedListener mColorsChangedListener =
            (colors, which) -> {
                mWallpaper = null;
                invalidate();
                if (mInvalidationTarget != null) mInvalidationTarget.invalidate();
            };

    public WallpaperBackdropView(Context context) {
        super(context);
        mWallpaperManager = context.getSystemService(WallpaperManager.class);
        setWillNotDraw(false);
        setOutlineProvider(mGlassOutlineProvider);
        setClipToOutline(true);
    }

    /**
     * Applies the Android GPU blur to the locally drawn wallpaper and clips it to the dock shape.
     * This does not sample another app's window or the live wallpaper surface.
     */
    public void configureGlassBlur(float blurRadiusPx, float cornerRadiusPx,
            int insetLeft, int insetTop, int insetRight, int insetBottom) {
        mCornerRadiusPx = Math.max(0f, cornerRadiusPx);
        mInsetLeft = Math.max(0, insetLeft);
        mInsetTop = Math.max(0, insetTop);
        mInsetRight = Math.max(0, insetRight);
        mInsetBottom = Math.max(0, insetBottom);
        if (Float.compare(mBlurRadiusPx, blurRadiusPx) != 0) {
            mBlurRadiusPx = Math.max(0f, blurRadiusPx);
            setRenderEffect(mBlurRadiusPx > 0f
                    ? RenderEffect.createBlurEffect(mBlurRadiusPx, mBlurRadiusPx,
                            Shader.TileMode.CLAMP)
                    : null);
        }
        invalidateOutline();
        invalidate();
    }

    public void clearGlassBlur() {
        if (mBlurRadiusPx == 0f) return;
        mBlurRadiusPx = 0f;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRenderEffect(null);
        invalidate();
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

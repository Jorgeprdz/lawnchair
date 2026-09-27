/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package com.android.launcher3.graphics;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.Nullable;

import app.lawnchair.util.FileAccessManager;
import app.lawnchair.util.FileAccessState;

/**
 * A local mirror of the wallpaper drawable used as the GPU blur source for dock glass. Android's
 * wallpaper is a separate surface and cannot be sampled by a normal View renderer; on devices with
 * layered/live wallpapers this mirrors the drawable exposed by WallpaperManager, not the system's
 * live composed surface. Its downsampled snapshot is cached at launcher-root dimensions, so the
 * dock renderer gets standard bitmap content without capturing app icons or allocating per frame.
 */
public final class WallpaperBackdropView extends View {
    private static final String TAG = "WallpaperBackdrop";

    private final WallpaperManager mWallpaperManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private Drawable mWallpaper;
    private Bitmap mWallpaperSnapshot;
    private final Canvas mSnapshotCanvas = new Canvas();
    private final Paint mSnapshotPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect mSnapshotBounds = new Rect();
    private @Nullable View mInvalidationTarget;
    private boolean mListenerRegistered;
    private boolean mLoggedSnapshot;
    private boolean mLoggedFirstDraw;
    private boolean mLoggedNoWallpaperSource;
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
                mLoggedSnapshot = false;
                mLoggedNoWallpaperSource = false;
                clearWallpaperSnapshot();
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
     * Applies the Android GPU blur to a stable bitmap-backed wallpaper view and clips it to the
     * dock shape. On Android 13+, LiquidGlass samples this ordinary bitmap content and adds its
     * refraction shader; this avoids trying to record Samsung's special wallpaper drawable.
     */
    public void configureGlassBlur(float blurRadiusPx, float cornerRadiusPx,
            int insetLeft, int insetTop, int insetRight, int insetBottom,
            boolean clipToDockShape) {
        mCornerRadiusPx = Math.max(0f, cornerRadiusPx);
        mInsetLeft = Math.max(0, insetLeft);
        mInsetTop = Math.max(0, insetTop);
        mInsetRight = Math.max(0, insetRight);
        mInsetBottom = Math.max(0, insetBottom);
        setClipToOutline(clipToDockShape);
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
        mLoggedSnapshot = false;
        mLoggedFirstDraw = false;
        mLoggedNoWallpaperSource = false;
        invalidate();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (changedView == this) {
            updateWallpaperListener();
            if (visibility == VISIBLE) {
                mWallpaper = null;
                mLoggedSnapshot = false;
                mLoggedNoWallpaperSource = false;
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
        if (!mLoggedFirstDraw) {
            View root = getRootView();
            Log.d(TAG, "onDraw size=" + getWidth() + "x" + getHeight()
                    + " visibility=" + getVisibility()
                    + " hardware=" + canvas.isHardwareAccelerated()
                    + " wallpaperManager=" + (mWallpaperManager != null)
                    + " root=" + (root == null ? "null" : root.getWidth() + "x" + root.getHeight()));
            mLoggedFirstDraw = true;
        }
        if (mWallpaperManager == null) return;
        if (mWallpaper == null) {
            try {
                WallpaperInfo wallpaperInfo = mWallpaperManager.getWallpaperInfo();
                if (wallpaperInfo != null) {
                    // WallpaperManager.getDrawable() is restricted on Android 14+ and a live
                    // wallpaper is rendered in a separate system surface. Use the service's
                    // public preview thumbnail when available; it requires no broad storage grant.
                    mWallpaper = wallpaperInfo.loadThumbnail(getContext().getPackageManager());
                    if (mWallpaper != null) {
                        Log.d(TAG, "using live wallpaper service thumbnail: "
                                + wallpaperInfo.getPackageName());
                        mLoggedNoWallpaperSource = false;
                    } else if (!mLoggedNoWallpaperSource) {
                        Log.w(TAG, "Live wallpaper service supplied no preview thumbnail");
                        mLoggedNoWallpaperSource = true;
                    }
                } else if (FileAccessManager.getInstance(getContext())
                        .getWallpaperAccessState().getValue() == FileAccessState.Full.INSTANCE) {
                    mWallpaper = mWallpaperManager.getDrawable();
                    mLoggedNoWallpaperSource = false;
                } else if (!mLoggedNoWallpaperSource) {
                    Log.w(TAG, "No wallpaper image source: live service thumbnail unavailable "
                            + "and wallpaper file access is not granted");
                    mLoggedNoWallpaperSource = true;
                }
            } catch (RuntimeException | LinkageError ignored) {
                if (!mLoggedNoWallpaperSource) {
                    Log.w(TAG, "Could not load a permitted wallpaper preview", ignored);
                    mLoggedNoWallpaperSource = true;
                }
                return;
            }
        }
        if (mWallpaper == null) {
            Log.w(TAG, "WallpaperManager.getDrawable returned null");
            return;
        }

        View root = getRootView();
        if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) {
            Log.w(TAG, "Wallpaper snapshot skipped: invalid root size");
            return;
        }
        if (!ensureWallpaperSnapshot(root.getWidth(), root.getHeight())) {
            Log.w(TAG, "Wallpaper snapshot creation failed");
            return;
        }
        getLocationInWindow(mViewLocation);
        root.getLocationInWindow(mRootLocation);
        int left = mViewLocation[0] - mRootLocation[0];
        int top = mViewLocation[1] - mRootLocation[1];

        int save = canvas.save();
        canvas.translate(-left, -top);
        mSnapshotBounds.set(0, 0, root.getWidth(), root.getHeight());
        canvas.drawBitmap(mWallpaperSnapshot, null, mSnapshotBounds, mSnapshotPaint);
        canvas.restoreToCount(save);
    }

    private boolean ensureWallpaperSnapshot(int width, int height) {
        if (mWallpaperSnapshot != null
                && mSnapshotBounds.right == width && mSnapshotBounds.bottom == height) {
            return true;
        }
        clearWallpaperSnapshot();
        float scale = Math.min(1f, Math.min(2048f / Math.max(width, height),
                (float) Math.sqrt(3_200_000d / ((long) width * height))));
        int bitmapWidth = Math.max(1, Math.round(width * scale));
        int bitmapHeight = Math.max(1, Math.round(height * scale));
        try {
            mWallpaperSnapshot = Bitmap.createBitmap(
                    bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            mSnapshotCanvas.setBitmap(mWallpaperSnapshot);
            mWallpaper.setBounds(0, 0, bitmapWidth, bitmapHeight);
            mWallpaper.draw(mSnapshotCanvas);
            mSnapshotBounds.set(0, 0, width, height);
            if (!mLoggedSnapshot) {
                int c1 = mWallpaperSnapshot.getPixel(bitmapWidth / 4, bitmapHeight / 4);
                int c2 = mWallpaperSnapshot.getPixel(bitmapWidth / 2, bitmapHeight / 2);
                int c3 = mWallpaperSnapshot.getPixel(bitmapWidth / 2, bitmapHeight * 3 / 4);
                int c4 = mWallpaperSnapshot.getPixel(bitmapWidth / 2, bitmapHeight * 9 / 10);
                Log.d(TAG, "snapshot drawable=" + mWallpaper.getClass().getName()
                        + " bitmap=" + bitmapWidth + "x" + bitmapHeight
                        + " samples=" + Integer.toHexString(c1) + ","
                        + Integer.toHexString(c2) + "," + Integer.toHexString(c3) + ","
                        + Integer.toHexString(c4));
                mLoggedSnapshot = true;
            }
            return true;
        } catch (OutOfMemoryError | RuntimeException | LinkageError unavailable) {
            Log.w(TAG, "Wallpaper drawable could not be snapshotted", unavailable);
            clearWallpaperSnapshot();
            return false;
        }
    }

    private void clearWallpaperSnapshot() {
        mSnapshotCanvas.setBitmap(null);
        if (mWallpaperSnapshot != null && !mWallpaperSnapshot.isRecycled()) {
            mWallpaperSnapshot.recycle();
        }
        mWallpaperSnapshot = null;
        mSnapshotBounds.setEmpty();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (mInvalidationTarget != null) mInvalidationTarget.invalidate();
    }
}

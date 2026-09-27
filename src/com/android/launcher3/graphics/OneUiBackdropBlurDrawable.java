/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package com.android.launcher3.graphics;

import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.lawnchair.oneui.OneUiGlassStyle;
import java.lang.ref.WeakReference;

/**
 * Local wallpaper blur used when WindowManager has disabled cross-window blur.
 * The wallpaper patch and shader are reused; backdrop extraction is throttled rather than run on
 * every frame. This does not capture app content or cross the launcher window boundary.
 */
final class OneUiBackdropBlurDrawable extends Drawable {
    private static final int API_RUNTIME_SHADER = 33;
    private static final int MAX_CAPTURE_SIZE = 2048;
    private static final long MAX_CAPTURE_PIXELS = 1_600_000L;
    private static final long CAPTURE_INTERVAL_MS = 160L;
    private static final String SHADER_SOURCE =
            "uniform shader backdrop;\n"
                    + "uniform float2 origin;\n"
                    + "uniform float pad;\n"
                    + "uniform float blurRadius;\n"
                    + "uniform float saturation;\n"
                    + "half4 main(float2 coord) {\n"
                    + "  float2 p = coord - origin + float2(pad, pad);\n"
                    + "  float2 x = float2(blurRadius, 0.0);\n"
                    + "  float2 y = float2(0.0, blurRadius);\n"
                    + "  half4 c = backdrop.eval(p) * 0.20;\n"
                    + "  c += backdrop.eval(p + x) * 0.10;\n"
                    + "  c += backdrop.eval(p - x) * 0.10;\n"
                    + "  c += backdrop.eval(p + y) * 0.10;\n"
                    + "  c += backdrop.eval(p - y) * 0.10;\n"
                    + "  c += backdrop.eval(p + x + y) * 0.10;\n"
                    + "  c += backdrop.eval(p + x - y) * 0.10;\n"
                    + "  c += backdrop.eval(p - x + y) * 0.10;\n"
                    + "  c += backdrop.eval(p - x - y) * 0.10;\n"
                    + "  half l = dot(c.rgb, half3(0.2126, 0.7152, 0.0722));\n"
                    + "  c.rgb = mix(half3(l), c.rgb, saturation);\n"
                    + "  c.a = 1.0;\n"
                    + "  return c;\n"
                    + "}";

    private final View mHost;
    private final WallpaperManager mWallpaperManager;
    private final WallpaperManager.OnColorsChangedListener mWallpaperListener;
    private final View.OnAttachStateChangeListener mAttachStateListener;
    private final int mStyle;
    private final int mBlurRadiusPx;
    private final float mCornerRadius;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix mShaderMatrix = new Matrix();
    private final Canvas mCaptureCanvas = new Canvas();
    private final RectF mRect = new RectF();
    private final Rect mWallpaperBounds = new Rect();
    private final int[] mHostLocation = new int[2];
    private final int[] mRootLocation = new int[2];

    private Bitmap mBackdrop;
    private BitmapShader mBackdropShader;
    private Drawable mWallpaper;
    private Shader mRuntimeShader;
    private RenderEffect mBlurEffect;
    private int mCapturePad;
    private long mLastCaptureMs;
    private int mAlpha = 255;
    private @Nullable ColorFilter mColorFilter;
    private boolean mWallpaperListenerRegistered;
    private boolean mAttachListenerRegistered;

    static Drawable create(View host, int style, float cornerRadius, int blurRadiusPx) {
        return new OneUiBackdropBlurDrawable(host, style, cornerRadius, blurRadiusPx);
    }

    private OneUiBackdropBlurDrawable(View host, int style, float cornerRadius,
            int blurRadiusPx) {
        mHost = host;
        mWallpaperManager = WallpaperManager.getInstance(host.getContext());
        WeakReference<OneUiBackdropBlurDrawable> weakSelf = new WeakReference<>(this);
        mWallpaperListener = (colors, which) -> {
            OneUiBackdropBlurDrawable drawable = weakSelf.get();
            if (drawable != null) {
                drawable.mWallpaper = null;
                drawable.mLastCaptureMs = 0;
                drawable.invalidateSelf();
            }
        };
        mAttachStateListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                OneUiBackdropBlurDrawable drawable = weakSelf.get();
                if (drawable != null) drawable.registerWallpaperListener();
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                OneUiBackdropBlurDrawable drawable = weakSelf.get();
                if (drawable != null) drawable.unregisterWallpaperListener();
            }
        };
        addAttachListener();
        mStyle = style;
        mCornerRadius = cornerRadius;
        mBlurRadiusPx = Math.max(1, blurRadiusPx);
        mCapturePad = Math.max(12, mBlurRadiusPx * 2);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mBlurEffect = RenderEffect.createBlurEffect(mBlurRadiusPx, mBlurRadiusPx,
                    Shader.TileMode.CLAMP);
        }
        if (mBlurEffect == null && Build.VERSION.SDK_INT >= API_RUNTIME_SHADER) {
            try {
                mRuntimeShader = Api33Impl.createShader(SHADER_SOURCE);
                Api33Impl.setFloatUniform(mRuntimeShader, "blurRadius", mBlurRadiusPx);
                Api33Impl.setFloatUniform(mRuntimeShader, "saturation",
                        style == OneUiGlassStyle.FROSTY ? 0.70f : 1f);
                mPaint.setShader(mRuntimeShader);
            } catch (RuntimeException | LinkageError unavailable) {
                mRuntimeShader = null;
            }
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty() || !mHost.isAttachedToWindow()) return;
        captureWallpaper(bounds);
        if (mBackdropShader == null) return;

        mPaint.setAlpha(mAlpha);
        mPaint.setColorFilter(mColorFilter);
        mRect.set(bounds);
        if (mBlurEffect != null) {
            mShaderMatrix.setTranslate(mCapturePad - bounds.left, mCapturePad - bounds.top);
            mBackdropShader.setLocalMatrix(mShaderMatrix);
            mPaint.setShader(mBackdropShader);
            if (canvas.isHardwareAccelerated()) {
                // RenderEffect performs a real GPU blur over the full-resolution wallpaper patch.
                // The previous nine-tap RuntimeShader produced visible sampling artifacts in
                // small folder surfaces, especially around detailed wallpaper edges.
                mPaint.setRenderEffect(mBlurEffect);
            }
            canvas.drawRoundRect(mRect, mCornerRadius, mCornerRadius, mPaint);
            if (canvas.isHardwareAccelerated()) mPaint.setRenderEffect(null);
            mPaint.setShader(null);
        } else if (mRuntimeShader != null) {
            Api33Impl.setInputShader(mRuntimeShader, "backdrop", mBackdropShader);
            Api33Impl.setFloatUniform(mRuntimeShader, "origin", bounds.left, bounds.top);
            Api33Impl.setFloatUniform(mRuntimeShader, "pad", mCapturePad);
            mPaint.setShader(mRuntimeShader);
            canvas.drawRoundRect(mRect, mCornerRadius, mCornerRadius, mPaint);
        } else {
            // Keep the wallpaper patch visible on software-backed transitions; the color layer
            // above it remains the safe fallback until the hardware renderer is available again.
            mShaderMatrix.setTranslate(mCapturePad - bounds.left, mCapturePad - bounds.top);
            mBackdropShader.setLocalMatrix(mShaderMatrix);
            mPaint.setShader(mBackdropShader);
            canvas.drawRoundRect(mRect, mCornerRadius, mCornerRadius, mPaint);
            mPaint.setShader(null);
        }
    }

    private void captureWallpaper(Rect bounds) {
        long now = android.os.SystemClock.uptimeMillis();
        if (mBackdrop != null && now - mLastCaptureMs < CAPTURE_INTERVAL_MS) return;

        View root = mHost.getRootView();
        if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        int width = bounds.width() + mCapturePad * 2;
        int height = bounds.height() + mCapturePad * 2;
        if (width <= 0 || height <= 0 || width > MAX_CAPTURE_SIZE || height > MAX_CAPTURE_SIZE
                || (long) width * height > MAX_CAPTURE_PIXELS) return;

        try {
            if (mBackdrop == null || mBackdrop.getWidth() != width
                    || mBackdrop.getHeight() != height) {
                mBackdrop = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                mBackdropShader = new BitmapShader(mBackdrop, Shader.TileMode.CLAMP,
                        Shader.TileMode.CLAMP);
                mCaptureCanvas.setBitmap(mBackdrop);
                Api33Impl.setInputShader(mRuntimeShader, "backdrop", mBackdropShader);
            }
            if (mWallpaper == null) {
                mWallpaper = mWallpaperManager.getDrawable();
            }
            if (mWallpaper == null) {
                mLastCaptureMs = now;
                return;
            }

            mHost.getLocationInWindow(mHostLocation);
            root.getLocationInWindow(mRootLocation);
            int left = mHostLocation[0] - mRootLocation[0] + bounds.left - mCapturePad;
            int top = mHostLocation[1] - mRootLocation[1] + bounds.top - mCapturePad;
            mCaptureCanvas.drawColor(android.graphics.Color.TRANSPARENT,
                    android.graphics.PorterDuff.Mode.CLEAR);
            int save = mCaptureCanvas.save();
            mCaptureCanvas.translate(-left, -top);
            mWallpaperBounds.set(0, 0, root.getWidth(), root.getHeight());
            mWallpaper.setBounds(mWallpaperBounds);
            mWallpaper.draw(mCaptureCanvas);
            mCaptureCanvas.restoreToCount(save);
            mLastCaptureMs = now;
        } catch (OutOfMemoryError | RuntimeException | LinkageError unavailable) {
            mCaptureCanvas.setBitmap(null);
            if (mBackdrop != null) mBackdrop.recycle();
            mBackdrop = null;
            mBackdropShader = null;
            mLastCaptureMs = now;
        }
    }

    private void registerWallpaperListener() {
        if (mWallpaperListenerRegistered) return;
        try {
            mWallpaperManager.addOnColorsChangedListener(mWallpaperListener,
                    new Handler(Looper.getMainLooper()));
            mWallpaperListenerRegistered = true;
        } catch (RuntimeException | LinkageError ignored) {
            // WallpaperManager callbacks are an optimization; the normal cached path still works.
        }
    }

    private void addAttachListener() {
        if (mAttachListenerRegistered) return;
        mHost.addOnAttachStateChangeListener(mAttachStateListener);
        mAttachListenerRegistered = true;
        if (mHost.isAttachedToWindow()) registerWallpaperListener();
    }

    private void removeAttachListener() {
        if (!mAttachListenerRegistered) return;
        mHost.removeOnAttachStateChangeListener(mAttachStateListener);
        mAttachListenerRegistered = false;
        unregisterWallpaperListener();
    }

    private void unregisterWallpaperListener() {
        if (!mWallpaperListenerRegistered) return;
        try {
            mWallpaperManager.removeOnColorsChangedListener(mWallpaperListener);
        } catch (RuntimeException | LinkageError ignored) {
            // Ignore teardown races.
        }
        mWallpaperListenerRegistered = false;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        if (mBackdrop != null && (mBackdrop.getWidth() != bounds.width() + mCapturePad * 2
                || mBackdrop.getHeight() != bounds.height() + mCapturePad * 2)) {
            mCaptureCanvas.setBitmap(null);
            mBackdrop.recycle();
            mBackdrop = null;
            mBackdropShader = null;
        }
        mLastCaptureMs = 0;
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (visible) {
            addAttachListener();
        } else {
            removeAttachListener();
        }
        return changed;
    }

    @Override
    public void setAlpha(int alpha) {
        mAlpha = Math.max(0, Math.min(255, alpha));
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mColorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    private static final class Api33Impl {
        private Api33Impl() { }
        static Shader createShader(String source) {
            return new android.graphics.RuntimeShader(source);
        }
        static void setInputShader(Shader shader, String name, Shader input) {
            ((android.graphics.RuntimeShader) shader).setInputShader(name, input);
        }
        static void setFloatUniform(Shader shader, String name, float value) {
            ((android.graphics.RuntimeShader) shader).setFloatUniform(name, value);
        }
        static void setFloatUniform(Shader shader, String name, float first, float second) {
            ((android.graphics.RuntimeShader) shader).setFloatUniform(name, first, second);
        }
    }
}

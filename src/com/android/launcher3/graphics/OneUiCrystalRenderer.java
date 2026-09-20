/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0 (the "License");
 *
 * Crystal optical renderer adapted from the architecture of
 * QWEA0/Liquid-Glass-Android at commit 73e22530f4f6d1d525d7d76ca83efe07dd816eee.
 * The implementation keeps Lawnchair's own shapes and contains no proprietary
 * assets or external resources.
 */
package com.android.launcher3.graphics;

import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Real Crystal material for One UI surfaces.
 *
 * <p>The rendering pipeline mirrors the Liquid-Glass-Android approach: capture a backdrop around
 * the surface, feed it into a GPU shader, apply a rounded-rect SDF lens, refract/displace the
 * sample coordinates, blur the displaced backdrop, add chromatic dispersion, bevel lighting and a
 * specular highlight. API < 33 keeps a real captured backdrop fallback instead of reverting to a
 * pure translucent tint.</p>
 */
final class OneUiCrystalRenderer extends Drawable {
    private static final int API_RUNTIME_SHADER = 33;
    private static final int MAX_CAPTURE_SIZE = 2048;
    private static final long MAX_CAPTURE_PIXELS = 2_500_000L;
    private static final String LAUNCHER_PREVIEW_VIEW = "app.lawnchair.views.LauncherPreviewView";

    private static final ThreadLocal<Boolean> sCapturingBackdrop =
            new ThreadLocal<Boolean>() {
                @Override
                protected Boolean initialValue() {
                    return false;
                }
            };

    private static final String CRYSTAL_SHADER =
            "uniform shader backdrop;\n"
                    + "uniform float2 size;\n"
                    + "uniform float2 origin;\n"
                    + "uniform float capturePad;\n"
                    + "uniform float radius;\n"
                    + "uniform float blurPx;\n"
                    + "uniform float refractionPx;\n"
                    + "uniform float depth;\n"
                    + "uniform float specular;\n"
                    + "uniform float4 tint;\n"
                    + "\n"
                    + "float sdRoundRect(float2 p, float2 halfSize, float r) {\n"
                    + "    float2 q = abs(p) - halfSize + r;\n"
                    + "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n"
                    + "}\n"
                    + "\n"
                    + "half4 blurBackdrop(float2 p, float r) {\n"
                    + "    float2 dx = float2(max(1.0, r), 0.0);\n"
                    + "    float2 dy = float2(0.0, max(1.0, r));\n"
                    + "    half4 c = backdrop.eval(p) * 0.28;\n"
                    + "    c += backdrop.eval(p + dx) * 0.10;\n"
                    + "    c += backdrop.eval(p - dx) * 0.10;\n"
                    + "    c += backdrop.eval(p + dy) * 0.10;\n"
                    + "    c += backdrop.eval(p - dy) * 0.10;\n"
                    + "    c += backdrop.eval(p + dx + dy) * 0.08;\n"
                    + "    c += backdrop.eval(p + dx - dy) * 0.08;\n"
                    + "    c += backdrop.eval(p - dx + dy) * 0.08;\n"
                    + "    c += backdrop.eval(p - dx - dy) * 0.08;\n"
                    + "    return c;\n"
                    + "}\n"
                    + "\n"
                    + "half4 main(float2 coord) {\n"
                    + "    float2 local = coord - origin;\n"
                    + "    float2 center = size * 0.5;\n"
                    + "    float corner = min(radius, min(center.x, center.y));\n"
                    + "    float2 p = local - center;\n"
                    + "    float sd = sdRoundRect(p, center, corner);\n"
                    + "    float2 norm = p / max(center, float2(1.0, 1.0));\n"
                    + "    float len = length(norm);\n"
                    + "    float2 normal = normalize(norm + float2(0.001, 0.001));\n"
                    + "    float rim = 1.0 - smoothstep(-depth * 2.8, -1.0, sd);\n"
                    + "    float lens = pow(max(0.0, 1.0 - len), 1.35);\n"
                    + "    float2 tangent = float2(-normal.y, normal.x);\n"
                    + "    float2 warp = normal * refractionPx * (0.24 + 0.76 * rim);\n"
                    + "    warp += tangent * refractionPx * 0.10 * lens;\n"
                    + "    float2 baseCoord = local + float2(capturePad, capturePad) + warp;\n"
                    + "\n"
                    + "    half4 soft = blurBackdrop(baseCoord, blurPx);\n"
                    + "    half4 red = blurBackdrop(baseCoord + float2(refractionPx * 0.30, 0.0), blurPx);\n"
                    + "    half4 blue = blurBackdrop(baseCoord - float2(refractionPx * 0.24, 0.0), blurPx);\n"
                    + "    half4 dispersed = half4(red.r, soft.g, blue.b, soft.a);\n"
                    + "    half4 tintColor = half4(tint.r, tint.g, tint.b, tint.a);\n"
                    + "    half4 glass = mix(soft, dispersed, 0.48);\n"
                    + "    glass = mix(glass, tintColor, tint.a);\n"
                    + "\n"
                    + "    float topGlow = (1.0 - smoothstep(0.0, 0.52, local.y / max(1.0, size.y)))\n"
                    + "            * (0.34 + 0.66 * lens);\n"
                    + "    float light = max(0.0, dot(-normal, normalize(float2(-0.55, -0.82))));\n"
                    + "    float bevel = rim * (0.20 + 0.80 * light) * specular;\n"
                    + "    float lowerShadow = rim * smoothstep(0.48, 1.0, local.y / max(1.0, size.y))\n"
                    + "            * depth * 0.020;\n"
                    + "    float diagonal = pow(max(0.0, 1.0 - length((local - size * float2(0.22, 0.18))\n"
                    + "            / max(size * float2(0.82, 0.55), float2(1.0, 1.0)))), 3.0);\n"
                    + "\n"
                    + "    glass.rgb += half3(topGlow * 0.10 + bevel * 0.30 + diagonal * specular * 0.16);\n"
                    + "    glass.rgb -= half3(lowerShadow);\n"
                    + "    glass.a = 1.0;\n"
                    + "    return glass;\n"
                    + "}";

    private final View mHost;
    private final int mColor;
    private final float mCornerRadius;
    private final Paint mShaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mFallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Rect mSrc = new Rect();
    private final RectF mDst = new RectF();
    private final Path mClipPath = new Path();
    private final int[] mHostLocation = new int[2];
    private final int[] mRootLocation = new int[2];

    private Bitmap mBackdrop;
    private BitmapShader mBackdropShader;
    private Drawable mWallpaper;
    private int mCapturePad;
    private int mAlpha = 255;
    private float mStrength;
    private float mBlurPx;
    private float mRefractionPx;
    private float mDepthPx;
    private float mSpecular;
    private @Nullable ColorFilter mColorFilter;

    static Drawable create(View host, int color, float cornerRadius, int intensityPercent) {
        return new OneUiCrystalRenderer(host, color, cornerRadius, intensityPercent);
    }

    private OneUiCrystalRenderer(View host, int color, float cornerRadius, int intensityPercent) {
        mHost = host;
        mColor = color;
        mCornerRadius = cornerRadius;
        setIntensity(intensityPercent);
    }

    private void setIntensity(int intensityPercent) {
        float slider = Math.max(0f, Math.min(100f, intensityPercent)) / 100f;
        // Crystal selected at 0% still remains a visible optical material.
        mStrength = 0.24f + 0.76f * slider;
        float density = 1f;
        try {
            if (mHost != null) {
                density = Math.max(1f, mHost.getResources().getDisplayMetrics().density);
            }
        } catch (RuntimeException | LinkageError ignored) {
            density = 1f;
        }
        mBlurPx = (2.5f + 9.5f * mStrength) * density;
        mRefractionPx = (3.0f + 15.0f * mStrength) * density;
        mDepthPx = (3.0f + 13.0f * mStrength) * density;
        mSpecular = 0.30f + 0.70f * mStrength;
        mCapturePad = Math.round((20f + 28f * mStrength) * density + mRefractionPx + mBlurPx);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        try {
            drawSafely(canvas);
        } catch (ThreadDeath death) {
            throw death;
        } catch (Throwable ignored) {
            try {
                drawStaticFallback(canvas, getBounds());
            } catch (ThreadDeath death) {
                throw death;
            } catch (Throwable ignoredAgain) {
                // Never let Crystal close Launcher; an empty draw is safer than a crash.
            }
        }
    }

    private void drawSafely(@NonNull Canvas canvas) {
        if (Boolean.TRUE.equals(sCapturingBackdrop.get())) {
            return;
        }

        Rect bounds = getBounds();
        if (bounds == null || bounds.isEmpty()) {
            return;
        }

        boolean captured = captureBackdrop(bounds);
        if (captured && Build.VERSION.SDK_INT >= API_RUNTIME_SHADER && mBackdropShader != null) {
            try {
                Api33Impl.drawShader(this, canvas, bounds);
                return;
            } catch (ThreadDeath death) {
                throw death;
            } catch (Throwable ignored) {
                // Fall through to the captured-backdrop/static renderer below.
            }
        }

        if (captured) {
            drawCapturedFallback(canvas, bounds);
        } else {
            drawStaticFallback(canvas, bounds);
        }
    }

    private boolean captureBackdrop(Rect bounds) {
        if (!isHostSafeForCapture(bounds)) {
            return false;
        }

        View root = mHost.getRootView();
        int width = bounds.width() + mCapturePad * 2;
        int height = bounds.height() + mCapturePad * 2;
        long pixels = (long) width * (long) height;
        if (width <= 0 || height <= 0 || width > MAX_CAPTURE_SIZE || height > MAX_CAPTURE_SIZE
                || pixels > MAX_CAPTURE_PIXELS) {
            return false;
        }

        try {
            if (mBackdrop == null || mBackdrop.getWidth() != width || mBackdrop.getHeight() != height) {
                recycleBackdrop();
                mBackdrop = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                mBackdropShader = new BitmapShader(mBackdrop, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            }

            mHost.getLocationInWindow(mHostLocation);
            root.getLocationInWindow(mRootLocation);

            int captureLeft = mHostLocation[0] - mRootLocation[0] + bounds.left - mCapturePad;
            int captureTop = mHostLocation[1] - mRootLocation[1] + bounds.top - mCapturePad;

            Canvas captureCanvas = new Canvas(mBackdrop);
            captureCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            drawWallpaperUnderlay(root, captureCanvas, captureLeft, captureTop);

            sCapturingBackdrop.set(true);
            try {
                captureCanvas.translate(-captureLeft, -captureTop);
                root.draw(captureCanvas);
            } finally {
                sCapturingBackdrop.set(false);
            }
            return true;
        } catch (OutOfMemoryError oom) {
            recycleBackdrop();
            return false;
        } catch (ThreadDeath death) {
            throw death;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isHostSafeForCapture(Rect bounds) {
        if (mHost == null || bounds == null || bounds.isEmpty()) {
            return false;
        }
        if (!mHost.isAttachedToWindow() || mHost.getWindowToken() == null) {
            return false;
        }
        if (isInsideLauncherPreview()) {
            return false;
        }
        View root = mHost.getRootView();
        return root != null && root.getWidth() > 0 && root.getHeight() > 0;
    }

    private boolean isInsideLauncherPreview() {
        View view = mHost;
        for (int depth = 0; view != null && depth < 16; depth++) {
            if (LAUNCHER_PREVIEW_VIEW.equals(view.getClass().getName())) {
                return true;
            }
            ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private void drawWallpaperUnderlay(View root, Canvas canvas, int captureLeft, int captureTop) {
        try {
            if (mWallpaper == null) {
                mWallpaper = WallpaperManager.getInstance(mHost.getContext()).getDrawable();
            }
            if (mWallpaper == null) {
                return;
            }

            int save = canvas.save();
            canvas.translate(-captureLeft, -captureTop);
            mWallpaper.setBounds(0, 0, root.getWidth(), root.getHeight());
            mWallpaper.draw(canvas);
            canvas.restoreToCount(save);
        } catch (ThreadDeath death) {
            throw death;
        } catch (Throwable ignored) {
            // Live wallpapers or restricted wallpaper access simply fall back to the root capture.
        }
    }

    private void drawCapturedFallback(Canvas canvas, Rect bounds) {
        try {
            mRect.set(bounds);
            mClipPath.reset();
            mClipPath.addRoundRect(mRect, mCornerRadius, mCornerRadius, Path.Direction.CW);

            int save = canvas.save();
            canvas.clipPath(mClipPath);

            if (mBackdrop != null && !mBackdrop.isRecycled()) {
                mSrc.set(mCapturePad, mCapturePad,
                        Math.min(mBackdrop.getWidth(), mCapturePad + bounds.width()),
                        Math.min(mBackdrop.getHeight(), mCapturePad + bounds.height()));
                mDst.set(bounds);
                mDst.inset(-mRefractionPx * 0.10f, -mRefractionPx * 0.10f);
                canvas.drawBitmap(mBackdrop, mSrc, mDst, mFallbackPaint);
            }

            drawSoberOverlays(canvas, bounds);
            canvas.restoreToCount(save);
        } catch (ThreadDeath death) {
            throw death;
        } catch (Throwable ignored) {
            drawStaticFallback(canvas, bounds);
        }
    }

    private void drawStaticFallback(Canvas canvas, Rect bounds) {
        if (bounds == null || bounds.isEmpty()) {
            return;
        }
        mRect.set(bounds);
        drawSoberOverlays(canvas, bounds);
    }

    private void drawSoberOverlays(Canvas canvas, Rect bounds) {
        mRect.set(bounds);
        float tintAlpha = 0.09f + 0.08f * mStrength;
        int fillAlpha = Math.round(Math.max(72, Color.alpha(mColor)) * tintAlpha);

        mOverlayPaint.setShader(null);
        mOverlayPaint.setColor(applyAlpha(mColor, fillAlpha));
        mOverlayPaint.setStyle(Paint.Style.FILL);
        mOverlayPaint.setColorFilter(mColorFilter);
        canvas.drawRoundRect(mRect, mCornerRadius, mCornerRadius, mOverlayPaint);

        mOverlayPaint.setColorFilter(null);
        mOverlayPaint.setStyle(Paint.Style.STROKE);
        mOverlayPaint.setStrokeWidth(Math.max(1f, mDepthPx * 0.08f));
        mOverlayPaint.setColor(Color.argb(Math.round(34f + 34f * mStrength), 255, 255, 255));
        canvas.drawRoundRect(mRect, mCornerRadius, mCornerRadius, mOverlayPaint);

        float highlightHeight = Math.max(1f, bounds.height() * 0.08f);
        RectF highlight = new RectF(bounds.left + 2f, bounds.top + 2f,
                bounds.right - 2f, bounds.top + highlightHeight);
        mOverlayPaint.setStyle(Paint.Style.FILL);
        mOverlayPaint.setColor(Color.argb(Math.round(10f + 22f * mStrength), 255, 255, 255));
        canvas.drawRoundRect(highlight, Math.min(mCornerRadius, highlightHeight),
                Math.min(mCornerRadius, highlightHeight), mOverlayPaint);

        mOverlayPaint.setColorFilter(mColorFilter);
    }

    private static int applyAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private void recycleBackdrop() {
        if (mBackdrop != null) {
            try {
                mBackdrop.recycle();
            } catch (RuntimeException ignored) {
                // Ignore recycle races; the next draw will use the static fallback.
            }
            mBackdrop = null;
            mBackdropShader = null;
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        recycleBackdrop();
        invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
        mAlpha = Math.max(0, Math.min(255, alpha));
        mShaderPaint.setAlpha(mAlpha);
        mFallbackPaint.setAlpha(mAlpha);
        mOverlayPaint.setAlpha(mAlpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mColorFilter = colorFilter;
        mShaderPaint.setColorFilter(colorFilter);
        mFallbackPaint.setColorFilter(colorFilter);
        mOverlayPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    private static final class Api33Impl {
        private Api33Impl() { }

        static void drawShader(OneUiCrystalRenderer renderer, Canvas canvas, Rect bounds) {
            if (renderer.mBackdropShader == null) {
                throw new IllegalStateException("Crystal backdrop shader is missing");
            }
            android.graphics.RuntimeShader shader =
                    new android.graphics.RuntimeShader(CRYSTAL_SHADER);
            shader.setInputShader("backdrop", renderer.mBackdropShader);
            shader.setFloatUniform("size", bounds.width(), bounds.height());
            shader.setFloatUniform("origin", bounds.left, bounds.top);
            shader.setFloatUniform("capturePad", renderer.mCapturePad);
            shader.setFloatUniform("radius", renderer.mCornerRadius);
            shader.setFloatUniform("blurPx", renderer.mBlurPx);
            shader.setFloatUniform("refractionPx", renderer.mRefractionPx);
            shader.setFloatUniform("depth", renderer.mDepthPx);
            shader.setFloatUniform("specular", renderer.mSpecular);

            float tintAlpha = (0.05f + 0.13f * renderer.mStrength) * Color.alpha(renderer.mColor) / 255f;
            shader.setFloatUniform("tint",
                    Color.red(renderer.mColor) / 255f,
                    Color.green(renderer.mColor) / 255f,
                    Color.blue(renderer.mColor) / 255f,
                    tintAlpha);

            renderer.mShaderPaint.setShader(shader);
            renderer.mShaderPaint.setAlpha(renderer.mAlpha);
            renderer.mRect.set(bounds);
            canvas.drawRoundRect(renderer.mRect, renderer.mCornerRadius,
                    renderer.mCornerRadius, renderer.mShaderPaint);
            renderer.mShaderPaint.setShader(null);
        }
    }
}

/*
 * Copyright (C) 2026 The Lawnchair Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.launcher3.graphics;

import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.graphics.OneUiWallpaperBackdropRepository.Mapping;
import com.android.launcher3.graphics.WallpaperBackdropTransform.Input;
import com.android.launcher3.graphics.WallpaperBackdropTransform.Result;
import com.android.launcher3.views.ActivityContext;

/** Draws Crystal exclusively from immutable, launcher-scoped wallpaper snapshots. */
final class OneUiCrystalRenderer extends Drawable {
    private static final int API_RUNTIME_SHADER = 33;

    private static final String CRYSTAL_SHADER =
            "uniform shader backdrop;\n"
                    + "uniform float2 size;\n"
                    + "uniform float2 origin;\n"
                    + "uniform float radius;\n"
                    + "uniform float blurPx;\n"
                    + "uniform float refractionPx;\n"
                    + "uniform float edgeBandPx;\n"
                    + "uniform float dispersionPx;\n"
                    + "uniform float rimAlpha;\n"
                    + "uniform float4 tint;\n"
                    + "float sdRoundRect(float2 p, float2 halfSize, float r) {\n"
                    + "  float2 q = abs(p) - halfSize + r;\n"
                    + "  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n"
                    + "}\n"
                    + "half4 sampleBlur(float2 p, float r) {\n"
                    + "  float2 dx = float2(max(0.75, r), 0.0);\n"
                    + "  float2 dy = float2(0.0, max(0.75, r));\n"
                    + "  half4 c = backdrop.eval(p) * 0.36;\n"
                    + "  c += backdrop.eval(p + dx) * 0.16;\n"
                    + "  c += backdrop.eval(p - dx) * 0.16;\n"
                    + "  c += backdrop.eval(p + dy) * 0.16;\n"
                    + "  c += backdrop.eval(p - dy) * 0.16;\n"
                    + "  return c;\n"
                    + "}\n"
                    + "half4 main(float2 coord) {\n"
                    + "  float2 local = coord - origin;\n"
                    + "  float2 halfSize = size * 0.5;\n"
                    + "  float2 p = local - halfSize;\n"
                    + "  float corner = min(radius, min(halfSize.x, halfSize.y));\n"
                    + "  float sd = sdRoundRect(p, halfSize, corner);\n"
                    + "  float edge = 1.0 - smoothstep(0.0, edgeBandPx, max(0.0, -sd));\n"
                    + "  float2 normal = normalize(p / max(halfSize, float2(1.0)) + float2(0.0001));\n"
                    + "  float displacement = refractionPx * (0.20 + 0.80 * edge);\n"
                    + "  float2 warped = coord + normal * displacement;\n"
                    + "  half4 center = sampleBlur(warped, blurPx);\n"
                    + "  half4 red = sampleBlur(warped + normal * dispersionPx, blurPx);\n"
                    + "  half4 blue = sampleBlur(warped - normal * dispersionPx, blurPx);\n"
                    + "  half4 glass = mix(center, half4(red.r, center.g, blue.b, center.a), edge * 0.42);\n"
                    + "  glass = mix(glass, half4(tint.rgb, glass.a), tint.a);\n"
                    + "  float light = max(0.0, dot(-normal, normalize(float2(-0.55, -0.84))));\n"
                    + "  float contour = (1.0 - smoothstep(0.0, 1.35, abs(sd))) * edge;\n"
                    + "  glass.rgb += half3(contour * light * rimAlpha);\n"
                    + "  glass.rgb -= half3(contour * (1.0 - light) * 0.035);\n"
                    + "  glass.a = 1.0;\n"
                    + "  return glass;\n"
                    + "}";

    private final View mHost;
    private final OneUiCrystalSurfaceRole mRole;
    private final int mColor;
    private final float mCornerRadius;
    private final OneUiCrystalOptics.Values mOptics;
    private final Paint mShaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mFallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mClipPath = new Path();
    private final Matrix mBitmapMatrix = new Matrix();
    private final RectF mSurfaceRect = new RectF();
    private final RectF mSampleRect = new RectF();
    private final RectF mExpandedRect = new RectF();
    private final int[] mHostLocation = new int[2];
    private final Runnable mRepositoryListener = this::invalidateSelf;

    @Nullable private final OneUiWallpaperBackdropRepository mExplicitRepository;
    @Nullable private OneUiWallpaperBackdropRepository mObservedRepository;
    @Nullable private WallpaperSnapshotStore.SnapshotPin<WallpaperBackdropSnapshot> mAnimationPin;
    @Nullable private BitmapShader mBitmapShader;
    @Nullable private Api33State mApi33State;
    private long mBoundGeneration = Long.MIN_VALUE;
    private int mBoundWidth = -1;
    private int mBoundHeight = -1;
    private int mLastScreenLeft = Integer.MIN_VALUE;
    private int mLastScreenTop = Integer.MIN_VALUE;
    private float mLastHorizontalOffset = Float.NaN;
    private float mLastVerticalOffset = Float.NaN;
    private int mShaderBuildCount;
    private int mBitmapShaderBuildCount;
    private int mAlpha = 255;
    private @Nullable ColorFilter mColorFilter;

    static Drawable create(View host, OneUiCrystalSurfaceRole role, int color,
            float cornerRadius, int intensityPercent) {
        return new OneUiCrystalRenderer(host, role, color, cornerRadius, intensityPercent, null);
    }

    static OneUiCrystalRenderer createForTesting(View host, OneUiCrystalSurfaceRole role, int color,
            float cornerRadius, int intensityPercent,
            OneUiWallpaperBackdropRepository repository) {
        return new OneUiCrystalRenderer(
                host, role, color, cornerRadius, intensityPercent, repository);
    }

    private OneUiCrystalRenderer(View host, OneUiCrystalSurfaceRole role, int color,
            float cornerRadius, int intensityPercent,
            @Nullable OneUiWallpaperBackdropRepository repository) {
        mHost = host;
        mRole = role;
        mColor = color;
        mCornerRadius = cornerRadius;
        mExplicitRepository = repository;
        float density = Math.max(0.1f, host.getResources().getDisplayMetrics().density);
        mOptics = OneUiCrystalOptics.forIntensity(intensityPercent, density);
        mFallbackPaint.setColor(coldFallbackColor(color));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        try {
            OneUiWallpaperBackdropRepository repository = resolveRepository();
            WallpaperBackdropSnapshot snapshot = mAnimationPin != null
                    ? mAnimationPin.value()
                    : repository == null ? null : repository.currentSnapshot();
            if (snapshot == null || snapshot.bitmap().isRecycled()
                    || !bindSnapshotAndGeometry(repository, snapshot, bounds)) {
                drawFallback(canvas, bounds);
                return;
            }
            if (Build.VERSION.SDK_INT >= API_RUNTIME_SHADER && mApi33State != null) {
                mApi33State.draw(this, canvas, bounds);
            } else {
                drawBitmapFallback(canvas, bounds);
            }
        } catch (ThreadDeath death) {
            throw death;
        } catch (Throwable unavailable) {
            drawFallback(canvas, bounds);
        }
    }

    private boolean bindSnapshotAndGeometry(
            OneUiWallpaperBackdropRepository repository,
            WallpaperBackdropSnapshot snapshot,
            Rect bounds) {
        if (mBoundGeneration != snapshot.wallpaperGeneration() || mBitmapShader == null) {
            mBitmapShader = new BitmapShader(
                    snapshot.bitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            mBitmapShaderBuildCount++;
            mApi33State = Build.VERSION.SDK_INT >= API_RUNTIME_SHADER
                    ? Api33State.create(mBitmapShader) : null;
            if (mApi33State != null) mShaderBuildCount++;
            mBoundGeneration = snapshot.wallpaperGeneration();
            resetGeometryKey();
        }

        Mapping mapping = repository.currentMapping();
        mHost.getLocationOnScreen(mHostLocation);
        int screenLeft = mHostLocation[0] + bounds.left;
        int screenTop = mHostLocation[1] + bounds.top;
        boolean geometryChanged = mBoundWidth != bounds.width()
                || mBoundHeight != bounds.height()
                || mLastScreenLeft != screenLeft
                || mLastScreenTop != screenTop
                || Float.compare(mLastHorizontalOffset, mapping.horizontalOffset()) != 0
                || Float.compare(mLastVerticalOffset, mapping.verticalOffset()) != 0;
        if (!geometryChanged) return true;

        float opticalPadding = mOptics.refractionPx() * mRole.refractionScale()
                + mOptics.blurRadiusPx();
        Result sample = WallpaperBackdropTransform.map(new Input(
                snapshot.sourceWidth(), snapshot.sourceHeight(),
                snapshot.decodedWidth(), snapshot.decodedHeight(),
                mapping.displayWidth(), mapping.displayHeight(),
                screenLeft, screenTop, bounds.width(), bounds.height(),
                mapping.horizontalOffset(), mapping.verticalOffset(), opticalPadding,
                mHost.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL));
        if (!sample.isValid()) return false;

        mSampleRect.set(sample.left(), sample.top(), sample.right(), sample.bottom());
        mExpandedRect.set(bounds);
        mExpandedRect.inset(-opticalPadding, -opticalPadding);
        mBitmapMatrix.setRectToRect(mSampleRect, mExpandedRect, Matrix.ScaleToFit.FILL);
        mBitmapShader.setLocalMatrix(mBitmapMatrix);
        mBoundWidth = bounds.width();
        mBoundHeight = bounds.height();
        mLastScreenLeft = screenLeft;
        mLastScreenTop = screenTop;
        mLastHorizontalOffset = mapping.horizontalOffset();
        mLastVerticalOffset = mapping.verticalOffset();
        return true;
    }

    @Nullable
    private OneUiWallpaperBackdropRepository resolveRepository() {
        OneUiWallpaperBackdropRepository repository = mExplicitRepository;
        if (repository == null) {
            ActivityContext context = ActivityContext.lookupContextNoThrow(mHost.getContext());
            repository = context instanceof Launcher
                    ? ((Launcher) context).getWallpaperBackdropRepository() : null;
        }
        if (repository != mObservedRepository) {
            releaseAnimationPin();
            if (mObservedRepository != null) {
                mObservedRepository.removeListener(mRepositoryListener);
            }
            mObservedRepository = repository;
            if (repository != null) repository.addListener(mRepositoryListener);
            mBoundGeneration = Long.MIN_VALUE;
        }
        return repository;
    }

    void setAnimationRunning(boolean running) {
        if (running) {
            if (mAnimationPin != null) return;
            OneUiWallpaperBackdropRepository repository = resolveRepository();
            if (repository != null) mAnimationPin = repository.pinCurrentSnapshot();
        } else {
            releaseAnimationPin();
        }
        invalidateSelf();
    }

    private void releaseAnimationPin() {
        if (mAnimationPin == null) return;
        mAnimationPin.close();
        mAnimationPin = null;
    }

    private void drawBitmapFallback(Canvas canvas, Rect bounds) {
        if (mBitmapShader == null) {
            drawFallback(canvas, bounds);
            return;
        }
        mSurfaceRect.set(bounds);
        mClipPath.reset();
        mClipPath.addRoundRect(mSurfaceRect, mCornerRadius, mCornerRadius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(mClipPath);
        mShaderPaint.setShader(mBitmapShader);
        canvas.drawRoundRect(mSurfaceRect, mCornerRadius, mCornerRadius, mShaderPaint);
        drawContour(canvas, bounds);
        canvas.restoreToCount(save);
    }

    private void drawFallback(Canvas canvas, Rect bounds) {
        mSurfaceRect.set(bounds);
        canvas.drawRoundRect(mSurfaceRect, mCornerRadius, mCornerRadius, mFallbackPaint);
        drawContour(canvas, bounds);
    }

    private void drawContour(Canvas canvas, Rect bounds) {
        mSurfaceRect.set(bounds);
        mOverlayPaint.setShader(null);
        mOverlayPaint.setStyle(Paint.Style.STROKE);
        mOverlayPaint.setStrokeWidth(Math.max(1f, mOptics.edgeBandPx() * 0.12f));
        mOverlayPaint.setColor(Color.argb(
                Math.round(255f * mOptics.rimAlpha()), 238, 247, 255));
        canvas.drawRoundRect(mSurfaceRect, mCornerRadius, mCornerRadius, mOverlayPaint);
    }

    private static int coldFallbackColor(int source) {
        int alpha = Math.max(54, Math.min(104, Color.alpha(source)));
        return Color.argb(alpha,
                Math.max(188, Color.red(source)),
                Math.max(204, Color.green(source)),
                Math.max(220, Color.blue(source)));
    }

    private void resetGeometryKey() {
        mBoundWidth = -1;
        mBoundHeight = -1;
        mLastScreenLeft = Integer.MIN_VALUE;
        mLastScreenTop = Integer.MIN_VALUE;
        mLastHorizontalOffset = Float.NaN;
        mLastVerticalOffset = Float.NaN;
    }

    int getShaderBuildCountForTesting() { return mShaderBuildCount; }
    int getBitmapShaderBuildCountForTesting() { return mBitmapShaderBuildCount; }
    boolean hasPinnedSnapshotForTesting() { return mAnimationPin != null; }
    OneUiCrystalSurfaceRole surfaceRole() { return mRole; }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        if (!visible) releaseAnimationPin();
        return super.setVisible(visible, restart);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        resetGeometryKey();
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
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    /** Keeps all API-33 verifier-visible shader state out of the base draw path. */
    private static final class Api33State {
        private final android.graphics.RuntimeShader mShader;

        private Api33State(android.graphics.RuntimeShader shader) {
            mShader = shader;
        }

        static Api33State create(BitmapShader backdrop) {
            android.graphics.RuntimeShader shader =
                    new android.graphics.RuntimeShader(CRYSTAL_SHADER);
            shader.setInputShader("backdrop", backdrop);
            return new Api33State(shader);
        }

        void draw(OneUiCrystalRenderer renderer, Canvas canvas, Rect bounds) {
            float roleScale = renderer.mRole.refractionScale();
            mShader.setFloatUniform("size", bounds.width(), bounds.height());
            mShader.setFloatUniform("origin", bounds.left, bounds.top);
            mShader.setFloatUniform("radius", renderer.mCornerRadius);
            mShader.setFloatUniform("blurPx", renderer.mOptics.blurRadiusPx());
            mShader.setFloatUniform("refractionPx", renderer.mOptics.refractionPx() * roleScale);
            mShader.setFloatUniform("edgeBandPx", renderer.mOptics.edgeBandPx());
            mShader.setFloatUniform("dispersionPx", renderer.mOptics.dispersionPx() * roleScale);
            mShader.setFloatUniform("rimAlpha", renderer.mOptics.rimAlpha());
            mShader.setFloatUniform("tint",
                    Math.max(0.82f, Color.red(renderer.mColor) / 255f),
                    Math.max(0.88f, Color.green(renderer.mColor) / 255f),
                    Math.max(0.94f, Color.blue(renderer.mColor) / 255f),
                    renderer.mOptics.tintAlpha());
            renderer.mShaderPaint.setShader(mShader);
            renderer.mShaderPaint.setAlpha(renderer.mAlpha);
            renderer.mSurfaceRect.set(bounds);
            canvas.drawRoundRect(renderer.mSurfaceRect, renderer.mCornerRadius,
                    renderer.mCornerRadius, renderer.mShaderPaint);
        }
    }
}

/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */
package com.android.launcher3.graphics;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.lawnchair.oneui.OneUiGlassPreferences;
import app.lawnchair.oneui.OneUiGlassStyle;

/**
 * Shared bounded glass renderer for One UI-inspired dock and folder backgrounds.
 *
 * <p>Liquid Glass uses a refractive lens, Crystal stays clearer and sharper, and Frosty adds a
 * broad GPU blur with diffusion and an edge highlight. Each material has its own intensity.</p>
 */
public final class OneUiGlassBackground {
    private OneUiGlassBackground() { }

    /** Dock entry point. The selected material is explicit and shared with settings. */
    public static Drawable createDock(View host, int style, int color, float cornerRadius) {
        return new DynamicGlassDrawable(host,
                OneUiGlassStyle.normalize(style, OneUiGlassStyle.LIQUID_GLASS),
                color, cornerRadius, true, true);
    }

    public static Drawable createDockOverlay(View host, int style, int color, float cornerRadius) {
        return new DynamicGlassDrawable(host,
                OneUiGlassStyle.normalize(style, OneUiGlassStyle.LIQUID_GLASS),
                color, cornerRadius, true, false);
    }

    /** Folder entry point; style and intensity are resolved live from shared preferences. */
    public static Drawable createFolder(View host, int color, float cornerRadius) {
        return new DynamicGlassDrawable(host, OneUiGlassStyle.LIQUID_GLASS, color, cornerRadius,
                false, true);
    }

    public static Drawable createFolderOverlay(View host, int color, float cornerRadius) {
        return new DynamicGlassDrawable(host, OneUiGlassStyle.LIQUID_GLASS, color, cornerRadius,
                false, false);
    }

    /** Foreground diffusion layer for Frosty surfaces using WallpaperBackdropView's GPU blur. */
    public static Drawable createFrostyBackdropOverlay(View host, int color,
            float cornerRadius, int intensityPercent) {
        boolean dark = (host.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        OneUiGlassProfile profile = OneUiGlassProfile.create(
                OneUiGlassStyle.FROSTY, intensityPercent, dark);
        int neutralTint = OneUiGlassProfile.neutralizeTint(color);
        int tintRgb = blendRgb(neutralTint, dark ? Color.BLACK : Color.WHITE, profile.tintBlend);
        GradientDrawable tint = rounded(cornerRadius, Color.argb(
                resolveTintAlpha(Color.alpha(color), profile, true),
                Color.red(tintRgb), Color.green(tintRgb), Color.blue(tintRgb)));

        int hazeTarget = dark ? Color.rgb(42, 46, 54) : Color.WHITE;
        int hazeRgb = blendRgb(neutralTint, hazeTarget, dark ? 0.10f : 0.22f);
        GradientDrawable haze = rounded(cornerRadius,
                alphaColor(hazeRgb, profile.hazeAlpha));
        float density = host.getResources().getDisplayMetrics().density;
        haze.setStroke(Math.max(1, Math.round(density)),
                alphaColor(Color.WHITE, profile.highlightAlpha));
        return new LayerDrawable(new Drawable[] {tint, haze});
    }

    private static Drawable buildSurface(View host, int style, int color,
            float cornerRadius, int intensityPercent, boolean allowPlatformBlur) {
        final int intensity = clamp(intensityPercent, 0, 100);
        if (style < OneUiGlassStyle.LIQUID_GLASS) {
            return new ColorDrawable(Color.TRANSPARENT);
        }
        if (style == OneUiGlassStyle.CRYSTAL || style == OneUiGlassStyle.LIQUID_GLASS) {
            return OneUiCrystalRenderer.create(host, style, color, cornerRadius, intensity);
        }
        if (intensity == 0) {
            return new ColorDrawable(Color.TRANSPARENT);
        }

        final float density = host.getResources().getDisplayMetrics().density;
        final float strength = intensity / 100f;
        final boolean dark = (host.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        final OneUiGlassProfile profile = OneUiGlassProfile.create(style, intensity, dark);
        final boolean platformBlurEnabled = canUsePlatformBlur(host);
        Drawable blur = allowPlatformBlur && platformBlurEnabled ? createPlatformBlur(host,
                Math.max(1, Math.round(profile.radiusDp * density)), cornerRadius) : null;
        if (!platformBlurEnabled && Build.VERSION.SDK_INT >= 33) {
            // Some devices disable window blur globally (the S25 currently reports mBlurEnabled=false).
            // Use a bounded wallpaper-only GPU blur so our own surfaces still have a real backdrop.
            float radiusScale = style == OneUiGlassStyle.FROSTY ? 0.46f : 0.34f;
            blur = OneUiBackdropBlurDrawable.create(host, style, cornerRadius,
                    Math.max(1, Math.round(profile.radiusDp * density * radiusScale)));
        }

        final int sourceAlpha = Color.alpha(color);
        final int tintRgb = blendRgb(color, dark ? Color.BLACK : Color.WHITE, profile.tintBlend);

        int tintAlpha = resolveTintAlpha(sourceAlpha, profile, blur != null);
        GradientDrawable tint = rounded(cornerRadius, Color.argb(
                tintAlpha, Color.red(tintRgb), Color.green(tintRgb), Color.blue(tintRgb)));

        Drawable base = blur == null
                ? tint
                : new LayerDrawable(new Drawable[] {blur, tint});

        if (style == OneUiGlassStyle.CRYSTAL) {
            GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {
                            alphaColor(Color.WHITE, Math.round(20f + 34f * strength)),
                            alphaColor(Color.WHITE, Math.round(5f + 9f * strength)),
                            Color.TRANSPARENT,
                            alphaColor(Color.BLACK, Math.round(5f + 9f * strength)),
                    });
            sheen.setCornerRadius(cornerRadius);
            sheen.setStroke(Math.max(1, Math.round(1.2f * density)),
                    alphaColor(Color.WHITE, Math.round(42f + 48f * strength)));

            GradientDrawable specular = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[] {
                            alphaColor(Color.WHITE, Math.round(28f + 40f * strength)),
                            Color.TRANSPARENT,
                            Color.TRANSPARENT,
                            alphaColor(Color.WHITE, Math.round(5f + 10f * strength)),
                    });
            specular.setCornerRadius(cornerRadius);
            return new LayerDrawable(new Drawable[] {base, sheen, specular});
        }

        if (style == OneUiGlassStyle.FROSTY) {
            int hazeColor = blendRgb(color, dark ? Color.rgb(42, 46, 54) : Color.WHITE,
                    dark ? 0.10f : 0.22f);
            GradientDrawable haze = rounded(cornerRadius,
                    alphaColor(hazeColor, profile.hazeAlpha));
            haze.setStroke(Math.max(1, Math.round(density)),
                    alphaColor(Color.WHITE, profile.highlightAlpha));
            return new LayerDrawable(new Drawable[] {base, haze});
        }
        return base;
    }

    static int resolveTintAlpha(int sourceAlpha, OneUiGlassProfile profile,
            boolean hasBackdropBlur) {
        int tintAlpha = Math.round(sourceAlpha * profile.tintAlphaScale);
        if (!hasBackdropBlur) tintAlpha = Math.max(tintAlpha, profile.fallbackAlpha);
        return clamp(tintAlpha, 0, 255);
    }

    private static GradientDrawable rounded(float cornerRadius, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(cornerRadius);
        drawable.setColor(color);
        return drawable;
    }

    private static int alphaColor(int rgb, int alpha) {
        return Color.argb(clamp(alpha, 0, 255), Color.red(rgb), Color.green(rgb), Color.blue(rgb));
    }

    private static int blendRgb(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean canUsePlatformBlur(View host) {
        if (Build.VERSION.SDK_INT < 31 || host == null || !host.isAttachedToWindow()
                || !host.isHardwareAccelerated()) return false;
        try {
            WindowManager manager = host.getContext().getSystemService(WindowManager.class);
            return manager != null && manager.isCrossWindowBlurEnabled();
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    @Nullable
    private static Drawable createPlatformBlur(View host, int radius, float corners) {
        if (!canUsePlatformBlur(host)) return null;
        Drawable drawable = null;
        try {
            Object root = View.class.getMethod("getViewRootImpl").invoke(host);
            if (root == null) return null;
            drawable = (Drawable) root.getClass().getMethod("createBackgroundBlurDrawable")
                    .invoke(root);
            drawable.getClass().getMethod("setBlurRadius", int.class).invoke(drawable, radius);
            drawable.getClass().getMethod("setCornerRadius", float.class).invoke(drawable, corners);
            return drawable;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            if (drawable != null) drawable.setVisible(false, false);
            return null;
        }
    }

    /** Drawable that re-resolves One UI preferences without rebuilding Hotseat/Folder logic. */
    private static final class DynamicGlassDrawable extends Drawable {
        private final View mHost;
        private final int mBaseStyle;
        private final int mColor;
        private final float mCornerRadius;
        private final boolean mDockControlled;
        private final boolean mAllowPlatformBlur;

        private Drawable mDelegate;
        private int mLastStyle = Integer.MIN_VALUE;
        private int mLastIntensity = Integer.MIN_VALUE;
        private int mLastNightMode = Integer.MIN_VALUE;
        private boolean mLastBlurAvailable;
        private int mAlpha = 255;
        private @Nullable ColorFilter mColorFilter;

        DynamicGlassDrawable(View host, int baseStyle, int color, float cornerRadius,
                boolean dockControlled, boolean allowPlatformBlur) {
            mHost = host;
            mBaseStyle = baseStyle;
            mColor = color;
            mCornerRadius = cornerRadius;
            mDockControlled = dockControlled;
            mAllowPlatformBlur = allowPlatformBlur;
        }

        private int resolveStyle() {
            return mDockControlled
                    ? OneUiGlassStyle.normalize(mBaseStyle, OneUiGlassStyle.LIQUID_GLASS)
                    : OneUiGlassPreferences.getFolderMode(mHost.getContext());
        }

        private int resolveIntensity(int style) {
            return mDockControlled
                    ? OneUiGlassPreferences.getDockIntensity(mHost.getContext(), style)
                    : OneUiGlassPreferences.getFolderIntensity(mHost.getContext(), style);
        }

        private void ensureDelegate() {
            int style = resolveStyle();
            int intensity = resolveIntensity(style);
            int nightMode = mHost.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            boolean blurAvailable = mAllowPlatformBlur && canUsePlatformBlur(mHost);
            if (mDelegate != null && style == mLastStyle && intensity == mLastIntensity
                    && nightMode == mLastNightMode && blurAvailable == mLastBlurAvailable) return;

            if (mDelegate != null) mDelegate.setVisible(false, false);
            mDelegate = buildSurface(mHost, style, mColor, mCornerRadius, intensity,
                    mAllowPlatformBlur);
            mLastStyle = style;
            mLastIntensity = intensity;
            mLastNightMode = nightMode;
            mLastBlurAvailable = blurAvailable;
            mDelegate.setBounds(getBounds());
            mDelegate.setAlpha(mAlpha);
            mDelegate.setColorFilter(mColorFilter);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            ensureDelegate();
            mDelegate.draw(canvas);
        }

        @Override
        protected void onBoundsChange(android.graphics.Rect bounds) {
            if (mDelegate != null) mDelegate.setBounds(bounds);
        }

        @Override
        public void setAlpha(int alpha) {
            mAlpha = alpha;
            if (mDelegate != null) mDelegate.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            mColorFilter = colorFilter;
            if (mDelegate != null) mDelegate.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}

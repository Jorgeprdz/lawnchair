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
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.lawnchair.oneui.OneUiGlassPreferences;

/**
 * Bounded glass surface for OneUI-inspired dock/folder backgrounds.
 *
 * <p>Uses the platform background-blur drawable when the current OEM/window allows it. When
 * unavailable, Blur/Crystal/Frosty remain honest translucent glass fallbacks; Frosty adds a
 * stronger milky haze and edge sheen so it still reads like One UI rather than plain alpha.</p>
 */
public final class DockGlassBackground {
    public static final int STYLE_BLUR = 2;
    public static final int STYLE_CRYSTAL = 3;
    public static final int STYLE_FROSTY = 4;

    private DockGlassBackground() { }

    /** Existing dock entry point. Crystal can be promoted dynamically to Frosty by preferences. */
    public static Drawable create(View host, boolean crystal, int color, float cornerRadius) {
        return new DynamicGlassDrawable(host, crystal ? STYLE_CRYSTAL : STYLE_BLUR,
                color, cornerRadius, true);
    }

    /** Folder entry point. Folder style and intensity are resolved dynamically from preferences. */
    public static Drawable createFolder(View host, int color, float cornerRadius) {
        return new DynamicGlassDrawable(host, STYLE_BLUR, color, cornerRadius, false);
    }

    private static Drawable buildSurface(View host, int style, int color, float cornerRadius,
            int intensityPercent) {
        if (style < STYLE_BLUR) {
            GradientDrawable clear = rounded(cornerRadius, Color.TRANSPARENT);
            return clear;
        }

        final float density = host.getResources().getDisplayMetrics().density;
        final float strength = clamp(intensityPercent, 0, 100) / 100f;
        final int blurDp;
        if (style == STYLE_CRYSTAL) {
            blurDp = Math.round(8 + 20 * strength);
        } else if (style == STYLE_FROSTY) {
            blurDp = Math.round(18 + 42 * strength);
        } else {
            blurDp = Math.round(10 + 42 * strength);
        }

        Drawable blur = createPlatformBlur(host, Math.max(1, Math.round(blurDp * density)),
                cornerRadius);

        final int sourceAlpha = Color.alpha(color);
        final boolean dark = (host.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        int tintRgb = color;
        float tintScale;
        int fallbackFloor;
        if (style == STYLE_CRYSTAL) {
            tintScale = 0.10f + 0.18f * strength;
            fallbackFloor = Math.round(28 + 24 * strength);
        } else if (style == STYLE_FROSTY) {
            tintRgb = blendRgb(color, dark ? Color.BLACK : Color.WHITE,
                    dark ? 0.16f : 0.30f);
            tintScale = 0.42f + 0.28f * strength;
            fallbackFloor = Math.round(104 + 54 * strength);
        } else {
            tintScale = 0.28f + 0.30f * strength;
            fallbackFloor = Math.round(70 + 46 * strength);
        }

        int tintAlpha = Math.round(sourceAlpha * tintScale);
        if (blur == null) tintAlpha = Math.max(tintAlpha, fallbackFloor);
        tintAlpha = clamp(tintAlpha, 0, 255);

        GradientDrawable tint = rounded(cornerRadius, Color.argb(
                tintAlpha, Color.red(tintRgb), Color.green(tintRgb), Color.blue(tintRgb)));
        Drawable surface = blur == null ? tint : new LayerDrawable(new Drawable[]{blur, tint});

        if (style == STYLE_BLUR) return surface;

        int top = style == STYLE_FROSTY
                ? alphaColor(Color.WHITE, Math.round(24 + 30 * strength))
                : alphaColor(Color.WHITE, Math.round(10 + 14 * strength));
        int middle = style == STYLE_FROSTY
                ? alphaColor(Color.WHITE, Math.round(7 + 12 * strength))
                : Color.TRANSPARENT;
        int bottom = alphaColor(Color.BLACK, style == STYLE_FROSTY
                ? Math.round(8 + 14 * strength)
                : Math.round(5 + 8 * strength));
        GradientDrawable haze = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, middle, bottom});
        haze.setCornerRadius(cornerRadius);
        haze.setStroke(Math.max(1, Math.round(density)), alphaColor(Color.WHITE,
                style == STYLE_FROSTY
                        ? Math.round(34 + 34 * strength)
                        : Math.round(24 + 20 * strength)));

        if (style == STYLE_CRYSTAL) {
            return new LayerDrawable(new Drawable[]{surface, haze});
        }

        // One UI-style frosted bloom: a diagonal, very low-alpha highlight over the milky haze.
        GradientDrawable bloom = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{
                        alphaColor(Color.WHITE, Math.round(24 + 30 * strength)),
                        Color.TRANSPARENT,
                        alphaColor(dark ? Color.BLACK : Color.WHITE,
                                Math.round(7 + 12 * strength)),
                });
        bloom.setCornerRadius(cornerRadius);
        return new LayerDrawable(new Drawable[]{surface, haze, bloom});
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
        if (Build.VERSION.SDK_INT < 31 || !host.isAttachedToWindow()
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
            // This platform API is not available to every build/OEM. A rejected lookup uses
            // the translucent fallback; no hidden-API exemptions or additional host are added.
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

    /** Drawable that re-resolves standalone OneUI prefs without rebuilding Hotseat/Folder logic. */
    private static final class DynamicGlassDrawable extends Drawable {
        private final View mHost;
        private final int mBaseStyle;
        private final int mColor;
        private final float mCornerRadius;
        private final boolean mDockControlled;

        private Drawable mDelegate;
        private int mLastStyle = Integer.MIN_VALUE;
        private int mLastIntensity = Integer.MIN_VALUE;
        private boolean mLastBlurAvailable;
        private int mAlpha = 255;
        private @Nullable ColorFilter mColorFilter;

        DynamicGlassDrawable(View host, int baseStyle, int color, float cornerRadius,
                boolean dockControlled) {
            mHost = host;
            mBaseStyle = baseStyle;
            mColor = color;
            mCornerRadius = cornerRadius;
            mDockControlled = dockControlled;
        }

        private int resolveStyle() {
            if (!mDockControlled) {
                return OneUiGlassPreferences.getFolderMode(mHost.getContext());
            }
            if (mBaseStyle == STYLE_CRYSTAL
                    && OneUiGlassPreferences.isDockFrosty(mHost.getContext())) {
                return STYLE_FROSTY;
            }
            return mBaseStyle;
        }

        private int resolveIntensity() {
            return mDockControlled
                    ? OneUiGlassPreferences.getDockIntensity(mHost.getContext())
                    : OneUiGlassPreferences.getFolderIntensity(mHost.getContext());
        }

        private void ensureDelegate() {
            int style = resolveStyle();
            int intensity = resolveIntensity();
            boolean blurAvailable = canUsePlatformBlur(mHost);
            if (mDelegate != null && style == mLastStyle && intensity == mLastIntensity
                    && blurAvailable == mLastBlurAvailable) return;

            if (mDelegate != null) mDelegate.setVisible(false, false);
            mDelegate = buildSurface(mHost, style, mColor, mCornerRadius, intensity);
            mLastStyle = style;
            mLastIntensity = intensity;
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

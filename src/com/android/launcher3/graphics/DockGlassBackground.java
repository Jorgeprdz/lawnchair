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

/**
 * Shared bounded glass surface for One UI-inspired dock and folder backgrounds.
 *
 * <p>Blur is deliberately frosted rather than acrylic: a broad background diffusion, a neutral
 * milky veil and a restrained edge bloom reproduce the soft One UI appearance while preserving
 * wallpaper colour. Crystal stays clearer, sharper and more reflective. Both effects are driven by
 * independent 0-100 intensity values.</p>
 */
public final class DockGlassBackground {
    public static final int STYLE_BLUR = 2;
    public static final int STYLE_CRYSTAL = 3;
    /** Legacy prototype alias. Frosty is now the normal Blur rendering. */
    public static final int STYLE_FROSTY = 4;

    private DockGlassBackground() { }

    /** Existing dock entry point; intensity is resolved live from preferences. */
    public static Drawable create(View host, boolean crystal, int color, float cornerRadius) {
        return new DynamicGlassDrawable(host, crystal ? STYLE_CRYSTAL : STYLE_BLUR,
                color, cornerRadius, true);
    }

    /** Folder entry point; style and intensity are both resolved live from preferences. */
    public static Drawable createFolder(View host, int color, float cornerRadius) {
        return new DynamicGlassDrawable(host, STYLE_BLUR, color, cornerRadius, false);
    }

    private static int normalizeStyle(int style) {
        return style == STYLE_FROSTY ? STYLE_BLUR : style;
    }

    private static Drawable buildSurface(View host, int requestedStyle, int color,
            float cornerRadius, int intensityPercent) {
        final int style = normalizeStyle(requestedStyle);
        final int intensity = clamp(intensityPercent, 0, 100);
        if (style < STYLE_BLUR || intensity == 0) {
            return new ColorDrawable(Color.TRANSPARENT);
        }

        final float density = host.getResources().getDisplayMetrics().density;
        final float strength = intensity / 100f;
        final boolean dark = (host.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        // One UI Blur is intentionally broad and soft. Crystal keeps more wallpaper structure.
        final int blurDp = style == STYLE_CRYSTAL
                ? Math.round(8f + 20f * strength)
                : Math.round(18f + 46f * strength);
        Drawable blur = createPlatformBlur(host, Math.max(1, Math.round(blurDp * density)),
                cornerRadius);

        final int sourceAlpha = Color.alpha(color);
        final int tintRgb;
        final float tintScale;
        final int fallbackFloor;
        if (style == STYLE_CRYSTAL) {
            tintRgb = color;
            tintScale = 0.08f + 0.14f * strength;
            fallbackFloor = Math.round(24f + 26f * strength);
        } else {
            // Pull the chosen tint slightly toward neutral white/black. This is the frosted
            // Samsung look: colours remain visible underneath but do not turn into acrylic paint.
            tintRgb = blendRgb(color, dark ? Color.BLACK : Color.WHITE, dark ? 0.10f : 0.22f);
            tintScale = 0.20f + 0.20f * strength;
            fallbackFloor = Math.round(64f + 58f * strength);
        }

        int tintAlpha = Math.round(sourceAlpha * tintScale);
        if (blur == null) tintAlpha = Math.max(tintAlpha, fallbackFloor);
        tintAlpha = clamp(tintAlpha, 0, 255);
        GradientDrawable tint = rounded(cornerRadius, Color.argb(
                tintAlpha, Color.red(tintRgb), Color.green(tintRgb), Color.blue(tintRgb)));

        Drawable base = blur == null
                ? tint
                : new LayerDrawable(new Drawable[] {blur, tint});

        if (style == STYLE_CRYSTAL) {
            // Thin directional highlight: clearer and more glass-like than Blur.
            GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {
                            alphaColor(Color.WHITE, Math.round(14f + 28f * strength)),
                            alphaColor(Color.WHITE, Math.round(3f + 7f * strength)),
                            Color.TRANSPARENT,
                            alphaColor(Color.BLACK, Math.round(4f + 8f * strength)),
                    });
            sheen.setCornerRadius(cornerRadius);
            sheen.setStroke(Math.max(1, Math.round(density)),
                    alphaColor(Color.WHITE, Math.round(30f + 42f * strength)));
            return new LayerDrawable(new Drawable[] {base, sheen});
        }

        // Neutral haze supplies the characteristic frosted/milky diffusion even when an OEM
        // reduces cross-window blur. It is intentionally subtle in dark mode to avoid grey panels.
        int hazeRgb = dark ? Color.rgb(38, 39, 42) : Color.WHITE;
        int hazeAlpha = dark
                ? Math.round(10f + 24f * strength)
                : Math.round(18f + 42f * strength);
        GradientDrawable haze = rounded(cornerRadius, alphaColor(hazeRgb, hazeAlpha));

        // Very soft top-to-bottom bloom. No hard shine: Blur should read as frost, not Crystal.
        GradientDrawable bloom = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        alphaColor(Color.WHITE, Math.round(12f + 22f * strength)),
                        alphaColor(Color.WHITE, Math.round(3f + 7f * strength)),
                        Color.TRANSPARENT,
                        alphaColor(Color.BLACK, Math.round(2f + 6f * strength)),
                });
        bloom.setCornerRadius(cornerRadius);
        bloom.setStroke(Math.max(1, Math.round(density)),
                alphaColor(Color.WHITE, Math.round(18f + 24f * strength)));

        return new LayerDrawable(new Drawable[] {base, haze, bloom});
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
            return normalizeStyle(mDockControlled
                    ? mBaseStyle
                    : OneUiGlassPreferences.getFolderMode(mHost.getContext()));
        }

        private int resolveIntensity(int style) {
            if (mDockControlled) {
                return style == STYLE_CRYSTAL
                        ? OneUiGlassPreferences.getDockCrystalIntensity(mHost.getContext())
                        : OneUiGlassPreferences.getDockBlurIntensity(mHost.getContext());
            }
            return style == STYLE_CRYSTAL
                    ? OneUiGlassPreferences.getFolderCrystalIntensity(mHost.getContext())
                    : OneUiGlassPreferences.getFolderBlurIntensity(mHost.getContext());
        }

        private void ensureDelegate() {
            int style = resolveStyle();
            int intensity = resolveIntensity(style);
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

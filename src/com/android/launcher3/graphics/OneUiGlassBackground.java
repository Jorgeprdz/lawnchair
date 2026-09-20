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
 * <p>Blur is broad and soft, Crystal stays clearer and sharper, and Frosty keeps the original
 * stronger milky One UI treatment. Blur and Crystal retain independent intensity controls;
 * Frosty intentionally follows the Blur intensity control.</p>
 */
public final class OneUiGlassBackground {
    private OneUiGlassBackground() { }

    /** Dock entry point. The selected material is explicit and shared with settings. */
    public static Drawable createDock(View host, int style, int color, float cornerRadius) {
        return new DynamicGlassDrawable(host, OneUiGlassStyle.normalize(style, OneUiGlassStyle.BLUR),
                color, cornerRadius, true, true, OneUiCrystalSurfaceRole.DOCK);
    }

    public static Drawable createDockOverlay(View host, int style, int color, float cornerRadius) {
        return new DynamicGlassDrawable(host, OneUiGlassStyle.normalize(style, OneUiGlassStyle.BLUR),
                color, cornerRadius, true, false, OneUiCrystalSurfaceRole.DOCK_OVERLAY);
    }

    /** Open-folder entry point; style and intensity are resolved live from shared preferences. */
    public static Drawable createOpenFolder(View host, int color, float cornerRadius) {
        return new DynamicGlassDrawable(host, OneUiGlassStyle.BLUR, color, cornerRadius,
                false, true, OneUiCrystalSurfaceRole.OPEN_FOLDER);
    }

    public static Drawable createOpenFolderOverlay(View host, int color, float cornerRadius) {
        return new DynamicGlassDrawable(host, OneUiGlassStyle.BLUR, color, cornerRadius,
                false, false, OneUiCrystalSurfaceRole.OPEN_FOLDER_OVERLAY);
    }

    public static Drawable createFolderIcon(View host, int color, float cornerRadius) {
        return new DynamicGlassDrawable(host, OneUiGlassStyle.BLUR, color, cornerRadius,
                false, true, OneUiCrystalSurfaceRole.FOLDER_ICON);
    }

    public static Drawable createFolderIconOverlay(View host, int color, float cornerRadius) {
        return new DynamicGlassDrawable(host, OneUiGlassStyle.BLUR, color, cornerRadius,
                false, false, OneUiCrystalSurfaceRole.FOLDER_ICON_OVERLAY);
    }

    public static Drawable createFolderDrawable(View host, int color, float cornerRadius) {
        return new DynamicGlassDrawable(host, OneUiGlassStyle.BLUR, color, cornerRadius,
                false, true, OneUiCrystalSurfaceRole.FOLDER_DRAWABLE);
    }

    public static boolean shouldApplySamsungBackdrop(View host, int style) {
        return style != OneUiGlassStyle.CRYSTAL
                || OneUiCrystalSourcePolicy.chooseForView(host, true)
                == OneUiCrystalSourcePolicy.Decision.SAMSUNG_LIVE_BACKDROP;
    }

    public static void setAnimationRunning(@Nullable Drawable drawable, boolean running) {
        if (drawable instanceof OneUiCrystalRenderer renderer) {
            renderer.setAnimationRunning(running);
        } else if (drawable instanceof DynamicGlassDrawable dynamic) {
            dynamic.setAnimationRunning(running);
        }
    }

    @Nullable
    public static OneUiCrystalSurfaceRole getSurfaceRoleForTesting(Drawable drawable) {
        if (drawable instanceof OneUiCrystalRenderer renderer) return renderer.surfaceRole();
        if (drawable instanceof DynamicGlassDrawable dynamic) return dynamic.mRole;
        return null;
    }

    private static Drawable buildSurface(View host, int style, int color,
            float cornerRadius, int intensityPercent, boolean allowPlatformBlur,
            OneUiCrystalSurfaceRole role) {
        final int intensity = clamp(intensityPercent, 0, 100);
        if (style < OneUiGlassStyle.BLUR) {
            return new ColorDrawable(Color.TRANSPARENT);
        }
        if (style == OneUiGlassStyle.CRYSTAL) {
            return OneUiCrystalRenderer.create(host, role, color, cornerRadius, intensity);
        }
        if (intensity == 0) {
            return new ColorDrawable(Color.TRANSPARENT);
        }

        final float density = host.getResources().getDisplayMetrics().density;
        final float strength = intensity / 100f;
        final boolean dark = (host.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        final int blurDp;
        if (style == OneUiGlassStyle.CRYSTAL) {
            blurDp = Math.round(8f + 20f * strength);
        } else if (style == OneUiGlassStyle.FROSTY) {
            blurDp = Math.round(18f + 42f * strength);
        } else {
            blurDp = Math.round(18f + 46f * strength);
        }
        Drawable blur = allowPlatformBlur
                ? createPlatformBlur(host, Math.max(1, Math.round(blurDp * density)), cornerRadius)
                : null;

        final int sourceAlpha = Color.alpha(color);
        final int tintRgb;
        final float tintScale;
        final int fallbackFloor;
        if (style == OneUiGlassStyle.CRYSTAL) {
            tintRgb = color;
            tintScale = 0.08f + 0.14f * strength;
            fallbackFloor = Math.round(24f + 26f * strength);
        } else if (style == OneUiGlassStyle.FROSTY) {
            tintRgb = blendRgb(color, dark ? Color.BLACK : Color.WHITE,
                    dark ? 0.16f : 0.30f);
            tintScale = 0.42f + 0.28f * strength;
            fallbackFloor = Math.round(104f + 54f * strength);
        } else {
            tintRgb = blendRgb(color, dark ? Color.BLACK : Color.WHITE, dark ? 0.10f : 0.22f);
            tintScale = 0.20f + 0.20f * strength;
            fallbackFloor = Math.round(64f + 58f * strength);
        }

        int tintAlpha = Math.round(sourceAlpha * tintScale);
        if (allowPlatformBlur && blur == null) tintAlpha = Math.max(tintAlpha, fallbackFloor);
        tintAlpha = clamp(tintAlpha, 0, 255);
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
            int milk = dark
                    ? alphaColor(Color.rgb(210, 214, 220), Math.round(14f + 24f * strength))
                    : alphaColor(Color.WHITE, Math.round(34f + 62f * strength));
            GradientDrawable haze = rounded(cornerRadius, milk);
            haze.setStroke(Math.max(1, Math.round(density)),
                    alphaColor(Color.WHITE, Math.round(12f + 18f * strength)));
            return new LayerDrawable(new Drawable[] {base, haze});
        }

        int hazeRgb = dark ? Color.rgb(38, 39, 42) : Color.WHITE;
        int hazeAlpha = dark
                ? Math.round(10f + 24f * strength)
                : Math.round(18f + 42f * strength);
        GradientDrawable haze = rounded(cornerRadius, alphaColor(hazeRgb, hazeAlpha));

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
        private final boolean mAllowPlatformBlur;
        private final OneUiCrystalSurfaceRole mRole;

        private Drawable mDelegate;
        private int mLastStyle = Integer.MIN_VALUE;
        private int mLastIntensity = Integer.MIN_VALUE;
        private boolean mLastBlurAvailable;
        private boolean mAnimationRunning;
        private int mAlpha = 255;
        private @Nullable ColorFilter mColorFilter;

        DynamicGlassDrawable(View host, int baseStyle, int color, float cornerRadius,
                boolean dockControlled, boolean allowPlatformBlur,
                OneUiCrystalSurfaceRole role) {
            mHost = host;
            mBaseStyle = baseStyle;
            mColor = color;
            mCornerRadius = cornerRadius;
            mDockControlled = dockControlled;
            mAllowPlatformBlur = allowPlatformBlur;
            mRole = role;
        }

        private int resolveStyle() {
            return mDockControlled
                    ? OneUiGlassStyle.normalize(mBaseStyle, OneUiGlassStyle.BLUR)
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
            boolean blurAvailable = mAllowPlatformBlur && canUsePlatformBlur(mHost);
            if (mDelegate != null && style == mLastStyle && intensity == mLastIntensity
                    && blurAvailable == mLastBlurAvailable) return;

            if (mDelegate != null) mDelegate.setVisible(false, false);
            mDelegate = buildSurface(mHost, style, mColor, mCornerRadius, intensity,
                    mAllowPlatformBlur, mRole);
            mLastStyle = style;
            mLastIntensity = intensity;
            mLastBlurAvailable = blurAvailable;
            mDelegate.setBounds(getBounds());
            mDelegate.setAlpha(mAlpha);
            mDelegate.setColorFilter(mColorFilter);
            OneUiGlassBackground.setAnimationRunning(mDelegate, mAnimationRunning);
        }

        void setAnimationRunning(boolean running) {
            mAnimationRunning = running;
            OneUiGlassBackground.setAnimationRunning(mDelegate, running);
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

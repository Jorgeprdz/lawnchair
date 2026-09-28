/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package com.android.launcher3.graphics;

import app.lawnchair.oneui.OneUiGlassStyle;

/** Immutable, inexpensive material parameters shared by the two non-Crystal glass renderers. */
public final class OneUiGlassProfile {
    public final int radiusDp;
    public final float tintBlend;
    public final float tintAlphaScale;
    public final int fallbackAlpha;
    public final int hazeAlpha;
    public final int highlightAlpha;

    private OneUiGlassProfile(int radiusDp, float tintBlend, float tintAlphaScale,
            int fallbackAlpha, int hazeAlpha, int highlightAlpha) {
        this.radiusDp = radiusDp;
        this.tintBlend = tintBlend;
        this.tintAlphaScale = tintAlphaScale;
        this.fallbackAlpha = fallbackAlpha;
        this.hazeAlpha = hazeAlpha;
        this.highlightAlpha = highlightAlpha;
    }

    public static OneUiGlassProfile create(int style, int intensityPercent, boolean dark) {
        float strength = Math.max(0, Math.min(100, intensityPercent)) / 100f;
        if (style == OneUiGlassStyle.FROSTY) {
            return new OneUiGlassProfile(
                    Math.round(34f + 42f * strength),
                    dark ? 0.08f : 0.14f,
                    0.14f + 0.12f * strength,
                    Math.round(40f + 16f * strength),
                    Math.round((dark ? 14f : 18f) + (dark ? 10f : 15f) * strength),
                    Math.round(5f + 9f * strength));
        }
        return new OneUiGlassProfile(
                Math.round(22f + 22f * strength),
                dark ? 0.04f : 0.08f,
                0.06f + 0.08f * strength,
                Math.round(30f + 18f * strength),
                0,
                0);
    }

    /**
     * Folder accent colors can be highly saturated; using one as Frosty's backdrop tint makes
     * neutral wallpapers look like a solid green/blue pill. Preserve only a faint color trace.
     */
    static int neutralizeTint(int color) {
        int alpha = color >>> 24;
        int red = (color >>> 16) & 0xff;
        int green = (color >>> 8) & 0xff;
        int blue = color & 0xff;
        int gray = (red * 30 + green * 59 + blue * 11) / 100;
        float neutralize = 1f;
        red = Math.round(red + (gray - red) * neutralize);
        green = Math.round(green + (gray - green) * neutralize);
        blue = Math.round(blue + (gray - blue) * neutralize);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    static int frostyFolderHazeAlpha(OneUiGlassProfile profile) {
        // The source is the wallpaper snapshot; this extra diffusion masks launcher content
        // beneath the folder while staying well below the old near-opaque double haze.
        return Math.min(255, profile.hazeAlpha + 22);
    }
}

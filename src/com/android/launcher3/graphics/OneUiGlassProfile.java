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
                    Math.round(28f + 40f * strength),
                    dark ? 0.10f : 0.20f,
                    0.18f + 0.12f * strength,
                    Math.round(48f + 30f * strength),
                    Math.round((dark ? 10f : 20f) + (dark ? 12f : 22f) * strength),
                    Math.round(9f + 12f * strength));
        }
        return new OneUiGlassProfile(
                Math.round(18f + 24f * strength),
                dark ? 0.06f : 0.12f,
                0.09f + 0.10f * strength,
                Math.round(30f + 20f * strength),
                0,
                0);
    }
}

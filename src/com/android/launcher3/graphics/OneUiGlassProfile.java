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
}

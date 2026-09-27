/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package com.android.launcher3.graphics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import app.lawnchair.oneui.OneUiGlassStyle;
import org.junit.Test;

public class OneUiGlassProfileTest {
    @Test
    public void liquidGlassAndFrostyHaveDistinctGlassProfiles() {
        OneUiGlassProfile liquidGlass = OneUiGlassProfile.create(
                OneUiGlassStyle.LIQUID_GLASS, 100, false);
        OneUiGlassProfile frosty = OneUiGlassProfile.create(OneUiGlassStyle.FROSTY, 100, false);

        assertTrue("Frosty needs a visibly broader backdrop blur",
                frosty.radiusDp >= liquidGlass.radiusDp + 25);
        assertEquals(0, liquidGlass.hazeAlpha);
        assertTrue("Frosty haze must keep the wallpaper clearly visible", frosty.hazeAlpha <= 36);
        assertTrue("Liquid Glass should keep the wallpaper more visible",
                liquidGlass.tintAlphaScale < frosty.tintAlphaScale);
        assertTrue(frosty.highlightAlpha > liquidGlass.highlightAlpha);
        assertTrue("Backdrop blur must not receive the no-blur fallback tint",
                OneUiGlassBackground.resolveTintAlpha(102, frosty, true) < frosty.fallbackAlpha);
        assertTrue("Fallback tint still needs enough contrast when blur is unavailable",
                OneUiGlassBackground.resolveTintAlpha(102, frosty, false)
                        >= frosty.fallbackAlpha);
        assertTrue("Liquid Glass fallback should remain translucent",
                liquidGlass.fallbackAlpha <= 50);
    }

    @Test
    public void glassProfilesAdaptForLightAndDark() {
        OneUiGlassProfile light = OneUiGlassProfile.create(OneUiGlassStyle.FROSTY, 60, false);
        OneUiGlassProfile dark = OneUiGlassProfile.create(OneUiGlassStyle.FROSTY, 60, true);

        assertEquals(light.radiusDp, dark.radiusDp);
        assertTrue(light.tintBlend != dark.tintBlend);
        assertTrue(light.hazeAlpha > dark.hazeAlpha);
    }
}

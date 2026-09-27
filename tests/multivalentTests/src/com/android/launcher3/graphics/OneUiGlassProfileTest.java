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
    public void normalAndFrostyHaveDistinctGlassProfiles() {
        OneUiGlassProfile normal = OneUiGlassProfile.create(OneUiGlassStyle.BLUR, 100, false);
        OneUiGlassProfile frosty = OneUiGlassProfile.create(OneUiGlassStyle.FROSTY, 100, false);

        assertTrue("Frosty needs a visibly broader backdrop blur",
                frosty.radiusDp >= normal.radiusDp + 25);
        assertEquals(0, normal.hazeAlpha);
        assertTrue("Frosty needs a visible diffuse layer", frosty.hazeAlpha >= 60);
        assertTrue("Normal should keep the wallpaper more visible",
                normal.tintAlphaScale < frosty.tintAlphaScale);
        assertTrue(frosty.highlightAlpha > normal.highlightAlpha);
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

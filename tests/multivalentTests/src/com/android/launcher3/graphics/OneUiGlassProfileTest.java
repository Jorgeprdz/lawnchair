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
        OneUiGlassProfile normal = OneUiGlassProfile.create(OneUiGlassStyle.BLUR, 70, false);
        OneUiGlassProfile frosty = OneUiGlassProfile.create(OneUiGlassStyle.FROSTY, 70, false);

        assertTrue(frosty.radiusDp > normal.radiusDp);
        assertEquals(0, normal.hazeAlpha);
        assertTrue(frosty.hazeAlpha > 0);
        assertTrue(frosty.tintAlphaScale > normal.tintAlphaScale);
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

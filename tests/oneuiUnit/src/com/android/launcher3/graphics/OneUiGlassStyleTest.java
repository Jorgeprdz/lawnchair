/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package com.android.launcher3.graphics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.lawnchair.oneui.OneUiGlassStyle;
import org.junit.Test;

public class OneUiGlassStyleTest {
    @Test
    public void liquidGlassUsesRefractionRendererOnlyForItsMode() {
        assertTrue(OneUiGlassStyle.usesLiquidGlassRenderer(OneUiGlassStyle.LIQUID_GLASS));
        assertFalse(OneUiGlassStyle.usesLiquidGlassRenderer(OneUiGlassStyle.CRYSTAL));
        assertFalse(OneUiGlassStyle.usesLiquidGlassRenderer(OneUiGlassStyle.FROSTY));
    }

    @Test
    public void liquidGlassAndFrostyUseTheDockBackdropView() {
        assertTrue(OneUiGlassStyle.usesDockBackdropView(OneUiGlassStyle.LIQUID_GLASS));
        assertTrue(OneUiGlassStyle.usesDockBackdropView(OneUiGlassStyle.FROSTY));
        assertFalse(OneUiGlassStyle.usesDockBackdropView(OneUiGlassStyle.CRYSTAL));
        assertFalse(OneUiGlassStyle.usesDockBackdropView(OneUiGlassStyle.SOLID));
    }

    @Test
    public void onlyFrostyFoldersUseTheWallpaperSnapshotBackdrop() {
        assertTrue(OneUiGlassStyle.usesWallpaperSnapshotForFolder(OneUiGlassStyle.FROSTY));
        assertFalse(OneUiGlassStyle.usesWallpaperSnapshotForFolder(OneUiGlassStyle.LIQUID_GLASS));
        assertFalse(OneUiGlassStyle.usesWallpaperSnapshotForFolder(OneUiGlassStyle.CRYSTAL));
    }

    @Test
    public void folderGlassNeverCapturesWorkspaceWidgetsAsItsBackdrop() {
        assertTrue(OneUiGlassStyle.usesWallpaperOnlyFolderBackdrop(OneUiGlassStyle.LIQUID_GLASS));
        assertTrue(OneUiGlassStyle.usesWallpaperOnlyFolderBackdrop(OneUiGlassStyle.CRYSTAL));
        assertTrue(OneUiGlassStyle.usesWallpaperOnlyFolderBackdrop(OneUiGlassStyle.FROSTY));
        assertFalse(OneUiGlassStyle.usesWallpaperOnlyFolderBackdrop(OneUiGlassStyle.SOLID));
        assertFalse(OneUiGlassStyle.usesWallpaperOnlyFolderBackdrop(OneUiGlassStyle.OFF));
    }

    @Test
    public void folderLiquidGlassKeepsWallpaperDetailWithoutChromaticFringing() {
        assertEquals(0.12f, OneUiGlassStyle.folderRefractionScale(
                OneUiGlassStyle.LIQUID_GLASS), 0.001f);
        assertEquals(0f, OneUiGlassStyle.folderDispersion(), 0.001f);
        assertFalse("Large Liquid Glass folders use the stable GPU-blurred backdrop",
                OneUiGlassStyle.usesFolderRuntimeShader(OneUiGlassStyle.LIQUID_GLASS));
        assertFalse("Frosty folders use diffusion instead of the high-frequency lens shader",
                OneUiGlassStyle.usesFolderRuntimeShader(OneUiGlassStyle.FROSTY));
        assertTrue("Crystal keeps its existing optical shader in folders",
                OneUiGlassStyle.usesFolderRuntimeShader(OneUiGlassStyle.CRYSTAL));
    }

    @Test
    public void folderFallbackBlurKeepsLiquidAndFrostyVisuallyDistinct() {
        float liquidBlur = OneUiGlassStyle.folderFallbackBlurScale(
                OneUiGlassStyle.LIQUID_GLASS);
        float frostyBlur = OneUiGlassStyle.folderFallbackBlurScale(OneUiGlassStyle.FROSTY);

        assertTrue("Liquid folders need a real backdrop blur", liquidBlur > 0f);
        assertTrue("Liquid folders retain more wallpaper detail than Frosty",
                liquidBlur <= 0.35f);
        assertTrue("Frosty folders need stronger diffusion than Liquid Glass",
                frostyBlur > liquidBlur);
        assertEquals("Crystal keeps its existing shader profile", 0f,
                OneUiGlassStyle.folderFallbackBlurScale(OneUiGlassStyle.CRYSTAL), 0.001f);
    }

    @Test
    public void openFolderVeilSoftensUnblurredLauncherContentByMaterial() {
        int liquid = OneUiGlassStyle.openFolderVeilAlpha(
                OneUiGlassStyle.LIQUID_GLASS, 35);
        int frosty = OneUiGlassStyle.openFolderVeilAlpha(
                OneUiGlassStyle.FROSTY, 35);

        assertTrue("Liquid Glass keeps the open folder translucent", liquid >= 88 && liquid <= 110);
        assertTrue("Frosty adds stronger diffusion over launcher content",
                frosty > liquid && frosty <= 180);
        assertEquals("Crystal keeps its own renderer without an extra veil", 0,
                OneUiGlassStyle.openFolderVeilAlpha(OneUiGlassStyle.CRYSTAL, 35));
    }

    @Test
    public void glassFolderTintPreservesBrightnessWithoutWallpaperHueCast() {
        int tint = OneUiGlassStyle.neutralizeGlassTint(0xffc9f28f);
        int red = (tint >>> 16) & 0xff;
        int green = (tint >>> 8) & 0xff;
        int blue = tint & 0xff;
        assertEquals(red, green);
        assertEquals(green, blue);
        assertTrue(red > 0 && red < 255);
        assertEquals(0xff, tint >>> 24);
    }

    @Test
    public void folderCanFollowDockOrKeepAnExplicitMaterial() {
        assertEquals(OneUiGlassStyle.LIQUID_GLASS,
                OneUiGlassStyle.resolveFolderMode(OneUiGlassStyle.FOLDER_FOLLOW_DOCK,
                        OneUiGlassStyle.LIQUID_GLASS));
        assertEquals(OneUiGlassStyle.FROSTY,
                OneUiGlassStyle.resolveFolderMode(OneUiGlassStyle.FOLDER_FOLLOW_DOCK,
                        OneUiGlassStyle.FROSTY));
        assertEquals(OneUiGlassStyle.CRYSTAL,
                OneUiGlassStyle.resolveFolderMode(OneUiGlassStyle.CRYSTAL,
                        OneUiGlassStyle.LIQUID_GLASS));
    }
}

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

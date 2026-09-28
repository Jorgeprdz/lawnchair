/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package app.lawnchair.oneui

/** Canonical One UI glass style ids shared by settings, dock, folders and the renderer. */
object OneUiGlassStyle {
    const val FOLDER_FOLLOW_DOCK = -1
    const val OFF = 0
    const val SOLID = 1
    const val LIQUID_GLASS = 2
    /** Stored value retained for preference compatibility with earlier builds. */
    @Deprecated("Use LIQUID_GLASS")
    const val BLUR = LIQUID_GLASS
    const val CRYSTAL = 3
    const val FROSTY = 4

    @JvmStatic
    fun normalize(value: Int, fallback: Int = SOLID): Int =
        if (value in OFF..FROSTY) value else fallback

    @JvmStatic
    fun isGlass(value: Int): Boolean = value in LIQUID_GLASS..FROSTY

    @JvmStatic
    fun usesLiquidGlassRenderer(value: Int): Boolean = value == LIQUID_GLASS

    @JvmStatic
    fun usesDockBackdropView(value: Int): Boolean =
        value == LIQUID_GLASS || value == FROSTY

    @JvmStatic
    fun usesWallpaperSnapshotForFolder(value: Int): Boolean = value == FROSTY

    /** Folder glass samples the wallpaper only; recursively drawing the workspace touches widgets. */
    @JvmStatic
    fun usesWallpaperOnlyFolderBackdrop(value: Int): Boolean = isGlass(value)

    @JvmStatic
    fun resolveFolderMode(folderMode: Int, dockMode: Int): Int =
        normalize(if (folderMode == FOLDER_FOLLOW_DOCK) dockMode else folderMode)
}

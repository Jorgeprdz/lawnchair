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

    /**
     * Folder surfaces are much larger than the dock. Keep their lens subtle enough to retain
     * recognizable wallpaper detail instead of stretching it into oily bands.
     */
    @JvmStatic
    fun folderRefractionScale(value: Int): Float = when (value) {
        // Large-folder bounds magnify these samples across a much wider surface than the dock.
        // Keep just a small optical offset so high-contrast wallpaper details do not smear into bands.
        LIQUID_GLASS -> 0.12f
        FROSTY -> 0.12f
        else -> 1f
    }

    /** GPU backdrop blur used by large folders, which avoid the high-frequency lens shader. */
    @JvmStatic
    fun folderFallbackBlurScale(value: Int): Float = when (value) {
        LIQUID_GLASS -> 0.35f
        FROSTY -> 1.65f
        else -> 0f
    }

    /** A small color-matched veil keeps unblurred launcher content from bleeding through open folders. */
    @JvmStatic
    fun openFolderVeilAlpha(value: Int, intensityPercent: Int): Int {
        val strength = intensityPercent.coerceIn(0, 100) / 100f
        return when (value) {
            LIQUID_GLASS -> (88f + 18f * strength).toInt()
            FROSTY -> (148f + 22f * strength).toInt()
            else -> 0
        }
    }

    /** Glass-folder tint keeps the selected color's brightness without a strong color cast. */
    @JvmStatic
    fun neutralizeGlassTint(color: Int): Int {
        val gray = (0.2126f * ((color shr 16) and 0xff)
                + 0.7152f * ((color shr 8) and 0xff)
                + 0.0722f * (color and 0xff)).toInt().coerceIn(0, 255)
        return (color and -0x1000000) or (gray shl 16) or (gray shl 8) or gray
    }

    /** Folder Liquid Glass avoids chromatic splitting, which reads as oily color fringing. */
    @JvmStatic
    fun folderDispersion(): Float = 0f

    /** Crystal keeps its edge lens; Liquid/Frosty use the stable GPU-blurred folder backdrop. */
    @JvmStatic
    fun usesFolderRuntimeShader(value: Int): Boolean =
        value == CRYSTAL

    @JvmStatic
    fun resolveFolderMode(folderMode: Int, dockMode: Int): Int =
        normalize(if (folderMode == FOLDER_FOLLOW_DOCK) dockMode else folderMode)
}

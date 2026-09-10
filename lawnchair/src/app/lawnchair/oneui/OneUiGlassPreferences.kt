/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package app.lawnchair.oneui

import android.content.Context

/**
 * Isolated preference store for One UI glass controls.
 *
 * Blur and Crystal intentionally keep independent intensities so tuning one surface style does not
 * change the other. Legacy single-intensity values are used as migration fallbacks.
 */
object OneUiGlassPreferences {
    private const val PREFS = "oneui_glass"

    private const val KEY_DOCK_BLUR_INTENSITY = "dock_blur_intensity"
    private const val KEY_DOCK_CRYSTAL_INTENSITY = "dock_crystal_intensity"
    private const val KEY_FOLDER_MODE = "folder_glass_mode"
    private const val KEY_FOLDER_BLUR_INTENSITY = "folder_blur_intensity"
    private const val KEY_FOLDER_CRYSTAL_INTENSITY = "folder_crystal_intensity"
    private const val KEY_LARGE_FOLDER_SHAPE = "large_folder_shape"

    // Kept only to migrate builds that already exposed the first glass prototype.
    private const val LEGACY_DOCK_FROSTY = "dock_frosty"
    private const val LEGACY_DOCK_INTENSITY = "dock_glass_intensity"
    private const val LEGACY_FOLDER_INTENSITY = "folder_glass_intensity"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readIntensity(context: Context, key: String, legacyKey: String, default: Int): Int {
        val p = prefs(context)
        return p.getInt(key, p.getInt(legacyKey, default)).coerceIn(0, 100)
    }

    @JvmStatic
    fun getDockBlurIntensity(context: Context): Int =
        readIntensity(context, KEY_DOCK_BLUR_INTENSITY, LEGACY_DOCK_INTENSITY, 70)

    @JvmStatic
    fun setDockBlurIntensity(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_DOCK_BLUR_INTENSITY, value.coerceIn(0, 100)).apply()
    }

    @JvmStatic
    fun getDockCrystalIntensity(context: Context): Int =
        readIntensity(context, KEY_DOCK_CRYSTAL_INTENSITY, LEGACY_DOCK_INTENSITY, 55)

    @JvmStatic
    fun setDockCrystalIntensity(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_DOCK_CRYSTAL_INTENSITY, value.coerceIn(0, 100)).apply()
    }

    /** 0 Off, 1 Solid, 2 frosted Blur, 3 Crystal. */
    @JvmStatic
    fun getFolderMode(context: Context): Int {
        val value = prefs(context).getInt(KEY_FOLDER_MODE, 1)
        // Prototype mode 4 (Frosty) becomes the new One UI-style Blur.
        return if (value == 4) 2 else value.coerceIn(0, 3)
    }

    @JvmStatic
    fun setFolderMode(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_FOLDER_MODE, value.coerceIn(0, 3)).apply()
    }

    @JvmStatic
    fun getFolderBlurIntensity(context: Context): Int =
        readIntensity(context, KEY_FOLDER_BLUR_INTENSITY, LEGACY_FOLDER_INTENSITY, 70)

    @JvmStatic
    fun setFolderBlurIntensity(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_FOLDER_BLUR_INTENSITY, value.coerceIn(0, 100)).apply()
    }

    @JvmStatic
    fun getFolderCrystalIntensity(context: Context): Int =
        readIntensity(context, KEY_FOLDER_CRYSTAL_INTENSITY, LEGACY_FOLDER_INTENSITY, 55)

    @JvmStatic
    fun setFolderCrystalIntensity(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_FOLDER_CRYSTAL_INTENSITY, value.coerceIn(0, 100)).apply()
    }

    /**
     * Shape of the 2x2 large-folder preview. Rounded square remains the default One UI appearance;
     * Circle is retained as an explicit alternative instead of being imposed by glass rendering.
     */
    @JvmStatic
    fun getLargeFolderShape(context: Context): Int =
        prefs(context).getInt(KEY_LARGE_FOLDER_SHAPE, OneUiLargeFolderShape.ROUNDED_SQUARE)
            .coerceIn(OneUiLargeFolderShape.ROUNDED_SQUARE, OneUiLargeFolderShape.CIRCLE)

    @JvmStatic
    fun setLargeFolderShape(context: Context, value: Int) {
        prefs(context).edit().putInt(
            KEY_LARGE_FOLDER_SHAPE,
            value.coerceIn(OneUiLargeFolderShape.ROUNDED_SQUARE, OneUiLargeFolderShape.CIRCLE),
        ).apply()
    }

    @JvmStatic
    fun isLargeFolderCircular(context: Context): Boolean =
        getLargeFolderShape(context) == OneUiLargeFolderShape.CIRCLE

    // Binary/source compatibility for the first prototype while the branch transitions.
    @JvmStatic
    @Deprecated("Blur is now the frosted One UI style")
    fun isDockFrosty(context: Context): Boolean = prefs(context).getBoolean(LEGACY_DOCK_FROSTY, false)

    @JvmStatic
    @Deprecated("Blur is now the frosted One UI style")
    fun setDockFrosty(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(LEGACY_DOCK_FROSTY, enabled).apply()
    }

    @JvmStatic
    @Deprecated("Use the style-specific intensity")
    fun getDockIntensity(context: Context): Int = getDockBlurIntensity(context)

    @JvmStatic
    @Deprecated("Use the style-specific intensity")
    fun setDockIntensity(context: Context, value: Int) {
        setDockBlurIntensity(context, value)
        setDockCrystalIntensity(context, value)
    }

    @JvmStatic
    @Deprecated("Use the style-specific intensity")
    fun getFolderIntensity(context: Context): Int = getFolderBlurIntensity(context)

    @JvmStatic
    @Deprecated("Use the style-specific intensity")
    fun setFolderIntensity(context: Context, value: Int) {
        setFolderBlurIntensity(context, value)
        setFolderCrystalIntensity(context, value)
    }
}

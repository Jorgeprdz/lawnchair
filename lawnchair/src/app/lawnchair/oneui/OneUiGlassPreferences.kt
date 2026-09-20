/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package app.lawnchair.oneui

import android.content.Context

/**
 * Isolated preference store for One UI glass controls.
 *
 * Each optical material owns its intensity. The renderer and both surfaces consume the same
 * canonical style ids from [OneUiGlassStyle]. Legacy prototype keys are read only as migration
 * fallbacks so old installs keep their appearance without keeping old wiring alive.
 */
object OneUiGlassPreferences {
    private const val PREFS = "oneui_glass"

    private const val KEY_DOCK_BLUR_INTENSITY = "dock_blur_intensity"
    private const val KEY_DOCK_CRYSTAL_INTENSITY = "dock_crystal_intensity"
    private const val KEY_DOCK_FROSTY_INTENSITY = "dock_frosty_intensity"
    private const val KEY_FOLDER_MODE = "folder_glass_mode"
    private const val KEY_FOLDER_BLUR_INTENSITY = "folder_blur_intensity"
    private const val KEY_FOLDER_CRYSTAL_INTENSITY = "folder_crystal_intensity"
    private const val KEY_FOLDER_FROSTY_INTENSITY = "folder_frosty_intensity"
    private const val KEY_LARGE_FOLDER_SHAPE = "large_folder_shape"

    // First glass prototype migration keys.
    private const val LEGACY_DOCK_FROSTY = "dock_frosty"
    private const val LEGACY_DOCK_INTENSITY = "dock_glass_intensity"
    private const val LEGACY_FOLDER_INTENSITY = "folder_glass_intensity"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readIntensity(context: Context, key: String, legacyKey: String, default: Int): Int {
        val p = prefs(context)
        return p.getInt(key, p.getInt(legacyKey, default)).coerceIn(0, 100)
    }

    /**
     * Converts the old "Crystal + dock_frosty=true" encoding into the explicit Frosty style.
     * New selections are written directly as style 4 by the native hotseat preference.
     */
    @JvmStatic
    fun resolveDockStyle(context: Context, nativeMode: Int): Int {
        val normalized = OneUiGlassStyle.normalize(nativeMode)
        return if (
            normalized == OneUiGlassStyle.CRYSTAL &&
            prefs(context).getBoolean(LEGACY_DOCK_FROSTY, false)
        ) {
            OneUiGlassStyle.FROSTY
        } else {
            normalized
        }
    }

    @JvmStatic
    fun clearLegacyDockStyle(context: Context) {
        prefs(context).edit().remove(LEGACY_DOCK_FROSTY).apply()
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

    @JvmStatic
    fun getDockFrostyIntensity(context: Context): Int {
        val p = prefs(context)
        return p.getInt(KEY_DOCK_FROSTY_INTENSITY, getDockBlurIntensity(context)).coerceIn(0, 100)
    }

    @JvmStatic
    fun setDockFrostyIntensity(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_DOCK_FROSTY_INTENSITY, value.coerceIn(0, 100)).apply()
    }

    @JvmStatic
    fun getDockIntensity(context: Context, style: Int): Int = when (style) {
        OneUiGlassStyle.CRYSTAL -> getDockCrystalIntensity(context)
        OneUiGlassStyle.FROSTY -> getDockFrostyIntensity(context)
        else -> getDockBlurIntensity(context)
    }

    @JvmStatic
    fun setDockIntensity(context: Context, style: Int, value: Int) {
        when (style) {
            OneUiGlassStyle.CRYSTAL -> setDockCrystalIntensity(context, value)
            OneUiGlassStyle.FROSTY -> setDockFrostyIntensity(context, value)
            else -> setDockBlurIntensity(context, value)
        }
    }

    /** 0 Off, 1 Solid, 2 Blur, 3 Crystal, 4 Frosty. */
    @JvmStatic
    fun getFolderMode(context: Context): Int =
        OneUiGlassStyle.normalize(prefs(context).getInt(KEY_FOLDER_MODE, OneUiGlassStyle.SOLID))

    @JvmStatic
    fun setFolderMode(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_FOLDER_MODE, OneUiGlassStyle.normalize(value)).apply()
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

    @JvmStatic
    fun getFolderFrostyIntensity(context: Context): Int {
        val p = prefs(context)
        return p.getInt(KEY_FOLDER_FROSTY_INTENSITY, getFolderBlurIntensity(context)).coerceIn(0, 100)
    }

    @JvmStatic
    fun setFolderFrostyIntensity(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_FOLDER_FROSTY_INTENSITY, value.coerceIn(0, 100)).apply()
    }

    @JvmStatic
    fun getFolderIntensity(context: Context, style: Int): Int = when (style) {
        OneUiGlassStyle.CRYSTAL -> getFolderCrystalIntensity(context)
        OneUiGlassStyle.FROSTY -> getFolderFrostyIntensity(context)
        else -> getFolderBlurIntensity(context)
    }

    @JvmStatic
    fun setFolderIntensity(context: Context, style: Int, value: Int) {
        when (style) {
            OneUiGlassStyle.CRYSTAL -> setFolderCrystalIntensity(context, value)
            OneUiGlassStyle.FROSTY -> setFolderFrostyIntensity(context, value)
            else -> setFolderBlurIntensity(context, value)
        }
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

    // Source compatibility for old callers while downstream branches transition.
    @JvmStatic
    @Deprecated("Use resolveDockStyle; Frosty is now an explicit style")
    fun isDockFrosty(context: Context): Boolean =
        prefs(context).getBoolean(LEGACY_DOCK_FROSTY, false)

    @JvmStatic
    @Deprecated("Use explicit style 4")
    fun setDockFrosty(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(LEGACY_DOCK_FROSTY, enabled).apply()
    }

    @JvmStatic
    @Deprecated("Use the style-specific overload")
    fun getDockIntensity(context: Context): Int = getDockBlurIntensity(context)

    @JvmStatic
    @Deprecated("Use the style-specific overload")
    fun setDockIntensity(context: Context, value: Int) {
        setDockBlurIntensity(context, value)
        setDockCrystalIntensity(context, value)
        setDockFrostyIntensity(context, value)
    }

    @JvmStatic
    @Deprecated("Use the style-specific overload")
    fun getFolderIntensity(context: Context): Int = getFolderBlurIntensity(context)

    @JvmStatic
    @Deprecated("Use the style-specific overload")
    fun setFolderIntensity(context: Context, value: Int) {
        setFolderBlurIntensity(context, value)
        setFolderCrystalIntensity(context, value)
        setFolderFrostyIntensity(context, value)
    }
}

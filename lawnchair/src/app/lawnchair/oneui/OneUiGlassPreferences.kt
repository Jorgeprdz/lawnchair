/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package app.lawnchair.oneui

import android.content.Context

/** Small isolated preference store for experimental One UI glass controls. */
object OneUiGlassPreferences {
    private const val PREFS = "oneui_glass"
    private const val KEY_DOCK_FROSTY = "dock_frosty"
    private const val KEY_DOCK_INTENSITY = "dock_glass_intensity"
    private const val KEY_FOLDER_MODE = "folder_glass_mode"
    private const val KEY_FOLDER_INTENSITY = "folder_glass_intensity"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @JvmStatic
    fun isDockFrosty(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DOCK_FROSTY, false)

    @JvmStatic
    fun setDockFrosty(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DOCK_FROSTY, enabled).apply()
    }

    @JvmStatic
    fun getDockIntensity(context: Context): Int =
        prefs(context).getInt(KEY_DOCK_INTENSITY, 70).coerceIn(0, 100)

    @JvmStatic
    fun setDockIntensity(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_DOCK_INTENSITY, value.coerceIn(0, 100)).apply()
    }

    /** 0 Off, 1 Solid, 2 Blur, 3 Crystal, 4 Frosty. Solid preserves Lawnchair defaults. */
    @JvmStatic
    fun getFolderMode(context: Context): Int =
        prefs(context).getInt(KEY_FOLDER_MODE, 1).coerceIn(0, 4)

    @JvmStatic
    fun setFolderMode(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_FOLDER_MODE, value.coerceIn(0, 4)).apply()
    }

    @JvmStatic
    fun getFolderIntensity(context: Context): Int =
        prefs(context).getInt(KEY_FOLDER_INTENSITY, 70).coerceIn(0, 100)

    @JvmStatic
    fun setFolderIntensity(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_FOLDER_INTENSITY, value.coerceIn(0, 100)).apply()
    }
}

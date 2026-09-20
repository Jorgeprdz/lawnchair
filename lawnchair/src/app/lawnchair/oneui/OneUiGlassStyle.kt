/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package app.lawnchair.oneui

/** Canonical One UI glass style ids shared by settings, dock, folders and the renderer. */
object OneUiGlassStyle {
    const val OFF = 0
    const val SOLID = 1
    const val BLUR = 2
    const val CRYSTAL = 3
    const val FROSTY = 4

    @JvmStatic
    fun normalize(value: Int, fallback: Int = SOLID): Int =
        if (value in OFF..FROSTY) value else fallback

    @JvmStatic
    fun isGlass(value: Int): Boolean = value in BLUR..FROSTY
}

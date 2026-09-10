/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 * Distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 */
package com.android.launcher3.widget

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.widget.Toast
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.model.DeviceGridState
import com.android.launcher3.util.Executors.MAIN_EXECUTOR

/** Refuses a destructive grid reduction before the native migrator opens its destination DB. */
object WidgetGridMigrationGuard {
    @JvmStatic
    fun deferIfIncompatible(context: Context, db: SQLiteDatabase, idp: InvariantDeviceProfile): Boolean {
        val source = DeviceGridState(context)
        val columns = source.columns
        val rows = source.rows
        if (columns <= 0 || rows <= 0 ||
            (columns == idp.numColumns && rows == idp.numRows)) return false
        val manager = WidgetManagerHelper(context)
        var incompatible = false
        // Stack membership is read from the same DB, without requiring a partially loaded model.
        db.rawQuery(
            "SELECT w.appWidgetId,w.appWidgetProvider,COALESCE(p.spanX,w.spanX)," +
                "COALESCE(p.spanY,w.spanY) FROM favorites w LEFT JOIN favorites p " +
                "ON w.container=p._id AND p.itemType=? WHERE w.itemType=? " +
                "AND (w.container=? OR p._id IS NOT NULL)",
            arrayOf(Favorites.ITEM_TYPE_WIDGET_STACK.toString(), Favorites.ITEM_TYPE_APPWIDGET.toString(),
                Favorites.CONTAINER_DESKTOP.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val provider = try {
                    manager.getLauncherAppWidgetInfo(cursor.getInt(0),
                        cursor.getString(1)?.let(ComponentName::unflattenFromString))
                } catch (_: RuntimeException) {
                    null
                }
                val spanX = cursor.getInt(2)
                val spanY = cursor.getInt(3)
                val minX = if (provider != null &&
                    provider.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0) {
                    provider.minSpanX.coerceAtLeast(1)
                } else spanX
                val minY = if (provider != null &&
                    provider.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0) {
                    provider.minSpanY.coerceAtLeast(1)
                } else spanY
                if (minX > idp.numColumns || minY > idp.numRows) {
                    incompatible = true
                    break
                }
            }
        }
        if (!incompatible) return false
        MAIN_EXECUTOR.execute {
            // Use the existing grid preferences and IDP reload, retaining the source database.
            val prefs = PreferenceManager.getInstance(context)
            prefs.workspaceColumns.set(columns)
            prefs.workspaceRows.set(rows)
            idp.onConfigChanged(context)
            Toast.makeText(context, R.string.widget_grid_preserved, Toast.LENGTH_LONG).show()
        }
        return true
    }
}

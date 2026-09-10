/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 * Distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 */
package app.lawnchair.qsb

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import app.lawnchair.hotseat.PixelSearchHotseat
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.qsb.providers.PixelSearch
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.qsb.QsbContainerView

/** Provider selection only; binding, configuration, rendering and listening use the native QSB host. */
class PixelSearchQsbFragment : QsbContainerView.QsbFragment() {
    override fun onInit(savedInstanceState: Bundle?) {
        mKeyWidgetId = WIDGET_ID_KEY
        super.onInit(savedInstanceState)
    }

    override fun isQsbEnabled(): Boolean =
        PreferenceManager2.getInstance(context).hotseatMode.firstCached() == PixelSearchHotseat

    override fun getSearchWidgetProvider(): AppWidgetProviderInfo? = try {
        AppWidgetManager.getInstance(context)
            .getInstalledProvidersForPackage(PixelSearch.packageName, android.os.Process.myUserHandle())
            .filter { it.provider.packageName == PixelSearch.packageName }
            .sortedWith(compareByDescending<AppWidgetProviderInfo> {
                it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX != 0
            }.thenBy { it.minHeight }.thenBy { it.provider.flattenToString() })
            .firstOrNull()
    } catch (_: RuntimeException) {
        null
    }

    companion object {
        private const val WIDGET_ID_KEY = "pixel_search_widget_id"

        /** Preference switching deletes only the IDs reserved by this dock widget. */
        @JvmStatic
        fun release(context: Context) {
            val prefs = LauncherPrefs.getPrefs(context)
            val host = AppWidgetHost(context, QSB_WIDGET_HOST_ID)
            val ids = setOf(prefs.getInt(WIDGET_ID_KEY, -1), prefs.getInt(WIDGET_ID_KEY + "_pending", -1))
            ids.filter { it >= 0 }.forEach(host::deleteAppWidgetId)
            prefs.edit().remove(WIDGET_ID_KEY).remove(WIDGET_ID_KEY + "_pending")
                .remove(WIDGET_ID_KEY + "_configured").apply()
        }
    }
}

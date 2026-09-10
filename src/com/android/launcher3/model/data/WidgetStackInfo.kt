/*
 * Copyright (C) 2026 Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.model.data

import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.util.ContentWriter

/**
 * One workspace footprint whose member widget rows use this item's ID as their container.
 * Callers persist membership/rank changes and manage AppWidget IDs through the existing host.
 */
class WidgetStackInfo : CollectionInfo() {
    private val widgets = mutableListOf<LauncherAppWidgetInfo>()

    // OPTIONS stores a member database ID, so reordering does not change the active widget.
    // During load the referenced member may not have been read yet.
    var activeWidgetId: Int = NO_ID

    init {
        itemType = Favorites.ITEM_TYPE_WIDGET_STACK
    }

    override fun add(item: ItemInfo) {
        require(item is LauncherAppWidgetInfo && item.itemType == Favorites.ITEM_TYPE_APPWIDGET) {
            "A widget stack accepts Android AppWidgets only"
        }
        require(widgets.none { it === item || (item.id != NO_ID && it.id == item.id) }) {
            "A widget cannot appear twice in a stack"
        }
        widgets.add(item)
    }

    override fun getContents(): List<LauncherAppWidgetInfo> = widgets.toList()

    override fun getAppContents(): List<WorkspaceItemInfo> = emptyList()

    fun getActiveWidget(): LauncherAppWidgetInfo? =
        widgets.firstOrNull { it.id == activeWidgetId } ?: widgets.firstOrNull()

    /** Run after all members load; rank, rather than cursor row order, determines paging order. */
    fun sortWidgetsByRank() {
        widgets.sortBy { it.rank }
        normalizeRanks()
        activeWidgetId = getActiveWidget()?.id ?: NO_ID
    }

    /** Reorders members while retaining the active widget. Caller persists the changed ranks. */
    fun moveWidget(from: Int, to: Int) {
        require(from in widgets.indices && to in widgets.indices)
        val active = getActiveWidget()
        widgets.add(to, widgets.removeAt(from))
        normalizeRanks()
        activeWidgetId = active?.id ?: NO_ID
    }

    /** Removes only membership. The caller must delete the removed widget's database row and ID. */
    fun removeWidget(widget: LauncherAppWidgetInfo): Boolean {
        val index = widgets.indexOf(widget)
        if (index < 0) return false
        val active = getActiveWidget()
        widgets.removeAt(index)
        normalizeRanks()
        activeWidgetId =
            if (active === widget) widgets.getOrNull(index.coerceAtMost(widgets.lastIndex))?.id ?: NO_ID
            else active?.id ?: NO_ID
        return true
    }

    private fun normalizeRanks() {
        widgets.forEachIndexed { rank, widget -> widget.rank = rank }
    }

    override fun onAddToDatabase(writer: ContentWriter) {
        super.onAddToDatabase(writer)
        writer.put(Favorites.OPTIONS, activeWidgetId)
    }

    override fun copyFrom(info: ItemInfo) {
        if (info === this) return
        super.copyFrom(info)
        widgets.clear()
        if (info is WidgetStackInfo) {
            widgets.addAll(info.widgets)
            activeWidgetId = info.activeWidgetId
        } else {
            activeWidgetId = NO_ID
        }
    }

    override fun makeShallowCopy(): ItemInfo = WidgetStackInfo().also { it.copyFrom(this) }
}

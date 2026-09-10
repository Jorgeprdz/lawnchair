/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */
package com.android.launcher3.widget

import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetHostView
import android.widget.Toast
import com.android.launcher3.Launcher
import com.android.launcher3.PendingAddItemInfo
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WidgetStackInfo

/** Routes stack requests through the launcher's existing widget binding and host. */
object WidgetStackController {
    /** Converts the existing widget row atomically, then reuses its actual host view. */
    @JvmStatic
    fun create(launcher: Launcher, widgetView: LauncherAppWidgetHostView) {
        val member = widgetView.tag as? LauncherAppWidgetInfo ?: return
        if (!widgetView.isAttachedToWindow ||
            !com.android.launcher3.touch.ItemLongClickListener.canStartDrag(launcher)) return
        launcher.modelWriter.createWidgetStack(member, { stack ->
            if (launcher.isDestroyed || !widgetView.isAttachedToWindow) {
                launcher.model.forceReload()
            } else {
                launcher.removeItem(widgetView, member, false, "converted to widget stack")
                val stackView = WidgetStackView(launcher)
                stackView.bind(stack, launcher.modelWriter, listOf(widgetView))
                launcher.workspace.addInScreen(stackView, stack)
                app.lawnchair.widgetstack.WidgetStackEditor.show(launcher, stackView)
            }
        }, {
            Toast.makeText(launcher, R.string.widget_stack_create_failed, Toast.LENGTH_SHORT).show()
        })
    }

    @JvmStatic
    fun findStack(launcher: Launcher, id: Int): WidgetStackView? =
        launcher.workspace.mapOverItems { item, view ->
            item?.id == id && view is WidgetStackView
        } as? WidgetStackView

    private fun fits(stack: WidgetStackInfo, provider: LauncherAppWidgetProviderInfo): Boolean {
        if (provider.isCustomWidget) return false
        val horizontal = provider.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
        val vertical = provider.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        val minX = if (horizontal) provider.minSpanX else provider.spanX
        val minY = if (vertical) provider.minSpanY else provider.spanY
        val maxX = if (horizontal) provider.maxSpanX else provider.spanX
        val maxY = if (vertical) provider.maxSpanY else provider.spanY
        return stack.spanX >= minX && stack.spanY >= minY &&
            (maxX <= 0 || stack.spanX <= maxX) && (maxY <= 0 || stack.spanY <= maxY)
    }

    @JvmStatic
    fun requestAdd(launcher: Launcher, stackId: Int, selected: PendingAddItemInfo) {
        val stack = findStack(launcher, stackId)?.tag as? WidgetStackInfo
        if (stack == null || selected !is PendingAddWidgetInfo || !fits(stack, selected.info)) {
            Toast.makeText(launcher, R.string.widget_stack_incompatible, Toast.LENGTH_SHORT).show()
            return
        }
        selected.bindOptions = com.android.launcher3.widget.util.WidgetSizes.getWidgetSizeOptions(
            launcher, selected.componentName, stack.spanX, stack.spanY,
        )
        launcher.addPendingItem(selected, stack.id, stack.screenId, intArrayOf(0, 0),
            stack.spanX, stack.spanY)
    }

    /** A nonnegative widget container is a persistent stack ID, even if it was since removed. */
    @JvmStatic
    fun completeAdd(launcher: Launcher, container: Int, member: LauncherAppWidgetInfo,
        provider: LauncherAppWidgetProviderInfo, view: AppWidgetHostView): Boolean {
        if (container < 0) return false
        val stackView = findStack(launcher, container)
        val stack = stackView?.tag as? WidgetStackInfo
        if (stackView == null || stack == null || !fits(stack, provider)) {
            launcher.appWidgetHolder.deleteAppWidgetId(member.appWidgetId)
            Toast.makeText(launcher, R.string.widget_stack_incompatible, Toast.LENGTH_SHORT).show()
            return true
        }
        if (stack.getContents().any { it.appWidgetId == member.appWidgetId }) return true
        member.spanX = stack.spanX
        member.spanY = stack.spanY
        launcher.modelWriter.addWidgetToStack(stack, member, {
            if (launcher.isDestroyed || !stackView.isAttachedToWindow) {
                launcher.model.forceReload()
            } else {
                launcher.itemInflater.prepareAppWidget(view, member)
                val pages = stack.getContents().map { item ->
                    if (item === member) view else stackView.findWidgetByAppWidgetId(item.appWidgetId)
                        ?: launcher.itemInflater.inflateWidgetStackMember(item)
                }
                stackView.bind(stack, launcher.modelWriter, pages)
            }
        }, {
            launcher.appWidgetHolder.deleteAppWidgetId(member.appWidgetId)
            Toast.makeText(launcher, R.string.widget_stack_incompatible, Toast.LENGTH_SHORT).show()
        })
        return true
    }

    @JvmStatic
    fun refresh(launcher: Launcher, view: WidgetStackView) {
        val stack = view.tag as WidgetStackInfo
        val pages = stack.getContents().map { member ->
            view.findWidgetByAppWidgetId(member.appWidgetId)
                ?: launcher.itemInflater.inflateWidgetStackMember(member)
        }
        view.bind(stack, launcher.modelWriter, pages)
    }

    @JvmStatic
    fun remove(launcher: Launcher, view: WidgetStackView, member: LauncherAppWidgetInfo) {
        val stack = view.tag as WidgetStackInfo
        if (member !in stack.getContents()) return
        if (stack.getContents().size == 1) {
            launcher.removeItem(view, stack, true, "last widget removed from stack")
            return
        }
        stack.removeWidget(member)
        launcher.modelWriter.deleteWidgetInfo(member, launcher.appWidgetHolder, "stack member removed")
        persistOrder(launcher, stack)
        refresh(launcher, view)
    }

    /** Every member must accept the same dimensions before CellLayout can reserve them. */
    fun resize(launcher: Launcher, view: WidgetStackView, spanX: Int, spanY: Int): Boolean {
        val stack = view.tag as WidgetStackInfo
        if (!view.isAttachedToWindow || spanX < 1 || spanY < 1) return false
        val candidate = stack.makeShallowCopy() as WidgetStackInfo
        candidate.spanX = spanX
        candidate.spanY = spanY
        for (member in stack.getContents()) {
            val provider = view.findWidgetByAppWidgetId(member.appWidgetId)?.appWidgetInfo
                as? LauncherAppWidgetProviderInfo ?: return false
            if (!fits(candidate, provider)) return false
        }
        val layout = launcher.workspace.getParentCellLayoutForView(view) ?: return false
        if (!layout.resizeWidgetStack(view, spanX, spanY)) return false
        for (member in stack.getContents()) {
            member.spanX = spanX
            member.spanY = spanY
            launcher.modelWriter.updateItemInDatabase(member)
            view.findWidgetByAppWidgetId(member.appWidgetId)?.let { host ->
                com.android.launcher3.widget.util.WidgetSizes.updateWidgetSizeRanges(
                    host, launcher, spanX, spanY,
                )
            }
        }
        return true
    }

    fun persistOrder(launcher: Launcher, stack: WidgetStackInfo) {
        launcher.modelWriter.moveItemsInDatabase(ArrayList<ItemInfo>(stack.getContents()),
            stack.id, stack.screenId)
        launcher.modelWriter.updateItemInDatabase(stack)
    }
}

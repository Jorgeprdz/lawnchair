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
package com.android.launcher3.widget

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.launcher3.PagedView
import com.android.launcher3.Reorderable
import com.android.launcher3.dragndrop.DraggableView
import com.android.launcher3.model.ModelWriter
import com.android.launcher3.model.data.WidgetStackInfo
import com.android.launcher3.pageindicators.PageIndicatorDots
import com.android.launcher3.util.MultiTranslateDelegate
import kotlin.math.abs

/** Hosts one page per real AppWidget while occupying a single CellLayout item. */
class WidgetStackView(context: Context) : FrameLayout(context), DraggableView, Reorderable {
    private val translationDelegate = MultiTranslateDelegate(this)
    private var bounceScale = 1f
    private val indicator = PageIndicatorDots(context)
    private val pager = StackPager(context)
    private lateinit var stack: WidgetStackInfo
    private lateinit var writer: ModelWriter

    init {
        clipChildren = true
        clipToPadding = true
        pager.attachIndicator(indicator)
        addView(pager, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        indicator.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        indicator.isClickable = false
        indicator.setShouldAutoHide(false)
        addView(indicator, LayoutParams(
            LayoutParams.MATCH_PARENT,
            (16 * resources.displayMetrics.density).toInt(),
            Gravity.BOTTOM,
        ))
        setOnLongClickListener {
            app.lawnchair.widgetstack.WidgetStackEditor.show(
                com.android.launcher3.Launcher.getLauncher(context), this,
            )
            true
        }
    }

    fun bind(info: WidgetStackInfo, modelWriter: ModelWriter, views: List<View>) {
        // Rebuilding pages must not persist a transient page as the user's selection.
        pager.onSettled = null
        stack = info
        writer = modelWriter
        tag = info
        pager.removeAllViews()
        views.forEach { view ->
            // The existing AppWidget host may return a view from the previous binding.
            (view.parent as? ViewGroup)?.removeView(view)
            val page = FrameLayout(context)
            view.setOnLongClickListener { performLongClick() }
            page.addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            pager.addView(page, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
        indicator.setMarkersCount(views.size)
        indicator.visibility = if (views.size > 1) VISIBLE else GONE
        val activePage = info.getContents().indexOf(info.getActiveWidget()).coerceAtLeast(0)
        pager.runOnPageScrollsInitialized {
            if (pager.childCount > 0) {
                pager.currentPage = activePage
                updatePageAccessibility()
                pager.onSettled = ::saveActivePage
            }
        }
    }

    private fun saveActivePage() {
        if (!::stack.isInitialized) return
        val active = stack.getContents().getOrNull(pager.currentPage) ?: return
        if (stack.activeWidgetId != active.id) {
            stack.activeWidgetId = active.id
            writer.updateItemInDatabase(stack)
        }
        updatePageAccessibility()
    }

    private fun updatePageAccessibility() {
        for (i in 0 until pager.childCount) {
            pager.getChildAt(i).importantForAccessibility =
                if (i == pager.currentPage) IMPORTANT_FOR_ACCESSIBILITY_AUTO
                else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    /** Replaces one page's host without changing membership, order, or the active page. */
    fun replaceWidgetView(previous: View, replacement: View) {
        for (i in 0 until pager.childCount) {
            val page = pager.getChildAt(i) as FrameLayout
            if (page.getChildAt(0) !== previous) continue
            if (replacement === previous) return
            (replacement.parent as? ViewGroup)?.removeView(replacement)
            page.removeView(previous)
            replacement.setOnLongClickListener { performLongClick() }
            page.addView(replacement, LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
            ))
            updatePageAccessibility()
            return
        }
    }

    /** Finds the actual host view, including widgets on inactive pages. */
    fun findWidgetByAppWidgetId(appWidgetId: Int): LauncherAppWidgetHostView? {
        for (i in 0 until pager.childCount) {
            val page = pager.getChildAt(i) as FrameLayout
            val host = page.getChildAt(0) as? LauncherAppWidgetHostView ?: continue
            if (host.appWidgetId == appWidgetId) return host
        }
        return null
    }

    /** Connects views created during asynchronous inflation to the activity's existing host. */
    fun attachWidgetsToHost(holder: LauncherWidgetHolder) {
        for (i in 0 until pager.childCount) {
            val page = pager.getChildAt(i) as FrameLayout
            val child = page.getChildAt(0)
            if (child is LauncherAppWidgetHostView) {
                val attached = holder.attachViewToHostAndGetAttachedView(child)
                if (attached !== child) {
                    page.removeView(child)
                    (attached.parent as? ViewGroup)?.removeView(attached)
                    attached.tag = child.tag
                    attached.setOnLongClickListener { performLongClick() }
                    page.addView(attached, LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
                    ))
                }
            }
        }
    }

    override fun getViewType() = DraggableView.DRAGGABLE_WIDGET

    override fun getWorkspaceVisualDragBounds(bounds: Rect) {
        bounds.set(0, 0, width, height)
    }

    override fun getTranslateDelegate() = translationDelegate

    override fun getReorderBounceScale() = bounceScale

    override fun setReorderBounceScale(scale: Float) {
        bounceScale = scale
        scaleX = scale
        scaleY = scale
    }

    private class StackPager(context: Context) : PagedView<PageIndicatorDots>(context) {
        var onSettled: (() -> Unit)? = null
        private var downX = 0f
        private var downY = 0f
        private var verticalGesture = false
        private val slop = ViewConfiguration.get(context).scaledTouchSlop

        fun attachIndicator(indicator: PageIndicatorDots) {
            mPageIndicator = indicator
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                downX = event.x
                downY = event.y
                verticalGesture = false
                parent?.requestDisallowInterceptTouchEvent(childCount > 1)
            }
            return try {
                super.dispatchTouchEvent(event)
            } finally {
                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
        }

        override fun determineScrollingStart(event: MotionEvent) {
            val dx = abs(event.x - downX)
            val dy = abs(event.y - downY)
            if (dy > slop && dy > dx) verticalGesture = true
            if (!verticalGesture && dx > dy && childCount > 1) {
                super.determineScrollingStart(event)
            }
        }

        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            // Keep direction arbitration here; child widgets still receive vertical gestures.
            parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
        }

        override fun onPageEndTransition() {
            if (childCount == 0) return
            super.onPageEndTransition()
            onSettled?.invoke()
        }

        override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
            super.onScrollChanged(l, t, oldl, oldt)
            if (childCount > 1 && isPageScrollsInitialized()) {
                val total = maxOf(getScrollForPage(0), getScrollForPage(childCount - 1))
                mPageIndicator?.setScroll(l, total)
            }
        }
    }
}

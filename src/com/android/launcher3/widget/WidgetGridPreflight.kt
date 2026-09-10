package com.android.launcher3.widget

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import com.android.launcher3.LauncherAppState
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlinx.coroutines.suspendCancellableCoroutine

/** Prevents an explicit grid preference change from dropping fixed-size widget footprints. */
object WidgetGridPreflight {
    suspend fun canApply(context: Context, columns: Int, rows: Int): Boolean =
        suspendCancellableCoroutine { result ->
            val app = LauncherAppState.getInstance(context)
            val oldColumns = app.invariantDeviceProfile.numColumns.coerceAtLeast(1)
            val oldRows = app.invariantDeviceProfile.numRows.coerceAtLeast(1)
            app.model.loadAsync { model ->
                if (!result.isActive) return@loadAsync
                val manager = WidgetManagerHelper(context)
                var fits = model != null && columns > 0 && rows > 0
                if (model != null) {
                    for (item in model.itemsIdMap) {
                        if (item !is LauncherAppWidgetInfo || item.isCustomWidget) continue
                        val provider = try {
                            manager.getLauncherAppWidgetInfo(item.appWidgetId, item.providerName)
                        } catch (_: RuntimeException) {
                            null
                        }
                        val horizontal = provider != null &&
                            provider.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
                        val vertical = provider != null &&
                            provider.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
                        val minX = if (horizontal) {
                            ceil(provider!!.minSpanX.coerceAtLeast(1).toDouble() * columns / oldColumns).toInt()
                        } else item.spanX
                        val minY = if (vertical) {
                            ceil(provider!!.minSpanY.coerceAtLeast(1).toDouble() * rows / oldRows).toInt()
                        } else item.spanY
                        if (minX > columns || minY > rows) {
                            fits = false
                            break
                        }
                    }
                }
                if (result.isActive) result.resume(fits)
            }
        }
}

package app.lawnchair.widgetstack

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.reorderable.ReorderableDragHandle
import app.lawnchair.ui.preferences.components.reorderable.a11yDrag
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WidgetStackInfo
import com.android.launcher3.touch.ItemLongClickListener
import com.android.launcher3.widget.WidgetStackController
import com.android.launcher3.widget.WidgetStackView
import com.android.launcher3.widget.picker.WidgetsFullSheet
import sh.calvin.reorderable.ReorderableColumn

/** Uses Lawnchair's sheet and reorder controls; all members remain real hosted widgets. */
object WidgetStackEditor {
    @JvmStatic
    fun show(launcher: Launcher, view: WidgetStackView) {
        if (!ItemLongClickListener.canStartDrag(launcher)) return
        val stack = view.tag as? WidgetStackInfo ?: return
        ComposeBottomSheet.show(launcher, PaddingValues(16.dp)) {
            var members by remember { mutableStateOf(stack.getContents()) }
            var active by remember { mutableStateOf(stack.getActiveWidget()?.id) }
            var size by remember { mutableStateOf(stack.spanX to stack.spanY) }
            val resize: (Int, Int) -> Unit = { x, y ->
                if (WidgetStackController.resize(launcher, view, x, y)) {
                    size = stack.spanX to stack.spanY
                } else {
                    Toast.makeText(launcher, R.string.widget_stack_resize_failed, Toast.LENGTH_SHORT).show()
                }
            }
            val reorder: (List<LauncherAppWidgetInfo>) -> Unit = { ordered ->
                ordered.forEachIndexed { index, member ->
                    val from = stack.getContents().indexOf(member)
                    if (from >= 0 && from != index) stack.moveWidget(from, index)
                }
                WidgetStackController.persistOrder(launcher, stack)
                WidgetStackController.refresh(launcher, view)
                members = stack.getContents()
                active = stack.getActiveWidget()?.id
            }
            Column(
                modifier = Modifier
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.widget_stack_title), style = MaterialTheme.typography.titleLarge)
                Column {
                    ReorderableColumn(
                        list = members,
                        onSettle = { from, to ->
                            reorder(members.toMutableList().apply { add(to, removeAt(from)) })
                        },
                    ) { index, member, _ ->
                        key(member.id) {
                            ReorderableItem {
                                val reorderScope = this
                                Row(Modifier.fillMaxWidth().a11yDrag(index, members, reorder, reorder)) {
                                    ReorderableDragHandle(scope = reorderScope)
                                    val label = remember(member.id) {
                                        view.findWidgetByAppWidgetId(member.appWidgetId)?.appWidgetInfo
                                            ?.loadLabel(launcher.packageManager)
                                            ?: member.providerName?.shortClassName.orEmpty()
                                    }
                                    TextButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            stack.activeWidgetId = member.id
                                            launcher.modelWriter.updateItemInDatabase(stack)
                                            WidgetStackController.refresh(launcher, view)
                                            active = member.id
                                        },
                                    ) {
                                        Column {
                                            Text(label)
                                            if (active == member.id) Text(stringResource(R.string.widget_stack_active))
                                        }
                                    }
                                    TextButton(onClick = {
                                        WidgetStackController.remove(launcher, view, member)
                                        if (members.size == 1) {
                                            close(true)
                                        } else {
                                            members = stack.getContents()
                                            active = stack.getActiveWidget()?.id
                                        }
                                    }) { Text(stringResource(R.string.widget_stack_remove)) }
                                }
                            }
                        }
                    }
                }
                Text(stringResource(R.string.widget_stack_dimensions, size.first, size.second))
                Row {
                    TextButton(onClick = { resize(size.first - 1, size.second) }) {
                        Text(stringResource(R.string.widget_stack_narrower))
                    }
                    TextButton(onClick = { resize(size.first + 1, size.second) }) {
                        Text(stringResource(R.string.widget_stack_wider))
                    }
                }
                Row {
                    TextButton(onClick = { resize(size.first, size.second - 1) }) {
                        Text(stringResource(R.string.widget_stack_shorter))
                    }
                    TextButton(onClick = { resize(size.first, size.second + 1) }) {
                        Text(stringResource(R.string.widget_stack_taller))
                    }
                }
                Row {
                    TextButton(onClick = {
                        addOnCloseListener {
                            WidgetsFullSheet.show(launcher, true).setWidgetStackTarget(stack.id)
                        }
                        close(true)
                    }) { Text(stringResource(R.string.widget_stack_add)) }
                    TextButton(onClick = {
                        addOnCloseListener {
                            launcher.accessibilityDelegate.performAccessibilityAction(view, R.id.action_move, null)
                        }
                        close(true)
                    }) { Text(stringResource(R.string.widget_stack_move)) }
                }
            }
        }
    }
}

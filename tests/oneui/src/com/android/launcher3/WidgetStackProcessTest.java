package com.android.launcher3;

import static org.junit.Assert.*;
import static com.android.launcher3.WidgetStackBindingTest.*;
import android.content.ComponentName;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WidgetStackInfo;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.OneUiTestWidgetProvider;
import com.android.launcher3.widget.WidgetStackController;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** The CI host force-stops the launcher between these two separate instrumentation invocations. */
@RunWith(AndroidJUnit4.class)
public class WidgetStackProcessTest {
    @Test
    public void stackSurvivesProcessDeath() throws Exception {
        String phase = InstrumentationRegistry.getArguments().getString("oneuiPhase");
        org.junit.Assume.assumeTrue("seed".equals(phase) || "verify".equals(phase));
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var context = instrumentation.getTargetContext();
        var state = context.getSharedPreferences("oneui-process", 0);
        ComponentName component = new ComponentName(instrumentation.getContext().getPackageName(),
                OneUiTestWidgetProvider.class.getName());
        try (var input = new ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.getUiAutomation().executeShellCommand(
                        "appwidget grantbind --package " + context.getPackageName() + " --user 0"))) {
            while (input.read() != -1) { }
        }
        Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setComponent(new ComponentName(context.getPackageName(), "app.lawnchair.LawnchairLauncher"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<Launcher> scenario = ActivityScenario.launch(launch)) {
            await(scenario, ItemLongClickListener::canStartDrag);
            if ("seed".equals(phase)) {
                AtomicReference<LauncherAppWidgetInfo> first = new AtomicReference<>();
                AtomicReference<LauncherAppWidgetHostView> host = new AtomicReference<>();
                scenario.onActivity(launcher -> {
                    int screen = launcher.getWorkspace().getScreenIdForPageIndex(0);
                    CellLayout grid = launcher.getWorkspace().getScreenWithId(screen);
                    int[] cell = new int[2];
                    assertTrue(grid.findCellForSpan(cell, 2, 2));
                    var provider = provider(launcher, component);
                    int id = bind(launcher, provider, new ArrayList<>());
                    var item = new LauncherAppWidgetInfo(id, component);
                    item.spanX = item.spanY = 2;
                    launcher.getModelWriter().addItemToDatabase(item,
                            LauncherSettings.Favorites.CONTAINER_DESKTOP, screen, cell[0], cell[1]);
                    var view = (LauncherAppWidgetHostView) launcher.getAppWidgetHolder().createView(id, provider);
                    launcher.getItemInflater().prepareAppWidget(view, item);
                    launcher.getWorkspace().addInScreen(view, item);
                    first.set(item);
                    host.set(view);
                });
                drainModel();
                scenario.onActivity(launcher -> WidgetStackController.create(launcher, host.get()));
                await(scenario, launcher -> first.get().container >= 0
                        && WidgetStackController.findStack(launcher, first.get().container) != null);
                scenario.onActivity(launcher -> {
                    AbstractFloatingView.closeAllOpenViews(launcher);
                    var provider = provider(launcher, component);
                    int id = bind(launcher, provider, new ArrayList<>());
                    assertTrue(WidgetStackController.completeAdd(launcher, first.get().container,
                            new LauncherAppWidgetInfo(id, component), provider,
                            launcher.getAppWidgetHolder().createView(id, provider)));
                });
                await(scenario, launcher -> info(launcher, first.get().container).getContents().size() == 2);
                scenario.onActivity(launcher -> {
                    WidgetStackInfo stack = info(launcher, first.get().container);
                    assertTrue("Process fixture must be durably saved", state.edit().putInt("stack", stack.id).putInt("active", stack.getActiveWidgetId())
                            .putInt("first", stack.getContents().get(0).appWidgetId)
                            .putInt("second", stack.getContents().get(1).appWidgetId).commit());
                });
                drainModel();
            } else {
                int id = state.getInt("stack", -1);
                assertTrue("Seed phase must save a real stack", id >= 0);
                await(scenario, launcher -> WidgetStackController.findStack(launcher, id) != null);
                scenario.onActivity(launcher -> {
                    var view = WidgetStackController.findStack(launcher, id);
                    WidgetStackInfo stack = (WidgetStackInfo) view.getTag();
                    assertEquals(2, stack.getContents().size());
                    assertEquals(state.getInt("active", -1), stack.getActiveWidgetId());
                    assertEquals(state.getInt("first", -1), stack.getContents().get(0).appWidgetId);
                    assertEquals(state.getInt("second", -1), stack.getContents().get(1).appWidgetId);
                    assertEquals(2, stack.spanX);
                    assertEquals(2, stack.spanY);
                    for (var member : stack.getContents()) {
                        assertEquals(id, member.container);
                        assertNotNull(view.findWidgetByAppWidgetId(member.appWidgetId));
                    }
                    launcher.removeItem(view, stack, true, "OneUI process test cleanup");
                });
                drainModel();
                state.edit().clear().commit();
            }
        }
    }
}

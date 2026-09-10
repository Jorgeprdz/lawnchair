package com.android.launcher3;

import static org.junit.Assert.*;
import static com.android.launcher3.icons.cache.CacheLookupFlag.DEFAULT_LOOKUP_FLAG;

import android.content.Intent;
import android.content.pm.LauncherApps;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.lawnchair.LawnchairLauncher;
import app.lawnchair.views.LawnchairFloatingSurfaceView;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercises the actual GNC surface handoff without requiring an OEM gesture service. */
@RunWith(AndroidJUnit4.class)
public class GestureHomeReturnTest {
    @Test
    public void surfaceCompletionAndLostCallbackRestoreIconWithoutMovingIt() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClassName(context, "app.lawnchair.LawnchairLauncher")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<Launcher> scenario = ActivityScenario.launch(home)) {
            WidgetStackBindingTest.await(scenario, l -> !l.isWorkspaceLoading());
            AppInfo app = Executors.MODEL_EXECUTOR.submit(() -> {
                var activities = context.getSystemService(LauncherApps.class)
                        .getActivityList("com.android.settings", Process.myUserHandle());
                assertFalse(activities.isEmpty());
                var info = new AppInfo(context, activities.get(0), Process.myUserHandle());
                LauncherAppState.getInstance(context).getIconCache()
                        .getTitleAndIcon(info, activities.get(0), DEFAULT_LOOKUP_FLAG);
                return info;
            }).get(30, TimeUnit.SECONDS);
            AtomicReference<BubbleTextView> icon = new AtomicReference<>();
            AtomicReference<Message> response = new AtomicReference<>();
            Messenger receiver = new Messenger(new Handler(Looper.getMainLooper(), message -> {
                response.set(Message.obtain(message));
                return true;
            }));
            Message callback = Message.obtain();
            callback.replyTo = receiver;
            try {
                scenario.onActivity(l -> {
                    int screen = l.getWorkspace().getScreenIdForPageIndex(0);
                    CellLayout grid = l.getWorkspace().getScreenWithId(screen);
                    int[] cell = new int[2];
                    assertTrue(grid.findCellForSpan(cell, 1, 1));
                    WorkspaceItemInfo item = new WorkspaceItemInfo(app);
                    l.getModelWriter().addItemToDatabase(item,
                            LauncherSettings.Favorites.CONTAINER_DESKTOP, screen, cell[0], cell[1]);
                    BubbleTextView view = (BubbleTextView) l.getItemInflater().inflateItem(item, grid);
                    l.getWorkspace().addInScreen(view, item);
                    icon.set(view);
                });
                WidgetStackBindingTest.await(scenario, l -> icon.get().isLaidOut()
                        && !icon.get().isLayoutRequested());
                Message previousFinish = null;
                for (int gesture = 0; gesture < 2; gesture++) {
                    response.set(null);
                    scenario.onActivity(l -> LawnchairFloatingSurfaceView.Companion.show(
                            (LawnchairLauncher) l,
                            new GestureNavContract(app.componentName, Process.myUserHandle(), callback)));
                    WidgetStackBindingTest.await(scenario, l -> response.get() != null);
                    Message finish = response.get().getData()
                            .getParcelable(GestureNavContract.EXTRA_ON_FINISH_CALLBACK);
                    RectF position = response.get().getData()
                            .getParcelable(GestureNavContract.EXTRA_ICON_POSITION);
                    assertNotNull(position);
                    assertTrue(position.width() > 0 && position.height() > 0);
                    scenario.onActivity(l -> {
                        assertEquals(0f, icon.get().getTranslationX(), 0f);
                        assertEquals(0f, icon.get().getTranslationY(), 0f);
                    });
                    if (previousFinish != null) {
                        previousFinish.replyTo.send(previousFinish);
                        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                        scenario.onActivity(l -> assertTrue(AbstractFloatingView.hasOpenView(
                                l, AbstractFloatingView.TYPE_ICON_SURFACE)));
                    }
                    previousFinish = Message.obtain(finish);
                    if (gesture == 0) {
                        assertNotNull(finish);
                        finish.replyTo.send(finish);
                    }
                    // Second gesture deliberately loses the finish callback: bounded cleanup restores it.
                    WidgetStackBindingTest.await(scenario, l -> !AbstractFloatingView.hasOpenView(
                            l, AbstractFloatingView.TYPE_ICON_SURFACE));
                    scenario.onActivity(l -> {
                        assertEquals(View.VISIBLE, icon.get().getVisibility());
                        assertSame(icon.get().getIcon(), icon.get().getCompoundDrawables()[1]);
                        assertEquals(0f, icon.get().getTranslationX(), 0f);
                        assertEquals(0f, icon.get().getTranslationY(), 0f);
                    });
                }
            } finally {
                scenario.onActivity(l -> {
                    AbstractFloatingView.closeOpenViews(l, false, AbstractFloatingView.TYPE_ICON_SURFACE);
                    if (icon.get() != null) l.removeItem(icon.get(),
                            (WorkspaceItemInfo) icon.get().getTag(), true, "GNC test cleanup");
                });
            }
        }
    }
}

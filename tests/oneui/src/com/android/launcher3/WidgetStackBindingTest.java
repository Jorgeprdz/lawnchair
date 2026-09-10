/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */
package com.android.launcher3;

import static org.junit.Assert.*;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.widget.TextView;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.widget.OneUiTestWidgetProvider;
import com.android.launcher3.widget.WidgetStackController;
import com.android.launcher3.widget.WidgetStackView;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.WidgetAddFlowHandler;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WidgetStackInfo;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.PendingRequestArgs;
import com.android.launcher3.LauncherConstants.ActivityCodes;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/** Real host IDs, model writes and model reload on the disposable remote emulator. */
@RunWith(AndroidJUnit4.class)
public class WidgetStackBindingTest {
    @Test
    public void realWidgetsShareFootprintAndRetainMembershipAcrossModelReload() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        String target = instrumentation.getTargetContext().getPackageName();
        ComponentName component = new ComponentName(instrumentation.getContext().getPackageName(),
                OneUiTestWidgetProvider.class.getName());
        try (var output = new ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.getUiAutomation().executeShellCommand(
                        "appwidget grantbind --package " + target + " --user 0"))) {
            while (output.read() != -1) { /* Drain the command before binding. */ }
        }
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName(target, "app.lawnchair.LawnchairLauncher"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        AtomicInteger stackId = new AtomicInteger(-1);
        AtomicInteger screen = new AtomicInteger();
        AtomicInteger baselineCount = new AtomicInteger();
        AtomicReference<LauncherAppWidgetInfo> first = new AtomicReference<>();
        AtomicReference<LauncherAppWidgetHostView> firstHost = new AtomicReference<>();
        List<Integer> allocatedIds = new ArrayList<>();
        try (ActivityScenario<Launcher> scenario = ActivityScenario.launch(intent)) {
            try {
                await(scenario, ItemLongClickListener::canStartDrag);
                scenario.onActivity(launcher -> {
                    screen.set(launcher.getWorkspace().getScreenIdForPageIndex(0));
                    CellLayout grid = launcher.getWorkspace().getScreenWithId(screen.get());
                    int[] cell = new int[2];
                    assertTrue("The test needs room for placement and resize", grid.findCellForSpan(cell, 3, 2));
                    baselineCount.set(grid.getShortcutsAndWidgets().getChildCount());
                    LauncherAppWidgetProviderInfo provider = provider(launcher, component);
                    int id = bind(launcher, provider, allocatedIds);
                    LauncherAppWidgetInfo item = new LauncherAppWidgetInfo(id, component);
                    item.spanX = item.spanY = 2;
                    LauncherAppWidgetHostView host = (LauncherAppWidgetHostView)
                            launcher.getAppWidgetHolder().createView(id, provider);
                    launcher.getModelWriter().addItemToDatabase(item, Favorites.CONTAINER_DESKTOP,
                            screen.get(), cell[0], cell[1]);
                    launcher.getItemInflater().prepareAppWidget(host, item);
                    launcher.getWorkspace().addInScreen(host, item);
                    first.set(item);
                    firstHost.set(host);
                });
                drainModel();
                scenario.onActivity(launcher -> WidgetStackController.create(launcher, firstHost.get()));
                await(scenario, launcher -> {
                    int id = first.get().container;
                    if (id < 0 || WidgetStackController.findStack(launcher, id) == null) return false;
                    stackId.set(id);
                    return true;
                });
                scenario.onActivity(launcher -> {
                    AbstractFloatingView.closeAllOpenViews(launcher);
                    LauncherAppWidgetProviderInfo provider = provider(launcher, component);
                    int cancelledId = launcher.getAppWidgetHolder().allocateAppWidgetId();
                    allocatedIds.add(cancelledId);
                    LauncherAppWidgetInfo pending = new LauncherAppWidgetInfo(cancelledId, component);
                    pending.container = stackId.get();
                    pending.screenId = screen.get();
                    launcher.setWaitingForResult(PendingRequestArgs.forWidgetInfo(cancelledId,
                            new WidgetAddFlowHandler(provider), pending));
                    launcher.onActivityResult(ActivityCodes.REQUEST_BIND_APPWIDGET,
                            Activity.RESULT_CANCELED, null);
                    assertFalse("Cancellation without an Intent must release its allocated ID",
                            Arrays.stream(launcher.getAppWidgetHolder().getAppWidgetIds())
                                    .anyMatch(id -> id == cancelledId));
                    assertEquals(1, info(launcher, stackId.get()).getContents().size());
                    int id = bind(launcher, provider, allocatedIds);
                    LauncherAppWidgetInfo member = new LauncherAppWidgetInfo(id, component);
                    assertTrue(WidgetStackController.completeAdd(launcher, stackId.get(), member,
                            provider, launcher.getAppWidgetHolder().createView(id, provider)));
                });
                await(scenario, launcher -> info(launcher, stackId.get()).getContents().size() == 2);
                Rect tapBounds = new Rect();
                await(scenario, launcher -> {
                    TextView text = activeText(launcher, stackId.get());
                    return text != null && text.getText().toString().startsWith("OneUI test widget")
                            && text.getGlobalVisibleRect(tapBounds);
                });
                sendGesture(tapBounds.centerX(), tapBounds.centerY(),
                        tapBounds.centerX(), tapBounds.centerY(), false);
                await(scenario, launcher -> {
                    TextView text = activeText(launcher, stackId.get());
                    return text != null && text.getText().toString().startsWith("Clicked widget");
                });
                Rect stackBounds = new Rect();
                AtomicInteger firstRow = new AtomicInteger();
                AtomicInteger secondRow = new AtomicInteger();
                AtomicInteger workspacePage = new AtomicInteger();
                scenario.onActivity(launcher -> {
                    WidgetStackView view = WidgetStackController.findStack(launcher, stackId.get());
                    assertTrue(view.getGlobalVisibleRect(stackBounds));
                    WidgetStackInfo stack = (WidgetStackInfo) view.getTag();
                    firstRow.set(stack.getContents().get(0).id);
                    secondRow.set(stack.getContents().get(1).id);
                    workspacePage.set(launcher.getWorkspace().getCurrentPage());
                    PagedView<?> pager = (PagedView<?>) view.getChildAt(0);
                    assertEquals("The newly added member must be visibly selected", 1, pager.getCurrentPage());
                    pager.setOnTouchListener((v, event) -> {
                        android.util.Log.i("OneUiPagingTest", "touch=" + event.getActionMasked()
                                + " x=" + event.getX() + " y=" + event.getY()
                                + " scroll=" + pager.getScrollX());
                        return false;
                    });
                    logPagingState(launcher, stackId.get());
                });
                float left = stackBounds.left + stackBounds.width() * 0.2f;
                float right = stackBounds.left + stackBounds.width() * 0.8f;
                sendGesture(left, stackBounds.centerY(), right, stackBounds.centerY(), true);
                awaitPage(scenario, stackId.get(), firstRow.get());
                sendGesture(right, stackBounds.centerY(), left, stackBounds.centerY(), true);
                awaitPage(scenario, stackId.get(), secondRow.get());
                scenario.onActivity(launcher -> assertEquals(workspacePage.get(),
                        launcher.getWorkspace().getCurrentPage()));
                AtomicInteger active = new AtomicInteger();
                AtomicReference<WidgetStackView> oldView = new AtomicReference<>();
                scenario.onActivity(launcher -> {
                    WidgetStackView view = WidgetStackController.findStack(launcher, stackId.get());
                    oldView.set(view);
                    WidgetStackInfo stack = (WidgetStackInfo) view.getTag();
                    assertEquals(baselineCount.get() + 1, launcher.getWorkspace()
                            .getScreenWithId(screen.get()).getShortcutsAndWidgets().getChildCount());
                    for (LauncherAppWidgetInfo member : stack.getContents()) {
                        assertEquals(stack.id, member.container);
                        assertEquals(stack.spanX, member.spanX);
                        assertNotNull(view.findWidgetByAppWidgetId(member.appWidgetId));
                    }
                    assertTrue("Both providers support a shared 3x2 footprint",
                            WidgetStackController.INSTANCE.resize(launcher, view, 3, 2));
                    assertEquals(3, stack.spanX);
                    assertEquals(2, stack.spanY);
                    for (LauncherAppWidgetInfo member : stack.getContents()) {
                        assertEquals(stack.spanX, member.spanX);
                        assertEquals(stack.spanY, member.spanY);
                    }
                    active.set(stack.getActiveWidgetId());
                    stack.moveWidget(1, 0);
                    WidgetStackController.INSTANCE.persistOrder(launcher, stack);
                    WidgetStackController.refresh(launcher, view);
                });
                drainModel();
                scenario.recreate();
                await(scenario, ItemLongClickListener::canStartDrag);
                scenario.onActivity(launcher -> launcher.getModel().forceReload());
                await(scenario, launcher -> {
                    WidgetStackView view = WidgetStackController.findStack(launcher, stackId.get());
                    return view != null && view != oldView.get() && ItemLongClickListener.canStartDrag(launcher);
                });
                scenario.onActivity(launcher -> {
                    WidgetStackView view = WidgetStackController.findStack(launcher, stackId.get());
                    WidgetStackInfo stack = (WidgetStackInfo) view.getTag();
                    assertEquals(2, stack.getContents().size());
                    assertEquals(3, stack.spanX);
                    assertEquals(2, stack.spanY);
                    for (LauncherAppWidgetInfo member : stack.getContents()) {
                        assertEquals(3, member.spanX);
                        assertEquals(2, member.spanY);
                        assertNotNull(view.findWidgetByAppWidgetId(member.appWidgetId));
                    }
                    assertEquals(active.get(), stack.getActiveWidgetId());
                    assertEquals(active.get(), stack.getContents().get(0).id);
                    WidgetStackController.remove(launcher, view, stack.getContents().get(0));
                    assertEquals(1, stack.getContents().size());
                    assertEquals(0, stack.getContents().get(0).rank);
                });
                drainModel();
            } finally {
                scenario.onActivity(launcher -> {
                    WidgetStackView view = WidgetStackController.findStack(launcher, stackId.get());
                    if (view != null) {
                        launcher.removeItem(view, (WidgetStackInfo) view.getTag(), true, "OneUI test cleanup");
                    } else if (first.get() != null && first.get().container == Favorites.CONTAINER_DESKTOP) {
                        launcher.removeItem(firstHost.get(), first.get(), true, "OneUI test cleanup");
                    }
                });
                drainModel();
                scenario.onActivity(launcher -> {
                    for (int id : allocatedIds) launcher.getAppWidgetHolder().deleteAppWidgetId(id);
                });
            }
        }
    }

    private static void awaitPage(ActivityScenario<Launcher> scenario, int stackId, int rowId)
            throws Exception {
        try {
            await(scenario, launcher -> info(launcher, stackId).getActiveWidgetId() == rowId);
        } finally {
            scenario.onActivity(launcher -> logPagingState(launcher, stackId));
        }
    }

    private static void logPagingState(Launcher launcher, int id) {
        WidgetStackView view = WidgetStackController.findStack(launcher, id);
        PagedView<?> pager = (PagedView<?>) view.getChildAt(0);
        Rect bounds = new Rect();
        view.getGlobalVisibleRect(bounds);
        android.util.Log.i("OneUiPagingTest", "active=" + info(launcher, id).getActiveWidgetId()
                + " page=" + pager.getCurrentPage() + " next=" + pager.getNextPage()
                + " scroll=" + pager.getScrollX() + " width=" + pager.getWidth()
                + " transitioning=" + pager.isPageInTransition()
                + " workspace=" + launcher.getWorkspace().getCurrentPage()
                + " bounds=" + bounds + " sheet=" + AbstractFloatingView.getTopOpenView(launcher));
    }

    private static TextView activeText(Launcher launcher, int id) {
        WidgetStackView view = WidgetStackController.findStack(launcher, id);
        WidgetStackInfo stack = (WidgetStackInfo) view.getTag();
        LauncherAppWidgetHostView host = view.findWidgetByAppWidgetId(stack.getActiveWidget().appWidgetId);
        return host == null ? null : host.findViewById(android.R.id.text1);
    }

    private static void sendGesture(float fromX, float fromY, float toX, float toY, boolean swipe) {
        long down = SystemClock.uptimeMillis();
        sendMotion(down, MotionEvent.ACTION_DOWN, fromX, fromY);
        if (swipe) {
            for (int step = 1; step <= 15; step++) {
                SystemClock.sleep(16);
                float progress = step / 15f;
                sendMotion(down, MotionEvent.ACTION_MOVE,
                        fromX + (toX - fromX) * progress, fromY + (toY - fromY) * progress);
            }
        }
        sendMotion(down, MotionEvent.ACTION_UP, toX, toY);
    }

    private static void sendMotion(long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        try {
            // Launcher touches also reach the system wallpaper window, owned by another UID.
            // UiAutomation supports that normal cross-window dispatch without changing the app.
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            assertTrue("Android must accept the touch event", InstrumentationRegistry
                    .getInstrumentation().getUiAutomation().injectInputEvent(event, true));
        } finally {
            event.recycle();
        }
    }

    private static WidgetStackInfo info(Launcher launcher, int id) {
        return (WidgetStackInfo) WidgetStackController.findStack(launcher, id).getTag();
    }

    private static LauncherAppWidgetProviderInfo provider(Launcher launcher, ComponentName component) {
        AppWidgetProviderInfo result = AppWidgetManager.getInstance(launcher).getInstalledProviders()
                .stream().filter(p -> component.equals(p.provider)).findFirst().orElse(null);
        assertNotNull("The actual instrumentation widget provider must be installed", result);
        return LauncherAppWidgetProviderInfo.fromProviderInfo(launcher, result);
    }

    private static int bind(Launcher launcher, LauncherAppWidgetProviderInfo provider, List<Integer> ids) {
        int id = launcher.getAppWidgetHolder().allocateAppWidgetId();
        ids.add(id);
        assertTrue("Android must bind the real provider", AppWidgetManager.getInstance(launcher)
                .bindAppWidgetIdIfAllowed(id, provider.provider));
        return id;
    }

    private static void drainModel() throws Exception {
        Executors.MODEL_EXECUTOR.submit(() -> {}).get(30, TimeUnit.SECONDS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void await(ActivityScenario<Launcher> scenario, Predicate<Launcher> condition)
            throws Exception {
        for (int attempt = 0; attempt < 150; attempt++) {
            drainModel();
            AtomicBoolean ready = new AtomicBoolean();
            scenario.onActivity(launcher -> ready.set(condition.test(launcher)));
            if (ready.get()) return;
            SystemClock.sleep(100);
        }
        fail("Timed out waiting for the launcher model and stack views");
    }
}

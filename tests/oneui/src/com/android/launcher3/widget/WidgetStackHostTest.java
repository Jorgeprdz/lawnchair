/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */
package com.android.launcher3.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import android.appwidget.AppWidgetProviderInfo;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.view.View;
import android.view.MotionEvent;
import android.os.SystemClock;
import android.widget.FrameLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.Launcher;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WidgetStackInfo;
import com.android.launcher3.util.MultiTranslateDelegate;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/** Host-view regressions on Android; these do not claim real provider/binding validation. */
@RunWith(AndroidJUnit4.class)
public class WidgetStackHostTest {
    @Test
    public void launcherStartsAndRecreatedHiddenHostKeepsItsPageAndGeometry() {
        String packageName = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getPackageName();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setComponent(new ComponentName(packageName, "app.lawnchair.LawnchairLauncher"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<Launcher> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(launcher -> {
                assertNotNull(launcher.getWorkspace());
                WidgetStackInfo stack = new WidgetStackInfo();
                LauncherAppWidgetInfo first = member(101);
                LauncherAppWidgetInfo second = member(102);
                stack.add(first);
                stack.add(second);
                stack.setActiveWidgetId(first.id);
                LauncherAppWidgetHostView firstHost = host(launcher, first);
                LauncherAppWidgetHostView secondHost = host(launcher, second);
                secondHost.setScaleToFit(0.5f);
                secondHost.getTranslateDelegate().setTranslation(
                        MultiTranslateDelegate.INDEX_WIDGET_CENTERING, 25f, 20f);
                WidgetStackView view = new WidgetStackView(launcher);
                InterceptionParent parent = new InterceptionParent(launcher);
                parent.addView(view);
                view.bind(stack, launcher.getModelWriter(), Arrays.asList(firstHost, secondHost));
                int spec = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY);
                view.measure(spec, spec);
                view.layout(0, 0, 400, 400);
                assertSame(secondHost, view.findWidgetByAppWidgetId(102));
                assertEquals(1f, secondHost.getScaleX(), 0f);
                assertEquals(0f, secondHost.getTranslationX(), 0f);
                LauncherAppWidgetHostView replacement = host(launcher, second);
                replacement.setScaleToFit(0.6f);
                replacement.getTranslateDelegate().setTranslation(
                        MultiTranslateDelegate.INDEX_WIDGET_CENTERING, 15f, 10f);
                view.replaceWidgetView(secondHost, replacement);
                assertSame(replacement, view.findWidgetByAppWidgetId(102));
                assertSame(firstHost, view.findWidgetByAppWidgetId(101));
                assertEquals(first.id, stack.getActiveWidgetId());
                assertEquals(1f, replacement.getScaleX(), 0f);
                assertEquals(0f, replacement.getTranslationX(), 0f);
                assertEquals(Arrays.asList(first, second), stack.getContents());
                long downTime = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(downTime, downTime,
                        MotionEvent.ACTION_DOWN, 100f, 100f, 0);
                MotionEvent cancel = MotionEvent.obtain(downTime, downTime + 10,
                        MotionEvent.ACTION_CANCEL, 100f, 100f, 0);
                try {
                    view.dispatchTouchEvent(down);
                    firstHost.requestDisallowInterceptTouchEvent(false);
                    assertTrue("A child must not return this gesture to Workspace", parent.blocked);
                    view.dispatchTouchEvent(cancel);
                    assertFalse("Cancellation must release Workspace interception", parent.blocked);
                } finally {
                    down.recycle();
                    cancel.recycle();
                }
            });
        }
    }

    private static class InterceptionParent extends FrameLayout {
        boolean blocked;

        InterceptionParent(Launcher launcher) {
            super(launcher);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallow) {
            blocked = disallow;
            super.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private static LauncherAppWidgetInfo member(int id) {
        LauncherAppWidgetInfo member = new LauncherAppWidgetInfo(id,
                new ComponentName(InstrumentationRegistry.getInstrumentation()
                        .getContext().getPackageName(), OneUiTestWidgetProvider.class.getName()));
        member.id = id;
        return member;
    }

    private static LauncherAppWidgetHostView host(Launcher launcher, LauncherAppWidgetInfo member) {
        LauncherAppWidgetHostView host = new LauncherAppWidgetHostView(launcher);
        AppWidgetProviderInfo provider = AppWidgetManager.getInstance(launcher)
                .getInstalledProviders().stream()
                .filter(info -> member.providerName.equals(info.provider)).findFirst().orElse(null);
        assertNotNull("Use installed provider metadata, including Android's receiver label", provider);
        host.setAppWidget(member.appWidgetId,
                LauncherAppWidgetProviderInfo.fromProviderInfo(launcher, provider));
        host.setTag(member);
        return host;
    }
}

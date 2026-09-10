package com.android.launcher3;

import static org.junit.Assert.*;
import android.content.ComponentName;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.lawnchair.preferences2.PreferenceManager2;
import app.lawnchair.preferences2.PreferenceCacheExtensionsKt;
import com.patrykmichalik.opto.core.PreferenceExtensionsKt;
import com.android.launcher3.touch.ItemLongClickListener;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Checks the four persisted modes on the actual dock, including activity recreation. */
@RunWith(AndroidJUnit4.class)
public class DockBackgroundTest {
    @Test
    public void modesRetainDockBoundsAndSurviveRecreation() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var context = instrumentation.getTargetContext();
        var preference = PreferenceManager2.getInstance(context).getHotseatBackgroundMode();
        int original = PreferenceCacheExtensionsKt.firstCached(preference);
        Intent launch = new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName(context.getPackageName(), "app.lawnchair.LawnchairLauncher"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String[] screenshots = {"04-dock-off.png", "05-dock-solid.png", "06-dock-blur.png", "07-dock-crystal.png"};
        int[] bounds = new int[2];
        try (ActivityScenario<Launcher> scenario = ActivityScenario.launch(launch)) {
            try {
                ready(scenario);
                scenario.onActivity(launcher -> {
                    bounds[0] = launcher.getHotseat().getWidth();
                    bounds[1] = launcher.getHotseat().getHeight();
                    assertTrue(bounds[0] > 0 && bounds[1] > 0);
                });
                for (int mode = 0; mode < 4; mode++) {
                    final int selectedMode = mode;
                    instrumentation.runOnMainSync(() -> PreferenceExtensionsKt.setBlocking(preference, selectedMode));
                    SystemClock.sleep(1000);
                    ready(scenario);
                    final int expected = mode;
                    scenario.onActivity(launcher -> {
                        assertEquals(expected, (int) PreferenceCacheExtensionsKt.firstCached(preference));
                        assertEquals(bounds[0], launcher.getHotseat().getWidth());
                        assertEquals(bounds[1], launcher.getHotseat().getHeight());
                        if (expected == 0) assertNull(launcher.getHotseat().getBackground());
                        else assertNotNull(launcher.getHotseat().getBackground());
                    });
                    OneUiScreenshots.capture(screenshots[mode]);
                }
                scenario.recreate();
                ready(scenario);
                scenario.onActivity(launcher -> {
                    assertEquals(3, (int) PreferenceCacheExtensionsKt.firstCached(preference));
                    assertNotNull(launcher.getHotseat().getBackground());
                });
            } finally {
                instrumentation.runOnMainSync(() -> PreferenceExtensionsKt.setBlocking(preference, original));
            }
        }
    }

    private static void ready(ActivityScenario<Launcher> scenario) {
        for (int attempt = 0; attempt < 150; attempt++) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicBoolean ready = new AtomicBoolean();
            scenario.onActivity(launcher -> ready.set(ItemLongClickListener.canStartDrag(launcher)
                    && launcher.hasWindowFocus()
                    && launcher.getWorkspace().isShown() && launcher.getWorkspace().getAlpha() >= 0.99f
                    && launcher.getHotseat().isShown() && launcher.getHotseat().getAlpha() >= 0.99f
                    && launcher.getHotseat().getWidth() > 0 && launcher.getHotseat().getHeight() > 0));
            if (ready.get()) return;
            SystemClock.sleep(100);
        }
        fail("Dock did not become ready");
    }
}

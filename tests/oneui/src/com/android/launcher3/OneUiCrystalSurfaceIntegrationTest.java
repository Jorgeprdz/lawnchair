package com.android.launcher3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.graphics.OneUiCrystalSurfaceRole;
import com.android.launcher3.graphics.OneUiGlassBackground;

import app.lawnchair.oneui.OneUiGlassStyle;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OneUiCrystalSurfaceIntegrationTest {
    @Test
    public void dockGlassIsPassiveAndBehindIconsAndSearch() {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setComponent(new ComponentName(
                        context.getPackageName(), "app.lawnchair.LawnchairLauncher"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<Launcher> scenario = ActivityScenario.launch(launch)) {
            scenario.onActivity(launcher -> {
                Hotseat hotseat = launcher.getHotseat();
                View glass = hotseat.getChildAt(0);
                assertTrue(hotseat.indexOfChild(glass) < hotseat.indexOfChild(hotseat.getQsb()));
                assertFalse(glass.isClickable());
                assertFalse(glass.isFocusable());
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO,
                        glass.getImportantForAccessibility());
            });
        }
    }

    @Test
    public void factoriesCarryDistinctExplicitSurfaceRoles() {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        View host = new View(context);
        Drawable dock = OneUiGlassBackground.createDock(
                host, OneUiGlassStyle.CRYSTAL, Color.WHITE, 48f);
        Drawable folder = OneUiGlassBackground.createOpenFolder(host, Color.WHITE, 48f);
        Drawable icon = OneUiGlassBackground.createFolderIcon(host, Color.WHITE, 48f);
        Drawable folderDrawable = OneUiGlassBackground.createFolderDrawable(
                host, Color.WHITE, 48f);

        assertEquals(OneUiCrystalSurfaceRole.DOCK,
                OneUiGlassBackground.getSurfaceRoleForTesting(dock));
        assertNotEquals(OneUiGlassBackground.getSurfaceRoleForTesting(dock),
                OneUiGlassBackground.getSurfaceRoleForTesting(folder));
        assertNotEquals(OneUiGlassBackground.getSurfaceRoleForTesting(folder),
                OneUiGlassBackground.getSurfaceRoleForTesting(icon));
        assertNotEquals(OneUiGlassBackground.getSurfaceRoleForTesting(icon),
                OneUiGlassBackground.getSurfaceRoleForTesting(folderDrawable));
    }
}

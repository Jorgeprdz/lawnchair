package com.android.launcher3;

import android.appwidget.AppWidgetManager;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Read-only diagnostics for provider and OEM rendering failures; does not edit the workspace. */
@RunWith(AndroidJUnit4.class)
public class DevicePlatformCapabilitiesTest {
    @Test
    public void reportDockCapabilities() {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var manager = context.getSystemService(WindowManager.class);
        Log.i("OneUiCapabilities", "model=" + Build.MODEL + " sdk=" + Build.VERSION.SDK_INT
                + " crossWindowBlur=" + (Build.VERSION.SDK_INT >= 31
                && manager != null && manager.isCrossWindowBlurEnabled()));
        for (String name : new String[]{"android.view.SemBlurInfo", "android.view.SemBlurInfo$Builder"}) {
            try {
                Class<?> type = Class.forName(name);
                for (var constructor : type.getConstructors()) {
                    Log.i("OneUiCapabilities", constructor.toString());
                }
                for (var method : type.getDeclaredMethods()) {
                    if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                        Log.i("OneUiCapabilities", method.toString());
                    }
                }
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                Log.i("OneUiCapabilities", name + " unavailable: " + unavailable.getClass().getSimpleName());
            }
        }
        for (var method : View.class.getMethods()) {
            if (method.getName().equals("semSetBlurInfo")) Log.i("OneUiCapabilities", method.toString());
        }
        for (var provider : AppWidgetManager.getInstance(context).getInstalledProvidersForPackage(
                "rk.android.app.pixelsearch", android.os.Process.myUserHandle())) {
            Log.i("OneUiCapabilities", "Pixel provider=" + provider.provider + " configure="
                    + provider.configure + " min=" + provider.minWidth + "x" + provider.minHeight);
        }
    }
}

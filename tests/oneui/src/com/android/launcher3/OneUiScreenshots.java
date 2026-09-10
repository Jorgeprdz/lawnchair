package com.android.launcher3;

import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import static org.junit.Assert.fail;

/** Requests real ADB screenshots from the CI host; never renders or fabricates an image. */
public final class OneUiScreenshots {
    private OneUiScreenshots() { }

    public static void capture(String filename) throws Exception {
        if (!"true".equals(InstrumentationRegistry.getArguments().getString("oneuiScreenshots"))) return;
        if (!filename.matches("[0-9]{2}-[a-z0-9-]+\\.png")) {
            throw new IllegalArgumentException("Invalid screenshot filename");
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(700);
        Log.i("OneUiScreenshot", "capture " + filename);
        for (int attempt = 0; attempt < 100; attempt++) {
            try (var input = new ParcelFileDescriptor.AutoCloseInputStream(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                            "test -f /storage/emulated/0/Download/lawnchair/" + filename + ".done && echo ready"))) {
                if (input.read() == 'r') return;
            }
            SystemClock.sleep(200);
        }
        fail("ADB screenshot was not captured and validated: " + filename);
    }
}

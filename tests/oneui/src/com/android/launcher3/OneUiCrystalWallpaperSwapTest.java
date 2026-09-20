package com.android.launcher3;

import static android.app.WallpaperManager.FLAG_SYSTEM;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.graphics.AndroidWallpaperBackdropSource;
import com.android.launcher3.graphics.OneUiWallpaperBackdropRepository;
import com.android.launcher3.graphics.WallpaperBackdropSnapshot;
import com.android.launcher3.graphics.WallpaperIdentity;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** Reversible real-device generation check. Never replaces an active live wallpaper. */
@RunWith(AndroidJUnit4.class)
public class OneUiCrystalWallpaperSwapTest {
    @Test
    public void staticWallpaperSwapPublishesNewGenerationAndRestoresOriginal() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WallpaperManager manager = WallpaperManager.getInstance(context);
        AndroidWallpaperBackdropSource source = new AndroidWallpaperBackdropSource(context);
        WallpaperIdentity originalIdentity = source.readIdentity();
        Assume.assumeFalse(
                "Safety: do not replace or rebind the user's live wallpaper",
                originalIdentity.isLive());

        File backup = new File(context.getCacheDir(), "crystal-wallpaper-restore");
        try (ParcelFileDescriptor descriptor = manager.getWallpaperFile(FLAG_SYSTEM)) {
            Assume.assumeNotNull(descriptor);
            try (var input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
                    var output = new FileOutputStream(backup)) {
                input.transferTo(output);
            }
        }

        OneUiWallpaperBackdropRepository repository =
                new OneUiWallpaperBackdropRepository(context, source, Runnable::run, Runnable::run);
        Bitmap fixture = createFixture();
        try {
            repository.start();
            WallpaperBackdropSnapshot original = repository.currentSnapshot();
            Assume.assumeNotNull(original);

            manager.setBitmap(fixture, null, true, FLAG_SYSTEM);
            WallpaperIdentity fixtureIdentity = source.readIdentity();
            assertTrue("Wallpaper id must change after the fixture is installed",
                    !fixtureIdentity.equals(originalIdentity));

            repository.onWallpaperChangedSignal();
            WallpaperBackdropSnapshot replacement = repository.currentSnapshot();
            assertNotNull(replacement);
            assertTrue(replacement.wallpaperGeneration() > original.wallpaperGeneration());
            assertTrue(hasLightAndDarkSamples(replacement.bitmap()));
        } finally {
            try (var input = new FileInputStream(backup)) {
                manager.setStream(input, null, true, FLAG_SYSTEM);
            }
            fixture.recycle();
            repository.close();
            //noinspection ResultOfMethodCallIgnored
            backup.delete();
        }
    }

    private static Bitmap createFixture() {
        Bitmap bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < bitmap.getHeight(); y++) {
            int color = y < bitmap.getHeight() / 2 ? Color.rgb(235, 245, 255)
                    : Color.rgb(8, 24, 48);
            for (int x = 0; x < bitmap.getWidth(); x++) {
                bitmap.setPixel(x, y, ((x / 80) & 1) == 0 ? color : blend(color, Color.CYAN));
            }
        }
        return bitmap;
    }

    private static int blend(int first, int second) {
        return Color.rgb(
                (Color.red(first) + Color.red(second)) / 2,
                (Color.green(first) + Color.green(second)) / 2,
                (Color.blue(first) + Color.blue(second)) / 2);
    }

    private static boolean hasLightAndDarkSamples(Bitmap bitmap) {
        boolean light = false;
        boolean dark = false;
        int step = Math.max(1, bitmap.getHeight() / 12);
        for (int y = 0; y < bitmap.getHeight(); y += step) {
            int pixel = bitmap.getPixel(bitmap.getWidth() / 2, y);
            int luminance = Color.red(pixel) + Color.green(pixel) + Color.blue(pixel);
            light |= luminance > 600;
            dark |= luminance < 180;
        }
        return light && dark;
    }
}

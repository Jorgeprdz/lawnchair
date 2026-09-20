package com.android.launcher3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.android.launcher3.graphics.AndroidWallpaperBackdropSource;
import com.android.launcher3.graphics.WallpaperBackdropSource;
import com.android.launcher3.graphics.WallpaperBackdropSource.LoadStatus;
import com.android.launcher3.graphics.WallpaperIdentity;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OneUiWallpaperBackdropSourceTest {
    @Test
    public void loadCurrentStaticWallpaperReturnsRealOpaquePixels() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AndroidWallpaperBackdropSource source = new AndroidWallpaperBackdropSource(context);
        WallpaperIdentity identity = source.readIdentity();
        Assume.assumeFalse("A live wallpaper has no readable static frame", identity.isLive());

        WallpaperBackdropSource.LoadResult result = source.load(
                new WallpaperBackdropSource.LoadRequest(
                        identity, 1080, 2400, 24L * 1024L * 1024L, 1));

        assertEquals(LoadStatus.SUCCESS, result.status());
        assertNotNull(result.snapshot());
        Bitmap bitmap = result.snapshot().bitmap();
        assertTrue(bitmap.getWidth() > 1);
        assertTrue(bitmap.getHeight() > 1);
        assertTrue("Wallpaper sample must contain real non-black opaque pixels",
                hasAtLeastOneNonBlackOpaqueSample(bitmap));
    }

    private static boolean hasAtLeastOneNonBlackOpaqueSample(Bitmap bitmap) {
        int xStep = Math.max(1, bitmap.getWidth() / 12);
        int yStep = Math.max(1, bitmap.getHeight() / 12);
        for (int y = 0; y < bitmap.getHeight(); y += yStep) {
            for (int x = 0; x < bitmap.getWidth(); x += xStep) {
                int pixel = bitmap.getPixel(x, y);
                int alpha = pixel >>> 24;
                int rgb = pixel & 0x00ffffff;
                if (alpha >= 0xf0 && rgb != 0) {
                    return true;
                }
            }
        }
        return false;
    }
}

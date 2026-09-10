package com.android.launcher3;

import static org.junit.Assert.assertTrue;

import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Emulator-only wallpaper fixture, installed before the UI screenshot scenarios. */
@RunWith(AndroidJUnit4.class)
public class OneUiWallpaperTest {
    @Test
    public void installLightWallpaper() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var metrics = context.getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(new LinearGradient(0, 0, width, height,
                    new int[] {Color.rgb(184, 218, 242), Color.rgb(225, 231, 221),
                            Color.rgb(255, 235, 202)}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
            paint.setShader(null);
            paint.setColor(Color.argb(90, 255, 255, 255));
            canvas.drawCircle(width * 0.85f, height * 0.72f, width * 0.65f, paint);
            paint.setColor(Color.argb(65, 158, 207, 218));
            canvas.drawCircle(width * 0.05f, height * 0.91f, width * 0.43f, paint);
            int wallpaperId = WallpaperManager.getInstance(context).setBitmap(
                    bitmap, null, false, WallpaperManager.FLAG_SYSTEM);
            assertTrue("System wallpaper was installed", wallpaperId > 0);
        } finally {
            bitmap.recycle();
        }
    }
}

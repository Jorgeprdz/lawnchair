package com.android.launcher3.graphics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.graphics.WallpaperBackdropSource.LoadRequest;
import com.android.launcher3.graphics.WallpaperBackdropSource.LoadResult;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OneUiCrystalRendererDeviceTest {
    @Test
    public void repeatedDrawsReuseHeavyResourcesUntilGenerationChanges() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        TestSource source = new TestSource();
        OneUiWallpaperBackdropRepository repository =
                new OneUiWallpaperBackdropRepository(source, Runnable::run, Runnable::run);
        repository.start();

        View host = new View(context);
        OneUiCrystalRenderer renderer = OneUiCrystalRenderer.createForTesting(
                host, OneUiCrystalSurfaceRole.DOCK, Color.WHITE, 64f, 70, repository);
        renderer.setBounds(0, 0, 900, 220);
        Bitmap target = Bitmap.createBitmap(900, 220, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);

        renderer.draw(canvas);
        int shaderBuilds = renderer.getShaderBuildCountForTesting();
        int bitmapShaderBuilds = renderer.getBitmapShaderBuildCountForTesting();
        assertTrue(bitmapShaderBuilds > 0);
        for (int draw = 0; draw < 20; draw++) {
            renderer.draw(canvas);
        }
        assertEquals(shaderBuilds, renderer.getShaderBuildCountForTesting());
        assertEquals(bitmapShaderBuilds, renderer.getBitmapShaderBuildCountForTesting());

        source.advance();
        repository.onWallpaperChangedSignal();
        renderer.draw(canvas);
        assertEquals(shaderBuilds + 1, renderer.getShaderBuildCountForTesting());
        assertEquals(bitmapShaderBuilds + 1, renderer.getBitmapShaderBuildCountForTesting());

        repository.close();
        target.recycle();
    }

    private static final class TestSource implements WallpaperBackdropSource {
        private int mWallpaperId = 1;

        void advance() {
            mWallpaperId++;
        }

        @Override
        public WallpaperIdentity readIdentity() {
            return new WallpaperIdentity(mWallpaperId, null, 0, mWallpaperId);
        }

        @Override
        public LoadResult load(LoadRequest request) {
            Bitmap mutable = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888);
            mutable.eraseColor(request.generation() % 2 == 0 ? Color.BLUE : Color.GREEN);
            Bitmap immutable = mutable.copy(Bitmap.Config.ARGB_8888, false);
            mutable.recycle();
            return LoadResult.success(new WallpaperBackdropSnapshot(
                    immutable,
                    request.generation(),
                    request.identity(),
                    1080,
                    2400,
                    request.horizontalOffset(),
                    request.verticalOffset(),
                    request.orientation(),
                    request.displayWidth(),
                    request.displayHeight(),
                    request.generation()));
        }
    }
}

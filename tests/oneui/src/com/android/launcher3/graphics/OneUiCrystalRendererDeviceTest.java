package com.android.launcher3.graphics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
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
    public void maximumCrystalVisiblyDisplacesARecognizableWallpaperEdge() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        GradientSource source = new GradientSource();
        OneUiWallpaperBackdropRepository repository =
                new OneUiWallpaperBackdropRepository(source, Runnable::run, Runnable::run);
        repository.start();

        View host = new View(context);
        OneUiCrystalRenderer renderer = OneUiCrystalRenderer.createForTesting(
                host, OneUiCrystalSurfaceRole.DOCK, Color.WHITE, 64f, 100, repository);
        // Keeping the surface away from the display edges makes the wallpaper mapping exactly 1:1.
        renderer.setBounds(100, 100, 1000, 320);
        Bitmap target = Bitmap.createBitmap(1080, 500, Bitmap.Config.ARGB_8888);
        renderer.draw(new Canvas(target));

        int edgeX = 104;
        int centerX = 550;
        int sampleY = 210;
        float edgeSourceX = inferSourceXFromGreen(target.getPixel(edgeX, sampleY));
        float centerSourceX = inferSourceXFromGreen(target.getPixel(centerX, sampleY));

        assertTrue("Maximum Crystal must visibly bend a recognizable wallpaper feature",
                edgeX - edgeSourceX >= 30f);
        assertTrue("The readable center must remain spatially stable",
                Math.abs(centerSourceX - centerX) <= 8f);

        repository.close();
        target.recycle();
    }

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

    @Test
    public void animationPinKeepsRetiredSnapshotUntilReleasedExactlyOnce() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        TestSource source = new TestSource();
        OneUiWallpaperBackdropRepository repository =
                new OneUiWallpaperBackdropRepository(source, Runnable::run, Runnable::run);
        repository.start();
        Bitmap first = source.mLastBitmap;

        View host = new View(context);
        OneUiCrystalRenderer renderer = OneUiCrystalRenderer.createForTesting(
                host, OneUiCrystalSurfaceRole.OPEN_FOLDER, Color.WHITE, 64f, 70, repository);
        OneUiGlassBackground.setAnimationRunning(renderer, true);
        assertTrue(renderer.hasPinnedSnapshotForTesting());

        source.advance();
        repository.onWallpaperChangedSignal();
        assertTrue("Retired generation must remain alive during animation", !first.isRecycled());

        OneUiGlassBackground.setAnimationRunning(renderer, false);
        assertTrue(first.isRecycled());
        OneUiGlassBackground.setAnimationRunning(renderer, false);
        assertTrue(!renderer.hasPinnedSnapshotForTesting());
        repository.close();
    }

    private static final class TestSource implements WallpaperBackdropSource {
        private int mWallpaperId = 1;
        private Bitmap mLastBitmap;

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
            mLastBitmap = immutable;
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

    /** A real immutable horizontal luminance ramp makes optical displacement measurable. */
    private static final class GradientSource implements WallpaperBackdropSource {
        private final WallpaperIdentity mIdentity = new WallpaperIdentity(1, null, 0, 1);

        @Override
        public WallpaperIdentity readIdentity() {
            return mIdentity;
        }

        @Override
        public LoadResult load(LoadRequest request) {
            Bitmap mutable = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(new LinearGradient(
                    0f, 0f, 1079f, 0f, Color.BLACK, Color.WHITE, Shader.TileMode.CLAMP));
            new Canvas(mutable).drawRect(0f, 0f, 1080f, 2400f, paint);
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

    private static float inferSourceXFromGreen(int color) {
        // At 100% with a white tint, the shader emits 89% wallpaper green plus 11% white.
        float untinted = ((Color.green(color) / 255f) - 0.11f) / 0.89f;
        return untinted * 1079f;
    }
}

package com.android.launcher3.graphics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.Launcher;
import com.android.launcher3.graphics.WallpaperBackdropSource.LoadRequest;
import com.android.launcher3.graphics.WallpaperBackdropSource.LoadResult;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OneUiCrystalRendererDeviceTest {
    @Test
    public void maximumCrystalVisiblyDisplacesARecognizableWallpaperEdge() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setComponent(new ComponentName(
                        context.getPackageName(), "app.lawnchair.LawnchairLauncher"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        AtomicReference<View> hostReference = new AtomicReference<>();
        AtomicReference<OneUiWallpaperBackdropRepository> repositoryReference =
                new AtomicReference<>();
        int[] hostLocation = new int[2];

        try (ActivityScenario<Launcher> scenario = ActivityScenario.launch(launch)) {
            scenario.onActivity(launcher -> {
                GradientSource source = new GradientSource();
                OneUiWallpaperBackdropRepository repository =
                        new OneUiWallpaperBackdropRepository(source, Runnable::run, Runnable::run);
                repository.start();
                View host = new View(launcher);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(900, 220);
                launcher.addContentView(host, params);
                host.setX(100f);
                host.setY(500f);
                host.setElevation(100f);
                host.setBackground(OneUiCrystalRenderer.createForTesting(
                        host, OneUiCrystalSurfaceRole.DOCK, Color.WHITE, 64f, 0, repository));
                hostReference.set(host);
                repositoryReference.set(repository);
            });
            instrumentation.waitForIdleSync();
            SystemClock.sleep(500);
            scenario.onActivity(launcher -> {
                View host = hostReference.get();
                assertEquals(900, host.getWidth());
                assertEquals(220, host.getHeight());
                assertTrue(host.isShown());
                host.getLocationOnScreen(hostLocation);
                assertEquals(100, hostLocation[0]);
                assertEquals(500, hostLocation[1]);
            });

            Bitmap baseline = instrumentation.getUiAutomation().takeScreenshot();
            scenario.onActivity(launcher -> hostReference.get().setBackground(
                    OneUiCrystalRenderer.createForTesting(
                            hostReference.get(), OneUiCrystalSurfaceRole.DOCK,
                            Color.WHITE, 64f, 100, repositoryReference.get())));
            instrumentation.waitForIdleSync();
            SystemClock.sleep(250);
            Bitmap maximum = instrumentation.getUiAutomation().takeScreenshot();
            int edgeX = hostLocation[0] + 896;
            int centerX = hostLocation[0] + 450;
            int sampleY = hostLocation[1] + 110;
            float baselineEdgeSourceX = inferSourceXFromGreen(
                    baseline.getPixel(edgeX, sampleY), 0.045f);
            float maximumEdgeSourceX = inferSourceXFromGreen(
                    maximum.getPixel(edgeX, sampleY), 0.11f);
            float baselineCenterSourceX = inferSourceXFromGreen(
                    baseline.getPixel(centerX, sampleY), 0.045f);
            float maximumCenterSourceX = inferSourceXFromGreen(
                    maximum.getPixel(centerX, sampleY), 0.11f);

            assertTrue("Maximum Crystal must visibly bend a recognizable wallpaper feature; edge="
                            + Integer.toHexString(maximum.getPixel(edgeX, sampleY))
                            + ", baselineSourceX=" + baselineEdgeSourceX
                            + ", maximumSourceX=" + maximumEdgeSourceX,
                    maximumEdgeSourceX - baselineEdgeSourceX >= 30f);
            assertTrue("The readable center must remain spatially stable; baselineSourceX="
                            + baselineCenterSourceX
                            + ", maximumSourceX=" + maximumCenterSourceX,
                    Math.abs(maximumCenterSourceX - baselineCenterSourceX) <= 8f);
            baseline.recycle();
            maximum.recycle();

            scenario.onActivity(launcher ->
                    ((ViewGroup) hostReference.get().getParent()).removeView(hostReference.get()));
        } finally {
            OneUiWallpaperBackdropRepository repository = repositoryReference.get();
            if (repository != null) repository.close();
        }
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

    private static float inferSourceXFromGreen(int color, float tintAlpha) {
        float untinted = ((Color.green(color) / 255f) - tintAlpha) / (1f - tintAlpha);
        return untinted * 1079f;
    }
}

package com.android.launcher3.graphics;

import static com.google.common.truth.Truth.assertThat;

import com.android.launcher3.graphics.WallpaperBackdropTransform.Input;
import com.android.launcher3.graphics.WallpaperBackdropTransform.Result;
import org.junit.Test;

public class WallpaperBackdropTransformTest {
    @Test
    public void centeredPortraitDockMapsIntoCenterCroppedWallpaper() {
        Input input = new Input(
                2160, 2400, 1080, 1200, 1080, 2400,
                120, 2120, 960, 220, 0.5f, 0.5f, 48, false);

        Result result = WallpaperBackdropTransform.map(input);

        assertThat(result.isValid()).isTrue();
        assertThat(result.left()).isAtLeast(0f);
        assertThat(result.right()).isAtMost(1080f);
        assertThat(result.top()).isLessThan(result.bottom());
    }

    @Test
    public void panoramicWallpaperMovesWithPageOffset() {
        Result left = WallpaperBackdropTransform.map(panoramicInput(0f, false));
        Result right = WallpaperBackdropTransform.map(panoramicInput(1f, false));
        Result rtl = WallpaperBackdropTransform.map(panoramicInput(1f, true));

        assertThat(right.left()).isGreaterThan(left.left());
        assertThat(rtl.left()).isWithin(0.01f).of(left.left());
    }

    @Test
    public void edgePaddingClampsWithoutCollapsingSampleWidth() {
        Input input = new Input(
                2160, 2400, 1080, 1200, 1080, 2400,
                0, 2100, 120, 220, 0f, 0.5f, 96, false);

        Result result = WallpaperBackdropTransform.map(input);

        assertThat(result.left()).isEqualTo(0f);
        assertThat(result.right() - result.left()).isGreaterThan(1f);
    }

    @Test
    public void sourceSmallerThanDisplayStillProducesFiniteClampedCrop() {
        Result result = WallpaperBackdropTransform.map(new Input(
                540, 960, 540, 960, 1440, 3200,
                80, 2860, 1280, 260, 0.5f, 0.5f, 64, false));

        assertThat(result.isValid()).isTrue();
        assertThat(result.left()).isAtLeast(0f);
        assertThat(result.top()).isAtLeast(0f);
        assertThat(result.right()).isAtMost(540f);
        assertThat(result.bottom()).isAtMost(960f);
    }

    @Test
    public void extremePanoramaOffsetsRemainAlignedAndWithinBitmap() {
        Input startInput = new Input(
                20000, 2000, 2500, 250, 1080, 2400,
                40, 2100, 1000, 240, 0f, 0.5f, 80, false);
        Input endInput = new Input(
                20000, 2000, 2500, 250, 1080, 2400,
                40, 2100, 1000, 240, 1f, 0.5f, 80, false);

        Result start = WallpaperBackdropTransform.map(startInput);
        Result end = WallpaperBackdropTransform.map(endInput);

        assertThat(start.isValid()).isTrue();
        assertThat(end.isValid()).isTrue();
        assertThat(end.left()).isGreaterThan(start.left());
        assertThat(end.right()).isAtMost(2500f);
    }

    private static Input panoramicInput(float horizontalOffset, boolean rtl) {
        return new Input(
                3240, 2400, 1620, 1200, 1080, 2400,
                100, 2100, 880, 220, horizontalOffset, 0.5f, 32, rtl);
    }
}

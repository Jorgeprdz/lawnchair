package com.android.launcher3.graphics;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class WallpaperDecodePlanTest {
    @Test
    public void decodePlanKeepsCurrentAndCandidateBelowBudget() {
        WallpaperDecodePlan plan = WallpaperDecodePlan.forBounds(
                12000, 6000, 1080, 2400, 24L * 1024L * 1024L);

        long twoBitmaps = 2L * plan.width() * plan.height() * 4L;
        assertThat(twoBitmaps).isAtMost(24L * 1024L * 1024L);
        assertThat(plan.sampleSize()).isAtLeast(1);
    }

    @Test
    public void corruptBoundsReturnUnavailablePlan() {
        WallpaperDecodePlan plan = WallpaperDecodePlan.forBounds(
                0, -1, 1080, 2400, 24L * 1024L * 1024L);

        assertThat(plan.isValid()).isFalse();
    }

    @Test
    public void sampleSizeIsPowerOfTwoForBitmapFactory() {
        WallpaperDecodePlan plan = WallpaperDecodePlan.forBounds(
                9000, 5000, 1080, 2400, 24L * 1024L * 1024L);

        assertThat(plan.sampleSize() & (plan.sampleSize() - 1)).isEqualTo(0);
    }

    @Test
    public void commonPhoneWallpapersRespectCombinedTwentyFourMiBBudget() {
        assertWithinBudget(WallpaperDecodePlan.forBounds(
                1080, 2400, 1080, 2400, 24L * 1024L * 1024L));
        assertWithinBudget(WallpaperDecodePlan.forBounds(
                1440, 3200, 1440, 3200, 24L * 1024L * 1024L));
    }

    @Test
    public void impossibleBudgetReturnsInvalidWithoutOverflow() {
        WallpaperDecodePlan plan = WallpaperDecodePlan.forBounds(
                Integer.MAX_VALUE, Integer.MAX_VALUE, 1080, 2400, 7L);

        assertThat(plan.isValid()).isFalse();
    }

    private static void assertWithinBudget(WallpaperDecodePlan plan) {
        assertThat(plan.isValid()).isTrue();
        long bytes = 2L * plan.width() * plan.height() * 4L;
        assertThat(bytes).isAtMost(24L * 1024L * 1024L);
    }
}

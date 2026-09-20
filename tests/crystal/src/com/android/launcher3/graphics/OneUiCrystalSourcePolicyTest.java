package com.android.launcher3.graphics;

import static com.google.common.truth.Truth.assertThat;

import com.android.launcher3.graphics.OneUiCrystalSourcePolicy.Decision;

import org.junit.Test;

public class OneUiCrystalSourcePolicyTest {
    @Test
    public void staticWallpaperWaitsForRealSnapshotWithoutSamsungBlur() {
        assertThat(OneUiCrystalSourcePolicy.choose(true, false, false, true))
                .isEqualTo(Decision.CACHED_OR_PENDING_STATIC);
    }

    @Test
    public void liveWallpaperUsesSamsungBackdropWhenAvailable() {
        assertThat(OneUiCrystalSourcePolicy.choose(true, true, false, true))
                .isEqualTo(Decision.SAMSUNG_LIVE_BACKDROP);
    }

    @Test
    public void missingPermissionWithoutOldSnapshotUsesSoberFallback() {
        assertThat(OneUiCrystalSourcePolicy.choose(false, false, false, true))
                .isEqualTo(Decision.SOBER_FALLBACK);
    }

    @Test
    public void validOldStaticSnapshotWinsWhileReplacementIsPending() {
        assertThat(OneUiCrystalSourcePolicy.choose(false, false, true, true))
                .isEqualTo(Decision.CACHED_OR_PENDING_STATIC);
    }

    @Test
    public void liveWallpaperWithoutSamsungSupportUsesSoberFallback() {
        assertThat(OneUiCrystalSourcePolicy.choose(true, true, false, false))
                .isEqualTo(Decision.SOBER_FALLBACK);
    }
}

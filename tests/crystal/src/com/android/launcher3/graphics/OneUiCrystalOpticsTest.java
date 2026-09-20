package com.android.launcher3.graphics;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class OneUiCrystalOpticsTest {
    @Test
    public void edgeBandRefractsMoreThanStableCenter() {
        float edge = OneUiCrystalOptics.edgeWeight(-2f, 14f);
        float center = OneUiCrystalOptics.edgeWeight(-80f, 14f);

        assertThat(edge).isGreaterThan(center);
    }

    @Test
    public void zeroPercentStillHasSubtleOpticalMaterial() {
        OneUiCrystalOptics.Values values = OneUiCrystalOptics.forIntensity(0, 3f);

        assertThat(values.refractionPx()).isGreaterThan(0f);
        assertThat(values.tintAlpha()).isGreaterThan(0f);
    }

    @Test
    public void intensityIsClampedBeforeCalculatingMaterial() {
        OneUiCrystalOptics.Values maximum = OneUiCrystalOptics.forIntensity(100, 3f);
        OneUiCrystalOptics.Values overMaximum = OneUiCrystalOptics.forIntensity(500, 3f);

        assertThat(overMaximum.refractionPx()).isEqualTo(maximum.refractionPx());
        assertThat(overMaximum.edgeBandPx()).isEqualTo(maximum.edgeBandPx());
    }
}

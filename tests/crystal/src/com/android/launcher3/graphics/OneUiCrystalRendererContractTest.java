package com.android.launcher3.graphics;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class OneUiCrystalRendererContractTest {
    @Test
    public void everySurfaceSamplesOnlyTheWallpaperRepository() {
        for (OneUiCrystalSurfaceRole role : OneUiCrystalSurfaceRole.values()) {
            assertThat(role.backdropSource())
                    .isEqualTo(OneUiCrystalSurfaceRole.BackdropSource.WALLPAPER_REPOSITORY);
            assertThat(role.allowsLauncherTreeCapture()).isFalse();
        }
    }

    @Test
    public void dockUsesCapsuleOpticsWithoutChangingSnapshotSource() {
        assertThat(OneUiCrystalSurfaceRole.DOCK.usesCapsuleOptics()).isTrue();
        assertThat(OneUiCrystalSurfaceRole.DOCK.refractionScale())
                .isGreaterThan(OneUiCrystalSurfaceRole.OPEN_FOLDER.refractionScale());
        assertThat(OneUiCrystalSurfaceRole.DOCK.backdropSource())
                .isEqualTo(OneUiCrystalSurfaceRole.OPEN_FOLDER.backdropSource());
    }
}

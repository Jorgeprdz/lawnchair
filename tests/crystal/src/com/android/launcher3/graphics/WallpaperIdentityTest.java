package com.android.launcher3.graphics;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class WallpaperIdentityTest {
    @Test
    public void liveComponentParticipatesInIdentity() {
        WallpaperIdentity staticId = new WallpaperIdentity(41, null, 0, 0);
        WallpaperIdentity liveId = new WallpaperIdentity(41, "pkg/.Wallpaper", 0, 0);

        assertThat(staticId).isNotEqualTo(liveId);
        assertThat(staticId.isLive()).isFalse();
        assertThat(liveId.isLive()).isTrue();
    }

    @Test
    public void equivalentMetadataProducesStableIdentity() {
        WallpaperIdentity first = new WallpaperIdentity(41, null, 1234, 5678);
        WallpaperIdentity second = new WallpaperIdentity(41, null, 1234, 5678);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}

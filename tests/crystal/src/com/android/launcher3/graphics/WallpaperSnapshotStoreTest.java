package com.android.launcher3.graphics;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class WallpaperSnapshotStoreTest {
    private final List<String> released = new ArrayList<>();

    @Test
    public void stalePublishCannotReplaceLatestGeneration() {
        WallpaperSnapshotStore<String> store = new WallpaperSnapshotStore<>(released::add);
        long first = store.requestGeneration();
        long second = store.requestGeneration();

        assertThat(store.publish(first, "old")).isFalse();
        assertThat(store.publish(second, "new")).isTrue();
        assertThat(store.current()).isEqualTo("new");
        assertThat(released).containsExactly("old");
    }

    @Test
    public void failedReloadKeepsCurrentSnapshot() {
        WallpaperSnapshotStore<String> store = new WallpaperSnapshotStore<>(released::add);
        long first = store.requestGeneration();
        store.publish(first, "current");
        long failed = store.requestGeneration();

        store.fail(failed);

        assertThat(store.current()).isEqualTo("current");
    }

    @Test
    public void retiredSnapshotWaitsForCancelledAnimationPin() {
        WallpaperSnapshotStore<String> store = new WallpaperSnapshotStore<>(released::add);
        long first = store.requestGeneration();
        store.publish(first, "pinned");
        WallpaperSnapshotStore.SnapshotPin<String> pin = store.pinCurrent();
        long second = store.requestGeneration();
        store.publish(second, "replacement");

        assertThat(released).isEmpty();
        pin.close();
        pin.close();

        assertThat(released).containsExactly("pinned");
    }
}

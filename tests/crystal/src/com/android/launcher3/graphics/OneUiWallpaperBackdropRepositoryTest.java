package com.android.launcher3.graphics;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.graphics.Bitmap;
import com.android.launcher3.graphics.WallpaperBackdropSource.LoadRequest;
import com.android.launcher3.graphics.WallpaperBackdropSource.LoadResult;
import com.android.launcher3.graphics.WallpaperBackdropSource.LoadStatus;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class OneUiWallpaperBackdropRepositoryTest {
    @Test
    public void oldSnapshotStaysVisibleUntilNewestDecodePublishes() {
        Fixture fixture = new Fixture();
        fixture.source.enqueueSuccess();
        fixture.repository.start();
        fixture.io.runNext();
        WallpaperBackdropSnapshot first = fixture.repository.currentSnapshot();

        fixture.source.enqueueSuccess();
        fixture.repository.onWallpaperChangedSignal();

        assertThat(fixture.repository.currentSnapshot()).isSameInstanceAs(first);
        fixture.io.runNext();
        assertThat(fixture.repository.currentSnapshot().wallpaperGeneration())
                .isGreaterThan(first.wallpaperGeneration());
    }

    @Test
    public void revokedPermissionDuringReloadKeepsPreviousSnapshot() {
        Fixture fixture = new Fixture();
        WallpaperBackdropSnapshot initial = fixture.publishInitial();
        fixture.source.enqueueFailure(LoadStatus.ACCESS_DENIED);

        fixture.repository.onWallpaperChangedSignal();
        fixture.io.runNext();

        assertThat(fixture.repository.currentSnapshot()).isSameInstanceAs(initial);
    }

    @Test
    public void resumeWithEqualIdentityDoesNotDecodeAgain() {
        Fixture fixture = new Fixture();
        fixture.publishInitial();

        fixture.repository.onResume();

        assertThat(fixture.source.loadCount).isEqualTo(1);
        assertThat(fixture.io.pendingCount()).isEqualTo(0);
    }

    @Test
    public void colorAndBroadcastSignalsCoalesceBeforeIoStarts() {
        Fixture fixture = new Fixture();
        fixture.source.enqueueSuccess();
        fixture.repository.start();

        fixture.repository.onWallpaperChangedSignal();
        fixture.repository.onColorsChanged();

        assertThat(fixture.io.pendingCount()).isEqualTo(1);
    }

    @Test
    public void successfulPublicationNotifiesListenerExactlyOnce() {
        Fixture fixture = new Fixture();
        AtomicInteger notifications = new AtomicInteger();
        Runnable listener = notifications::incrementAndGet;
        fixture.repository.addListener(listener);
        fixture.source.enqueueSuccess();

        fixture.repository.start();
        fixture.io.runNext();

        assertThat(notifications.get()).isEqualTo(1);
    }

    @Test
    public void closePreventsQueuedCompletionAndFutureSignals() {
        Fixture fixture = new Fixture();
        fixture.source.enqueueSuccess();
        fixture.repository.start();

        fixture.repository.close();
        fixture.io.runNext();
        fixture.repository.onWallpaperChangedSignal();

        assertThat(fixture.repository.currentSnapshot()).isNull();
        assertThat(fixture.io.pendingCount()).isEqualTo(0);
    }

    @Test
    public void offsetUpdateReusesBitmapButOrientationQueuesReload() {
        Fixture fixture = new Fixture();
        WallpaperBackdropSnapshot initial = fixture.publishInitial();

        fixture.repository.updateMapping(1080, 2400, 0.8f, 0.5f, 1);

        assertThat(fixture.repository.currentSnapshot()).isSameInstanceAs(initial);
        assertThat(fixture.source.loadCount).isEqualTo(1);
        assertThat(fixture.io.pendingCount()).isEqualTo(0);

        fixture.source.enqueueSuccess();
        fixture.repository.updateMapping(2400, 1080, 0.8f, 0.5f, 2);
        assertThat(fixture.io.pendingCount()).isEqualTo(1);
        assertThat(fixture.repository.currentSnapshot()).isSameInstanceAs(initial);
    }

    @Test
    public void sourceSecurityExceptionKeepsSnapshotAndAllowsNextReload() {
        Fixture fixture = new Fixture();
        WallpaperBackdropSnapshot initial = fixture.publishInitial();
        fixture.source.enqueueThrowable(new SecurityException("permission revoked"));

        fixture.repository.onWallpaperChangedSignal();
        fixture.io.runNext();

        assertThat(fixture.repository.currentSnapshot()).isSameInstanceAs(initial);
        fixture.source.enqueueSuccess();
        fixture.repository.onWallpaperChangedSignal();
        assertThat(fixture.io.pendingCount()).isEqualTo(1);
        fixture.io.runNext();
        assertThat(fixture.repository.currentSnapshot()).isNotSameInstanceAs(initial);
    }

    @Test
    public void sourceOutOfMemoryKeepsSnapshotAndAllowsNextReload() {
        Fixture fixture = new Fixture();
        WallpaperBackdropSnapshot initial = fixture.publishInitial();
        fixture.source.enqueueThrowable(new OutOfMemoryError("decode allocation"));

        fixture.repository.onWallpaperChangedSignal();
        fixture.io.runNext();

        assertThat(fixture.repository.currentSnapshot()).isSameInstanceAs(initial);
        fixture.source.enqueueSuccess();
        fixture.repository.onWallpaperChangedSignal();
        assertThat(fixture.io.pendingCount()).isEqualTo(1);
    }

    @Test
    public void orientationChangedWhileLoadQueuedPublishesLatestGeometry() {
        Fixture fixture = new Fixture();
        WallpaperBackdropSnapshot initial = fixture.publishInitial();
        fixture.source.enqueueSuccess();

        fixture.repository.onWallpaperChangedSignal();
        fixture.repository.updateMapping(2400, 1080, 0.7f, 0.5f, 2);
        fixture.io.runNext();

        assertThat(fixture.repository.currentSnapshot()).isNotSameInstanceAs(initial);
        assertThat(fixture.repository.currentSnapshot().orientation()).isEqualTo(2);
        assertThat(fixture.repository.currentSnapshot().displayWidth()).isEqualTo(2400);
        assertThat(fixture.repository.currentSnapshot().displayHeight()).isEqualTo(1080);
    }

    @Test
    public void resumeTransitionsFromStaticToLiveWithoutDroppingStaticSnapshot() {
        Fixture fixture = new Fixture();
        WallpaperBackdropSnapshot initial = fixture.publishInitial();
        fixture.source.identity = new WallpaperIdentity(
                -1, "example.live/.Wallpaper", 0, 0);
        fixture.source.enqueueFailure(LoadStatus.LIVE_WALLPAPER);

        fixture.repository.onResume();
        fixture.io.runNext();

        assertThat(fixture.repository.isLiveWallpaper()).isTrue();
        assertThat(fixture.repository.currentSnapshot()).isSameInstanceAs(initial);
    }

    private static final class Fixture {
        final FakeSource source = new FakeSource();
        final QueuedExecutor io = new QueuedExecutor();
        final OneUiWallpaperBackdropRepository repository =
                new OneUiWallpaperBackdropRepository(source, io, Runnable::run);

        WallpaperBackdropSnapshot publishInitial() {
            source.enqueueSuccess();
            repository.start();
            io.runNext();
            return repository.currentSnapshot();
        }
    }

    private static final class QueuedExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int pendingCount() {
            return tasks.size();
        }

        void runNext() {
            tasks.remove().run();
        }
    }

    private static final class FakeSource implements WallpaperBackdropSource {
        private final Queue<Object> results = new ArrayDeque<>();
        private WallpaperIdentity identity = new WallpaperIdentity(7, null, 0, 0);
        private int loadCount;

        void enqueueSuccess() {
            results.add(LoadStatus.SUCCESS);
        }

        void enqueueFailure(LoadStatus status) {
            results.add(status);
        }

        void enqueueThrowable(Throwable throwable) {
            results.add(throwable);
        }

        @Override
        public WallpaperIdentity readIdentity() {
            return identity;
        }

        @Override
        public LoadResult load(LoadRequest request) {
            loadCount++;
            Object next = results.remove();
            if (next instanceof SecurityException) {
                throw (SecurityException) next;
            }
            if (next instanceof OutOfMemoryError) {
                throw (OutOfMemoryError) next;
            }
            LoadStatus status = (LoadStatus) next;
            if (status != LoadStatus.SUCCESS) {
                return LoadResult.failure(status);
            }
            Bitmap bitmap = mock(Bitmap.class);
            when(bitmap.isMutable()).thenReturn(false);
            when(bitmap.isRecycled()).thenReturn(false);
            when(bitmap.getWidth()).thenReturn(540);
            when(bitmap.getHeight()).thenReturn(1200);
            return LoadResult.success(new WallpaperBackdropSnapshot(
                    bitmap,
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

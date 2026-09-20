/*
 * Copyright (C) 2026 The Lawnchair Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.android.launcher3.graphics;

import static android.content.Intent.ACTION_WALLPAPER_CHANGED;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.Nullable;

import app.lawnchair.wallpaper.WallpaperManagerCompat;

import com.android.launcher3.graphics.WallpaperBackdropSource.LoadRequest;
import com.android.launcher3.graphics.WallpaperBackdropSource.LoadResult;
import com.android.launcher3.graphics.WallpaperBackdropSource.LoadStatus;
import com.android.launcher3.util.SimpleBroadcastReceiver;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Launcher-scoped owner of cached wallpaper generations and their change signals. */
public final class OneUiWallpaperBackdropRepository implements AutoCloseable {
    private static final String TAG = "CrystalWallpaper";
    private static final long COMBINED_BITMAP_BUDGET_BYTES = 24L * 1024L * 1024L;

    /** Current screen geometry and wallpaper offsets, independent from bitmap ownership. */
    public static final class Mapping {
        private final int mDisplayWidth;
        private final int mDisplayHeight;
        private final float mHorizontalOffset;
        private final float mVerticalOffset;
        private final int mOrientation;

        private Mapping(
                int displayWidth,
                int displayHeight,
                float horizontalOffset,
                float verticalOffset,
                int orientation) {
            mDisplayWidth = displayWidth;
            mDisplayHeight = displayHeight;
            mHorizontalOffset = horizontalOffset;
            mVerticalOffset = verticalOffset;
            mOrientation = orientation;
        }

        public int displayWidth() { return mDisplayWidth; }
        public int displayHeight() { return mDisplayHeight; }
        public float horizontalOffset() { return mHorizontalOffset; }
        public float verticalOffset() { return mVerticalOffset; }
        public int orientation() { return mOrientation; }

        private boolean sameGeometry(int width, int height, int orientation) {
            return mDisplayWidth == width && mDisplayHeight == height && mOrientation == orientation;
        }

        private boolean sameValues(
                int width, int height, float horizontal, float vertical, int orientation) {
            return sameGeometry(width, height, orientation)
                    && Float.compare(mHorizontalOffset, horizontal) == 0
                    && Float.compare(mVerticalOffset, vertical) == 0;
        }
    }

    private final WallpaperBackdropSource mSource;
    private final Executor mIoExecutor;
    private final Executor mMainExecutor;
    private final Consumer<String> mEventLogger;
    private final WallpaperSnapshotStore<WallpaperBackdropSnapshot> mStore =
            new WallpaperSnapshotStore<>(WallpaperBackdropSnapshot::recycle);
    private final List<WeakReference<Runnable>> mListeners = new ArrayList<>();
    @Nullable private SimpleBroadcastReceiver mWallpaperChangedReceiver;
    @Nullable private WallpaperManagerCompat mWallpaperManagerCompat;
    @Nullable private WallpaperManagerCompat.OnColorsChangedListener mColorsListener;

    private volatile boolean mLoadScheduled;
    private volatile boolean mLoadRunning;
    private boolean mStarted;
    private volatile boolean mClosed;
    private volatile boolean mLiveWallpaper;
    private volatile Mapping mMapping;
    @Nullable private volatile WallpaperIdentity mLastKnownIdentity;
    @Nullable private volatile WallpaperIdentity mPendingIdentity;

    public OneUiWallpaperBackdropRepository(
            Context context,
            WallpaperBackdropSource source,
            Executor ioExecutor,
            Executor mainExecutor) {
        this(source, ioExecutor, mainExecutor, initialMapping(context),
                message -> Log.i(TAG, message));
        Context applicationContext = context.getApplicationContext();
        mWallpaperChangedReceiver = new SimpleBroadcastReceiver(
                applicationContext, MAIN_EXECUTOR, intent -> onWallpaperChangedSignal());
        mWallpaperManagerCompat = WallpaperManagerCompat.INSTANCE.get(applicationContext);
        mColorsListener = this::onColorsChanged;
    }

    OneUiWallpaperBackdropRepository(
            WallpaperBackdropSource source, Executor ioExecutor, Executor mainExecutor) {
        this(source, ioExecutor, mainExecutor,
                new Mapping(1080, 2400, 0.5f, 0.5f, Configuration.ORIENTATION_PORTRAIT),
                message -> { });
    }

    private OneUiWallpaperBackdropRepository(
            WallpaperBackdropSource source,
            Executor ioExecutor,
            Executor mainExecutor,
            Mapping initialMapping,
            Consumer<String> eventLogger) {
        mSource = Objects.requireNonNull(source);
        mIoExecutor = Objects.requireNonNull(ioExecutor);
        mMainExecutor = Objects.requireNonNull(mainExecutor);
        mEventLogger = Objects.requireNonNull(eventLogger);
        mMapping = initialMapping;
        mWallpaperChangedReceiver = null;
        mWallpaperManagerCompat = null;
        mColorsListener = null;
    }

    public void start() {
        if (mStarted || mClosed) {
            return;
        }
        mStarted = true;
        if (mWallpaperChangedReceiver != null) {
            mWallpaperChangedReceiver.register(ACTION_WALLPAPER_CHANGED);
        }
        if (mWallpaperManagerCompat != null && mColorsListener != null) {
            mWallpaperManagerCompat.addOnChangeListener(mColorsListener);
        }
        requestReload(mSource.readIdentity(), "start");
    }

    public void onResume() {
        if (!mStarted || mClosed) {
            return;
        }
        WallpaperIdentity identity = mSource.readIdentity();
        if (!identity.equals(mLastKnownIdentity)) {
            requestReload(identity, "resume");
        }
    }

    public void onWallpaperChangedSignal() {
        if (mStarted && !mClosed) {
            requestReload(mSource.readIdentity(), "broadcast");
        }
    }

    public void onColorsChanged() {
        if (mStarted && !mClosed) {
            requestReload(mSource.readIdentity(), "colors");
        }
    }

    public void updateMapping(
            int displayWidth,
            int displayHeight,
            float horizontalOffset,
            float verticalOffset,
            int orientation) {
        int safeWidth = Math.max(1, displayWidth);
        int safeHeight = Math.max(1, displayHeight);
        float safeHorizontal = clamp01(horizontalOffset);
        float safeVertical = clamp01(verticalOffset);
        Mapping previous = mMapping;
        if (previous.sameValues(
                safeWidth, safeHeight, safeHorizontal, safeVertical, orientation)) {
            return;
        }
        boolean geometryChanged = !previous.sameGeometry(safeWidth, safeHeight, orientation);
        mMapping = new Mapping(
                safeWidth, safeHeight, safeHorizontal, safeVertical, orientation);
        notifyListeners();
        if (geometryChanged && mStarted && !mClosed) {
            requestReload(mSource.readIdentity(), "geometry");
        }
    }

    public Mapping currentMapping() {
        return mMapping;
    }

    @Nullable
    public WallpaperBackdropSnapshot currentSnapshot() {
        return mStore.current();
    }

    @Nullable
    public WallpaperSnapshotStore.SnapshotPin<WallpaperBackdropSnapshot> pinCurrentSnapshot() {
        return mStore.pinCurrent();
    }

    public boolean isLiveWallpaper() {
        return mLiveWallpaper;
    }

    public void addListener(Runnable listener) {
        removeListener(listener);
        mListeners.add(new WeakReference<>(listener));
    }

    public void removeListener(Runnable listener) {
        for (Iterator<WeakReference<Runnable>> iterator = mListeners.iterator(); iterator.hasNext();) {
            Runnable candidate = iterator.next().get();
            if (candidate == null || candidate == listener) {
                iterator.remove();
            }
        }
    }

    @Override
    public void close() {
        if (mClosed) {
            return;
        }
        mClosed = true;
        if (mWallpaperChangedReceiver != null) {
            mWallpaperChangedReceiver.unregisterReceiverSafely();
        }
        if (mWallpaperManagerCompat != null && mColorsListener != null) {
            mWallpaperManagerCompat.removeOnChangeListener(mColorsListener);
        }
        mListeners.clear();
        mStore.clear();
    }

    private void requestReload(WallpaperIdentity identity, String reason) {
        mLastKnownIdentity = identity;
        mPendingIdentity = identity;
        mLiveWallpaper = identity.isLive();
        long generation = mStore.requestGeneration();
        mEventLogger.accept("Requested generation " + generation + " (" + reason + ")");
        scheduleLoadIfIdle();
    }

    private void scheduleLoadIfIdle() {
        if (mClosed || mLoadScheduled || mLoadRunning) {
            return;
        }
        mLoadScheduled = true;
        mIoExecutor.execute(this::runLatestLoad);
    }

    private void runLatestLoad() {
        if (mClosed) {
            mLoadScheduled = false;
            return;
        }
        mLoadScheduled = false;
        mLoadRunning = true;
        long generation = mStore.requestedGeneration();
        WallpaperIdentity identity = mPendingIdentity;
        Mapping mapping = mMapping;
        if (identity == null) {
            mMainExecutor.execute(() -> finishLoad(generation, null,
                    LoadResult.failure(LoadStatus.UNAVAILABLE)));
            return;
        }
        LoadResult result = mSource.load(new LoadRequest(
                identity,
                mapping.displayWidth(),
                mapping.displayHeight(),
                COMBINED_BITMAP_BUDGET_BYTES,
                generation,
                mapping.horizontalOffset(),
                mapping.verticalOffset(),
                mapping.orientation()));
        mMainExecutor.execute(() -> finishLoad(generation, identity, result));
    }

    private void finishLoad(
            long generation, @Nullable WallpaperIdentity requestedIdentity, LoadResult result) {
        mLoadRunning = false;
        WallpaperBackdropSnapshot candidate = result.snapshot();
        if (mClosed) {
            if (candidate != null) {
                candidate.recycle();
            }
            return;
        }

        boolean newest = generation == mStore.requestedGeneration();
        boolean identityMatches = requestedIdentity != null
                && requestedIdentity.equals(mSource.readIdentity());
        if (result.status() == LoadStatus.SUCCESS && candidate != null) {
            if (newest && identityMatches && mStore.publish(generation, candidate)) {
                mLiveWallpaper = false;
                mEventLogger.accept("Published generation " + generation);
                notifyListeners();
            } else if (!newest) {
                mStore.publish(generation, candidate);
            } else {
                candidate.recycle();
                mStore.fail(generation);
            }
        } else {
            if (result.status() == LoadStatus.LIVE_WALLPAPER) {
                mLiveWallpaper = true;
            }
            mStore.fail(generation);
            mEventLogger.accept("Generation " + generation + " retained previous snapshot: "
                    + result.status());
        }

        if (mStore.requestedGeneration() > generation || !identityMatches) {
            if (!identityMatches) {
                WallpaperIdentity latest = mSource.readIdentity();
                mLastKnownIdentity = latest;
                mPendingIdentity = latest;
                mLiveWallpaper = latest.isLive();
                mStore.requestGeneration();
            }
            scheduleLoadIfIdle();
        }
    }

    private void notifyListeners() {
        List<Runnable> live = new ArrayList<>(mListeners.size());
        for (Iterator<WeakReference<Runnable>> iterator = mListeners.iterator(); iterator.hasNext();) {
            Runnable listener = iterator.next().get();
            if (listener == null) {
                iterator.remove();
            } else {
                live.add(listener);
            }
        }
        for (Runnable listener : live) {
            listener.run();
        }
    }

    private static Mapping initialMapping(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return new Mapping(
                Math.max(1, metrics.widthPixels),
                Math.max(1, metrics.heightPixels),
                0.5f,
                0.5f,
                context.getResources().getConfiguration().orientation);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}

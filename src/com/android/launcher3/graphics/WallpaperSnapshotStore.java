/*
 * Copyright (C) 2026 The Lawnchair Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.graphics;

import androidx.annotation.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Owns a generation-ordered snapshot and defers releasing replaced values while they are pinned.
 *
 * <p>This class is intentionally main-thread confined. Identity bookkeeping prevents two equal but
 * distinct bitmap-backed snapshots from sharing a lifetime accidentally.
 */
public final class WallpaperSnapshotStore<T> {
    /** Releases ownership of a value after it is rejected, replaced, or cleared. */
    public interface Releaser<T> {
        void release(T value);
    }

    /** A stable reference to the snapshot that was current when the pin was acquired. */
    public static final class SnapshotPin<T> implements AutoCloseable {
        private final WallpaperSnapshotStore<T> mOwner;
        private final T mValue;
        private boolean mClosed;

        private SnapshotPin(WallpaperSnapshotStore<T> owner, T value) {
            mOwner = owner;
            mValue = value;
        }

        public T value() {
            return mValue;
        }

        @Override
        public void close() {
            if (mClosed) {
                return;
            }
            mClosed = true;
            mOwner.releasePin(mValue);
        }
    }

    private final Releaser<T> mReleaser;
    private final Map<T, Integer> mPinCounts = new IdentityHashMap<>();
    private final Map<T, Boolean> mRetired = new IdentityHashMap<>();

    private long mRequestedGeneration;
    @Nullable private T mCurrent;

    public WallpaperSnapshotStore(Releaser<T> releaser) {
        mReleaser = Objects.requireNonNull(releaser);
    }

    /** Starts a new load generation, making every earlier unpublished candidate stale. */
    public long requestGeneration() {
        return ++mRequestedGeneration;
    }

    public long requestedGeneration() {
        return mRequestedGeneration;
    }

    /** Publishes only the newest requested generation. Ownership transfers on every call. */
    public boolean publish(long generation, T candidate) {
        Objects.requireNonNull(candidate);
        if (generation != mRequestedGeneration) {
            mReleaser.release(candidate);
            return false;
        }

        if (candidate == mCurrent) {
            return true;
        }

        T replaced = mCurrent;
        mCurrent = candidate;
        if (replaced != null) {
            retire(replaced);
        }
        return true;
    }

    /** Records a failed request without disturbing the last valid snapshot. */
    public void fail(long generation) {
        // The generation remains the newest request, so a late candidate cannot replace current.
    }

    @Nullable
    public T current() {
        return mCurrent;
    }

    @Nullable
    public SnapshotPin<T> pinCurrent() {
        T value = mCurrent;
        if (value == null) {
            return null;
        }
        mPinCounts.put(value, mPinCounts.getOrDefault(value, 0) + 1);
        return new SnapshotPin<>(this, value);
    }

    /** Invalidates pending work and releases current once its final pin closes. */
    public void clear() {
        ++mRequestedGeneration;
        T cleared = mCurrent;
        mCurrent = null;
        if (cleared != null) {
            retire(cleared);
        }
    }

    private void retire(T value) {
        if (mPinCounts.getOrDefault(value, 0) == 0) {
            mReleaser.release(value);
        } else {
            mRetired.put(value, Boolean.TRUE);
        }
    }

    private void releasePin(T value) {
        int count = mPinCounts.getOrDefault(value, 0);
        if (count <= 1) {
            mPinCounts.remove(value);
            if (mRetired.remove(value) != null) {
                mReleaser.release(value);
            }
        } else {
            mPinCounts.put(value, count - 1);
        }
    }
}

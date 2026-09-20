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

/** Maps a screen-space Crystal surface onto a center-cropped wallpaper snapshot. */
public final class WallpaperBackdropTransform {
    private WallpaperBackdropTransform() {}

    /** Immutable geometry captured outside the draw path. */
    public static final class Input {
        private final int mSourceWidth;
        private final int mSourceHeight;
        private final int mBitmapWidth;
        private final int mBitmapHeight;
        private final int mDisplayWidth;
        private final int mDisplayHeight;
        private final float mSurfaceLeft;
        private final float mSurfaceTop;
        private final float mSurfaceWidth;
        private final float mSurfaceHeight;
        private final float mHorizontalOffset;
        private final float mVerticalOffset;
        private final float mOpticalPadding;
        private final boolean mRtl;

        public Input(
                int sourceWidth,
                int sourceHeight,
                int bitmapWidth,
                int bitmapHeight,
                int displayWidth,
                int displayHeight,
                float surfaceLeft,
                float surfaceTop,
                float surfaceWidth,
                float surfaceHeight,
                float horizontalOffset,
                float verticalOffset,
                float opticalPadding,
                boolean rtl) {
            mSourceWidth = sourceWidth;
            mSourceHeight = sourceHeight;
            mBitmapWidth = bitmapWidth;
            mBitmapHeight = bitmapHeight;
            mDisplayWidth = displayWidth;
            mDisplayHeight = displayHeight;
            mSurfaceLeft = surfaceLeft;
            mSurfaceTop = surfaceTop;
            mSurfaceWidth = surfaceWidth;
            mSurfaceHeight = surfaceHeight;
            mHorizontalOffset = horizontalOffset;
            mVerticalOffset = verticalOffset;
            mOpticalPadding = opticalPadding;
            mRtl = rtl;
        }
    }

    /** Bitmap-space sample bounds. */
    public static final class Result {
        private static final Result INVALID = new Result(0f, 0f, 0f, 0f, false);

        private final float mLeft;
        private final float mTop;
        private final float mRight;
        private final float mBottom;
        private final boolean mValid;

        private Result(float left, float top, float right, float bottom, boolean valid) {
            mLeft = left;
            mTop = top;
            mRight = right;
            mBottom = bottom;
            mValid = valid;
        }

        public float left() {
            return mLeft;
        }

        public float top() {
            return mTop;
        }

        public float right() {
            return mRight;
        }

        public float bottom() {
            return mBottom;
        }

        public boolean isValid() {
            return mValid;
        }
    }

    public static Result map(Input input) {
        if (input == null
                || input.mSourceWidth <= 0
                || input.mSourceHeight <= 0
                || input.mBitmapWidth <= 0
                || input.mBitmapHeight <= 0
                || input.mDisplayWidth <= 0
                || input.mDisplayHeight <= 0
                || input.mSurfaceWidth <= 0f
                || input.mSurfaceHeight <= 0f) {
            return Result.INVALID;
        }

        float scale = Math.max(
                input.mDisplayWidth / (float) input.mSourceWidth,
                input.mDisplayHeight / (float) input.mSourceHeight);
        float scaledWidth = input.mSourceWidth * scale;
        float scaledHeight = input.mSourceHeight * scale;
        float overflowX = Math.max(0f, scaledWidth - input.mDisplayWidth);
        float overflowY = Math.max(0f, scaledHeight - input.mDisplayHeight);
        float horizontalOffset = clamp01(input.mHorizontalOffset);
        if (input.mRtl) {
            horizontalOffset = 1f - horizontalOffset;
        }
        float visibleLeft = overflowX * horizontalOffset;
        float visibleTop = overflowY * clamp01(input.mVerticalOffset);

        float padding = Math.max(0f, input.mOpticalPadding);
        float bitmapScaleX = input.mBitmapWidth / (float) input.mSourceWidth;
        float bitmapScaleY = input.mBitmapHeight / (float) input.mSourceHeight;
        float left = ((visibleLeft + input.mSurfaceLeft - padding) / scale) * bitmapScaleX;
        float top = ((visibleTop + input.mSurfaceTop - padding) / scale) * bitmapScaleY;
        float right = ((visibleLeft + input.mSurfaceLeft + input.mSurfaceWidth + padding) / scale)
                * bitmapScaleX;
        float bottom = ((visibleTop + input.mSurfaceTop + input.mSurfaceHeight + padding) / scale)
                * bitmapScaleY;

        left = clamp(left, 0f, input.mBitmapWidth);
        top = clamp(top, 0f, input.mBitmapHeight);
        right = clamp(right, 0f, input.mBitmapWidth);
        bottom = clamp(bottom, 0f, input.mBitmapHeight);
        boolean valid = Float.isFinite(left)
                && Float.isFinite(top)
                && Float.isFinite(right)
                && Float.isFinite(bottom)
                && right - left > 1f
                && bottom - top > 1f;
        return valid ? new Result(left, top, right, bottom, true) : Result.INVALID;
    }

    private static float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

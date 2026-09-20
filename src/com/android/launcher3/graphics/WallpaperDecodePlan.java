/*
 * Copyright (C) 2026 The Lawnchair Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.android.launcher3.graphics;

/** Memory-bounded immutable bitmap decode dimensions. */
public final class WallpaperDecodePlan {
    private static final int BYTES_PER_PIXEL = 4;
    private static final int SIMULTANEOUS_BITMAPS = 2;
    private static final WallpaperDecodePlan INVALID = new WallpaperDecodePlan(0, 0, 0, false);

    private final int mSampleSize;
    private final int mWidth;
    private final int mHeight;
    private final boolean mValid;

    private WallpaperDecodePlan(int sampleSize, int width, int height, boolean valid) {
        mSampleSize = sampleSize;
        mWidth = width;
        mHeight = height;
        mValid = valid;
    }

    public static WallpaperDecodePlan forBounds(
            int sourceWidth,
            int sourceHeight,
            int displayWidth,
            int displayHeight,
            long combinedBudgetBytes) {
        if (sourceWidth <= 0
                || sourceHeight <= 0
                || displayWidth <= 0
                || displayHeight <= 0
                || combinedBudgetBytes < SIMULTANEOUS_BITMAPS * BYTES_PER_PIXEL) {
            return INVALID;
        }

        int sampleSize = 1;
        while (!fitsBudget(sourceWidth, sourceHeight, sampleSize, combinedBudgetBytes)) {
            if (sampleSize > (1 << 29)) {
                return INVALID;
            }
            sampleSize <<= 1;
        }
        int width = divideRoundUp(sourceWidth, sampleSize);
        int height = divideRoundUp(sourceHeight, sampleSize);
        return width > 0 && height > 0
                ? new WallpaperDecodePlan(sampleSize, width, height, true)
                : INVALID;
    }

    public int sampleSize() {
        return mSampleSize;
    }

    public int width() {
        return mWidth;
    }

    public int height() {
        return mHeight;
    }

    public boolean isValid() {
        return mValid;
    }

    private static boolean fitsBudget(int width, int height, int sampleSize, long budget) {
        long decodedWidth = divideRoundUp(width, sampleSize);
        long decodedHeight = divideRoundUp(height, sampleSize);
        if (decodedWidth > Long.MAX_VALUE / decodedHeight) {
            return false;
        }
        long pixels = decodedWidth * decodedHeight;
        long multiplier = (long) SIMULTANEOUS_BITMAPS * BYTES_PER_PIXEL;
        return pixels <= budget / multiplier;
    }

    private static int divideRoundUp(int value, int divisor) {
        return (int) ((value + (long) divisor - 1L) / divisor);
    }
}

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

/** Precomputed material values and allocation-free signed-distance optics for Crystal surfaces. */
public final class OneUiCrystalOptics {
    private OneUiCrystalOptics() {}

    /** Immutable values recalculated only when density or user intensity changes. */
    public static final class Values {
        private final float mRefractionPx;
        private final float mEdgeBandPx;
        private final float mBlurRadiusPx;
        private final float mDispersionPx;
        private final float mTintAlpha;
        private final float mRimAlpha;
        private final float mShadowAlpha;

        private Values(
                float refractionPx,
                float edgeBandPx,
                float blurRadiusPx,
                float dispersionPx,
                float tintAlpha,
                float rimAlpha,
                float shadowAlpha) {
            mRefractionPx = refractionPx;
            mEdgeBandPx = edgeBandPx;
            mBlurRadiusPx = blurRadiusPx;
            mDispersionPx = dispersionPx;
            mTintAlpha = tintAlpha;
            mRimAlpha = rimAlpha;
            mShadowAlpha = shadowAlpha;
        }

        public float refractionPx() {
            return mRefractionPx;
        }

        public float edgeBandPx() {
            return mEdgeBandPx;
        }

        public float blurRadiusPx() {
            return mBlurRadiusPx;
        }

        public float dispersionPx() {
            return mDispersionPx;
        }

        public float tintAlpha() {
            return mTintAlpha;
        }

        public float rimAlpha() {
            return mRimAlpha;
        }

        public float shadowAlpha() {
            return mShadowAlpha;
        }
    }

    public static Values forIntensity(int intensityPercent, float density) {
        float amount = clamp(intensityPercent / 100f, 0f, 1f);
        float safeDensity = Math.max(0.1f, density);
        return new Values(
                lerp(3f, 15f, amount) * safeDensity,
                lerp(5f, 13f, amount) * safeDensity,
                lerp(3f, 9f, amount) * safeDensity,
                lerp(0.12f, 1.15f, amount) * safeDensity,
                lerp(0.045f, 0.11f, amount),
                lerp(0.16f, 0.32f, amount),
                lerp(0.10f, 0.20f, amount));
    }

    /** Returns one at the contour and smoothly approaches zero toward the stable center. */
    public static float edgeWeight(float signedDistancePx, float edgeBandPx) {
        if (edgeBandPx <= 0f || !Float.isFinite(signedDistancePx)) {
            return 0f;
        }
        float distanceInside = Math.max(0f, -signedDistancePx);
        float normalized = clamp(distanceInside / edgeBandPx, 0f, 1f);
        float smooth = normalized * normalized * (3f - 2f * normalized);
        return 1f - smooth;
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

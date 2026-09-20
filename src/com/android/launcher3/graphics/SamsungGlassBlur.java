/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package com.android.launcher3.graphics;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

import app.lawnchair.oneui.OneUiGlassStyle;

/**
 * Samsung One UI backdrop blur bridge.
 *
 * <p>SemBlurInfo is part of Samsung's framework rather than the Android SDK, so this class resolves
 * it at runtime and fails closed on non-Samsung builds. Both Samsung's hidden_* entry points and
 * the older public names are tried; no Samsung classes are linked at compile time.</p>
 */
public final class SamsungGlassBlur {
    private static final String TAG = "SamsungGlassBlur";
    private static final String BLUR_INFO = "android.view.SemBlurInfo";
    private static final String BUILDER = "android.view.SemBlurInfo$Builder";
    private static final int BLUR_MODE_WINDOW = 0;

    private static final WeakHashMap<View, State> STATES = new WeakHashMap<>();

    private SamsungGlassBlur() { }

    public static boolean apply(View view, int style, int intensityPercent, int sourceColor,
            float cornerRadius) {
        if (view == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || !OneUiGlassStyle.isGlass(style) || intensityPercent <= 0) {
            clear(view);
            return false;
        }

        final int intensity = clamp(intensityPercent, 0, 100);
        State old = STATES.get(view);
        if (old != null && old.matches(style, intensity, sourceColor, cornerRadius)) {
            return old.active;
        }

        boolean active = false;
        try {
            Class<?> blurInfoClass = Class.forName(BLUR_INFO);
            Class<?> builderClass = Class.forName(BUILDER);
            Constructor<?> constructor = builderClass.getDeclaredConstructor(int.class);
            constructor.setAccessible(true);
            Object builder = constructor.newInstance(BLUR_MODE_WINDOW);

            boolean dark = (view.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            float density = view.getResources().getDisplayMetrics().density;
            float strength = intensity / 100f;

            // Preset first because Samsung presets also carry a radius; our material radius wins.
            invokeOptional(builderClass, builder,
                    new String[]{"setColorCurvePreset", "hidden_setColorCurvePreset"},
                    new Class<?>[]{int.class}, presetFor(style, dark));

            invokeRequired(builderClass, builder,
                    new String[]{"hidden_setRadius", "setRadius"},
                    new Class<?>[]{int.class}, radiusPx(style, strength, density));

            invokeRequired(builderClass, builder,
                    new String[]{"hidden_setBackgroundColor", "setBackgroundColor"},
                    new Class<?>[]{int.class},
                    backgroundColor(style, strength, sourceColor, dark));

            invokeRequired(builderClass, builder,
                    new String[]{"hidden_setBackgroundCornerRadius", "setBackgroundCornerRadius"},
                    new Class<?>[]{float.class}, Math.max(0f, cornerRadius));

            Object blurInfo = invokeRequired(builderClass, builder,
                    new String[]{"hidden_build", "build"}, new Class<?>[0]);

            Method apply = findMethod(View.class,
                    new String[]{"hidden_semSetBlurInfo", "semSetBlurInfo"}, blurInfoClass);
            apply.invoke(view, blurInfo);
            active = true;
        } catch (Throwable error) {
            clearInternal(view);
            Log.d(TAG, "Samsung backdrop blur unavailable; using renderer fallback", error);
        }

        STATES.put(view, new State(style, intensity, sourceColor, cornerRadius, active));
        return active;
    }

    public static void clear(View view) {
        if (view == null) return;
        State state = STATES.remove(view);
        if (state != null && state.active) clearInternal(view);
    }

    private static void clearInternal(View view) {
        try {
            Class<?> blurInfoClass = Class.forName(BLUR_INFO);
            Method apply = findMethod(View.class,
                    new String[]{"hidden_semSetBlurInfo", "semSetBlurInfo"}, blurInfoClass);
            apply.invoke(view, new Object[]{null});
        } catch (Throwable ignored) {
            // Non-Samsung devices and unsupported One UI builds intentionally land here.
        }
    }

    private static int presetFor(int style, boolean dark) {
        if (style == OneUiGlassStyle.CRYSTAL) return dark ? 116 : 101; // LOW / ULTRA_THIN
        if (style == OneUiGlassStyle.FROSTY) return dark ? 130 : 115; // HIGH / ULTRA_THICK
        return dark ? 123 : 108; // MEDIUM / REGULAR
    }

    private static int radiusPx(int style, float strength, float density) {
        final float dp;
        if (style == OneUiGlassStyle.CRYSTAL) {
            dp = 8f + 16f * strength;
        } else if (style == OneUiGlassStyle.FROSTY) {
            dp = 32f + 48f * strength;
        } else {
            dp = 18f + 34f * strength;
        }
        return Math.max(1, Math.round(dp * density));
    }

    private static int backgroundColor(int style, float strength, int source, boolean dark) {
        int rgb = source;
        float alphaScale;
        if (style == OneUiGlassStyle.CRYSTAL) {
            alphaScale = 0.05f + 0.08f * strength;
        } else if (style == OneUiGlassStyle.FROSTY) {
            rgb = blendRgb(source, dark ? Color.rgb(36, 38, 42) : Color.WHITE,
                    dark ? 0.18f : 0.38f);
            alphaScale = 0.24f + 0.22f * strength;
        } else {
            rgb = blendRgb(source, dark ? Color.BLACK : Color.WHITE, dark ? 0.08f : 0.14f);
            alphaScale = 0.10f + 0.13f * strength;
        }
        int alpha = clamp(Math.round(Color.alpha(source) * alphaScale), 0, 255);
        return Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb));
    }

    private static Object invokeRequired(Class<?> owner, Object target, String[] names,
            Class<?>[] types, Object... args) throws Exception {
        Method method = findMethod(owner, names, types);
        return method.invoke(target, args);
    }

    private static Object invokeOptional(Class<?> owner, Object target, String[] names,
            Class<?>[] types, Object... args) {
        try {
            return invokeRequired(owner, target, names, types, args);
        } catch (Throwable ignored) {
            return target;
        }
    }

    private static Method findMethod(Class<?> owner, String[] names, Class<?>... types)
            throws NoSuchMethodException {
        NoSuchMethodException last = null;
        for (String name : names) {
            try {
                Method method = owner.getDeclaredMethod(name, types);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException error) {
                last = error;
            }
            try {
                Method method = owner.getMethod(name, types);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException error) {
                last = error;
            }
        }
        throw last != null ? last : new NoSuchMethodException();
    }

    private static int blendRgb(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class State {
        final int style;
        final int intensity;
        final int color;
        final float corner;
        final boolean active;

        State(int style, int intensity, int color, float corner, boolean active) {
            this.style = style;
            this.intensity = intensity;
            this.color = color;
            this.corner = corner;
            this.active = active;
        }

        boolean matches(int style, int intensity, int color, float corner) {
            return this.style == style && this.intensity == intensity && this.color == color
                    && Math.abs(this.corner - corner) < 0.5f;
        }
    }
}

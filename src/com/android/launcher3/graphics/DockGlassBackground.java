/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */
package com.android.launcher3.graphics;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;

/** A bounded platform blur surface with static tint/highlight layers; never captures wallpaper. */
public final class DockGlassBackground {
    private DockGlassBackground() { }

    public static Drawable create(View host, boolean crystal, int color, float cornerRadius) {
        float density = host.getResources().getDisplayMetrics().density;
        Drawable blur = createPlatformBlur(host, Math.round((crystal ? 16 : 32) * density),
                cornerRadius);
        int alpha = Math.round(Color.alpha(color) * (crystal ? 0.28f : 0.6f));
        if (blur == null) alpha = Math.max(alpha, crystal ? 38 : 100);
        GradientDrawable tint = new GradientDrawable();
        tint.setCornerRadius(cornerRadius);
        tint.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
        Drawable surface = blur == null ? tint : new LayerDrawable(new Drawable[]{blur, tint});
        if (!crystal) return surface;
        GradientDrawable highlight = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x12ffffff, 0x00000000, 0x09000000});
        highlight.setCornerRadius(cornerRadius);
        highlight.setStroke(Math.max(1, Math.round(density)), 0x35ffffff);
        return new LayerDrawable(new Drawable[]{surface, highlight});
    }

    private static Drawable createPlatformBlur(View host, int radius, float corners) {
        if (Build.VERSION.SDK_INT < 31 || !host.isAttachedToWindow()
                || !host.isHardwareAccelerated()) return null;
        Drawable drawable = null;
        try {
            WindowManager manager = host.getContext().getSystemService(WindowManager.class);
            if (manager == null || !manager.isCrossWindowBlurEnabled()) return null;
            // This platform API is not available to every build/OEM. A rejected lookup uses
            // the translucent fallback; no hidden-API exemptions or additional host are added.
            Object root = View.class.getMethod("getViewRootImpl").invoke(host);
            if (root == null) return null;
            drawable = (Drawable) root.getClass().getMethod("createBackgroundBlurDrawable")
                    .invoke(root);
            drawable.getClass().getMethod("setBlurRadius", int.class).invoke(drawable, radius);
            drawable.getClass().getMethod("setCornerRadius", float.class).invoke(drawable, corners);
            return drawable;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            if (drawable != null) drawable.setVisible(false, false);
            return null;
        }
    }
}

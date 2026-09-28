/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package com.android.launcher3.graphics;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

/** Wallpaper-backed Frosty material shared by open and closed folder surfaces. */
public final class OneUiFrostedBackdropView extends FrameLayout {
    private static final String TAG = "OneUiFolderBackdrop";
    private final WallpaperBackdropView mBackdrop;
    private int mColor = Integer.MIN_VALUE;
    private int mIntensity = Integer.MIN_VALUE;
    private int mNightMode = Integer.MIN_VALUE;
    private float mCornerRadius = Float.NaN;

    public OneUiFrostedBackdropView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        mBackdrop = new WallpaperBackdropView(context);
        mBackdrop.setVisibility(View.GONE);
        addView(mBackdrop, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    /** Measures the contained wallpaper view too; these folder surfaces are overlay-laid out. */
    public void layoutSurface(int left, int top, int right, int bottom) {
        int width = Math.max(0, right - left);
        int height = Math.max(0, bottom - top);
        if (getMeasuredWidth() != width || getMeasuredHeight() != height) {
            int widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            int heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            measure(widthSpec, heightSpec);
        }
        layout(left, top, right, bottom);
    }

    /** Configures the same cached wallpaper snapshot and GPU blur path used by the dock. */
    public void configure(int color, float cornerRadius, int intensity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        mBackdrop.setVisibility(View.VISIBLE);
        int nightMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        if (color == mColor && intensity == mIntensity && nightMode == mNightMode
                && Float.compare(cornerRadius, mCornerRadius) == 0) return;

        float density = getResources().getDisplayMetrics().density;
        OneUiGlassProfile profile = OneUiGlassProfile.create(
                app.lawnchair.oneui.OneUiGlassStyle.FROSTY, intensity,
                nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES);
        float blurRadius = profile.radiusDp * density * 0.46f;
        // Wallpaper drawables can expose translucent edge pixels. Back them with the selected
        // neutral tint so same-window widgets cannot bleed through the frosted folder surface.
        mBackdrop.setBackgroundColor(
                OneUiGlassProfile.frostyBackdropFallbackColor(color));
        mBackdrop.configureGlassBlur(blurRadius, cornerRadius, 0, 0, 0, 0, true);

        // The wallpaper snapshot is the surface. Keep the fallback transparent so a failed
        // snapshot cannot turn the folder's chosen accent into a flat colored capsule.
        setBackgroundColor(Color.TRANSPARENT);
        setForeground(OneUiGlassBackground.createFrostyBackdropOverlay(
                this, color, cornerRadius, intensity));
        setForegroundGravity(android.view.Gravity.FILL);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });
        setClipToOutline(true);
        invalidateOutline();

        mColor = color;
        mIntensity = intensity;
        mNightMode = nightMode;
        mCornerRadius = cornerRadius;
    }

    public void clearGlass() {
        mBackdrop.clearGlassBlur();
        mBackdrop.setVisibility(View.GONE);
        setForeground(null);
        setBackground(null);
        setClipToOutline(false);
        mColor = Integer.MIN_VALUE;
        mIntensity = Integer.MIN_VALUE;
        mNightMode = Integer.MIN_VALUE;
        mCornerRadius = Float.NaN;
    }

    /** Re-records the wallpaper crop after the folder icon moves between workspace pages. */
    public void refreshBackdropPosition() {
        Log.d(TAG, "refreshing wallpaper crop after folder page movement");
        mBackdrop.invalidate();
        invalidate();
    }
}

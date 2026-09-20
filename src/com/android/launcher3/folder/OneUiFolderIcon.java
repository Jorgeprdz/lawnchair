/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package com.android.launcher3.folder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.graphics.OneUiGlassBackground;
import com.android.launcher3.graphics.SamsungGlassBlur;

import app.lawnchair.oneui.OneUiGlassPreferences;
import app.lawnchair.oneui.OneUiGlassStyle;

/** FolderIcon wrapper with a blur surface bounded to the preview instead of the whole icon cell. */
public class OneUiFolderIcon extends FolderIcon {
    private View mGlassSurface;
    private boolean mNativeGlassActive;
    private int mLastMode = Integer.MIN_VALUE;
    private int mLastIntensity = Integer.MIN_VALUE;
    private int mLastColor = Integer.MIN_VALUE;
    private float mLastCorner = Float.NaN;
    private boolean mLastNative;

    public OneUiFolderIcon(Context context) {
        super(context);
        installGlassBackground(context);
    }

    public OneUiFolderIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
        installGlassBackground(context);
    }

    private void installGlassBackground(Context context) {
        setFolderBackground(new OneUiPreviewBackground(context));
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        ensureGlassSurface();
    }

    private void ensureGlassSurface() {
        if (mGlassSurface != null) return;
        mGlassSurface = new View(getContext());
        mGlassSurface.setClickable(false);
        mGlassSurface.setFocusable(false);
        mGlassSurface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        mGlassSurface.setVisibility(GONE);
        addView(mGlassSurface, 0, new FrameLayout.LayoutParams(1, 1));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        updateGlassSurface();
        super.dispatchDraw(canvas);
    }

    boolean isNativeGlassActive() {
        return mNativeGlassActive;
    }

    private void updateGlassSurface() {
        ensureGlassSurface();
        int mode = OneUiGlassPreferences.getFolderMode(getContext());
        if (!OneUiGlassStyle.isGlass(mode)) {
            mNativeGlassActive = false;
            SamsungGlassBlur.clear(mGlassSurface);
            mGlassSurface.setBackground(null);
            mGlassSurface.setVisibility(GONE);
            mLastMode = Integer.MIN_VALUE;
            return;
        }

        PreviewBackground preview = getFolderBackground();
        int left = preview.getOffsetX();
        int top = preview.getOffsetY();
        int diameter = Math.max(1, preview.getScaledRadius() * 2);
        mGlassSurface.layout(left, top, left + diameter, top + diameter);

        float corner;
        if (isLargeFolder()) {
            corner = OneUiGlassPreferences.isLargeFolderCircular(getContext())
                    ? diameter / 2f
                    : diameter * 0.16f;
        } else {
            corner = diameter / 2f;
        }

        int color = preview.getBgColor();
        int intensity = OneUiGlassPreferences.getFolderIntensity(getContext(), mode);
        boolean allowNativeBlur = OneUiGlassBackground.shouldApplySamsungBackdrop(
                mGlassSurface, mode);
        if (!allowNativeBlur) SamsungGlassBlur.clear(mGlassSurface);
        boolean nativeBlur = allowNativeBlur && SamsungGlassBlur.apply(
                mGlassSurface, mode, intensity, color, corner);
        mNativeGlassActive = nativeBlur;

        if (mode != mLastMode || intensity != mLastIntensity || color != mLastColor
                || Math.abs(corner - mLastCorner) >= 0.5f || nativeBlur != mLastNative) {
            Drawable material = nativeBlur
                    ? OneUiGlassBackground.createFolderIconOverlay(mGlassSurface, color, corner)
                    : OneUiGlassBackground.createFolderIcon(mGlassSurface, color, corner);
            mGlassSurface.setBackground(material);
            mLastMode = mode;
            mLastIntensity = intensity;
            mLastColor = color;
            mLastCorner = corner;
            mLastNative = nativeBlur;
        }
        mGlassSurface.setVisibility(VISIBLE);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mGlassSurface != null) SamsungGlassBlur.clear(mGlassSurface);
        super.onDetachedFromWindow();
    }
}

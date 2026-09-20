/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package com.android.launcher3.folder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import com.android.launcher3.R;
import com.android.launcher3.graphics.OneUiGlassBackground;
import com.android.launcher3.graphics.SamsungGlassBlur;

import app.lawnchair.oneui.OneUiGlassPreferences;
import app.lawnchair.oneui.OneUiGlassStyle;
import app.lawnchair.util.LawnchairUtilsKt;

/** Open Folder wrapper adding the same shared glass material used by the dock. */
public class OneUiFolder extends Folder {
    private Path mOneUiClipPath;
    private Drawable mGlass;
    private int mLastGlassColor = Integer.MIN_VALUE;
    private boolean mLastNativeBlur;
    private boolean mGlassAnimationRunning;
    private final OnFolderStateChangedListener mGlassStateListener = state -> {
        mGlassAnimationRunning = state == STATE_ANIMATING;
        OneUiGlassBackground.setAnimationRunning(mGlass, mGlassAnimationRunning);
    };

    public OneUiFolder(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setClipPath(Path clipPath) {
        mOneUiClipPath = clipPath;
        super.setClipPath(clipPath);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int mode = OneUiGlassPreferences.getFolderMode(getContext());
        Drawable solid = super.getBackground();
        int solidAlpha = LawnchairUtilsKt.getFolderBackgroundAlpha(getContext());
        solid.setAlpha(mode == OneUiGlassStyle.SOLID ? solidAlpha : 0);

        if (OneUiGlassStyle.isGlass(mode)) {
            int baseColor = LawnchairUtilsKt.resolveFolderBackgroundColor(getContext());
            int glassColor = Color.argb(solidAlpha,
                    Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
            float corners = getResources().getDimension(R.dimen.bg_round_rect_radius);
            int intensity = OneUiGlassPreferences.getFolderIntensity(getContext(), mode);
            boolean allowNativeBlur = OneUiGlassBackground.shouldApplySamsungBackdrop(this, mode);
            if (!allowNativeBlur) SamsungGlassBlur.clear(this);
            boolean nativeBlur = allowNativeBlur
                    && SamsungGlassBlur.apply(this, mode, intensity, glassColor, corners);

            if (mGlass == null || glassColor != mLastGlassColor
                    || nativeBlur != mLastNativeBlur) {
                if (mGlass != null) mGlass.setVisible(false, false);
                mGlass = nativeBlur
                        ? OneUiGlassBackground.createOpenFolderOverlay(this, glassColor, corners)
                        : OneUiGlassBackground.createOpenFolder(this, glassColor, corners);
                OneUiGlassBackground.setAnimationRunning(mGlass, mGlassAnimationRunning);
                mLastGlassColor = glassColor;
                mLastNativeBlur = nativeBlur;
            }
            mGlass.setBounds(0, 0, getWidth(), getHeight());
            int save = canvas.save();
            if (mOneUiClipPath != null) canvas.clipPath(mOneUiClipPath);
            mGlass.draw(canvas);
            canvas.restoreToCount(save);
        } else {
            SamsungGlassBlur.clear(this);
            if (mGlass != null) mGlass.setVisible(false, false);
            mGlass = null;
            mLastGlassColor = Integer.MIN_VALUE;
            mLastNativeBlur = false;
        }

        super.dispatchDraw(canvas);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeOnFolderStateChangedListener(mGlassStateListener);
        addOnFolderStateChangedListener(mGlassStateListener);
        mGlassAnimationRunning = getState() == STATE_ANIMATING;
        OneUiGlassBackground.setAnimationRunning(mGlass, mGlassAnimationRunning);
    }

    @Override
    protected void onDetachedFromWindow() {
        mGlassAnimationRunning = false;
        OneUiGlassBackground.setAnimationRunning(mGlass, false);
        removeOnFolderStateChangedListener(mGlassStateListener);
        SamsungGlassBlur.clear(this);
        super.onDetachedFromWindow();
    }
}

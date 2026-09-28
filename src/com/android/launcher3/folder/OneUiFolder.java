/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package com.android.launcher3.folder;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import com.android.launcher3.R;
import com.android.launcher3.graphics.OneUiGlassBackground;
import com.android.launcher3.graphics.OneUiFrostedBackdropView;
import com.android.launcher3.graphics.SamsungGlassBlur;

import app.lawnchair.oneui.OneUiGlassPreferences;
import app.lawnchair.oneui.OneUiGlassStyle;
import app.lawnchair.util.LawnchairUtilsKt;

/** Open Folder wrapper adding the same shared glass material used by the dock. */
public class OneUiFolder extends Folder {
    private Path mOneUiClipPath;
    private final android.graphics.Rect mEmptyBackdropClip = new android.graphics.Rect();
    private Drawable mGlass;
    private int mLastGlassMode = Integer.MIN_VALUE;
    private int mLastGlassIntensity = Integer.MIN_VALUE;
    private int mLastGlassColor = Integer.MIN_VALUE;
    private int mLastNightMode = Integer.MIN_VALUE;
    private boolean mLastNativeBlur;
    private OneUiFrostedBackdropView mFrostyBackdrop;

    public OneUiFolder(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mFrostyBackdrop = new OneUiFrostedBackdropView(getContext());
        mFrostyBackdrop.setVisibility(View.GONE);
        // Keep this child out of LinearLayout measurement; it is laid over the folder bounds
        // immediately before dispatchDraw so the existing folder content keeps its geometry.
        addView(mFrostyBackdrop, 0, new LinearLayout.LayoutParams(0, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mFrostyBackdrop != null) {
            // layoutSurface() temporarily measures the backdrop to the folder's full bounds for
            // manual drawing. Restore its zero-size LinearLayout measurement before layout so it
            // cannot push FolderPagedView or the footer out of the visible folder bounds.
            int zero = MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY);
            mFrostyBackdrop.measure(zero, zero);
        }
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
            int nightMode = getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            boolean wallpaperBackdrop = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && OneUiGlassStyle.usesWallpaperSnapshotForFolder(mode);
            if (wallpaperBackdrop && mFrostyBackdrop != null) {
                if (mGlass != null) {
                    mGlass.setVisible(false, false);
                    mGlass = null;
                }
                mFrostyBackdrop.layoutSurface(0, 0, getWidth(), getHeight());
                mFrostyBackdrop.configure(glassColor, corners,
                        OneUiGlassPreferences.getFolderIntensity(getContext(), mode));
                mFrostyBackdrop.setVisibility(View.VISIBLE);
                int backdropSave = canvas.save();
                if (mOneUiClipPath != null) canvas.clipPath(mOneUiClipPath);
                mFrostyBackdrop.draw(canvas);
                canvas.restoreToCount(backdropSave);
                // It was drawn above with the folder's animation clip; suppress the normal child
                // pass to avoid drawing the full-screen wallpaper twice.
                mFrostyBackdrop.setClipBounds(mEmptyBackdropClip);
                super.dispatchDraw(canvas);
                mFrostyBackdrop.setClipBounds(null);
                return;
            } else if (mFrostyBackdrop != null) {
                mFrostyBackdrop.clearGlass();
                mFrostyBackdrop.setVisibility(View.GONE);
            }
            boolean nativeBlur;
            if (mode == OneUiGlassStyle.LIQUID_GLASS || mode == OneUiGlassStyle.FROSTY) {
                SamsungGlassBlur.clear(this);
                nativeBlur = false;
            } else {
                nativeBlur = SamsungGlassBlur.apply(this, mode, intensity, glassColor, corners);
            }

            if (mGlass == null || mode != mLastGlassMode || intensity != mLastGlassIntensity
                    || glassColor != mLastGlassColor
                    || nightMode != mLastNightMode || nativeBlur != mLastNativeBlur) {
                if (mGlass != null) mGlass.setVisible(false, false);
                mGlass = nativeBlur
                        ? OneUiGlassBackground.createFolderOverlay(this, glassColor, corners)
                        : OneUiGlassBackground.createFolder(this, glassColor, corners);
                mLastGlassColor = glassColor;
                mLastGlassMode = mode;
                mLastGlassIntensity = intensity;
                mLastNightMode = nightMode;
                mLastNativeBlur = nativeBlur;
            }
            mGlass.setBounds(0, 0, getWidth(), getHeight());
            int save = canvas.save();
            if (mOneUiClipPath != null) canvas.clipPath(mOneUiClipPath);
            mGlass.draw(canvas);
            canvas.restoreToCount(save);
        } else {
            if (mFrostyBackdrop != null) {
                mFrostyBackdrop.clearGlass();
                mFrostyBackdrop.setVisibility(View.GONE);
            }
            SamsungGlassBlur.clear(this);
            if (mGlass != null) mGlass.setVisible(false, false);
            mGlass = null;
            mLastGlassColor = Integer.MIN_VALUE;
            mLastGlassMode = Integer.MIN_VALUE;
            mLastGlassIntensity = Integer.MIN_VALUE;
            mLastNightMode = Integer.MIN_VALUE;
            mLastNativeBlur = false;
        }

        super.dispatchDraw(canvas);
    }

    @Override
    protected void onDetachedFromWindow() {
        SamsungGlassBlur.clear(this);
        super.onDetachedFromWindow();
    }
}

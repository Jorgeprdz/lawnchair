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
import com.android.launcher3.graphics.DockGlassBackground;

import app.lawnchair.oneui.OneUiGlassPreferences;
import app.lawnchair.util.LawnchairUtilsKt;

/** Open Folder wrapper adding the same One UI glass surface used by the dock. */
public class OneUiFolder extends Folder {
    private Path mOneUiClipPath;
    private Drawable mGlass;
    private int mLastGlassColor = Integer.MIN_VALUE;

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
        solid.setAlpha(mode == 1 ? solidAlpha : 0);

        if (mode >= DockGlassBackground.STYLE_BLUR) {
            int baseColor = LawnchairUtilsKt.resolveFolderBackgroundColor(getContext());
            int glassColor = Color.argb(solidAlpha,
                    Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
            if (mGlass == null || glassColor != mLastGlassColor) {
                if (mGlass != null) mGlass.setVisible(false, false);
                float corners = getResources().getDimension(R.dimen.bg_round_rect_radius);
                mGlass = DockGlassBackground.createFolder(this, glassColor, corners);
                mLastGlassColor = glassColor;
            }
            mGlass.setBounds(0, 0, getWidth(), getHeight());
            int save = canvas.save();
            if (mOneUiClipPath != null) canvas.clipPath(mOneUiClipPath);
            mGlass.draw(canvas);
            canvas.restoreToCount(save);
        }

        super.dispatchDraw(canvas);
    }
}

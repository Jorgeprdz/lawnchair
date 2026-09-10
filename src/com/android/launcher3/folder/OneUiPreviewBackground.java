/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package com.android.launcher3.folder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.android.launcher3.graphics.DockGlassBackground;
import com.android.launcher3.views.ActivityContext;

import app.lawnchair.oneui.OneUiGlassPreferences;

/** Preview background that layers One UI glass without changing FolderIcon placement logic. */
public class OneUiPreviewBackground extends PreviewBackground {
    private View mHost;
    private Drawable mGlass;
    private int mLastColor = Integer.MIN_VALUE;

    public OneUiPreviewBackground(Context context) {
        super(context);
    }

    @Override
    public void setup(Context context, ActivityContext activity, View invalidateDelegate,
            int availableSpaceX, int topPadding) {
        super.setup(context, activity, invalidateDelegate, availableSpaceX, topPadding);
        mHost = invalidateDelegate;
        mGlass = null;
        mLastColor = Integer.MIN_VALUE;
    }

    @Override
    public void drawBackground(Canvas canvas) {
        if (mHost == null) {
            super.drawBackground(canvas);
            return;
        }

        int mode = OneUiGlassPreferences.getFolderMode(mHost.getContext());
        if (mode == 0) return;
        if (mode == 1) {
            super.drawBackground(canvas);
            return;
        }

        int color = getBgColor();
        if (mGlass == null || color != mLastColor) {
            if (mGlass != null) mGlass.setVisible(false, false);
            mGlass = DockGlassBackground.createFolder(mHost, color, getRadius());
            mLastColor = color;
        }

        int left = getOffsetX();
        int top = getOffsetY();
        int diameter = getScaledRadius() * 2;
        mGlass.setBounds(left, top, left + diameter, top + diameter);

        int save = canvas.save();
        canvas.clipPath(getClipPath());
        mGlass.draw(canvas);
        canvas.restoreToCount(save);
        drawShadow(canvas);
    }
}

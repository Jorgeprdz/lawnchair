/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package com.android.launcher3.folder;

import android.content.Context;
import android.util.AttributeSet;

/** FolderIcon wrapper that swaps only the preview renderer, preserving all native folder logic. */
public class OneUiFolderIcon extends FolderIcon {
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
}

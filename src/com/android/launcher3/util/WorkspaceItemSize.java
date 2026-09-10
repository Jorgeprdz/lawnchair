/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */
package com.android.launcher3.util;

import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.widget.Toast;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DropTarget;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.logging.StatsLogManager.LauncherEvent;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.views.OptionsPopupView;

import java.util.Collections;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.R;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemLongClickListener;

/** One model/placement operation for Max Icons and the canonical M1 Large Folder. */
public final class WorkspaceItemSize {
    private WorkspaceItemSize() { }

    public static boolean isSupported(ItemInfo item) {
        return item.container == Favorites.CONTAINER_DESKTOP
                && (item instanceof FolderInfo || item instanceof WorkspaceItemInfo
                    && item.itemType == Favorites.ITEM_TYPE_APPLICATION)
                && item.spanX == item.spanY && (item.spanX == 1 || item.spanX == 2);
    }

    public static boolean isMax(ItemInfo item) {
        if (item.container != Favorites.CONTAINER_DESKTOP) return false;
        return item instanceof FolderInfo folder && folder.isLargeFolder()
                || item instanceof WorkspaceItemInfo icon && icon.isMaxIcon();
    }

    /** Uses the native popup and pre-drag lifecycle so holding and moving still drags. */
    public static DragOptions.PreDragCondition showFolderActions(Launcher launcher, View view) {
        if (!(view.getTag() instanceof FolderInfo folder)) return null;
        var shortcut = SystemShortcut.WORKSPACE_SIZE.getShortcut(launcher, folder, view);
        if (shortcut == null) return null;
        Rect bounds = new Rect();
        launcher.getDragLayer().getDescendantRectRelativeToSelf(view, bounds);
        int label = isMax(folder) ? R.string.workspace_item_normal_size
                : R.string.workspace_item_maximize;
        OptionsPopupView<Launcher> popup = OptionsPopupView.show(launcher, new RectF(bounds),
                Collections.singletonList(new OptionsPopupView.OptionItem(launcher, label,
                        R.drawable.ic_workspace_maximize, LauncherEvent.IGNORE, clicked -> {
                            shortcut.onClick(clicked);
                            return true;
                        })), true);
        if (popup == null) return null;
        int threshold = launcher.getResources().getDimensionPixelSize(
                R.dimen.deep_shortcuts_start_drag_threshold);
        return new DragOptions.PreDragCondition() {
            @Override
            public boolean shouldStartDrag(double distanceDragged) {
                return distanceDragged > threshold;
            }

            @Override
            public void onPreDragStart(DropTarget.DragObject dragObject) {
                view.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onPreDragEnd(DropTarget.DragObject dragObject, boolean dragStarted) {
                view.setVisibility(dragStarted ? View.INVISIBLE : View.VISIBLE);
                if (dragStarted) popup.close(false);
            }
        };
    }

    public static boolean toggle(Launcher launcher, View view) {
        if (!view.isAttachedToWindow() || !(view.getTag() instanceof ItemInfo item)
                || !isSupported(item) || !ItemLongClickListener.canStartDrag(launcher)
                || !(view.getParent() instanceof View parent)
                || !(parent.getParent() instanceof CellLayout layout)) return false;
        boolean wasMax = isMax(item);
        int span = wasMax ? 1 : 2;
        setMax(item, !wasMax);
        if (!layout.resizeWorkspaceItem(view, span, span)) {
            setMax(item, wasMax);
            Toast.makeText(launcher, R.string.workspace_item_resize_unavailable, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (view instanceof BubbleTextView icon && item instanceof WorkspaceItemInfo info) {
            icon.applyFromWorkspaceItem(info);
        }
        if (view instanceof com.android.launcher3.folder.FolderIcon folder) {
            folder.onItemsChanged(false);
        }
        view.requestLayout();
        view.invalidate();
        return true;
    }

    private static void setMax(ItemInfo item, boolean enabled) {
        if (item instanceof FolderInfo folder) {
            folder.setOption(FolderInfo.FLAG_LARGE_FOLDER, enabled, null);
        } else if (item instanceof WorkspaceItemInfo icon) {
            icon.options = enabled ? icon.options | WorkspaceItemInfo.FLAG_MAX_ICON
                    : icon.options & ~WorkspaceItemInfo.FLAG_MAX_ICON;
        }
    }
}

/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.model;

import static com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME;
import static com.android.launcher3.provider.LauncherDbUtils.itemIdMatch;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.ContentValues;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherModel.CallbackTask;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Utilities;
import com.android.launcher3.celllayout.CellPosMapper;
import com.android.launcher3.celllayout.CellPosMapper.CellPos;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.data.CollectionInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.model.data.WidgetStackInfo;
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction;
import com.android.launcher3.util.ContentWriter;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.widget.LauncherWidgetHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Class for handling model updates.
 */
public class ModelWriter {

    private static final String TAG = "ModelWriter";

    private final Context mContext;
    private final LauncherModel mModel;
    private final BgDataModel mBgDataModel;
    private final LooperExecutor mUiExecutor;

    @Nullable
    private final Callbacks mOwner;

    private final boolean mVerifyChanges;

    // Keep track of delete operations that occur when an Undo option is present; we
    // may not commit.
    private final List<ModelTask> mDeleteRunnables = new ArrayList<>();
    private boolean mPreparingToUndo;
    private final CellPosMapper mCellPosMapper;

    public ModelWriter(Context context, LauncherModel model, BgDataModel dataModel,
            boolean verifyChanges, CellPosMapper cellPosMapper, @Nullable Callbacks owner) {
        mContext = context;
        mModel = model;
        mBgDataModel = dataModel;
        mVerifyChanges = verifyChanges;
        mOwner = owner;
        mCellPosMapper = cellPosMapper;
        mUiExecutor = Executors.MAIN_EXECUTOR;
    }

    /** Updates the location properties of the item */
    public void updateItemInfoProps(
            ItemInfo item, int container, int screenId, int cellX, int cellY) {
        CellPos modelPos = mCellPosMapper.mapPresenterToModel(cellX, cellY, screenId, container);
        item.container = container;
        item.cellX = modelPos.cellX;
        item.cellY = modelPos.cellY;
        item.screenId = modelPos.screenId;
    }

    /**
     * Adds an item to the DB if it was not created previously, or move it to a new
     * <container, screen, cellX, cellY>
     */
    public void addOrMoveItemInDatabase(ItemInfo item,
            int container, int screenId, int cellX, int cellY) {
        if (item.id == ItemInfo.NO_ID) {
            // From all apps
            addItemToDatabase(item, container, screenId, cellX, cellY);
        } else {
            // From somewhere else
            moveItemInDatabase(item, container, screenId, cellX, cellY);
        }
    }

    private void checkItemInfoLocked(int itemId, ItemInfo item, StackTraceElement[] stackTrace) {
        ItemInfo modelItem = mBgDataModel.itemsIdMap.get(itemId);
        if (modelItem != null && item != modelItem) {
            // check all the data is consistent
            if (!Utilities.IS_DEBUG_DEVICE && !FeatureFlags.IS_STUDIO_BUILD
                    && modelItem instanceof WorkspaceItemInfo
                    && item instanceof WorkspaceItemInfo) {
                if (modelItem.title.toString().equals(item.title.toString()) &&
                        modelItem.getIntent().filterEquals(item.getIntent()) &&
                        modelItem.id == item.id &&
                        modelItem.itemType == item.itemType &&
                        modelItem.container == item.container &&
                        modelItem.screenId == item.screenId &&
                        modelItem.cellX == item.cellX &&
                        modelItem.cellY == item.cellY &&
                        modelItem.spanX == item.spanX &&
                        modelItem.spanY == item.spanY) {
                    // For all intents and purposes, this is the same object
                    return;
                }
            }

            // the modelItem needs to match up perfectly with item if our model is
            // to be consistent with the database-- for now, just require
            // modelItem == item or the equality check above
            String msg = "item: " + ((item != null) ? item.toString() : "null") +
                    "modelItem: " +
                    ((modelItem != null) ? modelItem.toString() : "null") +
                    "Error: ItemInfo passed to checkItemInfo doesn't match original";
            RuntimeException e = new RuntimeException(msg);
            if (stackTrace != null) {
                e.setStackTrace(stackTrace);
            }
            throw e;
        }
    }
    
    /**
     * Clears all views from the home screen.
     */
    public boolean clearAllHomeScreenViewsByType(int type) {
        final ArrayList<ItemInfo> itemsToRemove = new ArrayList<>();
        synchronized (mBgDataModel) {
            for (ItemInfo item : mBgDataModel.itemsIdMap) {
                if (item.container == type) {
                    itemsToRemove.add(item);
                }
            }
        }

        if (itemsToRemove.isEmpty()) {
            return false;
        }

        deleteItemsFromDatabase(itemsToRemove, "clearAllHomeScreenViewsByType");
        return true;
    }

    /**
     * Move an item in the DB to a new <container, screen, cellX, cellY>
     */
    public void moveItemInDatabase(final ItemInfo item,
            int container, int screenId, int cellX, int cellY) {
        updateItemInfoProps(item, container, screenId, cellX, cellY);
        notifyItemModified(item);

        enqueueDeleteRunnable(new UpdateItemRunnable(item, () -> new ContentWriter(mContext)
                .put(Favorites.CONTAINER, item.container)
                .put(Favorites.CELLX, item.cellX)
                .put(Favorites.CELLY, item.cellY)
                .put(Favorites.RANK, item.rank)
                .put(Favorites.SCREEN, item.screenId)));
    }

    /**
     * Move items in the DB to a new <container, screen, cellX, cellY>. We assume
     * that the
     * cellX, cellY have already been updated on the ItemInfos.
     */
    public void moveItemsInDatabase(final ArrayList<ItemInfo> items, int container, int screen) {
        ArrayList<ContentValues> contentValues = new ArrayList<>();
        int count = items.size();
        notifyOtherCallbacks(c -> c.bindItemsUpdated(new HashSet<>(items)));

        for (int i = 0; i < count; i++) {
            ItemInfo item = items.get(i);
            updateItemInfoProps(item, container, screen, item.cellX, item.cellY);

            final ContentValues values = new ContentValues();
            values.put(Favorites.CONTAINER, item.container);
            values.put(Favorites.CELLX, item.cellX);
            values.put(Favorites.CELLY, item.cellY);
            values.put(Favorites.RANK, item.rank);
            values.put(Favorites.SCREEN, item.screenId);

            contentValues.add(values);
        }
        enqueueDeleteRunnable(new UpdateItemsRunnable(items, contentValues));
    }

    /**
     * Remaps workspace screen ids for all desktop items using the provided mapping.
     */
    public void moveWorkspaceScreensInDatabase(SparseIntArray screenIdMap) {
        moveWorkspaceScreensInDatabase(screenIdMap, null);
    }

    /**
     * Remaps workspace screen ids for all desktop items using the provided mapping.
     *
     * @param onComplete optional runnable executed on the main thread after item callbacks are
     *                   dispatched (always runs, including when there are no item updates).
     */
    public void moveWorkspaceScreensInDatabase(SparseIntArray screenIdMap, Runnable onComplete) {
        if (screenIdMap == null || screenIdMap.size() == 0) {
            if (onComplete != null) {
                mUiExecutor.execute(onComplete);
            }
            return;
        }
        ModelVerifier verifier = new ModelVerifier();
        enqueueDeleteRunnable(newModelTask(() -> {
            try (SQLiteTransaction t = mModel.getModelDbController().newTransaction()) {
                // First pass to temporary ids to avoid collisions in cycles.
                for (int i = 0; i < screenIdMap.size(); i++) {
                    int fromScreenId = screenIdMap.keyAt(i);
                    int tempScreenId = Integer.MIN_VALUE + i;
                    ContentValues tempValues = new ContentValues();
                    tempValues.put(Favorites.SCREEN, tempScreenId);
                    mModel.getModelDbController().update(
                            tempValues,
                            Favorites.CONTAINER + "=" + Favorites.CONTAINER_DESKTOP + " AND "
                                    + Favorites.SCREEN + "=" + fromScreenId,
                            null);
                }
                // Second pass to final ids.
                for (int i = 0; i < screenIdMap.size(); i++) {
                    int toScreenId = screenIdMap.valueAt(i);
                    int tempScreenId = Integer.MIN_VALUE + i;
                    ContentValues finalValues = new ContentValues();
                    finalValues.put(Favorites.SCREEN, toScreenId);
                    mModel.getModelDbController().update(
                            finalValues,
                            Favorites.CONTAINER + "=" + Favorites.CONTAINER_DESKTOP + " AND "
                                    + Favorites.SCREEN + "=" + tempScreenId,
                            null);
                }
                t.commit();
            } catch (Exception e) {
                Log.e(TAG, "Failed to remap workspace screens", e);
                if (onComplete != null) {
                    mUiExecutor.execute(onComplete);
                }
                return;
            }

            ArrayList<ItemInfo> updatedItems = new ArrayList<>();
            synchronized (mBgDataModel) {
                for (ItemInfo item : mBgDataModel.itemsIdMap) {
                    if (item.container != Favorites.CONTAINER_DESKTOP) {
                        continue;
                    }
                    int newScreenId = screenIdMap.get(item.screenId, item.screenId);
                    if (newScreenId != item.screenId) {
                        item.screenId = newScreenId;
                        updatedItems.add(item);
                    }
                }
                if (!updatedItems.isEmpty()) {
                    mBgDataModel.updateItems(updatedItems, mOwner);
                }
                verifier.verifyModel();
            }
            final HashSet<ItemInfo> updates = new HashSet<>(updatedItems);
            mUiExecutor.execute(() -> {
                if (!updates.isEmpty()) {
                    if (mOwner != null) {
                        mOwner.bindItemsUpdated(updates);
                    }
                    notifyOtherCallbacks(c -> c.bindItemsUpdated(updates));
                }
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        }));
    }

    /**
     * Persists explicit workspace screen order synchronously.
     */
    public void persistWorkspaceScreenOrderSync(IntArray screenOrder) {
        if (screenOrder == null || screenOrder.isEmpty()) {
            return;
        }
        String serialized = screenOrder.toConcatString();
        LauncherPrefs.get(mContext).putSync(LauncherPrefs.WORKSPACE_SCREEN_ORDER.to(serialized));
    }

    /**
     * Move and/or resize item in the DB to a new <container, screen, cellX, cellY,
     * spanX, spanY>
     */
    public void modifyItemInDatabase(final ItemInfo item,
            int container, int screenId, int cellX, int cellY, int spanX, int spanY) {
        updateItemInfoProps(item, container, screenId, cellX, cellY);
        item.spanX = spanX;
        item.spanY = spanY;
        notifyItemModified(item);
        new UpdateItemRunnable(item, () -> {
            ContentWriter writer = new ContentWriter(mContext)
                .put(Favorites.CONTAINER, item.container)
                .put(Favorites.CELLX, item.cellX)
                .put(Favorites.CELLY, item.cellY)
                .put(Favorites.RANK, item.rank)
                .put(Favorites.SPANX, item.spanX)
                .put(Favorites.SPANY, item.spanY)
                .put(Favorites.SCREEN, item.screenId);
            // Keep explicit size state and geometry in the same row update.
            if (item instanceof WorkspaceItemInfo icon) {
                writer.put(Favorites.OPTIONS, icon.options);
            } else if (item instanceof com.android.launcher3.model.data.FolderInfo folder) {
                writer.put(Favorites.OPTIONS, folder.options);
            }
            return writer;
        }).executeOnModelThread();
    }

    /**
     * Update an item to the database in a specified container.
     */
    public void updateItemInDatabase(ItemInfo item) {
        notifyItemModified(item);
        new UpdateItemRunnable(item, () -> {
            ContentWriter writer = new ContentWriter(mContext);
            item.onAddToDatabase(writer);
            return writer;
        }).executeOnModelThread();
    }

    public void notifyItemModified(ItemInfo item) {
        notifyOtherCallbacks(c -> c.bindItemsUpdated(Collections.singleton(item)));
    }

    /**
     * Add an item to the database in a specified container. Sets the container,
     * screen, cellX and
     * cellY fields of the item. Also assigns an ID to the item.
     */
    public void addItemToDatabase(final ItemInfo item,
            int container, int screenId, int cellX, int cellY) {
        updateItemInfoProps(item, container, screenId, cellX, cellY);
        addItemsToDatabase(Collections.singletonList(item));
    }

    /** Inserts a member and the active selection together, or reports failure for ID cleanup. */
    public void addWidgetToStack(WidgetStackInfo stack, LauncherAppWidgetInfo member,
            Runnable onAdded, Runnable onFailure) {
        final int loadId = mModel.getLastLoadId();
        member.id = mModel.getModelDbController().generateNewItemId();
        member.container = stack.id;
        member.screenId = stack.screenId;
        member.cellX = 0;
        member.cellY = 0;
        final int spanX = member.spanX;
        final int spanY = member.spanY;
        // Unlike an ordinary ModelTask, an obsolete bind must report failure so its host ID
        // can be released rather than silently dropping the task during a model reload.
        MODEL_EXECUTOR.execute(() -> {
            if (loadId != mModel.getLastLoadId()
                    || mBgDataModel.itemsIdMap.get(stack.id) != stack) {
                mUiExecutor.execute(onFailure);
                return;
            }
            try (SQLiteTransaction transaction = mModel.getModelDbController().newTransaction()) {
                ContentValues selection = new ContentValues();
                selection.put(Favorites.OPTIONS, member.id);
                int updated = mModel.getModelDbController().update(selection,
                        Favorites._ID + "=? AND " + Favorites.ITEM_TYPE + "=? AND "
                                + Favorites.SPANX + "=? AND " + Favorites.SPANY + "=?",
                        new String[]{String.valueOf(stack.id),
                                String.valueOf(Favorites.ITEM_TYPE_WIDGET_STACK),
                                String.valueOf(spanX), String.valueOf(spanY)});
                if (updated != 1) {
                    throw new IllegalStateException("Widget stack was removed or resized");
                }
                member.rank = stack.getContents().size();
                ContentWriter values = new ContentWriter(mContext);
                member.onAddToDatabase(values);
                values.put(Favorites._ID, member.id);
                if (mModel.getModelDbController().insert(values.getValues(mContext)) < 0) {
                    throw new IllegalStateException("Unable to insert widget stack member");
                }
                transaction.commit();
            } catch (RuntimeException e) {
                Log.e(TAG, "Unable to add widget stack member", e);
                mUiExecutor.execute(onFailure);
                return;
            }
            synchronized (mBgDataModel) {
                stack.add(member);
                stack.setActiveWidgetId(member.id);
                mBgDataModel.addItems(mContext, Collections.singletonList(member), mOwner);
                mBgDataModel.updateItems(Collections.singletonList(stack), mOwner);
            }
            notifyOtherCallbacks(c -> c.bindItemsUpdated(Collections.singleton(stack)));
            mUiExecutor.execute(onAdded);
        });
    }

    /** Wraps an existing widget in a stack without a partially committed overlapping footprint. */
    public void createWidgetStack(final LauncherAppWidgetInfo widget,
            java.util.function.Consumer<WidgetStackInfo> onCreated, Runnable onFailure) {
        if (widget.container != Favorites.CONTAINER_DESKTOP || widget.id == ItemInfo.NO_ID
                || widget.itemType != Favorites.ITEM_TYPE_APPWIDGET) {
            mUiExecutor.execute(onFailure);
            return;
        }
        final WidgetStackInfo stack = new WidgetStackInfo();
        stack.copyFrom(widget);
        stack.itemType = Favorites.ITEM_TYPE_WIDGET_STACK;
        stack.id = mModel.getModelDbController().generateNewItemId();
        stack.setActiveWidgetId(widget.id);
        stack.minSpanX = widget.minSpanX;
        stack.minSpanY = widget.minSpanY;
        final int widgetRowId = widget.id;
        newModelTask(() -> {
            try (SQLiteTransaction transaction = mModel.getModelDbController().newTransaction()) {
                ContentWriter values = new ContentWriter(mContext);
                stack.onAddToDatabase(values);
                values.put(Favorites._ID, stack.id);
                if (mModel.getModelDbController().insert(values.getValues(mContext)) < 0) {
                    throw new IllegalStateException("Unable to insert widget stack");
                }
                ContentValues member = new ContentValues();
                member.put(Favorites.CONTAINER, stack.id);
                member.put(Favorites.CELLX, 0);
                member.put(Favorites.CELLY, 0);
                member.put(Favorites.RANK, 0);
                // Refuse a stale request if the widget moved or was removed in the meantime.
                String selection = Favorites._ID + "=? AND " + Favorites.CONTAINER + "=? AND "
                        + Favorites.SCREEN + "=? AND " + Favorites.CELLX + "=? AND "
                        + Favorites.CELLY + "=? AND " + Favorites.SPANX + "=? AND "
                        + Favorites.SPANY + "=?";
                String[] args = {String.valueOf(widgetRowId),
                        String.valueOf(Favorites.CONTAINER_DESKTOP), String.valueOf(stack.screenId),
                        String.valueOf(stack.cellX), String.valueOf(stack.cellY),
                        String.valueOf(stack.spanX), String.valueOf(stack.spanY)};
                if (mModel.getModelDbController().update(member, selection, args) != 1) {
                    throw new IllegalStateException("Widget changed before stack creation");
                }
                transaction.commit();
            } catch (RuntimeException e) {
                Log.e("WidgetStack", "Stack creation failed", e);
                mUiExecutor.execute(onFailure);
                return;
            }
            synchronized (mBgDataModel) {
                widget.container = stack.id;
                widget.cellX = widget.cellY = widget.rank = 0;
                stack.add(widget);
                mBgDataModel.addItem(mContext, stack, mOwner);
            }
            notifyItemModified(widget);
            notifyOtherCallbacks(c -> c.bindItemsAdded(Collections.singletonList(stack)));
            mUiExecutor.execute(() -> onCreated.accept(stack));
        }).executeOnModelThread();
    }

    /**
     * Add provided items to the database. Also assigns an ID to each item.
     */
    public void addItemsToDatabase(final List<ItemInfo> items) {
        items.forEach(info -> info.id = mModel.getModelDbController().generateNewItemId());
        notifyOtherCallbacks(c -> c.bindItemsAdded(items));

        ModelVerifier verifier = new ModelVerifier();
        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        newModelTask(() -> {
            // Write the item on background thread, as some properties might have been
            // updated in
            // the background.
            for (ItemInfo item: items) {
                final ContentWriter writer = new ContentWriter(mContext);
                item.onAddToDatabase(writer);
                writer.put(Favorites._ID, item.id);
                mModel.getModelDbController().insert(writer.getValues(mContext));
            }

            synchronized (mBgDataModel) {
                for (ItemInfo item: items) {
                    checkItemInfoLocked(item.id, item, stackTrace);
                }
                mBgDataModel.addItems(mContext, items, mOwner);
                verifier.verifyModel();
            }
        }).executeOnModelThread();
    }

    /**
     * Removes the specified item from the database
     */
    public void deleteItemFromDatabase(ItemInfo item, @Nullable final String reason) {
        deleteItemsFromDatabase(Arrays.asList(item), reason);
    }

    /**
     * Removes all the items from the database matching {@param matcher}.
     */
    public void deleteItemsFromDatabase(@NonNull final Predicate<ItemInfo> matcher,
            @Nullable final String reason) {
        deleteItemsFromDatabase(StreamSupport.stream(mBgDataModel.itemsIdMap.spliterator(), false)
                .filter(matcher).collect(Collectors.toList()), reason);
    }

    /**
     * Removes the specified items from the database
     */
    public void deleteItemsFromDatabase(final Collection<? extends ItemInfo> items,
            @Nullable final String reason) {
        ModelVerifier verifier = new ModelVerifier();
        FileLog.d(TAG, "removing items from db " + items.stream().map(
                (item) -> item.getTargetComponent() == null ? ""
                        : item.getTargetComponent().getPackageName())
                .collect(
                        Collectors.joining(","))
                + ". Reason: [" + (TextUtils.isEmpty(reason) ? "unknown" : reason) + "]");
        notifyDelete(items);
        enqueueDeleteRunnable(newModelTask(() -> {
            for (ItemInfo item : items) {
                mModel.getModelDbController().delete(itemIdMatch(item.id), null);
            }
            mBgDataModel.removeItem(mContext, items, mOwner);
            verifier.verifyModel();
        }));
    }

    /**
     * Remove the specified folder and all its contents from the database.
     */
    public void deleteCollectionAndContentsFromDatabase(final CollectionInfo info) {
        ModelVerifier verifier = new ModelVerifier();
        notifyDelete(Collections.singleton(info));

        enqueueDeleteRunnable(newModelTask(() -> {
            mModel.getModelDbController().delete(
                    Favorites.CONTAINER + "=" + info.id, null);

            mModel.getModelDbController().delete(
                    Favorites._ID + "=" + info.id, null);

            List<ItemInfo> itemsToDelete = new ArrayList<>(info.getContents());
            itemsToDelete.add(info);
            mBgDataModel.removeItem(mContext, itemsToDelete, mOwner);
            verifier.verifyModel();
        }));
    }

    /** Deletes the stack and members together; host IDs remain intact until undo is committed. */
    public void deleteWidgetStack(final WidgetStackInfo stack, LauncherWidgetHolder holder,
            @Nullable String reason) {
        final List<LauncherAppWidgetInfo> members = new ArrayList<>(stack.getContents());
        notifyDelete(Collections.singleton(stack));
        enqueueDeleteRunnable(newModelTask(() -> {
            try (SQLiteTransaction transaction = mModel.getModelDbController().newTransaction()) {
                mModel.getModelDbController().delete(
                        Favorites.CONTAINER + "=?", new String[]{String.valueOf(stack.id)});
                mModel.getModelDbController().delete(itemIdMatch(stack.id), null);
                transaction.commit();
            }
            List<ItemInfo> removed = new ArrayList<>(members);
            removed.add(stack);
            mBgDataModel.removeItem(mContext, removed, mOwner);
            if (holder != null) {
                for (LauncherAppWidgetInfo member : members) {
                    if (!member.isCustomWidget() && member.isWidgetIdAllocated()) {
                        holder.deleteAppWidgetId(member.appWidgetId);
                    }
                }
            }
        }));
    }

    /**
     * Deletes the widget info and the widget id.
     */
    public void deleteWidgetInfo(final LauncherAppWidgetInfo info, LauncherWidgetHolder holder,
            @Nullable final String reason) {
        notifyDelete(Collections.singleton(info));
        if (holder != null && !info.isCustomWidget() && info.isWidgetIdAllocated()) {
            // Deleting an app widget ID is a void call but writes to disk before returning
            // to the caller...
            enqueueDeleteRunnable(newModelTask(() -> holder.deleteAppWidgetId(info.appWidgetId)));
        }
        deleteItemFromDatabase(info, reason);
    }

    private void notifyDelete(Collection<? extends ItemInfo> items) {
        notifyOtherCallbacks(c -> c.bindWorkspaceComponentsRemoved(ItemInfoMatcher.ofItems(items)));
    }

    /**
     * Delete operations tracked using {@link #enqueueDeleteRunnable} will only be
     * called
     * if {@link #commitDelete} is called. Note that one of {@link #commitDelete()}
     * or
     * {@link #abortDelete} MUST be called after this method, or else all delete
     * operations will remain uncommitted indefinitely.
     */
    public void prepareToUndoDelete() {
        if (!mPreparingToUndo) {
            if (!mDeleteRunnables.isEmpty() && FeatureFlags.IS_STUDIO_BUILD) {
                throw new IllegalStateException("There are still uncommitted delete operations!");
            }
            mDeleteRunnables.clear();
            mPreparingToUndo = true;
        }
    }

    /**
     * If {@link #prepareToUndoDelete} has been called, we store the Runnable to be
     * run when
     * {@link #commitDelete()} is called (or abandoned if {@link #abortDelete} is
     * called).
     * Otherwise, we run the Runnable immediately.
     */
    private void enqueueDeleteRunnable(ModelTask r) {
        if (mPreparingToUndo) {
            mDeleteRunnables.add(r);
        } else {
            r.executeOnModelThread();
        }
    }

    public void commitDelete() {
        mPreparingToUndo = false;
        mDeleteRunnables.forEach(ModelTask::executeOnModelThread);
        mDeleteRunnables.clear();
    }

    /**
     * Aborts a previous delete operation pending commit
     */
    public void abortDelete() {
        mPreparingToUndo = false;
        mDeleteRunnables.clear();
        // We do a full reload here instead of just a rebind because Folders change
        // their internal
        // state when dragging an item out, which clobbers the rebind unless we load
        // from the DB.
        mModel.forceReload();
    }

    private void notifyOtherCallbacks(CallbackTask task) {
        if (mOwner == null) {
            // If the call is happening from a model, it will take care of updating the
            // callbacks
            return;
        }
        mUiExecutor.execute(() -> {
            for (Callbacks c : mModel.getCallbacks()) {
                if (c != mOwner) {
                    task.execute(c);
                }
            }
        });
    }

    private class UpdateItemRunnable extends UpdateItemBaseRunnable {
        private final ItemInfo mItem;
        private final Supplier<ContentWriter> mWriter;
        private final int mItemId;

        UpdateItemRunnable(ItemInfo item, Supplier<ContentWriter> writer) {
            mItem = item;
            mWriter = writer;
            mItemId = item.id;
        }

        @Override
        public void runImpl() {
            mModel.getModelDbController().update(
                    mWriter.get().getValues(mContext), itemIdMatch(mItemId), null);
            updateItemArrays(mItem, mItemId);
            mBgDataModel.updateItems(Collections.singletonList(mItem), mOwner);
        }
    }

    private class UpdateItemsRunnable extends UpdateItemBaseRunnable {
        private final ArrayList<ContentValues> mValues;
        private final ArrayList<ItemInfo> mItems;

        UpdateItemsRunnable(ArrayList<ItemInfo> items, ArrayList<ContentValues> values) {
            mValues = values;
            mItems = items;
        }

        @Override
        public void runImpl() {
            try (SQLiteTransaction t = mModel.getModelDbController().newTransaction()) {
                int count = mItems.size();
                for (int i = 0; i < count; i++) {
                    ItemInfo item = mItems.get(i);
                    final int itemId = item.id;
                    mModel.getModelDbController().update(
                            mValues.get(i), itemIdMatch(itemId), null);
                    updateItemArrays(item, itemId);
                }
                t.commit();
                mBgDataModel.updateItems(mItems, mOwner);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private abstract class UpdateItemBaseRunnable extends ModelTask {
        private final StackTraceElement[] mStackTrace;
        private final ModelVerifier mVerifier = new ModelVerifier();

        UpdateItemBaseRunnable() {
            mStackTrace = new Throwable().getStackTrace();
        }

        protected void updateItemArrays(ItemInfo item, int itemId) {
            // Lock on mBgLock *after* the db operation
            synchronized (mBgDataModel) {
                checkItemInfoLocked(itemId, item, mStackTrace);

                if (item.container != Favorites.CONTAINER_DESKTOP &&
                        item.container != Favorites.CONTAINER_HOTSEAT) {
                    // Item is in a collection, make sure this collection exists
                    if (!(mBgDataModel.itemsIdMap.get(item.container) instanceof CollectionInfo)) {
                        // An items container is being set to a that of an item which is not in
                        // the list of collections.
                        String msg = "item: " + item + " container being set to: " +
                                item.container + ", not in the list of collections";
                        Log.e(TAG, msg);
                    }
                }
                mVerifier.verifyModel();
            }
        }
    }

    private abstract class ModelTask implements Runnable {

        private final int mLoadId = mBgDataModel.lastLoadId;

        @Override
        public final void run() {
            if (mLoadId != mModel.getLastLoadId()) {
                Log.d(TAG, "Model changed before the task could execute");
                return;
            }
            runImpl();
        }

        public final void executeOnModelThread() {
            MODEL_EXECUTOR.execute(this);
        }

        public abstract void runImpl();
    }

    private ModelTask newModelTask(Runnable r) {
        return new ModelTask() {
            @Override
            public void runImpl() {
                r.run();
            }
        };
    }

    /**
     * Utility class to verify model updates are propagated properly to the
     * callback.
     */
    public class ModelVerifier {

        final int startId;

        ModelVerifier() {
            startId = mBgDataModel.lastBindId;
        }

        void verifyModel() {
            if (!mVerifyChanges || !mModel.hasCallbacks()) {
                return;
            }

            int executeId = mBgDataModel.lastBindId;

            mUiExecutor.post(() -> {
                int currentId = mBgDataModel.lastBindId;
                if (currentId > executeId) {
                    // Model was already bound after job was executed.
                    return;
                }
                if (executeId == startId) {
                    // Bound model has not changed during the job
                    return;
                }

                // Bound model was changed between submitting the job and executing the job
                mModel.rebindCallbacks();
            });
        }
    }
}

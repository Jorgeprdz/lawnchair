package com.android.launcher3.model;

import static org.junit.Assert.*;
import static com.android.launcher3.LauncherSettings.Favorites.*;
import android.content.ContentValues;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercises the real migration reader and row/collection copier against disposable SQLite. */
@RunWith(AndroidJUnit4.class)
public class MaxItemMigrationTest {
    @Test public void validGridKeepsMaxStateAndFolderMembers() { migrate(4, 2); }
    @Test public void narrowGridRestoresNormalWithoutLosingMetadataOrMembers() { migrate(1, 1); }

    private void migrate(int columns, int expectedSpan) {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (DatabaseHelper helper = new DatabaseHelper(context, null, user -> 0L, () -> {})) {
            var db = helper.getWritableDatabase();
            try (var schema = db.rawQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='favorites'", null)) {
                assertTrue(schema.moveToFirst());
                db.execSQL(schema.getString(0).replaceFirst("favorites", "oneui_source"));
            }
            for (int type : new int[]{ITEM_TYPE_APPLICATION, ITEM_TYPE_FOLDER}) {
                int id = 9000 + type;
                int flag = type == ITEM_TYPE_FOLDER ? FolderInfo.FLAG_LARGE_FOLDER : WorkspaceItemInfo.FLAG_MAX_ICON;
                ContentValues values = new ContentValues();
                values.put(_ID, id);
                values.put(ITEM_TYPE, type);
                values.put(CONTAINER, CONTAINER_DESKTOP);
                values.put(SCREEN, 0);
                values.put(CELLX, 0);
                values.put(CELLY, 0);
                values.put(SPANX, 2);
                values.put(SPANY, 2);
                values.put(OPTIONS, flag | 256);
                values.put(INTENT, new Intent(Intent.ACTION_MAIN).setPackage("com.android.settings").toUri(0));
                assertTrue(db.insert("oneui_source", null, values) >= 0);
                if (type == ITEM_TYPE_FOLDER) {
                    for (int rank = 0; rank < 2; rank++) {
                        ContentValues child = new ContentValues(values);
                        child.put(_ID, 9100 + rank);
                        child.put(ITEM_TYPE, ITEM_TYPE_APPLICATION);
                        child.put(CONTAINER, id);
                        child.put(RANK, rank);
                        child.put(SPANX, 1);
                        child.put(SPANY, 1);
                        child.put(OPTIONS, 0);
                        assertTrue(db.insert("oneui_source", null, child) >= 0);
                    }
                }
            }
            var entries = new GridSizeMigrationDBController.DbReader(db, "oneui_source", context)
                    .loadAllWorkspaceEntries();
            assertEquals(2, entries.size());
            for (DbEntry entry : entries) {
                assertEquals(2, entry.minSpanX);
                assertEquals(2, entry.minSpanY);
                entry.prepareWorkspaceSize(columns, 4);
                entry.cellX = entry.cellY = 0;
                GridSizeMigrationDBController.insertEntryInDb(helper, entry,
                        "oneui_source", TABLE_NAME, new ArrayList<>());
            }
            try (var parents = db.query(TABLE_NAME, new String[]{_ID, ITEM_TYPE, SPANX, SPANY, OPTIONS},
                    CONTAINER + "=" + CONTAINER_DESKTOP, null, null, null, null)) {
                assertEquals(2, parents.getCount());
                while (parents.moveToNext()) {
                    int flag = parents.getInt(1) == ITEM_TYPE_FOLDER ? FolderInfo.FLAG_LARGE_FOLDER
                            : WorkspaceItemInfo.FLAG_MAX_ICON;
                    assertEquals(expectedSpan, parents.getInt(2));
                    assertEquals(expectedSpan, parents.getInt(3));
                    assertEquals(256 | (expectedSpan == 2 ? flag : 0), parents.getInt(4));
                    if (parents.getInt(1) == ITEM_TYPE_FOLDER) {
                        try (var children = db.query(TABLE_NAME, new String[]{RANK},
                                CONTAINER + "=" + parents.getInt(0), null, null, null, RANK)) {
                            assertEquals(2, children.getCount());
                            assertTrue(children.moveToFirst());
                            assertEquals(0, children.getInt(0));
                            assertTrue(children.moveToNext());
                            assertEquals(1, children.getInt(0));
                        }
                    }
                }
            }
            try (var source = db.query("oneui_source", new String[]{SPANX, SPANY},
                    CONTAINER + "=" + CONTAINER_DESKTOP, null, null, null, null)) {
                assertEquals(2, source.getCount());
                while (source.moveToNext()) {
                    assertEquals(2, source.getInt(0));
                    assertEquals(2, source.getInt(1));
                }
            }
        }
    }
}

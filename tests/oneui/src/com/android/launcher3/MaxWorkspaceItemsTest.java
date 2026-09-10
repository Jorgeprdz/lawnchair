package com.android.launcher3;

import static org.junit.Assert.*;
import static com.android.launcher3.icons.cache.CacheLookupFlag.DEFAULT_LOOKUP_FLAG;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.os.Process;
import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.WorkspaceItemSize;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercises real model rows and native workspace views on the disposable emulator. */
@RunWith(AndroidJUnit4.class)
public class MaxWorkspaceItemsTest {
    @Test
    public void maxItemsPreserveContentsGeometryAndIdentityAcrossRecreation() throws Exception {
        String target = InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
        Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setComponent(new ComponentName(target, "app.lawnchair.LawnchairLauncher"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        AtomicInteger iconId = new AtomicInteger(-1);
        AtomicInteger folderId = new AtomicInteger(-1);
        try (ActivityScenario<Launcher> scenario = ActivityScenario.launch(launch)) {
            try {
                ready(scenario);
                var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
                AppInfo app = Executors.MODEL_EXECUTOR.submit(() -> {
                    var apps = context.getSystemService(LauncherApps.class)
                            .getActivityList("com.android.settings", Process.myUserHandle());
                    assertFalse("An actual launchable Settings app is required", apps.isEmpty());
                    var result = new AppInfo(context, apps.get(0), Process.myUserHandle());
                    LauncherAppState.getInstance(context).getIconCache()
                            .getTitleAndIcon(result, apps.get(0), DEFAULT_LOOKUP_FLAG);
                    return result;
                }).get(30, TimeUnit.SECONDS);
                scenario.onActivity(launcher -> {
                    int screen = launcher.getWorkspace().getScreenIdForPageIndex(0);
                    CellLayout grid = launcher.getWorkspace().getScreenWithId(screen);
                    WorkspaceItemInfo icon = new WorkspaceItemInfo(app);
                    int[] cell = new int[2];
                    assertTrue(grid.findCellForSpan(cell, 2, 2));
                    launcher.getModelWriter().addItemToDatabase(icon,
                            LauncherSettings.Favorites.CONTAINER_DESKTOP, screen, cell[0], cell[1]);
                    View iconView = launcher.getItemInflater().inflateItem(icon, grid);
                    launcher.getWorkspace().addInScreen(iconView, icon);
                    iconId.set(icon.id);
                    assertTrue(WorkspaceItemSize.toggle(launcher, iconView));
                    assertEquals(2, icon.spanX);
                    assertEquals(2, icon.spanY);
                    assertTrue(grid.findCellForSpan(cell, 2, 2));
                    FolderInfo folder = new FolderInfo();
                    folder.title = "OneUI apps";
                    launcher.getModelWriter().addItemToDatabase(folder,
                            LauncherSettings.Favorites.CONTAINER_DESKTOP, screen, cell[0], cell[1]);
                    for (int rank = 0; rank < 10; rank++) {
                        WorkspaceItemInfo member = new WorkspaceItemInfo(app);
                        member.rank = rank;
                        folder.add(member);
                        launcher.getModelWriter().addItemToDatabase(member, folder.id, 0, rank % 3, rank / 3);
                    }
                    FolderIcon folderView = (FolderIcon) launcher.getItemInflater().inflateItem(folder, grid);
                    launcher.getWorkspace().addInScreen(folderView, folder);
                    folderId.set(folder.id);
                    assertTrue(WorkspaceItemSize.toggle(launcher, iconView));
                    assertEquals(1, icon.spanX);
                    assertNoOverlap(grid);
                });
                drain();
                OneUiScreenshots.capture("01-home.png");
                scenario.onActivity(launcher -> {
                    assertTrue(find(launcher, folderId.get()).performClick());
                    assertNotNull(Folder.getOpen(launcher));
                });
                scenario.onActivity(AbstractFloatingView::closeAllOpenViews);
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                SystemClock.sleep(500);
                scenario.onActivity(launcher -> {
                    assertTrue(WorkspaceItemSize.toggle(launcher, find(launcher, iconId.get())));
                });
                drain();
                OneUiScreenshots.capture("12-max-icon.png");
                scenario.onActivity(launcher -> {
                    FolderIcon view = (FolderIcon) find(launcher, folderId.get());
                    assertTrue(WorkspaceItemSize.toggle(launcher, view));
                    assertEquals(9, view.getPreviewItemsOnPage(0).size());
                    assertEquals(10, ((FolderInfo) view.getTag()).getContents().size());
                    assertNoOverlap((CellLayout) view.getParent().getParent());
                });
                drain();
                scenario.onActivity(launcher -> {
                    FolderIcon view = (FolderIcon) find(launcher, folderId.get());
                    var name = view.getFolderName();
                    float labelTop = name.getTop() + name.getBaseline() + name.getPaint().getFontMetrics().top;
                    assertTrue("Large folder background must not cover its label",
                            labelTop >= view.getPaddingTop() + view.getLargePreviewSize());
                });
                OneUiScreenshots.capture("02-large-folder.png");
                OneUiScreenshots.capture("13-max-folder.png");
                scenario.onActivity(launcher -> {
                    find(launcher, folderId.get()).performClick();
                    assertNotNull(Folder.getOpen(launcher));
                    assertEquals(10, Folder.getOpen(launcher).getItemCount());
                });
                OneUiScreenshots.capture("03-large-folder-open.png");
                scenario.onActivity(AbstractFloatingView::closeAllOpenViews);
                drain();
                scenario.recreate();
                ready(scenario);
                scenario.onActivity(launcher -> {
                    View icon = find(launcher, iconId.get());
                    View folder = find(launcher, folderId.get());
                    assertNotNull(icon);
                    assertNotNull(folder);
                    assertTrue(WorkspaceItemSize.isMax((ItemInfo) icon.getTag()));
                    assertTrue(WorkspaceItemSize.isMax((ItemInfo) folder.getTag()));
                    FolderInfo info = (FolderInfo) folder.getTag();
                    assertEquals("OneUI apps", info.title.toString());
                    assertEquals(10, info.getContents().size());
                    for (int rank = 0; rank < 10; rank++) assertEquals(rank, info.getContents().get(rank).rank);
                    assertTrue(WorkspaceItemSize.toggle(launcher, folder));
                    assertTrue(WorkspaceItemSize.toggle(launcher, icon));
                    assertEquals(folderId.get(), info.id);
                    assertEquals(10, info.getContents().size());
                    assertEquals(1, info.spanX);
                    assertEquals(1, info.spanY);
                    assertNoOverlap((CellLayout) folder.getParent().getParent());
                });
                drain();
            } finally {
                scenario.onActivity(launcher -> {
                    AbstractFloatingView.closeAllOpenViews(launcher);
                    for (int id : new int[]{iconId.get(), folderId.get()}) {
                        View view = find(launcher, id);
                        if (view != null) launcher.removeItem(view, (ItemInfo) view.getTag(), true, "OneUI test cleanup");
                    }
                });
                drain();
            }
        }
    }

    private static View find(Launcher launcher, int id) {
        return launcher.getWorkspace().mapOverItems((item, view) -> item != null && id >= 0 && item.id == id);
    }

    private static void assertNoOverlap(CellLayout grid) {
        boolean[][] used = new boolean[grid.getCountX()][grid.getCountY()];
        var children = grid.getShortcutsAndWidgets();
        for (int index = 0; index < children.getChildCount(); index++) {
            if (!(children.getChildAt(index).getTag() instanceof ItemInfo item)) continue;
            assertTrue(item.cellX >= 0 && item.cellY >= 0);
            assertTrue(item.cellX + item.spanX <= grid.getCountX());
            assertTrue(item.cellY + item.spanY <= grid.getCountY());
            for (int x = item.cellX; x < item.cellX + item.spanX; x++) {
                for (int y = item.cellY; y < item.cellY + item.spanY; y++) {
                    assertFalse("Workspace items must never overlap", used[x][y]);
                    used[x][y] = true;
                }
            }
        }
    }

    private static void drain() throws Exception {
        Executors.MODEL_EXECUTOR.submit(() -> {}).get(30, TimeUnit.SECONDS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void ready(ActivityScenario<Launcher> scenario) throws Exception {
        for (int attempt = 0; attempt < 150; attempt++) {
            drain();
            AtomicBoolean ready = new AtomicBoolean();
            scenario.onActivity(launcher -> ready.set(ItemLongClickListener.canStartDrag(launcher)));
            if (ready.get()) return;
            SystemClock.sleep(100);
        }
        fail("Launcher model did not become ready");
    }
}

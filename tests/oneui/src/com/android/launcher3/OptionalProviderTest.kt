package com.android.launcher3

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lawnchair.LawnchairLauncher
import app.lawnchair.gestures.handlers.OpenOneUiFinderGestureHandler
import app.lawnchair.hotseat.PixelSearchHotseat
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.qsb.providers.PixelSearch
import com.patrykmichalik.opto.core.setBlocking
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Validates absence safely; it does not claim device validation of uninstalled providers. */
@RunWith(AndroidJUnit4::class)
class OptionalProviderTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun launchIntent() = Intent(Intent.ACTION_MAIN)
        .setComponent(ComponentName(context.packageName, LawnchairLauncher::class.java.name))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @Test
    fun unavailableFinderDoesNotCrashLauncher() {
        val finder = ComponentName("com.sec.android.app.launcher", "com.sec.android.app.launcher.search.SearchActivity")
        assumeTrue(context.packageManager.resolveActivity(Intent().setComponent(finder), 0) == null)
        ActivityScenario.launch<LawnchairLauncher>(launchIntent()).use { scenario ->
            scenario.onActivity { launcher ->
                runBlocking { OpenOneUiFinderGestureHandler(launcher).onTrigger(launcher) }
                assertFalse(launcher.isFinishing)
                assertFalse(launcher.isDestroyed)
            }
        }
    }

    @Test
    fun missingPixelSearchKeepsSelectionAndUsesWorkingFallback() {
        val providers = AppWidgetManager.getInstance(context)
            .getInstalledProvidersForPackage(PixelSearch.packageName, Process.myUserHandle())
        assumeTrue(providers.isEmpty())
        val preference = PreferenceManager2.getInstance(context).hotseatMode
        val original = preference.firstCached()
        ActivityScenario.launch<LawnchairLauncher>(launchIntent()).use { scenario ->
            try {
                InstrumentationRegistry.getInstrumentation().runOnMainSync { preference.setBlocking(PixelSearchHotseat) }
                SystemClock.sleep(1200)
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { launcher ->
                    assertEquals(PixelSearchHotseat, preference.firstCached())
                    assertFalse(launcher.isDestroyed)
                    assertNotNull(launcher.findViewById<android.view.View>(R.id.btn_qsb_search))
                    assertEquals(-1, LauncherPrefs.getPrefs(launcher).getInt("pixel_search_widget_id", -1))
                }
                scenario.recreate()
                scenario.onActivity { launcher ->
                    assertEquals(PixelSearchHotseat, preference.firstCached())
                    assertNotNull(launcher.findViewById<android.view.View>(R.id.btn_qsb_search))
                }
            } finally {
                InstrumentationRegistry.getInstrumentation().runOnMainSync { preference.setBlocking(original) }
            }
        }
    }
}

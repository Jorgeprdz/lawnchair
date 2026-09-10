package app.lawnchair.gestures.handlers

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.R

class OpenOneUiFinderGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        val intent = Intent(Intent.ACTION_MAIN)
            .setComponent(FINDER_COMPONENT)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            val resolved = launcher.packageManager.resolveActivity(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY,
            )
            if (resolved == null) {
                Toast.makeText(launcher, R.string.lawnchair_action_failed, Toast.LENGTH_SHORT).show()
                return
            }

            launcher.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(launcher, R.string.lawnchair_action_failed, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(launcher, R.string.lawnchair_action_failed, Toast.LENGTH_SHORT).show()
        } catch (_: RuntimeException) {
            Toast.makeText(launcher, R.string.lawnchair_action_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        val FINDER_COMPONENT = ComponentName(
            "com.sec.android.app.launcher",
            "com.sec.android.app.launcher.search.SearchActivity",
        )
    }
}

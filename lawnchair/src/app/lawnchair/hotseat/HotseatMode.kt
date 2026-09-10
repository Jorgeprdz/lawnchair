package app.lawnchair.hotseat

import android.content.Context
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import com.android.launcher3.R

sealed class HotseatMode(
    @StringRes val nameResourceId: Int,
    @LayoutRes val layoutResourceId: Int,
) {
    companion object {
        fun fromString(value: String): HotseatMode = when (value) {
            "disabled" -> DisabledHotseat
            "google_search", "pixel_search" -> PixelSearchHotseat
            else -> LawnchairHotseat
        }

        /**
         * @return The list of all hot seat modes.
         */
        fun values() = listOf(
            DisabledHotseat,
            LawnchairHotseat,
            PixelSearchHotseat,
        )
    }

    abstract fun isAvailable(context: Context): Boolean
}

object LawnchairHotseat : HotseatMode(
    nameResourceId = R.string.hotseat_mode_lawnchair,
    layoutResourceId = R.layout.search_container_hotseat,
) {
    override fun toString() = "lawnchair"
    override fun isAvailable(context: Context): Boolean = true
}

object PixelSearchHotseat : HotseatMode(
    nameResourceId = R.string.search_provider_pixel_search,
    layoutResourceId = R.layout.search_container_hotseat_pixel_search,
) {
    override fun toString(): String = "pixel_search"

    // Keep the choice across uninstall/reinstall; the native host displays Lawnchair fallback.
    override fun isAvailable(context: Context): Boolean = true
}

object DisabledHotseat : HotseatMode(
    nameResourceId = R.string.hotseat_mode_disabled,
    layoutResourceId = R.layout.empty_view,
) {
    override fun toString(): String = "disabled"

    override fun isAvailable(context: Context): Boolean = true
}

/*
 * Copyright 2022, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.preferences.destinations

import android.content.res.Configuration
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.oneui.OneUiGlassPreferences
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.DummyLauncherBox
import app.lawnchair.ui.preferences.components.DummyLauncherLayout
import app.lawnchair.ui.preferences.components.WallpaperPreview
import app.lawnchair.ui.preferences.components.WithWallpaper
import app.lawnchair.ui.preferences.components.clipToBottomPercentage
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.OneUiGlassIntensityPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.controls.WarningPreference
import app.lawnchair.ui.preferences.components.createPreviewIdp
import app.lawnchair.ui.preferences.components.layout.DividerColumn
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupHeading
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R

@Composable
fun DockPreferences(modifier: Modifier = Modifier) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val context = LocalContext.current
    var dockFrosty by remember {
        mutableStateOf(OneUiGlassPreferences.isDockFrosty(context))
    }
    var dockBlurIntensity by remember {
        mutableIntStateOf(OneUiGlassPreferences.getDockBlurIntensity(context))
    }
    var dockCrystalIntensity by remember {
        mutableIntStateOf(OneUiGlassPreferences.getDockCrystalIntensity(context))
    }

    PreferenceLayout(
        label = stringResource(id = R.string.dock_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val hotseatBgAdapter = prefs.hotseatBG.getAdapter()
        val backgroundMode = prefs2.hotseatBackgroundMode.getAdapter()
        val baseMode = backgroundMode.state.value.takeIf { it in 0..3 }
            ?: if (hotseatBgAdapter.state.value) 1 else 0
        val selectedMode = if (baseMode == 3 && dockFrosty) 4 else baseMode

        MainSwitchPreference(adapter = prefs2.isHotseatEnabled.getAdapter(), label = stringResource(id = R.string.show_hotseat_title)) {
            DockPreferencesPreview()
            PreferenceGroup(heading = stringResource(id = R.string.style)) {
                ListPreference(
                    value = selectedMode,
                    onValueChange = { mode ->
                        val frosty = mode == 4
                        OneUiGlassPreferences.setDockFrosty(context, frosty)
                        dockFrosty = frosty
                        backgroundMode.onChange(if (frosty) 3 else mode)
                    },
                    entries = listOf(
                        ListPreferenceEntry(0) { stringResource(R.string.dock_background_off) },
                        ListPreferenceEntry(1) { stringResource(R.string.dock_background_solid) },
                        ListPreferenceEntry(2) { stringResource(R.string.dock_background_blur) },
                        ListPreferenceEntry(3) { stringResource(R.string.dock_background_crystal) },
                        ListPreferenceEntry(4) { stringResource(R.string.dock_background_frosty) },
                    ),
                    label = stringResource(id = R.string.hotseat_background),
                )
                ExpandAndShrink(visible = selectedMode == 2 || selectedMode == 4) {
                    OneUiGlassIntensityPreference(
                        label = stringResource(R.string.glass_effect_intensity),
                        value = dockBlurIntensity,
                        onValueChange = { value ->
                            OneUiGlassPreferences.setDockBlurIntensity(context, value)
                            dockBlurIntensity = value
                        },
                    )
                }
                ExpandAndShrink(visible = selectedMode == 3) {
                    OneUiGlassIntensityPreference(
                        label = stringResource(R.string.glass_effect_intensity),
                        value = dockCrystalIntensity,
                        onValueChange = { value ->
                            OneUiGlassPreferences.setDockCrystalIntensity(context, value)
                            dockCrystalIntensity = value
                        },
                    )
                }
                ExpandAndShrink(
                    visible = selectedMode != 0,
                ) {
                    HotseatBackgroundSettings(prefs, prefs2)
                }
            }
            SearchBarPreference(SearchRoute.DOCK_SEARCH)
            GridSettings(prefs, prefs2)
            PreferenceGroup(heading = stringResource(id = R.string.icons)) {
                SwitchPreference(
                    adapter = prefs2.enableLabelInDock.getAdapter(),
                    label = stringResource(id = R.string.show_labels),
                )
            }
        }
    }
}

@Composable
fun HotseatBackgroundSettings(prefs: PreferenceManager, prefs2: PreferenceManager2) {
    DividerColumn {
        ColorPreference(preference = prefs2.hotseatBackgroundColor)
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_corner_radius),
            adapter = prefs2.hotseatBackgroundCornerRadius.getAdapter(),
            step = 1f,
            valueRange = 0f..100f,
            showUnit = "dp",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_alpha),
            adapter = prefs.hotseatBGAlpha.getAdapter(),
            step = 5,
            valueRange = 5..100,
            showUnit = "%",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_horizontal_inset_left),
            adapter = prefs.hotseatBGHorizontalInsetLeft.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_horizontal_inset_right),
            adapter = prefs.hotseatBGHorizontalInsetRight.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_vertical_inset_top),
            adapter = prefs.hotseatBGVerticalInsetTop.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_vertical_inset_bottom),
            adapter = prefs.hotseatBGVerticalInsetBottom.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
    }
}

@Composable
fun GridSettings(prefs: PreferenceManager, prefs2: PreferenceManager2) {
    val isFoldable = InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY
    val hotseatColumnsAdapter = prefs.hotseatColumns.getAdapter()
    val hotseatColumnsUnfoldedAdapter = prefs.hotseatColumnsUnfolded.getAdapter()
    val hotseatRowsAdapter = prefs.hotseatRows.getAdapter()
    val dockPagesAdapter = prefs.dockPages.getAdapter()

    PreferenceGroup(heading = stringResource(id = R.string.grid)) {
        if (isFoldable) {
            SliderPreference(
                label = stringResource(id = R.string.state_folded, stringResource(id = R.string.dock_icons)),
                adapter = hotseatColumnsAdapter,
                step = 1,
                valueRange = 3..10,
            )
            SliderPreference(
                label = stringResource(id = R.string.state_unfolded, stringResource(id = R.string.dock_icons)),
                adapter = hotseatColumnsUnfoldedAdapter,
                step = 1,
                valueRange = 3..10,
            )
            ExpandAndShrink(
                visible = hotseatColumnsAdapter.state.value > hotseatColumnsUnfoldedAdapter.state.value,
            ) {
                WarningPreference(
                    text = stringResource(id = R.string.foldable_columns_error),
                )
            }
        } else {
            SliderPreference(
                label = stringResource(id = R.string.dock_icons),
                adapter = hotseatColumnsAdapter,
                step = 1,
                valueRange = 3..10,
            )
        }
        SliderPreference(
            label = stringResource(id = R.string.dock_rows),
            adapter = hotseatRowsAdapter,
            step = 1,
            valueRange = 1..2,
        )
        SliderPreference(
            label = stringResource(id = R.string.dock_pages),
            adapter = dockPagesAdapter,
            step = 1,
            valueRange = 1..5,
        )
        SliderPreference(
            adapter = prefs2.hotseatBottomFactor.getAdapter(),
            label = stringResource(id = R.string.hotseat_bottom_space_label),
            valueRange = 0.0F..1.7F,
            step = 0.1F,
            showAsPercentage = true,
        )
        SliderPreference(
            adapter = prefs2.pageIndicatorHeightFactor.getAdapter(),
            label = stringResource(id = R.string.page_indicator_height),
            valueRange = 0.0F..1.0F,
            step = 0.1F,
            showAsPercentage = true,
        )
    }
}

@Composable
fun ColumnScope.DockPreferencesPreview(modifier: Modifier = Modifier) {
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) {
        val prefs = preferenceManager()
        val prefs2 = preferenceManager2()

        val hotseatRows = prefs.hotseatRows
        val dockPages = prefs.dockPages

        val adapters = listOf(
            prefs2.hotseatMode.getAdapter(),
            prefs.hotseatColumns.getAdapter(),
            prefs.hotseatColumnsUnfolded.getAdapter(),
            hotseatRows.getAdapter(),
            dockPages.getAdapter(),
            prefs2.themedHotseatQsb.getAdapter(),
            prefs.hotseatQsbCornerRadius.getAdapter(),
            prefs.hotseatQsbAlpha.getAdapter(),
            prefs.hotseatQsbStrokeWidth.getAdapter(),
            prefs2.hotseatBottomFactor.getAdapter(),
            prefs2.strokeColorStyle.getAdapter(),
            prefs2.enableLabelInDock.getAdapter(),
            prefs.hotseatBG.getAdapter(),
            prefs2.hotseatBackgroundMode.getAdapter(),
            prefs.hotseatBGHorizontalInsetLeft.getAdapter(),
            prefs.hotseatBGVerticalInsetTop.getAdapter(),
            prefs.hotseatBGHorizontalInsetRight.getAdapter(),
            prefs.hotseatBGVerticalInsetBottom.getAdapter(),
            prefs2.pageIndicatorHeightFactor.getAdapter(),
            prefs2.hotseatBackgroundColor.getAdapter(),
            prefs2.hotseatBackgroundCornerRadius.getAdapter(),
            prefs.hotseatBGAlpha.getAdapter(),
        )

        PreferenceGroupHeading(
            heading = stringResource(id = R.string.preview_label),
        )
        DividerColumn(
            modifier = modifier.padding(horizontal = 16.dp),
        ) {
            WithWallpaper { wallpaper ->
                DummyLauncherBox(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterHorizontally)
                        .clip(MaterialTheme.shapes.large)
                        .clipToBottomPercentage(if (hotseatRows.getAdapter().state.value >= 2) .4f else .3f),
                ) {
                    WallpaperPreview(
                        wallpaper = wallpaper,
                        modifier = Modifier.fillMaxSize(),
                    )
                    key(adapters.map { it.state.value }.toTypedArray()) {
                        DummyLauncherLayout(
                            idp = createPreviewIdp { copy(numHotseatColumns = prefs.hotseatColumns.get()) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

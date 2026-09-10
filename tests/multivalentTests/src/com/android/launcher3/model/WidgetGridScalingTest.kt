/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.launcher3.model

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Point
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class WidgetGridScalingTest {
    private val migration = GridSizeMigrationLogic()

    private fun provider(mode: Int = AppWidgetProviderInfo.RESIZE_BOTH) =
        mock(LauncherAppWidgetProviderInfo::class.java).apply {
            resizeMode = mode
            minSpanX = 1
            minSpanY = 1
            maxSpanX = 6
            maxSpanY = 7
        }

    private fun widget() = DbEntry().apply {
        cellX = 1
        cellY = 1
        spanX = 3
        spanY = 3
        minSpanX = 1
        minSpanY = 1
    }

    @Test
    fun growingGridPreservesApproximateAreaAndCenter() {
        val entry = widget()
        migration.scaleWidgetForGrid(entry, Point(5, 6), Point(6, 7), provider())
        assertThat(entry.spanX).isEqualTo(4)
        assertThat(entry.spanY).isEqualTo(4)
        assertThat(entry.cellX).isEqualTo(1)
        assertThat(entry.cellY).isEqualTo(1)
    }

    @Test
    fun fixedAxisKeepsItsCurrentSpan() {
        val entry = widget()
        migration.scaleWidgetForGrid(
            entry, Point(5, 6), Point(6, 7),
            provider(AppWidgetProviderInfo.RESIZE_VERTICAL),
        )
        assertThat(entry.spanX).isEqualTo(3)
        assertThat(entry.spanY).isEqualTo(4)
    }

    @Test
    fun providerMaximumLimitsGrowth() {
        val entry = widget()
        val provider = provider().apply {
            maxSpanX = 3
            maxSpanY = 3
        }
        migration.scaleWidgetForGrid(entry, Point(5, 6), Point(6, 7), provider)
        assertThat(entry.spanX).isEqualTo(3)
        assertThat(entry.spanY).isEqualTo(3)
    }

    @Test
    fun shrinkingGridRespectsMinimumAndBounds() {
        val entry = widget().apply {
            cellX = 3
            cellY = 4
        }
        val provider = provider().apply {
            minSpanX = 3
            minSpanY = 3
        }
        migration.scaleWidgetForGrid(entry, Point(6, 7), Point(4, 5), provider)
        assertThat(entry.spanX).isEqualTo(3)
        assertThat(entry.spanY).isEqualTo(3)
        assertThat(entry.cellX).isEqualTo(1)
        assertThat(entry.cellY).isEqualTo(2)
    }

    @Test
    fun invalidSourceDoesNotChangeTheWidget() {
        val entry = widget()
        migration.scaleWidgetForGrid(entry, Point(0, 6), Point(6, 7), provider())
        assertThat(entry.spanX).isEqualTo(3)
        assertThat(entry.cellX).isEqualTo(1)
    }
}

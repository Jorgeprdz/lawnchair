/*
 * Copyright (C) 2026 Lawnchair
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
package com.android.launcher3.model.data

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class WidgetStackInfoTest {
    private fun widget(rowId: Int, memberRank: Int) =
        LauncherAppWidgetInfo(rowId, ComponentName("test.widgets", "test.widgets.Provider")).apply {
            id = rowId
            rank = memberRank
        }

    @Test
    fun loadOrderDoesNotDeterminePageOrder() {
        val stack = WidgetStackInfo()
        stack.activeWidgetId = 20
        stack.add(widget(30, 9))
        stack.add(widget(10, 0))
        stack.add(widget(20, 4))
        stack.sortWidgetsByRank()
        assertThat(stack.getContents().map { it.id }).containsExactly(10, 20, 30).inOrder()
        assertThat(stack.getContents().map { it.rank }).containsExactly(0, 1, 2).inOrder()
        assertThat(stack.getActiveWidget()?.id).isEqualTo(20)
    }

    @Test
    fun reorderPreservesActiveMember() {
        val stack = WidgetStackInfo()
        stack.add(widget(10, 0))
        stack.add(widget(20, 1))
        stack.activeWidgetId = 20
        stack.moveWidget(1, 0)
        assertThat(stack.getContents().map { it.id }).containsExactly(20, 10).inOrder()
        assertThat(stack.getActiveWidget()?.id).isEqualTo(20)
    }

    @Test
    fun removingActiveMemberSelectsSurvivorThenHandlesEmptyStack() {
        val stack = WidgetStackInfo()
        val first = widget(10, 0)
        val second = widget(20, 1)
        stack.add(first)
        stack.add(second)
        stack.activeWidgetId = 20
        stack.removeWidget(second)
        assertThat(stack.getActiveWidget()).isSameInstanceAs(first)
        assertThat(first.rank).isEqualTo(0)
        stack.removeWidget(first)
        assertThat(stack.getActiveWidget()).isNull()
        assertThat(stack.activeWidgetId).isEqualTo(ItemInfo.NO_ID)
    }

    @Test
    fun duplicateOrNonWidgetMemberIsRejected() {
        val stack = WidgetStackInfo()
        stack.add(widget(10, 0))
        assertThrows(IllegalArgumentException::class.java) { stack.add(widget(10, 1)) }
        assertThrows(IllegalArgumentException::class.java) { stack.add(WorkspaceItemInfo()) }
        assertThat(stack.getContents()).hasSize(1)
    }

    @Test
    fun repeatedCopyDoesNotDuplicateMembership() {
        val original = WidgetStackInfo()
        original.add(widget(10, 0))
        original.add(widget(20, 1))
        original.activeWidgetId = 20
        val copy = original.makeShallowCopy() as WidgetStackInfo
        copy.copyFrom(original)
        assertThat(copy.getContents()).hasSize(2)
        assertThat(copy.activeWidgetId).isEqualTo(20)
        assertThat(original.getContents()).hasSize(2)
    }
}

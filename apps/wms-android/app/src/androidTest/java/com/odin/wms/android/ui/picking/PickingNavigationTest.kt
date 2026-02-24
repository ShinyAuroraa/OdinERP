package com.odin.wms.android.ui.picking

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.odin.wms.android.domain.model.PickingTask
import com.odin.wms.android.domain.model.PickingTaskStatus
import com.odin.wms.android.ui.theme.WmsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * I5 — PickingNavigationTest instrumented test
 *
 * NOTE: Requires Android emulator API 26+. Not runnable locally on Windows.
 */
class PickingNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // I5: onTaskClick callback called when card is clicked
    @Test
    fun i5_pickingTaskCard_click_invokes_onTaskClick_callback() {
        val task = PickingTask(
            id = "task-nav-001",
            taskNumber = "PT-NAV-001",
            pickingOrderId = "po-001",
            status = PickingTaskStatus.PICKING_PENDING,
            corridor = "C",
            priority = 0,
            totalItems = 3,
            pickedItems = 0
        )
        var clickedTaskId: String? = null

        composeTestRule.setContent {
            WmsTheme {
                PickingTaskCard(task = task, onClick = { clickedTaskId = task.id })
            }
        }

        composeTestRule.onNodeWithText("PT-NAV-001").performClick()

        assertEquals("task-nav-001", clickedTaskId)
    }
}

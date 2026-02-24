package com.odin.wms.android.ui.picking

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.odin.wms.android.domain.model.PickingTask
import com.odin.wms.android.domain.model.PickingTaskStatus
import com.odin.wms.android.ui.theme.WmsTheme
import org.junit.Rule
import org.junit.Test

/**
 * I1 — PickingListScreen instrumented test
 *
 * NOTE: This test requires an Android emulator API 26+.
 * It CANNOT run locally on Windows due to non-ASCII path + AGP limitation.
 * Execute via CI/CD on Linux or Android Studio emulator.
 */
class PickingListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildTask(id: String, taskNumber: String, corridor: String) = PickingTask(
        id = id,
        taskNumber = taskNumber,
        pickingOrderId = "po-$id",
        status = PickingTaskStatus.PICKING_PENDING,
        corridor = corridor,
        priority = 0,
        totalItems = 5,
        pickedItems = 2
    )

    // I1: PickingListScreen renders 2 tasks in LazyColumn
    @Test
    fun i1_pickingListScreen_renders_two_tasks() {
        val tasks = listOf(
            buildTask("t1", "PT-0001", "A"),
            buildTask("t2", "PT-0002", "B")
        )

        composeTestRule.setContent {
            WmsTheme {
                LazyColumnPickingTasks(tasks = tasks, onTaskClick = {})
            }
        }

        composeTestRule.onNodeWithText("PT-0001").assertIsDisplayed()
        composeTestRule.onNodeWithText("PT-0002").assertIsDisplayed()
        composeTestRule.onNodeWithText("Corredor: A").assertIsDisplayed()
        composeTestRule.onNodeWithText("Corredor: B").assertIsDisplayed()
    }
}

// Helper composable for testing without ViewModel
@androidx.compose.runtime.Composable
fun LazyColumnPickingTasks(tasks: List<PickingTask>, onTaskClick: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn {
        tasks.forEach { task ->
            item(key = task.id) {
                PickingTaskCard(task = task, onClick = { onTaskClick(task.id) })
            }
        }
    }
}

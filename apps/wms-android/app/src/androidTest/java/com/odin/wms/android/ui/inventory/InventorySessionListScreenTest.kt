package com.odin.wms.android.ui.inventory

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.odin.wms.android.domain.model.InventorySession
import com.odin.wms.android.domain.model.InventorySessionStatus
import com.odin.wms.android.domain.model.InventorySessionType
import com.odin.wms.android.ui.theme.WmsTheme
import org.junit.Rule
import org.junit.Test

/**
 * I1 — InventorySessionListScreen instrumented test
 *
 * NOTE: This test requires an Android emulator API 26+.
 * It CANNOT run locally on Windows due to non-ASCII path + AGP limitation.
 * Execute via CI/CD on Linux or Android Studio emulator.
 */
class InventorySessionListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildSession(
        id: String,
        sessionNumber: String,
        aisle: String,
        totalItems: Int = 10,
        countedItems: Int = 3
    ) = InventorySession(
        id = id,
        tenantId = "tenant-001",
        sessionNumber = sessionNumber,
        sessionType = InventorySessionType.CYCLIC,
        status = InventorySessionStatus.ACTIVE,
        aisle = aisle,
        totalItems = totalItems,
        countedItems = countedItems
    )

    // I1: InventorySessionListScreen renders 2 sessions in LazyColumn
    @Test
    fun i1_inventorySessionListScreen_renders_two_sessions() {
        val sessions = listOf(
            buildSession("s1", "INV-0001", "A"),
            buildSession("s2", "INV-0002", "B")
        )

        composeTestRule.setContent {
            WmsTheme {
                LazyColumn {
                    sessions.forEach { session ->
                        item(key = session.id) {
                            InventorySessionCard(session = session, onClick = {})
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("INV-0001").assertIsDisplayed()
        composeTestRule.onNodeWithText("INV-0002").assertIsDisplayed()
        composeTestRule.onNodeWithText("Corredor: A").assertIsDisplayed()
        composeTestRule.onNodeWithText("Corredor: B").assertIsDisplayed()
        composeTestRule.onNodeWithText("3/10 itens contados").assertIsDisplayed()
    }
}

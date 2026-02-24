package com.odin.wms.android.ui.receiving

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.odin.wms.android.domain.model.ReceivingOrder
import com.odin.wms.android.domain.model.ReceivingOrderStatus
import com.odin.wms.android.ui.theme.WmsTheme
import org.junit.Rule
import org.junit.Test

/**
 * I1 — ReceivingListScreen instrumented test
 *
 * NOTE: This test requires an Android emulator API 26+.
 * It CANNOT run locally on Windows due to non-ASCII path + AGP limitation.
 * Execute via CI/CD on Linux or Android Studio emulator.
 */
class ReceivingListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildOrder(id: String, orderNumber: String, supplier: String) = ReceivingOrder(
        id = id,
        orderNumber = orderNumber,
        supplier = supplier,
        expectedDate = "2026-03-15",
        status = ReceivingOrderStatus.PENDING,
        totalItems = 5,
        confirmedItems = 0
    )

    // I1: ReceivingListScreen shows 2 mocked orders in LazyColumn
    @Test
    fun i1_receivingListScreen_shows_two_orders() {
        val orders = listOf(
            buildOrder("o1", "RC-0001", "Fornecedor Alpha"),
            buildOrder("o2", "RC-0002", "Fornecedor Beta")
        )

        composeTestRule.setContent {
            WmsTheme {
                // Render the state directly as OrdersLoaded with 2 orders
                val fakeState = ReceivingUiState.OrdersLoaded(orders = orders, isOffline = false)
                ReceivingListScreenContent(
                    uiState = fakeState,
                    onOrderClick = {},
                    onRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithText("RC-0001").assertIsDisplayed()
        composeTestRule.onNodeWithText("RC-0002").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fornecedor Alpha").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fornecedor Beta").assertIsDisplayed()
    }
}

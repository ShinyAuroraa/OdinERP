package com.odin.wms.android.ui.receiving

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import com.odin.wms.android.domain.model.ReceivingOrder
import com.odin.wms.android.domain.model.ReceivingOrderStatus
import com.odin.wms.android.ui.theme.WmsTheme
import org.junit.Rule
import org.junit.Test

/**
 * I5 — Receiving navigation test: ReceivingListScreen → ReceivingDetailScreen
 *
 * NOTE: Requires Android emulator API 26+. Cannot run locally on Windows.
 * Full navigation test requires HiltAndroidTest + TestNavController.
 */
class ReceivingNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildOrder(id: String, orderNumber: String) = ReceivingOrder(
        id = id,
        orderNumber = orderNumber,
        supplier = "Fornecedor Teste",
        expectedDate = "2026-03-15",
        status = ReceivingOrderStatus.PENDING,
        totalItems = 3,
        confirmedItems = 0
    )

    // I5: Clicking on order card navigates to detail screen
    @Test
    fun i5_clicking_order_card_triggers_onOrderClick_callback() {
        var clickedOrderId = ""
        val orders = listOf(buildOrder("order-nav-1", "RC-NAV-001"))

        composeTestRule.setContent {
            WmsTheme {
                ReceivingListScreenContent(
                    uiState = ReceivingUiState.OrdersLoaded(orders = orders),
                    onOrderClick = { orderId -> clickedOrderId = orderId },
                    onRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithText("RC-NAV-001").performClick()

        // Verify callback was invoked with correct orderId
        assert(clickedOrderId == "order-nav-1") {
            "Expected clickedOrderId='order-nav-1' but was '$clickedOrderId'"
        }
    }
}

package com.odin.wms.android.ui.shipping

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.odin.wms.android.domain.model.ShippingOrder
import com.odin.wms.android.domain.model.ShippingOrderStatus
import com.odin.wms.android.ui.theme.WmsTheme
import org.junit.Rule
import org.junit.Test

/**
 * I4 — ShippingListScreen instrumented test
 *
 * NOTE: Requires Android emulator API 26+. Not runnable locally on Windows.
 */
class ShippingListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildOrder(
        id: String,
        orderNumber: String,
        carrier: String,
        vehiclePlate: String?
    ) = ShippingOrder(
        id = id,
        orderNumber = orderNumber,
        carrier = carrier,
        vehiclePlate = vehiclePlate,
        status = ShippingOrderStatus.SHIPPING_PENDING,
        packages = emptyList(),
        totalPackages = 3,
        loadedPackages = 1
    )

    // I4: ShippingListScreen renders 1 order with package count
    @Test
    fun i4_shippingListScreen_renders_one_order_with_package_count() {
        val order = buildOrder("o1", "SH-0001", "Transportadora X", "ABC-1234")

        composeTestRule.setContent {
            WmsTheme {
                LazyColumnShippingOrders(orders = listOf(order), onOrderClick = {})
            }
        }

        composeTestRule.onNodeWithText("SH-0001").assertIsDisplayed()
        composeTestRule.onNodeWithText("Transportadora: Transportadora X").assertIsDisplayed()
        composeTestRule.onNodeWithText("Placa: ABC-1234").assertIsDisplayed()
        composeTestRule.onNodeWithText("1/3 volumes").assertIsDisplayed()
    }
}

@Composable
fun LazyColumnShippingOrders(orders: List<ShippingOrder>, onOrderClick: (String) -> Unit) {
    LazyColumn {
        orders.forEach { order ->
            item(key = order.id) {
                ShippingOrderCard(order = order, onClick = { onOrderClick(order.id) })
            }
        }
    }
}

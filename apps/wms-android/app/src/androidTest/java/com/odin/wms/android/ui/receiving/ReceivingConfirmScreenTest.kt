package com.odin.wms.android.ui.receiving

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.odin.wms.android.ui.theme.WmsTheme
import org.junit.Rule
import org.junit.Test

/**
 * I3 — ReceivingConfirmScreen instrumented test
 *
 * NOTE: Requires Android emulator API 26+. Cannot run locally on Windows.
 */
class ReceivingConfirmScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // I3: quantity field is pre-filled with expectedQty
    @Test
    fun i3_receivingConfirmScreen_quantity_field_prefilled_with_expectedQty() {
        val expectedQty = 7

        composeTestRule.setContent {
            WmsTheme {
                // The ReceivingConfirmScreen receives expectedQty as a parameter
                // and pre-fills the quantity field.
                // Full test requires HiltAndroidTest. Structure provided for CI.
                ReceivingConfirmScreen(
                    orderId = "order-1",
                    itemId = "item-1",
                    scannedCode = null,
                    expectedQty = expectedQty,
                    onNavigateBack = {},
                    onConfirmSuccess = {}
                )
            }
        }

        // Verify quantity field shows "7"
        composeTestRule.onNodeWithText("7").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirmar").assertIsDisplayed()
    }
}

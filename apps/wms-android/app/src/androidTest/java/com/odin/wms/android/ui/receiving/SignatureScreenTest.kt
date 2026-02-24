package com.odin.wms.android.ui.receiving

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.odin.wms.android.ui.theme.WmsTheme
import org.junit.Rule
import org.junit.Test

/**
 * I4 — SignatureScreen instrumented test
 *
 * NOTE: Requires Android emulator API 26+. Cannot run locally on Windows.
 */
class SignatureScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // I4: "Confirmar Assinatura" button disabled with empty canvas; enabled after drawing
    @Test
    fun i4_signatureScreen_confirm_button_disabled_when_canvas_empty() {
        composeTestRule.setContent {
            WmsTheme {
                SignatureScreen(
                    orderId = "order-1",
                    onNavigateBack = {},
                    onSignatureComplete = {}
                )
            }
        }

        // With no drawing, button should be disabled (totalPoints < 50)
        composeTestRule.onNodeWithText("Confirmar Assinatura").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Limpar").assertIsEnabled()
        composeTestRule.onNodeWithText("Canvas vazio").assertIsDisplayed()
    }
}

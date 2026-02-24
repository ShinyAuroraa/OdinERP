package com.odin.wms.android.ui.receiving

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.odin.wms.android.ui.theme.WmsTheme
import org.junit.Rule
import org.junit.Test

/**
 * I6 — DivergenceScreen instrumented test
 *
 * NOTE: Requires Android emulator API 26+. Cannot run locally on Windows.
 */
class DivergenceScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // I6: observacao field shows error if < 10 chars when submitting DAMAGE divergence
    @Test
    fun i6_divergenceScreen_shows_notes_validation_error_for_short_input() {
        composeTestRule.setContent {
            WmsTheme {
                DivergenceScreen(
                    orderId = "order-1",
                    itemId = "item-1",
                    onNavigateBack = {},
                    onDivergenceReported = {}
                )
            }
        }

        // Select DAMAGE type
        composeTestRule.onNodeWithText("Avaria").performClick()

        // Enter short observation (< 10 chars)
        composeTestRule.onNodeWithText("Observação (mínimo 10 caracteres)")
            .performTextInput("Curto")

        // Attempt to submit
        composeTestRule.onNodeWithText("Confirmar Divergência").performClick()

        // Validation error should appear
        composeTestRule.onNodeWithText("Observação é obrigatória (mínimo 10 caracteres)")
            .assertIsDisplayed()
    }
}

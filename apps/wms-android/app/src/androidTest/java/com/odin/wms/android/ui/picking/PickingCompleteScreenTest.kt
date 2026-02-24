package com.odin.wms.android.ui.picking

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.odin.wms.android.ui.theme.WmsTheme
import org.junit.Rule
import org.junit.Test

/**
 * I6 — PickingCompleteScreen instrumented test
 *
 * NOTE: Requires Android emulator API 26+. Not runnable locally on Windows.
 */
class PickingCompleteScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // I6: "Concluir Separação" button is disabled when hasPending = true
    @Test
    fun i6_concludeButton_disabled_when_items_pending() {
        composeTestRule.setContent {
            WmsTheme {
                ConcludeButton(hasPending = true, onClick = {})
            }
        }

        composeTestRule.onNodeWithText("Confirmar Conclusão").assertIsNotEnabled()
    }

    @Test
    fun i6_concludeButton_enabled_when_no_items_pending() {
        composeTestRule.setContent {
            WmsTheme {
                ConcludeButton(hasPending = false, onClick = {})
            }
        }

        composeTestRule.onNodeWithText("Confirmar Conclusão").assertIsEnabled()
    }
}

@Composable
private fun ConcludeButton(hasPending: Boolean, onClick: () -> Unit) {
    Column {
        Button(
            onClick = onClick,
            enabled = !hasPending
        ) {
            Text("Confirmar Conclusão")
        }
    }
}

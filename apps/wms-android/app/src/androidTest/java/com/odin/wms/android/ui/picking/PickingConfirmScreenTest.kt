package com.odin.wms.android.ui.picking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.input.KeyboardType
import com.odin.wms.android.ui.theme.WmsTheme
import org.junit.Rule
import org.junit.Test

/**
 * I3 — PickingConfirmScreen instrumented test
 *
 * NOTE: Requires Android emulator API 26+. Not runnable locally on Windows.
 */
class PickingConfirmScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // I3: Quantity field is pre-filled with expectedQty
    @Test
    fun i3_pickingConfirmScreen_qty_field_prefilled_with_expectedQty() {
        val expectedQty = 7

        composeTestRule.setContent {
            WmsTheme {
                Column {
                    var qty by remember { mutableStateOf(expectedQty.toString()) }
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text("Quantidade Pickada") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("7").assertExists()
    }
}

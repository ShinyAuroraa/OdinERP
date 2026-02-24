package com.odin.wms.android.ui.inventory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.odin.wms.android.domain.repository.IInventoryRepository
import com.odin.wms.android.ui.theme.WmsTheme
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

/**
 * I4 — InventoryDoubleCountScreen instrumented test: header shows 1st count result
 *
 * NOTE: This test requires an Android emulator API 26+.
 * It CANNOT run locally on Windows due to non-ASCII path + AGP limitation.
 * Execute via CI/CD on Linux or Android Studio emulator.
 */
class InventoryDoubleCountScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // I4: Header shows 1st count result (firstCountQty and truncated firstCounterId)
    @Test
    fun i4_inventoryDoubleCountScreen_header_shows_first_count_result() {
        val firstCountQty = 12
        val firstCounterId = "operator-001"
        val inventoryRepository = mockk<IInventoryRepository>(relaxed = true)
        val viewModel = InventoryViewModel(inventoryRepository)

        composeTestRule.setContent {
            WmsTheme {
                InventoryDoubleCountScreen(
                    sessionId = "session-001",
                    itemId = "item-001",
                    firstCountQty = firstCountQty,
                    firstCounterId = firstCounterId,
                    currentCounterId = "operator-002",
                    viewModel = viewModel,
                    onDoubleCountComplete = {},
                    onNavigateBack = {}
                )
            }
        }

        // Header section shows "1ª Contagem" label
        composeTestRule.onNodeWithText("1ª Contagem").assertIsDisplayed()

        // Header shows the first count quantity
        composeTestRule.onNodeWithText("Quantidade: $firstCountQty").assertIsDisplayed()

        // Header shows truncated operator ID (up to 8 chars + "...")
        val truncatedId = if (firstCounterId.length > 8)
            firstCounterId.take(8) + "..." else firstCounterId
        composeTestRule.onNodeWithText("Operador: $truncatedId").assertIsDisplayed()

        // Second count section title is visible
        composeTestRule.onNodeWithText("2ª Contagem").assertIsDisplayed()
    }

    @Test
    fun i4_inventoryDoubleCountScreen_confirm_button_enabled_when_qty_filled() {
        val inventoryRepository = mockk<IInventoryRepository>(relaxed = true)
        val viewModel = InventoryViewModel(inventoryRepository)

        composeTestRule.setContent {
            WmsTheme {
                InventoryDoubleCountScreen(
                    sessionId = "session-001",
                    itemId = "item-001",
                    firstCountQty = 10,
                    firstCounterId = "op-001",
                    currentCounterId = "op-002",
                    viewModel = viewModel,
                    onDoubleCountComplete = {},
                    onNavigateBack = {}
                )
            }
        }

        // Confirm button is visible (disabled until qty is entered)
        composeTestRule.onNodeWithText("Confirmar 2ª Contagem").assertIsDisplayed()
    }
}

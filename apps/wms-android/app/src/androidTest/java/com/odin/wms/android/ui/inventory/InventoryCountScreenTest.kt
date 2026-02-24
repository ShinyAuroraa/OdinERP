package com.odin.wms.android.ui.inventory

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.odin.wms.android.domain.repository.IInventoryRepository
import com.odin.wms.android.ui.theme.WmsTheme
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

/**
 * I3 — InventoryCountScreen instrumented test: counted_qty pre-filled with systemQty
 *
 * NOTE: This test requires an Android emulator API 26+.
 * It CANNOT run locally on Windows due to non-ASCII path + AGP limitation.
 * Execute via CI/CD on Linux or Android Studio emulator.
 */
class InventoryCountScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // I3: counted_qty field is pre-filled with systemQty when screen opens
    @Test
    fun i3_inventoryCountScreen_countedQty_prefilledWithSystemQty() {
        val systemQty = 15
        val inventoryRepository = mockk<IInventoryRepository>(relaxed = true)
        val viewModel = InventoryViewModel(inventoryRepository)

        composeTestRule.setContent {
            WmsTheme {
                InventoryCountScreen(
                    sessionId = "session-001",
                    itemId = "item-001",
                    productCode = "P-001",
                    gtin = "12345678901234",
                    description = "Produto Teste",
                    systemQty = systemQty,
                    position = "A-01-001",
                    scannedLotNumber = null,
                    viewModel = viewModel,
                    onCountSuccess = {},
                    onDoubleCountClick = { _, _ -> },
                    onNavigateBack = {}
                )
            }
        }

        // The "Quantidade Contada" field should be pre-filled with systemQty value
        composeTestRule
            .onNodeWithText(systemQty.toString())
            .assertExists("counted_qty field should be pre-filled with systemQty=$systemQty")

        // System quantity reference is visible
        composeTestRule.onNodeWithText("Qtd Sistema: $systemQty").assertExists()
    }

    @Test
    fun i3_inventoryCountScreen_productCode_and_position_displayed() {
        val inventoryRepository = mockk<IInventoryRepository>(relaxed = true)
        val viewModel = InventoryViewModel(inventoryRepository)

        composeTestRule.setContent {
            WmsTheme {
                InventoryCountScreen(
                    sessionId = "session-001",
                    itemId = "item-001",
                    productCode = "P-002",
                    gtin = "98765432109876",
                    description = "Produto B",
                    systemQty = 5,
                    position = "B-03-007",
                    scannedLotNumber = null,
                    viewModel = viewModel,
                    onCountSuccess = {},
                    onDoubleCountClick = { _, _ -> },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("P-002").assertExists()
        composeTestRule.onNodeWithText("Posição: B-03-007").assertExists()
        composeTestRule.onNodeWithText("GTIN: 98765432109876").assertExists()
    }
}

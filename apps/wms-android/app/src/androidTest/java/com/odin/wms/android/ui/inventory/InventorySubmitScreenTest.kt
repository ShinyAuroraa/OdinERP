package com.odin.wms.android.ui.inventory

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.odin.wms.android.domain.model.InventoryItem
import com.odin.wms.android.domain.model.InventoryItemLocalStatus
import com.odin.wms.android.domain.repository.IInventoryRepository
import com.odin.wms.android.ui.theme.WmsTheme
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

/**
 * I6 — InventorySubmitScreen instrumented test: submit button disabled when PENDING items exist
 *
 * NOTE: This test requires an Android emulator API 26+.
 * It CANNOT run locally on Windows due to non-ASCII path + AGP limitation.
 * Execute via CI/CD on Linux or Android Studio emulator.
 */
class InventorySubmitScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildItem(
        id: String,
        localStatus: InventoryItemLocalStatus,
        countedQty: Int? = null
    ) = InventoryItem(
        id = id,
        sessionId = "session-001",
        tenantId = "tenant-001",
        productCode = "P-00$id",
        gtin = "12345678901234",
        description = "Produto $id",
        position = "A-0$id-001",
        systemQty = 10,
        countedQty = countedQty,
        localStatus = localStatus
    )

    // I6: "Confirmar Submissão" button is disabled when at least one item has PENDING status
    @Test
    fun i6_inventorySubmitScreen_submitButton_disabled_when_pending_items_exist() {
        val items = listOf(
            buildItem("1", InventoryItemLocalStatus.COUNTED, countedQty = 10),
            buildItem("2", InventoryItemLocalStatus.PENDING, countedQty = null) // PENDING — blocks submit
        )

        val inventoryRepository = mockk<IInventoryRepository>(relaxed = true)
        val viewModel = InventoryViewModel(inventoryRepository)

        composeTestRule.setContent {
            WmsTheme {
                InventorySubmitScreen(
                    sessionId = "session-001",
                    sessionNumber = "INV-0001",
                    items = items,
                    viewModel = viewModel,
                    onSubmitSuccess = {},
                    onNavigateBack = {}
                )
            }
        }

        // "Confirmar Submissão" button must be DISABLED when there are PENDING items
        composeTestRule.onNodeWithText("Confirmar Submissão").assertIsNotEnabled()

        // Warning message about pending items is shown
        composeTestRule.onNodeWithText(
            "Existem 1 item(s) ainda não contados. Complete a contagem antes de submeter."
        ).assertExists()
    }

    @Test
    fun i6_inventorySubmitScreen_submitButton_enabled_when_all_items_counted() {
        val items = listOf(
            buildItem("1", InventoryItemLocalStatus.COUNTED, countedQty = 10),
            buildItem("2", InventoryItemLocalStatus.COUNTED_VERIFIED, countedQty = 8)
        )

        val inventoryRepository = mockk<IInventoryRepository>(relaxed = true)
        val viewModel = InventoryViewModel(inventoryRepository)

        composeTestRule.setContent {
            WmsTheme {
                InventorySubmitScreen(
                    sessionId = "session-001",
                    sessionNumber = "INV-0001",
                    items = items,
                    viewModel = viewModel,
                    onSubmitSuccess = {},
                    onNavigateBack = {}
                )
            }
        }

        // "Confirmar Submissão" button must be ENABLED when no PENDING items remain
        composeTestRule.onNodeWithText("Confirmar Submissão").assertIsEnabled()
    }
}

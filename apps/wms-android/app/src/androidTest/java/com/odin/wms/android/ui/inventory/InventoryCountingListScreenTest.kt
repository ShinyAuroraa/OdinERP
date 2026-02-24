package com.odin.wms.android.ui.inventory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.odin.wms.android.domain.model.InventoryItem
import com.odin.wms.android.domain.model.InventoryItemLocalStatus
import com.odin.wms.android.ui.theme.WmsTheme
import org.junit.Rule
import org.junit.Test

/**
 * I2 — InventoryCountingListScreen instrumented test: divergence badge
 *
 * NOTE: This test requires an Android emulator API 26+.
 * It CANNOT run locally on Windows due to non-ASCII path + AGP limitation.
 * Execute via CI/CD on Linux or Android Studio emulator.
 */
class InventoryCountingListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildItem(
        id: String = "item-001",
        productCode: String = "P-001",
        systemQty: Int = 10,
        countedQty: Int? = null,
        localStatus: InventoryItemLocalStatus = InventoryItemLocalStatus.PENDING
    ) = InventoryItem(
        id = id,
        sessionId = "session-001",
        tenantId = "tenant-001",
        productCode = productCode,
        gtin = "12345678901234",
        description = "Produto Teste",
        position = "A-01-001",
        systemQty = systemQty,
        countedQty = countedQty,
        localStatus = localStatus
    )

    // I2: DivergenceBadge shown (red) when countedQty != systemQty
    @Test
    fun i2_inventoryItemCard_shows_divergence_badge_when_counted_differs_from_system() {
        val divergentItem = buildItem(
            systemQty = 10,
            countedQty = 8, // -2 divergence
            localStatus = InventoryItemLocalStatus.COUNTED
        )

        composeTestRule.setContent {
            WmsTheme {
                InventoryItemCard(item = divergentItem, onClick = {})
            }
        }

        // Item shows product code and quantities
        composeTestRule.onNodeWithText("P-001").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sistema: 10").assertIsDisplayed()
        composeTestRule.onNodeWithText("Contado: 8").assertIsDisplayed()

        // DivergenceBadge shows the diff: countedQty(8) - systemQty(10) = -2
        composeTestRule.onNodeWithText("-2").assertIsDisplayed()
    }

    @Test
    fun i2_inventoryItemCard_shows_needs_review_badge_for_needs_review_status() {
        val needsReviewItem = buildItem(
            systemQty = 10,
            countedQty = 15,
            localStatus = InventoryItemLocalStatus.NEEDS_REVIEW
        )

        composeTestRule.setContent {
            WmsTheme {
                InventoryItemCard(item = needsReviewItem, onClick = {})
            }
        }

        composeTestRule.onNodeWithText("Revisão necessária").assertIsDisplayed()
    }
}

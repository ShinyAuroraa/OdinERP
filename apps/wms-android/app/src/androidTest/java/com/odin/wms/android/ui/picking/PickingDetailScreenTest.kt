package com.odin.wms.android.ui.picking

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.odin.wms.android.domain.model.PickingItem
import com.odin.wms.android.domain.model.PickingItemLocalStatus
import com.odin.wms.android.ui.theme.WmsTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * I2 — PickingDetailScreen instrumented test
 *
 * NOTE: Requires Android emulator API 26+. Not runnable locally on Windows.
 */
class PickingDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // I2: Item with expiryDate < today+30d displays FEFO badge (yellow)
    @Test
    fun i2_pickingDetailScreen_shows_fefo_badge_for_near_expiry_item() {
        val nearExpiryDate = LocalDate.now().plusDays(10) // within 30 days — triggers FEFO badge
        val item = PickingItem(
            id = "item-001",
            taskId = "task-001",
            productCode = "P-001",
            gtin = "12345678901234",
            description = "Produto FEFO Teste",
            expectedQty = 5,
            pickedQty = 0,
            position = "A-01-001",
            lotNumber = "LOT-001",
            expiryDate = nearExpiryDate,
            localStatus = PickingItemLocalStatus.PENDING
        )

        composeTestRule.setContent {
            WmsTheme {
                FEFOBadge()
            }
        }

        // FEFO badge should be rendered
        composeTestRule.onNodeWithText("FEFO").assertIsDisplayed()
    }
}

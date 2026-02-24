package com.odin.wms.android.ui.transfer

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.odin.wms.android.domain.model.Transfer
import com.odin.wms.android.domain.model.TransferStatus
import com.odin.wms.android.ui.theme.WmsTheme
import org.junit.Rule
import org.junit.Test

/**
 * I5 — TransferListScreen instrumented test
 *
 * NOTE: This test requires an Android emulator API 26+.
 * It CANNOT run locally on Windows due to non-ASCII path + AGP limitation.
 * Execute via CI/CD on Linux or Android Studio emulator.
 */
class TransferListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildTransfer(
        id: String,
        sourceLocation: String,
        destinationLocation: String,
        productCode: String = "P-001",
        qty: Int = 5,
        status: TransferStatus = TransferStatus.PENDING
    ) = Transfer(
        id = id,
        tenantId = "tenant-001",
        sourceLocation = sourceLocation,
        destinationLocation = destinationLocation,
        productCode = productCode,
        qty = qty,
        lotNumber = null,
        status = status,
        localStatus = "PENDING",
        createdAt = System.currentTimeMillis()
    )

    // I5: TransferListScreen renders 1 transfer with source and destination locations
    @Test
    fun i5_transferListScreen_renders_one_transfer_with_locations() {
        val transfer = buildTransfer(
            id = "transfer-001",
            sourceLocation = "A-01-001",
            destinationLocation = "B-02-003"
        )

        composeTestRule.setContent {
            WmsTheme {
                LazyColumn {
                    item(key = transfer.id) {
                        TransferCard(transfer = transfer)
                    }
                }
            }
        }

        // Card shows "source → destination" format
        composeTestRule.onNodeWithText("A-01-001 → B-02-003").assertIsDisplayed()

        // Card shows product and quantity
        composeTestRule.onNodeWithText("Produto: P-001").assertIsDisplayed()
        composeTestRule.onNodeWithText("Qtd: 5").assertIsDisplayed()
    }

    @Test
    fun i5_transferListScreen_renders_confirmed_transfer_with_status_badge() {
        val confirmedTransfer = buildTransfer(
            id = "transfer-002",
            sourceLocation = "C-03-005",
            destinationLocation = "D-04-007",
            qty = 10,
            status = TransferStatus.CONFIRMED
        )

        composeTestRule.setContent {
            WmsTheme {
                TransferCard(transfer = confirmedTransfer)
            }
        }

        composeTestRule.onNodeWithText("C-03-005 → D-04-007").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirmada").assertIsDisplayed()
    }
}

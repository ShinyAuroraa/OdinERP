package com.odin.wms.android.domain.model

import java.time.LocalDate

data class PickingItem(
    val id: String,
    val taskId: String,
    val productCode: String,
    val gtin: String,
    val description: String,
    val expectedQty: Int,
    val pickedQty: Int,
    val position: String,
    val lotNumber: String? = null,
    val expiryDate: LocalDate? = null,
    val localStatus: PickingItemLocalStatus
)

enum class PickingItemLocalStatus {
    PENDING,
    PICKED,
    PICKED_OFFLINE,
    SKIPPED
}

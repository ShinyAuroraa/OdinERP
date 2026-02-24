package com.odin.wms.android.domain.model

enum class InventoryItemLocalStatus {
    PENDING, COUNTED, COUNTED_VERIFIED, NEEDS_REVIEW, OFFLINE_COUNTED
}

data class InventoryItem(
    val id: String,
    val sessionId: String,
    val tenantId: String,
    val productCode: String,
    val gtin: String,
    val description: String,
    val position: String,
    val systemQty: Int,
    val countedQty: Int? = null,
    val doubleCountQty: Int? = null,
    val lotNumber: String? = null,
    val firstCounterId: String? = null,
    val localStatus: InventoryItemLocalStatus = InventoryItemLocalStatus.PENDING
)

package com.odin.wms.android.domain.model

data class ReceivingItem(
    val id: String,
    val orderId: String,
    val productCode: String,
    val gtin: String,
    val description: String,
    val expectedQty: Int,
    val confirmedQty: Int = 0,
    val localStatus: ReceivingItemStatus = ReceivingItemStatus.PENDING
)

enum class ReceivingItemStatus {
    PENDING,
    CONFIRMED,
    CONFIRMED_OFFLINE,
    DIVERGENT
}

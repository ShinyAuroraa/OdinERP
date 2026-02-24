package com.odin.wms.android.domain.model

data class ReceivingOrder(
    val id: String,
    val orderNumber: String,
    val supplier: String,
    val expectedDate: String,       // ISO-8601 date string
    val status: ReceivingOrderStatus,
    val totalItems: Int,
    val confirmedItems: Int,
    val items: List<ReceivingItem> = emptyList()
)

enum class ReceivingOrderStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

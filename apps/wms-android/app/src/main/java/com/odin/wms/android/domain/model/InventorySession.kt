package com.odin.wms.android.domain.model

enum class InventorySessionStatus {
    ACTIVE, COMPLETED, CANCELLED
}

enum class InventorySessionType {
    CYCLIC, FULL
}

data class InventorySession(
    val id: String,
    val tenantId: String,
    val sessionNumber: String,
    val sessionType: InventorySessionType,
    val status: InventorySessionStatus,
    val aisle: String? = null,
    val totalItems: Int = 0,
    val countedItems: Int = 0
)

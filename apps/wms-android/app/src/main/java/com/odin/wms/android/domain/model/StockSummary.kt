package com.odin.wms.android.domain.model

data class StockSummary(
    val tenantId: String,
    val totalAvailable: Int,
    val pendingPickingCount: Int,
    val pendingReceivingCount: Int,
    val lastUpdated: Long,
    val isOffline: Boolean = false
)

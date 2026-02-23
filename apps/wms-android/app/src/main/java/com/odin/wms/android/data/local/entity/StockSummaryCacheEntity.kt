package com.odin.wms.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_summary_cache")
data class StockSummaryCacheEntity(
    @PrimaryKey val tenantId: String,
    val totalAvailable: Int,
    val pendingPickingCount: Int,
    val pendingReceivingCount: Int,
    val lastSyncAt: Long
)

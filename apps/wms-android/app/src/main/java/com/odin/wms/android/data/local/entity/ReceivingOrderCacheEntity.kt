package com.odin.wms.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "receiving_orders_cache",
    indices = [Index(value = ["tenant_id", "order_number"], unique = true)]
)
data class ReceivingOrderCacheEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "tenant_id")
    val tenantId: String,

    @ColumnInfo(name = "order_number")
    val orderNumber: String,

    val supplier: String,

    @ColumnInfo(name = "expected_date")
    val expectedDate: String,   // ISO-8601

    val status: String,

    @ColumnInfo(name = "total_items")
    val totalItems: Int,

    @ColumnInfo(name = "confirmed_items")
    val confirmedItems: Int,

    @ColumnInfo(name = "last_sync_at")
    val lastSyncAt: Long
)

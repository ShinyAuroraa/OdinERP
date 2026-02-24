package com.odin.wms.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "picking_items_cache",
    indices = [Index(value = ["product_code", "expiry_date"])]
)
data class PickingItemCacheEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "tenant_id") val tenantId: String,
    @ColumnInfo(name = "product_code") val productCode: String,
    val gtin: String,
    val description: String,
    @ColumnInfo(name = "expected_qty") val expectedQty: Int,
    @ColumnInfo(name = "picked_qty") val pickedQty: Int = 0,
    val position: String,
    @ColumnInfo(name = "lot_number") val lotNumber: String? = null,
    @ColumnInfo(name = "expiry_date") val expiryDate: String? = null, // ISO-8601 TEXT for string ordering
    @ColumnInfo(name = "local_status") val localStatus: String = "PENDING"
)

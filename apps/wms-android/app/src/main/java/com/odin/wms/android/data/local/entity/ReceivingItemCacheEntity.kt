package com.odin.wms.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "receiving_items_cache")
data class ReceivingItemCacheEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "order_id")
    val orderId: String,

    @ColumnInfo(name = "tenant_id")
    val tenantId: String,

    @ColumnInfo(name = "product_code")
    val productCode: String,

    val gtin: String,

    val description: String,

    @ColumnInfo(name = "expected_qty")
    val expectedQty: Int,

    @ColumnInfo(name = "confirmed_qty")
    val confirmedQty: Int = 0,

    @ColumnInfo(name = "local_status")
    val localStatus: String = "PENDING"   // PENDING | CONFIRMED | CONFIRMED_OFFLINE | DIVERGENT
)

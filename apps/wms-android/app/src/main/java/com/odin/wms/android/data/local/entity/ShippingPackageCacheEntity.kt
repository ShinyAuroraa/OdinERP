package com.odin.wms.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shipping_packages_cache")
data class ShippingPackageCacheEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "order_id") val orderId: String,
    @ColumnInfo(name = "tenant_id") val tenantId: String,
    @ColumnInfo(name = "tracking_code") val trackingCode: String,
    val weight: Double? = null,
    val status: String = "PENDING"
)

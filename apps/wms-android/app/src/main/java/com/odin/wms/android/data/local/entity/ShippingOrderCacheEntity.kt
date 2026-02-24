package com.odin.wms.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shipping_orders_cache",
    indices = [Index(value = ["tenant_id", "order_number"], unique = true)]
)
data class ShippingOrderCacheEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tenant_id") val tenantId: String,
    @ColumnInfo(name = "order_number") val orderNumber: String,
    val carrier: String,
    @ColumnInfo(name = "vehicle_plate") val vehiclePlate: String?,
    val status: String,
    @ColumnInfo(name = "total_packages") val totalPackages: Int,
    @ColumnInfo(name = "loaded_packages") val loadedPackages: Int = 0,
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Long
)

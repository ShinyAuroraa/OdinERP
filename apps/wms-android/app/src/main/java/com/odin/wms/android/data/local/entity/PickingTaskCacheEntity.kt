package com.odin.wms.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "picking_tasks_cache",
    indices = [Index(value = ["tenant_id", "task_number"], unique = true)]
)
data class PickingTaskCacheEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tenant_id") val tenantId: String,
    @ColumnInfo(name = "task_number") val taskNumber: String,
    @ColumnInfo(name = "picking_order_id") val pickingOrderId: String,
    val status: String,
    val corridor: String?,
    val priority: Int = 0,
    @ColumnInfo(name = "total_items") val totalItems: Int,
    @ColumnInfo(name = "picked_items") val pickedItems: Int = 0,
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Long
)

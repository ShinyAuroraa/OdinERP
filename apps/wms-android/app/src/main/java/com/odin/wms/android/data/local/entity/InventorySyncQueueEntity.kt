package com.odin.wms.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_sync_queue")
data class InventorySyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "tenant_id") val tenantId: String,
    @ColumnInfo(name = "operation_type") val operationType: String, // COUNT_ITEM / DOUBLE_COUNT / SUBMIT_SESSION / TRANSFER
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "item_id") val itemId: String? = null,
    val payload: String, // JSON serialized
    val status: String = "PENDING", // PENDING / SYNCED / SYNC_FAILED / SYNC_CONFLICT
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

package com.odin.wms.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "receiving_sync_queue")
data class ReceivingSyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "tenant_id")
    val tenantId: String,

    @ColumnInfo(name = "operation_type")
    val operationType: String,   // CONFIRM | DIVERGENCE | SIGNATURE | COMPLETE

    @ColumnInfo(name = "order_id")
    val orderId: String,

    @ColumnInfo(name = "item_id")
    val itemId: String? = null,

    val payload: String,         // JSON serializado

    @ColumnInfo(name = "signature_base64")
    val signatureBase64: String? = null,

    val status: String = "PENDING",   // PENDING | SYNCED | SYNC_FAILED | SYNC_CONFLICT

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

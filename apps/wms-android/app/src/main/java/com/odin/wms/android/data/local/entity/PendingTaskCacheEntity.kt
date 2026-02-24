package com.odin.wms.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_task_cache")
data class PendingTaskCacheEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val operationType: String,
    val referenceId: String,
    val status: String,           // PENDING | SYNC_FAILED
    val createdAt: Long,
    val syncAttemptedAt: Long? = null
)

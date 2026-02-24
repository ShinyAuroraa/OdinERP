package com.odin.wms.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.odin.wms.android.domain.model.InventorySession
import com.odin.wms.android.domain.model.InventorySessionStatus
import com.odin.wms.android.domain.model.InventorySessionType

@Entity(
    tableName = "inventory_sessions_cache",
    indices = [Index(value = ["tenant_id", "session_number"], unique = true)]
)
data class InventorySessionCacheEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tenant_id") val tenantId: String,
    @ColumnInfo(name = "session_number") val sessionNumber: String,
    @ColumnInfo(name = "session_type") val sessionType: String,
    val status: String,
    val aisle: String? = null,
    @ColumnInfo(name = "total_items") val totalItems: Int = 0,
    @ColumnInfo(name = "counted_items") val countedItems: Int = 0,
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Long
) {
    fun toDomain(): InventorySession {
        val type = when (sessionType.uppercase()) {
            "FULL" -> InventorySessionType.FULL
            else -> InventorySessionType.CYCLIC
        }
        val sessionStatus = when (status.uppercase()) {
            "COMPLETED" -> InventorySessionStatus.COMPLETED
            "CANCELLED" -> InventorySessionStatus.CANCELLED
            else -> InventorySessionStatus.ACTIVE
        }
        return InventorySession(
            id = id,
            tenantId = tenantId,
            sessionNumber = sessionNumber,
            sessionType = type,
            status = sessionStatus,
            aisle = aisle,
            totalItems = totalItems,
            countedItems = countedItems
        )
    }
}

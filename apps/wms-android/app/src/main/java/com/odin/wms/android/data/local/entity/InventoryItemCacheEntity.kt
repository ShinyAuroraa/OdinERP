package com.odin.wms.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.odin.wms.android.domain.model.InventoryItem
import com.odin.wms.android.domain.model.InventoryItemLocalStatus

@Entity(
    tableName = "inventory_items_cache",
    indices = [
        Index(value = ["session_id", "position"]),
        Index(value = ["session_id", "product_code"])
    ]
)
data class InventoryItemCacheEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "tenant_id") val tenantId: String,
    @ColumnInfo(name = "product_code") val productCode: String,
    val gtin: String,
    val description: String,
    val position: String,
    @ColumnInfo(name = "system_qty") val systemQty: Int,
    @ColumnInfo(name = "counted_qty") val countedQty: Int? = null,
    @ColumnInfo(name = "double_count_qty") val doubleCountQty: Int? = null,
    @ColumnInfo(name = "lot_number") val lotNumber: String? = null,
    @ColumnInfo(name = "first_counter_id") val firstCounterId: String? = null,
    @ColumnInfo(name = "local_status") val localStatus: String = "PENDING"
) {
    fun toDomain(): InventoryItem {
        val status = when (localStatus.uppercase()) {
            "COUNTED" -> InventoryItemLocalStatus.COUNTED
            "COUNTED_VERIFIED" -> InventoryItemLocalStatus.COUNTED_VERIFIED
            "NEEDS_REVIEW" -> InventoryItemLocalStatus.NEEDS_REVIEW
            "OFFLINE_COUNTED" -> InventoryItemLocalStatus.OFFLINE_COUNTED
            else -> InventoryItemLocalStatus.PENDING
        }
        return InventoryItem(
            id = id,
            sessionId = sessionId,
            tenantId = tenantId,
            productCode = productCode,
            gtin = gtin,
            description = description,
            position = position,
            systemQty = systemQty,
            countedQty = countedQty,
            doubleCountQty = doubleCountQty,
            lotNumber = lotNumber,
            firstCounterId = firstCounterId,
            localStatus = status
        )
    }
}

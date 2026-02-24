package com.odin.wms.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.odin.wms.android.domain.model.Transfer
import com.odin.wms.android.domain.model.TransferStatus

@Entity(
    tableName = "transfers_cache",
    indices = [Index(value = ["tenant_id", "id"])]
)
data class TransferCacheEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tenant_id") val tenantId: String,
    @ColumnInfo(name = "source_location") val sourceLocation: String,
    @ColumnInfo(name = "destination_location") val destinationLocation: String,
    @ColumnInfo(name = "product_code") val productCode: String,
    val qty: Int,
    @ColumnInfo(name = "lot_number") val lotNumber: String? = null,
    val status: String, // PENDING / CONFIRMED / CANCELLED
    @ColumnInfo(name = "local_status") val localStatus: String = "PENDING",
    @ColumnInfo(name = "created_at") val createdAt: Long
) {
    fun toDomain(): Transfer {
        val transferStatus = when (status.uppercase()) {
            "CONFIRMED" -> TransferStatus.CONFIRMED
            "CANCELLED" -> TransferStatus.CANCELLED
            else -> TransferStatus.PENDING
        }
        return Transfer(
            id = id,
            tenantId = tenantId,
            sourceLocation = sourceLocation,
            destinationLocation = destinationLocation,
            productCode = productCode,
            qty = qty,
            lotNumber = lotNumber,
            status = transferStatus,
            localStatus = localStatus,
            createdAt = createdAt
        )
    }
}

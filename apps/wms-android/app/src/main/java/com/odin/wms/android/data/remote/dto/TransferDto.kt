package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.odin.wms.android.domain.model.Transfer
import com.odin.wms.android.domain.model.TransferStatus

data class TransferDto(
    @SerializedName("id") val id: String,
    @SerializedName("tenantId") val tenantId: String,
    @SerializedName("sourceLocation") val sourceLocation: String,
    @SerializedName("destinationLocation") val destinationLocation: String,
    @SerializedName("productCode") val productCode: String,
    @SerializedName("qty") val qty: Int,
    @SerializedName("lotNumber") val lotNumber: String? = null,
    @SerializedName("status") val status: String,
    @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis()
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
            localStatus = "PENDING",
            createdAt = createdAt
        )
    }
}

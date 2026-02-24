package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.odin.wms.android.domain.model.ReceivingItem
import com.odin.wms.android.domain.model.ReceivingItemStatus

data class ReceivingItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("orderId") val orderId: String,
    @SerializedName("productCode") val productCode: String,
    @SerializedName("gtin") val gtin: String,
    @SerializedName("description") val description: String,
    @SerializedName("expectedQty") val expectedQty: Int,
    @SerializedName("confirmedQty") val confirmedQty: Int = 0,
    @SerializedName("status") val status: String = "PENDING"
) {
    fun toDomain(): ReceivingItem = ReceivingItem(
        id = id,
        orderId = orderId,
        productCode = productCode,
        gtin = gtin,
        description = description,
        expectedQty = expectedQty,
        confirmedQty = confirmedQty,
        localStatus = when (status) {
            "CONFIRMED" -> ReceivingItemStatus.CONFIRMED
            "DIVERGENT" -> ReceivingItemStatus.DIVERGENT
            else -> ReceivingItemStatus.PENDING
        }
    )
}

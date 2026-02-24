package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.odin.wms.android.domain.model.ReceivingOrder
import com.odin.wms.android.domain.model.ReceivingOrderStatus

data class ReceivingOrderDto(
    @SerializedName("id") val id: String,
    @SerializedName("orderNumber") val orderNumber: String,
    @SerializedName("supplier") val supplier: String,
    @SerializedName("expectedDate") val expectedDate: String,
    @SerializedName("status") val status: String,
    @SerializedName("totalItems") val totalItems: Int = 0,
    @SerializedName("confirmedItems") val confirmedItems: Int = 0,
    @SerializedName("items") val items: List<ReceivingItemDto> = emptyList()
) {
    fun toDomain(): ReceivingOrder = ReceivingOrder(
        id = id,
        orderNumber = orderNumber,
        supplier = supplier,
        expectedDate = expectedDate,
        status = when (status) {
            "IN_PROGRESS" -> ReceivingOrderStatus.IN_PROGRESS
            "COMPLETED"   -> ReceivingOrderStatus.COMPLETED
            "CANCELLED"   -> ReceivingOrderStatus.CANCELLED
            else          -> ReceivingOrderStatus.PENDING
        },
        totalItems = totalItems,
        confirmedItems = confirmedItems,
        items = items.map { it.toDomain() }
    )
}

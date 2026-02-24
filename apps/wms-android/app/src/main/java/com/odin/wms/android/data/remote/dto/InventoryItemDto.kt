package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.odin.wms.android.domain.model.InventoryItem
import com.odin.wms.android.domain.model.InventoryItemLocalStatus

data class InventoryItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("tenantId") val tenantId: String,
    @SerializedName("productCode") val productCode: String,
    @SerializedName("gtin") val gtin: String,
    @SerializedName("description") val description: String,
    @SerializedName("position") val position: String,
    @SerializedName("systemQty") val systemQty: Int,
    @SerializedName("countedQty") val countedQty: Int? = null,
    @SerializedName("doubleCountQty") val doubleCountQty: Int? = null,
    @SerializedName("lotNumber") val lotNumber: String? = null,
    @SerializedName("firstCounterId") val firstCounterId: String? = null,
    @SerializedName("localStatus") val localStatus: String? = null
) {
    fun toDomain(): InventoryItem {
        val status = when (localStatus?.uppercase()) {
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

package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.odin.wms.android.domain.model.InventorySession
import com.odin.wms.android.domain.model.InventorySessionStatus
import com.odin.wms.android.domain.model.InventorySessionType

data class InventorySessionDto(
    @SerializedName("id") val id: String,
    @SerializedName("tenantId") val tenantId: String,
    @SerializedName("sessionNumber") val sessionNumber: String,
    @SerializedName("sessionType") val sessionType: String, // "CYCLIC" or "FULL"
    @SerializedName("status") val status: String,
    @SerializedName("aisle") val aisle: String? = null,
    @SerializedName("totalItems") val totalItems: Int = 0,
    @SerializedName("countedItems") val countedItems: Int = 0
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

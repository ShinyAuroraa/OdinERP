package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.odin.wms.android.domain.model.PickingItem
import com.odin.wms.android.domain.model.PickingItemLocalStatus
import java.time.LocalDate

data class PickingItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("taskId") val taskId: String,
    @SerializedName("productCode") val productCode: String,
    @SerializedName("gtin") val gtin: String,
    @SerializedName("description") val description: String,
    @SerializedName("expectedQty") val expectedQty: Int,
    @SerializedName("pickedQty") val pickedQty: Int = 0,
    @SerializedName("position") val position: String,
    @SerializedName("lotNumber") val lotNumber: String? = null,
    @SerializedName("expiryDate") val expiryDate: String? = null,
    @SerializedName("status") val status: String = "PENDING"
) {
    fun toDomain(): PickingItem {
        val expiry = expiryDate?.let {
            try { LocalDate.parse(it) } catch (e: Exception) { null }
        }
        val localStatus = when (status) {
            "PICKED"         -> PickingItemLocalStatus.PICKED
            "PICKED_OFFLINE" -> PickingItemLocalStatus.PICKED_OFFLINE
            "SKIPPED"        -> PickingItemLocalStatus.SKIPPED
            else             -> PickingItemLocalStatus.PENDING
        }
        return PickingItem(
            id = id,
            taskId = taskId,
            productCode = productCode,
            gtin = gtin,
            description = description,
            expectedQty = expectedQty,
            pickedQty = pickedQty,
            position = position,
            lotNumber = lotNumber,
            expiryDate = expiry,
            localStatus = localStatus
        )
    }
}

package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.odin.wms.android.domain.model.PickingTask
import com.odin.wms.android.domain.model.PickingTaskStatus

data class PickingTaskDto(
    @SerializedName("id") val id: String,
    @SerializedName("taskNumber") val taskNumber: String,
    @SerializedName("pickingOrderId") val pickingOrderId: String,
    @SerializedName("status") val status: String,
    @SerializedName("corridor") val corridor: String? = null,
    @SerializedName("priority") val priority: Int = 0,
    @SerializedName("totalItems") val totalItems: Int,
    @SerializedName("pickedItems") val pickedItems: Int = 0,
    @SerializedName("items") val items: List<PickingItemDto> = emptyList()
) {
    fun toDomain(): PickingTask {
        val taskStatus = when (status) {
            "IN_PROGRESS" -> PickingTaskStatus.IN_PROGRESS
            "COMPLETED"   -> PickingTaskStatus.COMPLETED
            "CANCELLED"   -> PickingTaskStatus.CANCELLED
            else          -> PickingTaskStatus.PICKING_PENDING
        }
        return PickingTask(
            id = id,
            taskNumber = taskNumber,
            pickingOrderId = pickingOrderId,
            status = taskStatus,
            corridor = corridor,
            priority = priority,
            totalItems = totalItems,
            pickedItems = pickedItems,
            items = items.map { it.toDomain() }
        )
    }
}

package com.odin.wms.android.domain.model

data class PickingTask(
    val id: String,
    val taskNumber: String,
    val pickingOrderId: String,
    val status: PickingTaskStatus,
    val corridor: String?,
    val priority: Int,
    val totalItems: Int,
    val pickedItems: Int,
    val items: List<PickingItem> = emptyList()
)

enum class PickingTaskStatus {
    PICKING_PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

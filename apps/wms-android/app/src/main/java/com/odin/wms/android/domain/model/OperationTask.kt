package com.odin.wms.android.domain.model

data class OperationTask(
    val id: String,
    val type: OperationType,
    val referenceId: String,
    val status: TaskStatus,
    val createdAt: Long
)

enum class OperationType(val displayName: String) {
    PICKING("Picking"),
    RECEIVING("Recebimento"),
    INVENTORY("Inventário"),
    TRANSFER("Transferência")
}

enum class TaskStatus {
    PENDING,
    SYNC_FAILED
}

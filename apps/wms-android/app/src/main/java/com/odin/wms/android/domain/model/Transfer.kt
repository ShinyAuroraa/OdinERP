package com.odin.wms.android.domain.model

enum class TransferStatus {
    PENDING, CONFIRMED, CANCELLED
}

data class Transfer(
    val id: String,
    val tenantId: String,
    val sourceLocation: String,
    val destinationLocation: String,
    val productCode: String,
    val qty: Int,
    val lotNumber: String? = null,
    val status: TransferStatus,
    val localStatus: String = "PENDING",
    val createdAt: Long
)

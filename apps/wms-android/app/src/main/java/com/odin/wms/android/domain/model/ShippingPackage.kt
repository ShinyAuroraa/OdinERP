package com.odin.wms.android.domain.model

data class ShippingPackage(
    val id: String,
    val orderId: String,
    val trackingCode: String,
    val weight: Double?,
    val status: ShippingPackageStatus
)

enum class ShippingPackageStatus {
    PENDING,
    LOADED
}

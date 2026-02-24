package com.odin.wms.android.domain.model

data class ShippingOrder(
    val id: String,
    val orderNumber: String,
    val carrier: String,
    val vehiclePlate: String?,
    val status: ShippingOrderStatus,
    val packages: List<ShippingPackage> = emptyList(),
    val totalPackages: Int,
    val loadedPackages: Int
)

enum class ShippingOrderStatus {
    SHIPPING_PENDING,
    IN_PROGRESS,
    COMPLETED
}

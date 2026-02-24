package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.odin.wms.android.domain.model.ShippingOrder
import com.odin.wms.android.domain.model.ShippingOrderStatus

data class ShippingOrderDto(
    @SerializedName("id") val id: String,
    @SerializedName("orderNumber") val orderNumber: String,
    @SerializedName("carrier") val carrier: String,
    @SerializedName("vehiclePlate") val vehiclePlate: String? = null,
    @SerializedName("status") val status: String,
    @SerializedName("packages") val packages: List<ShippingPackageDto> = emptyList(),
    @SerializedName("totalPackages") val totalPackages: Int,
    @SerializedName("loadedPackages") val loadedPackages: Int = 0
) {
    fun toDomain(): ShippingOrder {
        val orderStatus = when (status) {
            "IN_PROGRESS" -> ShippingOrderStatus.IN_PROGRESS
            "COMPLETED"   -> ShippingOrderStatus.COMPLETED
            else          -> ShippingOrderStatus.SHIPPING_PENDING
        }
        return ShippingOrder(
            id = id,
            orderNumber = orderNumber,
            carrier = carrier,
            vehiclePlate = vehiclePlate,
            status = orderStatus,
            packages = packages.map { it.toDomain() },
            totalPackages = totalPackages,
            loadedPackages = loadedPackages
        )
    }
}

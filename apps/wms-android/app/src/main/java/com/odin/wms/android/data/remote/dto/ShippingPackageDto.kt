package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.odin.wms.android.domain.model.ShippingPackage
import com.odin.wms.android.domain.model.ShippingPackageStatus

data class ShippingPackageDto(
    @SerializedName("id") val id: String,
    @SerializedName("orderId") val orderId: String,
    @SerializedName("trackingCode") val trackingCode: String,
    @SerializedName("weight") val weight: Double? = null,
    @SerializedName("status") val status: String = "PENDING"
) {
    fun toDomain(): ShippingPackage {
        val pkgStatus = when (status) {
            "LOADED" -> ShippingPackageStatus.LOADED
            else     -> ShippingPackageStatus.PENDING
        }
        return ShippingPackage(
            id = id,
            orderId = orderId,
            trackingCode = trackingCode,
            weight = weight,
            status = pkgStatus
        )
    }
}

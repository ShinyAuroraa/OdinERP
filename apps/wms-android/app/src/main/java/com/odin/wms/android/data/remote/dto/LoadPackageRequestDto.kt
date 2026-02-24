package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LoadPackageRequestDto(
    @SerializedName("trackingCode") val trackingCode: String,
    @SerializedName("vehiclePlate") val vehiclePlate: String? = null
)

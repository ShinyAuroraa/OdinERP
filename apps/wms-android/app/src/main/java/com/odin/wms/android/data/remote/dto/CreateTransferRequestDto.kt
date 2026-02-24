package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CreateTransferRequestDto(
    @SerializedName("sourceLocation") val sourceLocation: String,
    @SerializedName("destinationLocation") val destinationLocation: String,
    @SerializedName("productCode") val productCode: String,
    @SerializedName("qty") val qty: Int,
    @SerializedName("lotNumber") val lotNumber: String? = null
)

package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ConfirmItemRequestDto(
    @SerializedName("quantity") val quantity: Int,
    @SerializedName("lotNumber") val lotNumber: String?,
    @SerializedName("expiryDate") val expiryDate: String?,
    @SerializedName("serialNumber") val serialNumber: String?
)

package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ConfirmPickItemRequestDto(
    @SerializedName("quantity") val quantity: Int,
    @SerializedName("lotNumber") val lotNumber: String? = null,
    @SerializedName("position") val position: String,
    @SerializedName("serialNumber") val serialNumber: String? = null
)

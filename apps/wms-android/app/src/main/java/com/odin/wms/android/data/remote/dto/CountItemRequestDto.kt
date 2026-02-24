package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CountItemRequestDto(
    @SerializedName("productCode") val productCode: String,
    @SerializedName("countedQty") val countedQty: Int,
    @SerializedName("lotNumber") val lotNumber: String?,
    @SerializedName("position") val position: String
)

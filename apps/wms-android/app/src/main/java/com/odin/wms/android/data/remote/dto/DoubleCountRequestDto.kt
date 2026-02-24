package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName

data class DoubleCountRequestDto(
    @SerializedName("countedQty") val countedQty: Int,
    @SerializedName("counterId") val counterId: String
)

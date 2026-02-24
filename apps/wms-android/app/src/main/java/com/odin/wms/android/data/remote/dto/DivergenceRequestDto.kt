package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName

data class DivergenceRequestDto(
    @SerializedName("type") val type: String,
    @SerializedName("actualQty") val actualQty: Int,
    @SerializedName("notes") val notes: String,
    @SerializedName("photos") val photos: List<String> = emptyList()
)

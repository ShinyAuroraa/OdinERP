package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName

data class StockSummaryDto(
    @SerializedName("tenantId")         val tenantId: String,
    @SerializedName("totalAvailable")   val totalAvailable: Int,
    @SerializedName("pendingPicking")   val pendingPicking: Int,
    @SerializedName("pendingReceiving") val pendingReceiving: Int,
    @SerializedName("updatedAt")        val updatedAt: Long
)

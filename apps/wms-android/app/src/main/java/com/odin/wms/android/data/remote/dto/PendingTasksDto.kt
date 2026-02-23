package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PendingTasksDto(
    @SerializedName("pickingCount")   val pickingCount: Int,
    @SerializedName("receivingCount") val receivingCount: Int,
    @SerializedName("inventoryCount") val inventoryCount: Int,
    @SerializedName("transferCount")  val transferCount: Int
)

package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName

data class WarehouseDto(
    @SerializedName("id")       val id: String,
    @SerializedName("name")     val name: String,
    @SerializedName("tenantId") val tenantId: String,
    @SerializedName("active")   val active: Boolean
)

package com.odin.wms.android.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CompleteReceivingDto(
    @SerializedName("signatureBase64") val signatureBase64: String,
    @SerializedName("completedAt") val completedAt: String
)

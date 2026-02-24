package com.odin.wms.android.domain.model

data class DivergenceReport(
    val itemId: String,
    val type: DivergenceType,
    val actualQty: Int = 0,
    val notes: String = "",
    val photoBase64List: List<String> = emptyList()
)

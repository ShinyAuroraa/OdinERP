package com.odin.wms.android.domain.model

enum class DivergenceType(val displayName: String) {
    SHORTAGE("Falta"),
    EXCESS("Excesso"),
    DAMAGE("Avaria"),
    WRONG_PRODUCT("Produto Errado")
}

package com.odin.wms.android.domain.model

enum class WmsRole {
    WMS_OPERATOR,
    WMS_SUPERVISOR,
    WMS_ADMIN;

    val displayName: String
        get() = when (this) {
            WMS_OPERATOR -> "Operador"
            WMS_SUPERVISOR -> "Supervisor"
            WMS_ADMIN -> "Administrador"
        }

    fun canAccessReports(): Boolean = this == WMS_SUPERVISOR || this == WMS_ADMIN
}

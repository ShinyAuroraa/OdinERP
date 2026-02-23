package com.odin.wms.android.domain.model

data class User(
    val id: String,
    val tenantId: String,
    val username: String,
    val roles: List<WmsRole>
) {
    val primaryRole: WmsRole
        get() = when {
            roles.contains(WmsRole.WMS_ADMIN) -> WmsRole.WMS_ADMIN
            roles.contains(WmsRole.WMS_SUPERVISOR) -> WmsRole.WMS_SUPERVISOR
            else -> WmsRole.WMS_OPERATOR
        }

    val displayRole: String get() = primaryRole.displayName
}

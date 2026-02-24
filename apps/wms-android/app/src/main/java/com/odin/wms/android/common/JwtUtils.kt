package com.odin.wms.android.common

import android.util.Base64
import com.odin.wms.android.domain.model.WmsRole
import org.json.JSONObject

object JwtUtils {

    fun extractClaims(token: String): Map<String, Any> {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return emptyMap()
            val paddedPayload = parts[1].padEnd((parts[1].length + 3) / 4 * 4, '=')
            val payloadBytes = Base64.decode(paddedPayload, Base64.URL_SAFE)
            val json = JSONObject(String(payloadBytes))
            val map = mutableMapOf<String, Any>()
            json.keys().forEach { key -> map[key] = json.get(key) }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun extractRoles(claims: Map<String, Any>): List<WmsRole> {
        return try {
            val realmAccess = claims["realm_access"] as? JSONObject ?: return emptyList()
            val rolesArray = realmAccess.getJSONArray("roles")
            (0 until rolesArray.length()).mapNotNull { i ->
                try {
                    WmsRole.valueOf(rolesArray.getString(i))
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun extractUsername(claims: Map<String, Any>): String {
        return (claims["preferred_username"] as? String)
            ?: (claims["sub"] as? String)
            ?: "Operador"
    }

    fun extractTenantId(claims: Map<String, Any>): String {
        return (claims["tenant_id"] as? String) ?: ""
    }
}

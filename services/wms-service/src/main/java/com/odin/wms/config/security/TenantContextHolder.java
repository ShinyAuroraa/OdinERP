package com.odin.wms.config.security;

import java.util.UUID;

/**
 * Thread-local holder for the current request's tenant identifier.
 *
 * <p>Populated by {@link TenantContextFilter} at the beginning of each authenticated
 * request and cleared in a {@code finally} block to prevent ThreadLocal leaks in
 * application server thread pools.
 *
 * <p>Usage in services (Wave 2+):
 * <pre>{@code
 * UUID tenantId = TenantContextHolder.getTenantId();
 * }</pre>
 */
public final class TenantContextHolder {

    private static final ThreadLocal<UUID> TENANT_ID = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setTenantId(UUID tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static UUID getTenantId() {
        return TENANT_ID.get();
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}

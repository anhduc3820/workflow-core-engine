package workflow.core.engine.infrastructure.tenant;

import lombok.extern.slf4j.Slf4j;

/**
 * Tenant Context Holder (v2)
 * Thread-local storage for current tenant ID
 * Used for multi-tenant isolation throughout the application
 */
@Slf4j
public class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = ThreadLocal.withInitial(() -> "default");

    public static void setTenantId(String tenantId) {
        log.debug("Setting tenant context to: {}", tenantId);
        CURRENT_TENANT.set(tenantId != null ? tenantId : "default");
    }

    public static String getTenantId() {
        String tenantId = CURRENT_TENANT.get();
        log.trace("Retrieved tenant context: {}", tenantId);
        return tenantId;
    }

    public static void clear() {
        log.debug("Clearing tenant context");
        CURRENT_TENANT.remove();
    }

    /**
     * Execute a runnable with a specific tenant context
     */
    public static void executeWithTenant(String tenantId, Runnable action) {
        String previousTenant = getTenantId();
        try {
            setTenantId(tenantId);
            action.run();
        } finally {
            setTenantId(previousTenant);
        }
    }

    /**
     * Execute a callable with a specific tenant context
     */
    public static <T> T executeWithTenant(String tenantId, java.util.concurrent.Callable<T> action) throws Exception {
        String previousTenant = getTenantId();
        try {
            setTenantId(tenantId);
            return action.call();
        } finally {
            setTenantId(previousTenant);
        }
    }
}


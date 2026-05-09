package com.secondhand.platform.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class AdminAccessGuard {
    private static final String ADMIN_MODE_HEADER = "X-Admin-Mode";
    private static final String ENABLED = "enabled";

    private final CurrentUserResolver currentUserResolver;

    public AdminAccessGuard(CurrentUserResolver currentUserResolver) {
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * Phase dev/admin placeholder: real RBAC is not available yet.
     * Admin APIs must explicitly opt in with X-Admin-Mode=enabled and still pass current-user resolution.
     */
    public long requireAdmin(HttpServletRequest request) {
        if (request == null || !ENABLED.equalsIgnoreCase(request.getHeader(ADMIN_MODE_HEADER))) {
            throw new SecurityException("admin access required");
        }
        return currentUserResolver.resolve(request);
    }
}

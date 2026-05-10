package com.secondhand.platform.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AdminAccessGuard {
    private final CurrentUserResolver currentUserResolver;
    private final JdbcTemplate jdbcTemplate;

    public AdminAccessGuard(CurrentUserResolver currentUserResolver, JdbcTemplate jdbcTemplate) {
        this.currentUserResolver = currentUserResolver;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Admin APIs require a resolved operator identity and an enabled persisted permission.
     * Legacy development opt-in headers are no longer authorization factors.
     */
    public long requireAdmin(HttpServletRequest request) {
        return requireAdmin(request, "audit:read");
    }

    public long requireAdminSession(HttpServletRequest request) {
        if (request == null) {
            throw new SecurityException("admin access required");
        }
        long adminUserId = currentUserResolver.resolve(request);
        if (!hasActiveSession(adminUserId, request.getHeader("X-Admin-Session"))) {
            throw new SecurityException("admin session required");
        }
        return adminUserId;
    }

    public long requireAdmin(HttpServletRequest request, String permissionCode) {
        if (request == null) {
            throw new SecurityException("admin access required");
        }
        long adminUserId = currentUserResolver.resolve(request);
        if (!hasActiveSession(adminUserId, request.getHeader("X-Admin-Session"))) {
            throw new SecurityException("admin session required");
        }
        if (!hasPermission(adminUserId, permissionCode)) {
            throw new SecurityException("admin permission required");
        }
        return adminUserId;
    }

    private boolean hasActiveSession(long adminUserId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        String normalizedSessionId = sessionId.trim();
        if (!normalizedSessionId.matches("^adm_[a-fA-F0-9]{32}$")) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM admin_session
                WHERE user_id = ? AND session_id = ? AND revoked = FALSE AND expires_at > CURRENT_TIMESTAMP
                """, Integer.class, adminUserId, normalizedSessionId);
        return count != null && count > 0;
    }

    private boolean hasPermission(long adminUserId, String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM admin_user_permission p
                INNER JOIN user_account u ON u.id = p.user_id AND u.status = 'ACTIVE'
                WHERE p.user_id = ? AND p.permission_code = ? AND p.enabled = TRUE
                """, Integer.class, adminUserId, permissionCode);
        return count != null && count > 0;
    }
}

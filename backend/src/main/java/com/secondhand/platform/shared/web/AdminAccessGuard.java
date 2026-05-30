package com.secondhand.platform.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AdminAccessGuard {
    private final JdbcTemplate jdbcTemplate;

    public AdminAccessGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long requireAdmin(HttpServletRequest request) {
        return requireAdmin(request, "audit:read");
    }

    public long requireAdminSession(HttpServletRequest request) {
        long adminUserId = resolveAdminUserId(request);
        if (!hasActiveSession(adminUserId, request.getHeader("X-Admin-Session"))) {
            throw new SecurityException("admin session required");
        }
        return adminUserId;
    }

    public long requireAdmin(HttpServletRequest request, String permissionCode) {
        long adminUserId = resolveAdminUserId(request);
        if (!hasActiveSession(adminUserId, request.getHeader("X-Admin-Session"))) {
            throw new SecurityException("admin session required");
        }
        if (!hasPermission(adminUserId, permissionCode)) {
            throw new SecurityException("admin permission required");
        }
        return adminUserId;
    }

    private long resolveAdminUserId(HttpServletRequest request) {
        if (request == null) {
            throw new SecurityException("admin access required");
        }
        String headerUserId = request.getHeader("X-User-Id");
        if (headerUserId == null || !headerUserId.trim().matches("^[1-9]\\d*$")) {
            throw new SecurityException("admin access required");
        }
        return Long.parseLong(headerUserId.trim());
    }

    private boolean hasActiveSession(long adminUserId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        String normalizedSessionId = sessionId.trim();
        if (!normalizedSessionId.matches("^adm_[a-fA-F0-9]{32}$")) {
            return false;
        }
        List<Long> userIds = jdbcTemplate.queryForList("""
                SELECT s.user_id
                FROM admin_session s
                INNER JOIN user_account u ON u.id = s.user_id AND u.status = 'ACTIVE'
                WHERE s.user_id = ? AND s.session_id = ? AND s.revoked = FALSE AND s.expires_at > CURRENT_TIMESTAMP
                """, Long.class, adminUserId, normalizedSessionId);
        return !userIds.isEmpty();
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

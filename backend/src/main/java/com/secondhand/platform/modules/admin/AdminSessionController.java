package com.secondhand.platform.modules.admin;

import com.secondhand.platform.modules.auth.LoginRequest;
import com.secondhand.platform.shared.kernel.Result;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/session")
public class AdminSessionController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminSessionController.class);
    private static final Set<String> ALLOWED_PERMISSIONS = Set.of(
            "audit:read",
            "audit:review",
            "finance:read",
            "finance:review",
            "user:read",
            "user:risk-control",
            "order:read",
            "after-sales:read",
            "after-sales:review",
            "system:config",
            "audit:log",
            "operator:grant"
    );
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final int LOGIN_LOCK_MINUTES = 15;

    private final JdbcTemplate jdbcTemplate;

    public AdminSessionController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/login")
    public Result<AdminSessionResponse> login(@RequestBody(required = false) LoginRequest request) {
        if (request == null || request.getMobile() == null || request.getMobile().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new SecurityException("admin access required");
        }
        String mobile = request.getMobile().trim();
        try {
            rejectLockedAdminLogin(mobile);
            AdminLoginRow row = findActiveUser(mobile);
            if (row == null || !verifyPassword(request.getPassword(), row.passwordHash(), row.userId())) {
                recordFailedAdminLogin(mobile);
                throw new SecurityException("admin access required");
            }
            List<String> permissions = listPermissions(row.userId());
            if (permissions.isEmpty()) {
                recordFailedAdminLogin(mobile);
                throw new SecurityException("admin access required");
            }
            clearAdminLoginAttempts(mobile);
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(8);
            String sessionId = issueSession(row.userId(), expiresAt);
            return Result.ok(new AdminSessionResponse(row.nickname(), String.valueOf(row.userId()), permissions, sessionId, expiresAt.toString()));
        } catch (DataAccessException ex) {
            LOGGER.error("Admin login persistence failed for mobile {}", maskMobile(mobile), ex);
            throw ex;
        }
    }

    @PostMapping("/logout")
    public Result<Boolean> logout(@RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
                                  @RequestHeader(value = "X-Admin-Session", required = false) String sessionIdHeader) {
        Long userId = parsePositiveUserId(userIdHeader);
        String sessionId = normalizeSessionId(sessionIdHeader);
        if (userId == null || sessionId == null) {
            throw new SecurityException("admin session required");
        }
        int updated = jdbcTemplate.update("""
                UPDATE admin_session
                SET revoked = TRUE
                WHERE user_id = ? AND session_id = ? AND revoked = FALSE
                """, userId, sessionId);
        if (updated <= 0) {
            throw new SecurityException("admin session required");
        }
        return Result.ok(Boolean.TRUE);
    }

    @GetMapping("/me")
    public Result<AdminSessionResponse> me(@RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
                                           @RequestHeader(value = "X-Admin-Session", required = false) String sessionIdHeader) {
        Long userId = parsePositiveUserId(userIdHeader);
        String sessionId = normalizeSessionId(sessionIdHeader);
        if (userId == null || sessionId == null) {
            throw new SecurityException("admin session required");
        }
        AdminSessionRow session = findActiveSession(userId, sessionId);
        if (session == null) {
            throw new SecurityException("admin session required");
        }
        List<String> permissions = listPermissions(userId);
        if (permissions.isEmpty()) {
            throw new SecurityException("admin access required");
        }
        return Result.ok(new AdminSessionResponse(session.nickname(), String.valueOf(userId), permissions, sessionId, session.expiresAt().toString()));
    }

    private Long parsePositiveUserId(String userIdHeader) {
        if (userIdHeader == null || !userIdHeader.trim().matches("^[1-9]\\d*$")) {
            return null;
        }
        return Long.parseLong(userIdHeader.trim());
    }

    private String normalizeSessionId(String sessionIdHeader) {
        if (sessionIdHeader == null) {
            return null;
        }
        String sessionId = sessionIdHeader.trim();
        return sessionId.matches("^adm_[a-fA-F0-9]{32}$") ? sessionId : null;
    }

    private String issueSession(Long userId, LocalDateTime expiresAt) {
        String sessionId = "adm_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("""
                INSERT INTO admin_session (session_id, user_id, expires_at, revoked, created_at)
                VALUES (?, ?, ?, FALSE, CURRENT_TIMESTAMP)
                """, sessionId, userId, expiresAt);
        return sessionId;
    }

    private AdminLoginRow findActiveUser(String mobile) {
        List<AdminLoginRow> rows = jdbcTemplate.query("""
                SELECT id, password_hash, nickname
                FROM user_account
                WHERE phone = ? AND status = 'ACTIVE'
                """, (rs, rowNum) -> new AdminLoginRow(
                rs.getLong("id"),
                rs.getString("password_hash"),
                rs.getString("nickname")
        ), mobile);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void rejectLockedAdminLogin(String mobile) {
        List<LocalDateTime> lockedUntilRows = jdbcTemplate.query("""
                SELECT locked_until
                FROM admin_login_attempt
                WHERE mobile = ? AND locked_until IS NOT NULL
                """, (rs, rowNum) -> rs.getTimestamp("locked_until").toLocalDateTime(), mobile);
        if (!lockedUntilRows.isEmpty() && lockedUntilRows.get(0).isAfter(LocalDateTime.now())) {
            throw new SecurityException("admin access required");
        }
    }

    private void recordFailedAdminLogin(String mobile) {
        if (incrementFailedAdminLogin(mobile) > 0) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO admin_login_attempt (mobile, failed_count, locked_until, updated_at)
                    VALUES (?, 1, NULL, CURRENT_TIMESTAMP)
                    """, mobile);
        } catch (DuplicateKeyException ignored) {
            incrementFailedAdminLogin(mobile);
        }
    }

    private int incrementFailedAdminLogin(String mobile) {
        return jdbcTemplate.update("""
                UPDATE admin_login_attempt
                SET failed_count = failed_count + 1,
                    locked_until = CASE WHEN failed_count + 1 >= ? THEN ? ELSE locked_until END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE mobile = ?
                """, MAX_FAILED_LOGIN_ATTEMPTS, LocalDateTime.now().plusMinutes(LOGIN_LOCK_MINUTES), mobile);
    }

    private void clearAdminLoginAttempts(String mobile) {
        jdbcTemplate.update("DELETE FROM admin_login_attempt WHERE mobile = ?", mobile);
    }

    private AdminSessionRow findActiveSession(Long userId, String sessionId) {
        List<AdminSessionRow> rows = jdbcTemplate.query("""
                SELECT u.nickname, s.expires_at
                FROM admin_session s
                INNER JOIN user_account u ON u.id = s.user_id AND u.status = 'ACTIVE'
                WHERE s.user_id = ? AND s.session_id = ? AND s.revoked = FALSE AND s.expires_at > CURRENT_TIMESTAMP
                """, (rs, rowNum) -> new AdminSessionRow(
                rs.getString("nickname"),
                rs.getTimestamp("expires_at").toLocalDateTime()
        ), userId, sessionId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<String> listPermissions(Long userId) {
        return jdbcTemplate.query("""
                SELECT permission_code
                FROM admin_user_permission
                WHERE user_id = ? AND enabled = TRUE
                ORDER BY permission_code
                """, (rs, rowNum) -> rs.getString("permission_code"), userId)
                .stream()
                .filter(ALLOWED_PERMISSIONS::contains)
                .toList();
    }

    private boolean verifyPassword(String password, String storedHash, Long userId) {
        if (storedHash == null || !storedHash.startsWith("pbkdf2$")) {
            LOGGER.warn("Admin login rejected because stored password hash is missing or unsupported for userId={}", userId);
            return false;
        }
        String[] parts = storedHash.split("\\$", 4);
        if (parts.length != 4) {
            LOGGER.warn("Admin login rejected because stored PBKDF2 hash format is invalid for userId={}", userId);
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            String candidate = pbkdf2(password.trim(), salt, iterations);
            return MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8), parts[3].getBytes(StandardCharsets.UTF_8));
        } catch (NumberFormatException ex) {
            LOGGER.warn("Admin login rejected because stored PBKDF2 iteration count is invalid for userId={}", userId, ex);
            return false;
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("Admin login rejected because stored PBKDF2 salt or parameters are invalid for userId={}", userId, ex);
            return false;
        }
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() <= 4) {
            return "****";
        }
        return "****" + mobile.substring(mobile.length() - 4);
    }

    private String pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(encoded);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("PBKDF2 not available", ex);
        }
    }

    private record AdminLoginRow(Long userId, String passwordHash, String nickname) {
    }

    private record AdminSessionRow(String nickname, LocalDateTime expiresAt) {
    }

    public record AdminSessionResponse(String username, String userId, List<String> permissions, String sessionId, String expiresAt) {
    }
}

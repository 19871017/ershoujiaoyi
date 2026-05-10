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
import java.util.UUID;
import java.util.Set;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/session")
public class AdminSessionController {
    private static final Set<String> ALLOWED_PERMISSIONS = Set.of(
            "audit:read",
            "audit:review",
            "finance:read",
            "finance:review",
            "user:read",
            "order:read",
            "after-sales:read",
            "after-sales:review",
            "system:config",
            "audit:log"
    );

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
        AdminLoginRow row = findActiveUser(request.getMobile().trim());
        if (row == null || !verifyPassword(request.getPassword(), row.passwordHash())) {
            throw new SecurityException("admin access required");
        }
        List<String> permissions = listPermissions(row.userId());
        if (permissions.isEmpty()) {
            throw new SecurityException("admin access required");
        }
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(8);
        String sessionId = issueSession(row.userId(), expiresAt);
        return Result.ok(new AdminSessionResponse(row.nickname(), String.valueOf(row.userId()), permissions, sessionId, expiresAt.toString()));
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

    private boolean verifyPassword(String password, String storedHash) {
        if (storedHash == null || !storedHash.startsWith("pbkdf2$")) {
            return false;
        }
        String[] parts = storedHash.split("\\$", 4);
        if (parts.length != 4) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            String candidate = pbkdf2(password.trim(), salt, iterations);
            return MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8), parts[3].getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            return false;
        }
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

    public record AdminSessionResponse(String username, String userId, List<String> permissions, String sessionId, String expiresAt) {
    }
}

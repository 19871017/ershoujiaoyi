package com.secondhand.platform.modules.auth.application;

import com.secondhand.platform.modules.auth.AuthTokenResponse;
import com.secondhand.platform.modules.auth.LoginRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthApplicationService {
    private static final String MOBILE_PATTERN = "^1[3-9]\\d{9}$";
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final JdbcTemplate jdbcTemplate;

    public AuthApplicationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        validateLoginRequest(request);
        String normalizedMobile = request.getMobile().trim();
        String passwordHash = passwordHash(request.getPassword());
        UserAuthRow user = findByMobile(normalizedMobile);
        if (user == null) {
            user = createUser(normalizedMobile, passwordHash);
        } else if (!Objects.equals(user.passwordHash(), passwordHash)) {
            throw new IllegalArgumentException("mobile or password invalid");
        }
        return new AuthTokenResponse(buildToken("dev-access", user.userNo()), buildToken("dev-refresh", user.userNo()));
    }

    private UserAuthRow createUser(String mobile, String passwordHash) {
        String userNo = "U" + sha256("user:" + mobile).substring(0, 18).toUpperCase(Locale.ROOT);
        String nickname = "小原圈用户" + mobile.substring(mobile.length() - 4);
        jdbcTemplate.update("""
                INSERT INTO user_account (user_no, phone, password_hash, nickname, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userNo, mobile, passwordHash, nickname);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, mobile);
        jdbcTemplate.update("""
                INSERT INTO user_profile (user_id, gender, city, bio, identity_status, main_role, video_identity_status, video_verified, created_at, updated_at)
                VALUES (?, NULL, NULL, NULL, 'UNVERIFIED', 'BUYER', 'UNVERIFIED', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId);
        return new UserAuthRow(userId, userNo, mobile, passwordHash, nickname, "ACTIVE");
    }

    private UserAuthRow findByMobile(String mobile) {
        List<UserAuthRow> rows = jdbcTemplate.query("""
                SELECT id, user_no, phone, password_hash, nickname, status
                FROM user_account
                WHERE phone = ?
                """, (rs, rowNum) -> new UserAuthRow(
                rs.getLong("id"),
                rs.getString("user_no"),
                rs.getString("phone"),
                rs.getString("password_hash"),
                rs.getString("nickname"),
                rs.getString("status")
        ), mobile);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void validateLoginRequest(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("login request required");
        }
        if (request.getMobile() == null || request.getMobile().isBlank()) {
            throw new IllegalArgumentException("mobile required");
        }
        if (!request.getMobile().trim().matches(MOBILE_PATTERN)) {
            throw new IllegalArgumentException("mobile invalid");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("password required");
        }
        if (request.getPassword().trim().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("password too weak");
        }
    }

    private String buildToken(String tokenType, String userNo) {
        // SECURITY PLACEHOLDER: deterministic dev token for API wiring only.
        // Replace with signed JWT/session issuance and server-side token storage before production.
        return tokenType + '-' + sha256(tokenType + ':' + userNo).substring(0, 40);
    }

    private String passwordHash(String password) {
        // SECURITY PLACEHOLDER: unsalted SHA-256 is only an MVP dev fallback.
        // Replace with BCrypt/Argon2 and password policy before production.
        return "sha256$" + sha256("小原圈:" + password.trim());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(Objects.toString(value, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(encoded.length * 2);
            for (byte b : encoded) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private record UserAuthRow(Long id, String userNo, String mobile, String passwordHash, String nickname, String status) {
    }
}

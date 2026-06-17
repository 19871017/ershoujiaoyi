package com.secondhand.platform.modules.auth.application;

import com.secondhand.platform.modules.auth.AuthTokenResponse;
import com.secondhand.platform.modules.auth.LoginRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthApplicationService {
    private static final String MOBILE_PATTERN = "^1[3-9]\\d{9}$";
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int PBKDF2_KEY_LENGTH = 256;
    private static final int PASSWORD_SALT_BYTES = 16;
    private static final List<String> ALLOWED_GENDERS = List.of("god", "goddess");
    private static final String DEFAULT_GOD_AVATAR_URL = "/assets/profile/default-avatar-god.png";
    private static final String DEFAULT_GODDESS_AVATAR_URL = "/assets/profile/default-avatar-goddess.png";
    private static final String REGISTRATION_IP_LIMIT_KEY_PREFIX = "auth.registration.ip.";
    private static final DateTimeFormatter REGISTRATION_DAY_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final IpLocationResolver ipLocationResolver;

    public AuthApplicationService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new DefaultIpLocationResolver());
    }

    @Autowired
    public AuthApplicationService(JdbcTemplate jdbcTemplate, IpLocationResolver ipLocationResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.ipLocationResolver = ipLocationResolver;
        ensureLoginRecordTable();
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        return login(request, null);
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request, String clientIp) {
        validateLoginRequest(request);
        String normalizedMobile = request.getMobile().trim();
        UserAuthRow user = findByMobile(normalizedMobile);
        if (user == null || !"ACTIVE".equals(user.status()) || !verifyPassword(request.getPassword(), user.passwordHash())) {
            throw new IllegalArgumentException("mobile or password invalid");
        }
        return issueSession(user.id(), clientIp);
    }

    @Transactional
    public AuthTokenResponse register(LoginRequest request, String clientIp) {
        validateLoginRequest(request);
        String normalizedMobile = request.getMobile().trim();
        String normalizedGender = normalizeRegistrationGender(request.getGender());
        UserAuthRow existingUser = findByMobile(normalizedMobile);
        if (existingUser != null) {
            throw new IllegalStateException("mobile already registered");
        }
        recordDailyRegistration(clientIp);
        UserAuthRow user = createUser(normalizedMobile, passwordHash(request.getPassword()), normalizedGender);
        return issueSession(user.id(), clientIp);
    }

    private UserAuthRow createUser(String mobile, String passwordHash, String gender) {
        String userNo = "U" + sha256("user:" + mobile).substring(0, 18).toUpperCase(Locale.ROOT);
        String nickname = "小原圈用户" + mobile.substring(mobile.length() - 4);
        jdbcTemplate.update("""
                INSERT INTO user_account (user_no, phone, password_hash, nickname, avatar_url, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userNo, mobile, passwordHash, nickname, defaultAvatarUrl(gender));
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, mobile);
        jdbcTemplate.update("""
                INSERT INTO user_profile (user_id, gender, city, bio, identity_status, main_role, video_identity_status, video_verified, created_at, updated_at)
                VALUES (?, ?, NULL, NULL, 'UNVERIFIED', 'BUYER', 'UNVERIFIED', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId, gender);
        return new UserAuthRow(userId, userNo, mobile, passwordHash, nickname, "ACTIVE");
    }

    private String defaultAvatarUrl(String gender) {
        return "god".equals(gender) ? DEFAULT_GOD_AVATAR_URL : DEFAULT_GODDESS_AVATAR_URL;
    }

    private String normalizeRegistrationGender(String gender) {
        if (gender == null || gender.isBlank()) {
            throw new IllegalArgumentException("gender required");
        }
        String normalized = gender.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_GENDERS.contains(normalized)) {
            throw new IllegalArgumentException("gender invalid");
        }
        return normalized;
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

    private void recordDailyRegistration(String clientIp) {
        String key = registrationLimitKey(clientIp);
        try {
            jdbcTemplate.update("""
                    INSERT INTO system_config (config_key, config_value, config_type, config_group, remark, created_at, updated_at)
                    VALUES (?, '1', 'number', 'auth-registration-limit', 'one account per client ip per day', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, key);
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("daily registration limit exceeded", exception);
        }
    }

    private String registrationLimitKey(String clientIp) {
        String normalizedIp = normalizeClientIp(clientIp);
        String ipHash = sha256("registration-ip:" + normalizedIp).substring(0, 32);
        String day = LocalDateTime.now().format(REGISTRATION_DAY_FORMAT);
        return REGISTRATION_IP_LIMIT_KEY_PREFIX + day + "." + ipHash;
    }

    private String normalizeClientIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        return clientIp.trim().toLowerCase(Locale.ROOT);
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

    private AuthTokenResponse issueSession(Long userId, String clientIp) {
        String accessToken = opaqueToken();
        String refreshToken = opaqueToken();
        jdbcTemplate.update("""
                INSERT INTO user_session (access_token, refresh_token, user_id, expires_at, revoked)
                VALUES (?, ?, ?, ?, FALSE)
                """, accessToken, refreshToken, userId, LocalDateTime.now().plusDays(30));
        recordLogin(userId, clientIp);
        return new AuthTokenResponse(accessToken, refreshToken);
    }

    private void recordLogin(Long userId, String clientIp) {
        String normalizedIp = normalizeClientIp(clientIp);
        jdbcTemplate.update("""
                INSERT INTO user_login_record (user_id, login_ip_hash, ip_location, device_name, login_at, created_at)
                VALUES (?, ?, ?, '当前账号登录', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId, sha256("login-ip:" + normalizedIp), ipLocationResolver.resolve(normalizedIp));
    }

    private void ensureLoginRecordTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_login_record (
                  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  login_ip_hash VARCHAR(128) NOT NULL,
                  ip_location VARCHAR(64) NOT NULL DEFAULT 'IP属地未知',
                  device_name VARCHAR(64) NOT NULL DEFAULT '当前账号登录',
                  login_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_user_login_record_user ON user_login_record(user_id, login_at)");
    }

    private String opaqueToken() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return "usr_" + HexFormat.of().formatHex(bytes);
    }

    private String passwordHash(String password) {
        byte[] salt = new byte[PASSWORD_SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        String encodedSalt = Base64.getEncoder().encodeToString(salt);
        return "pbkdf2$" + PBKDF2_ITERATIONS + "$" + encodedSalt + "$" + pbkdf2(password.trim(), salt, PBKDF2_ITERATIONS);
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
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, PBKDF2_KEY_LENGTH);
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(encoded);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("PBKDF2 not available", ex);
        }
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

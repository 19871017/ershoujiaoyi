package com.secondhand.platform.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {
    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    @Autowired
    public CurrentUserResolver(JdbcTemplate jdbcTemplate, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    public CurrentUserResolver(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null);
    }

    public CurrentUserResolver() {
        this(null, null);
    }

    public long resolve(HttpServletRequest request) {
        String authorization = request == null ? null : request.getHeader("Authorization");
        String accessToken = extractBearerToken(authorization);
        if (accessToken == null) {
            return resolveLegacyHeader(request);
        }
        List<Long> userIds = jdbcTemplate.queryForList("""
                SELECT s.user_id
                FROM user_session s
                JOIN user_account u ON u.id = s.user_id AND u.status = 'ACTIVE'
                WHERE s.access_token = ?
                  AND s.revoked = FALSE
                  AND s.expires_at > CURRENT_TIMESTAMP
                """, Long.class, accessToken);
        if (userIds.isEmpty()) {
            throw new IllegalArgumentException("user session invalid");
        }
        return userIds.get(0);
    }

    public Long resolveOptional(HttpServletRequest request) {
        String authorization = request == null ? null : request.getHeader("Authorization");
        String accessToken = extractBearerToken(authorization);
        if (accessToken != null) {
            return resolve(request);
        }
        if (isDevelopmentProfile() && isDevModeHeaderEnabled(request)) {
            return resolveLegacyHeader(request);
        }
        return null;
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String prefix = "Bearer ";
        if (!authorization.startsWith(prefix)) {
            return null;
        }
        String token = authorization.substring(prefix.length()).trim();
        if (!token.matches("usr_[a-f0-9]{32}")) {
            return null;
        }
        return token;
    }

    private long resolveLegacyHeader(HttpServletRequest request) {
        if (!isDevelopmentProfile() || !isDevModeHeaderEnabled(request)) {
            throw new IllegalArgumentException("user session required");
        }
        String headerUserId = request == null ? null : request.getHeader("X-User-Id");
        if (headerUserId == null || headerUserId.isBlank()) {
            throw new IllegalArgumentException("X-User-Id required");
        }
        try {
            long userId = Long.parseLong(headerUserId.trim());
            if (userId <= 0) {
                throw new IllegalArgumentException("X-User-Id must be positive");
            }
            requireActiveUser(userId);
            return userId;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("X-User-Id invalid", ex);
        }
    }

    private boolean isDevModeHeaderEnabled(HttpServletRequest request) {
        return request != null && "enabled".equals(request.getHeader("X-Dev-Mode"));
    }

    private void requireActiveUser(long userId) {
        if (jdbcTemplate == null) {
            throw new IllegalArgumentException("user session invalid");
        }
        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE id = ? AND status = 'ACTIVE'",
                Integer.class,
                userId);
        if (activeCount == null || activeCount == 0) {
            throw new IllegalArgumentException("user session invalid");
        }
    }

    private boolean isDevelopmentProfile() {
        if (environment == null) {
            return false;
        }
        boolean production = false;
        boolean development = false;
        for (String profile : environment.getActiveProfiles()) {
            String normalizedProfile = profile.toLowerCase(Locale.ROOT);
            if ("prod".equals(normalizedProfile) || "production".equals(normalizedProfile)) {
                production = true;
            } else if ("dev".equals(normalizedProfile) || "local".equals(normalizedProfile)) {
                development = true;
            }
        }
        return development && !production;
    }
}

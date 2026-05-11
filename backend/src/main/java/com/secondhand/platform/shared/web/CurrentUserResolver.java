package com.secondhand.platform.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CurrentUserResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CurrentUserResolver() {
        this.jdbcTemplate = null;
    }

    public long resolve(HttpServletRequest request) {
        String authorization = request == null ? null : request.getHeader("Authorization");
        String accessToken = extractBearerToken(authorization);
        if (accessToken == null) {
            return resolveLegacyHeader(request);
        }
        List<Long> userIds = jdbcTemplate.queryForList("""
                SELECT user_id
                FROM user_session
                WHERE access_token = ?
                  AND revoked = FALSE
                  AND expires_at > CURRENT_TIMESTAMP
                """, Long.class, accessToken);
        if (userIds.isEmpty()) {
            throw new IllegalArgumentException("user session invalid");
        }
        return userIds.get(0);
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
        String headerUserId = request == null ? null : request.getHeader("X-User-Id");
        if (headerUserId == null || headerUserId.isBlank()) {
            throw new IllegalArgumentException("X-User-Id required");
        }
        try {
            long userId = Long.parseLong(headerUserId.trim());
            if (userId <= 0) {
                throw new IllegalArgumentException("X-User-Id must be positive");
            }
            return userId;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("X-User-Id invalid", ex);
        }
    }
}

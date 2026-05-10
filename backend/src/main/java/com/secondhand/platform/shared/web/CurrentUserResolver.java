package com.secondhand.platform.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {

    public long resolve(HttpServletRequest request) {
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

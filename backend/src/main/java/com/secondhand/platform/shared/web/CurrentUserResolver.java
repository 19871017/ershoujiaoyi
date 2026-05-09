package com.secondhand.platform.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {
    private static final long PHASE_PLACEHOLDER_USER_ID = 1L;
    private static final String DEV_MODE_HEADER = "X-Dev-Mode";
    private static final String ENABLED = "enabled";

    public long resolve(HttpServletRequest request) {
        String headerUserId = request == null ? null : request.getHeader("X-User-Id");
        if (headerUserId == null || headerUserId.isBlank()) {
            // Phase placeholder: until real authentication is introduced, allow default user 1 only in explicit dev mode.
            if (request != null && ENABLED.equalsIgnoreCase(request.getHeader(DEV_MODE_HEADER))) {
                return PHASE_PLACEHOLDER_USER_ID;
            }
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

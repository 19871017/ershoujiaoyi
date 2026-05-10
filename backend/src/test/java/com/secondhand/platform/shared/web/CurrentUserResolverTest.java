package com.secondhand.platform.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentUserResolverTest {
    private final CurrentUserResolver resolver = new CurrentUserResolver();

    @Test
    void requiresCurrentUserWhenRequestIsMissingEvenIfDevHeaderCannotBeVerified() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> resolver.resolve(null));

        assertEquals("X-User-Id required", error.getMessage());
    }

    @Test
    void rejectsMissingCurrentUserEvenWhenLegacyDevModeHeaderIsPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Dev-Mode", "enabled");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request));

        assertEquals("X-User-Id required", error.getMessage());
    }

    @Test
    void rejectsNonPositiveCurrentUserIds() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "0");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request));

        assertEquals("X-User-Id must be positive", error.getMessage());
    }
}

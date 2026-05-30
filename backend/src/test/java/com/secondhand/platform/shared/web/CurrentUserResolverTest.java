package com.secondhand.platform.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentUserResolverTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private CurrentUserResolver resolver;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        resolver = new CurrentUserResolver(jdbcTemplate);
    }

    @Test
    void requiresCurrentUserWhenRequestIsMissingEvenIfDevHeaderCannotBeVerified() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> resolver.resolve(null));

        assertEquals("user session required", error.getMessage());
    }

    @Test
    void rejectsMissingCurrentUserEvenWhenLegacyDevModeHeaderIsPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Dev-Mode", "enabled");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request));

        assertEquals("user session required", error.getMessage());
    }

    @Test
    void resolvesLegacyUserHeaderOnlyWithExplicitDevelopmentProfileDevModeHeaderAndActiveAccount() {
        insertUser(55, "ACTIVE");

        assertEquals(55L, devResolver("dev").resolve(legacyRequest(55, true)));
    }

    @Test
    void rejectsLegacyUserHeaderWithoutDevModeHeaderEvenInDevelopmentProfile() {
        insertUser(59, "ACTIVE");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> devResolver("dev").resolve(legacyRequest(59, false)));

        assertEquals("user session required", error.getMessage());
    }

    @Test
    void rejectsLegacyUserHeaderWhenProductionProfileIsAlsoActive() {
        insertUser(56, "ACTIVE");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> devResolver("prod", "dev").resolve(legacyRequest(56, true)));

        assertEquals("user session required", error.getMessage());
    }

    @Test
    void rejectsInactiveLegacyUserHeaderEvenInDevelopmentProfile() {
        insertUser(57, "DISABLED");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> devResolver("dev").resolve(legacyRequest(57, true)));

        assertEquals("user session invalid", error.getMessage());
    }

    @Test
    void rejectsMissingLegacyUserHeaderAccountEvenInDevelopmentProfile() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> devResolver("dev").resolve(legacyRequest(58, true)));

        assertEquals("user session invalid", error.getMessage());
    }

    @Test
    void resolvesCurrentUserFromValidPersistedBearerSession() {
        insertUser(101, "ACTIVE");
        insertSession("usr_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "usr_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", 101, 1, false);
        MockHttpServletRequest request = bearerRequest("usr_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        assertEquals(101L, resolver.resolve(request));
    }

    @Test
    void rejectsSpoofedUserHeaderWhenBearerSessionBelongsToAnotherUser() {
        insertUser(202, "ACTIVE");
        insertSession("usr_cccccccccccccccccccccccccccccccc", "usr_dddddddddddddddddddddddddddddddd", 202, 1, false);
        MockHttpServletRequest request = bearerRequest("usr_cccccccccccccccccccccccccccccccc");
        request.addHeader("X-User-Id", "999");

        assertEquals(202L, resolver.resolve(request));
    }

    @Test
    void rejectsInactiveBearerSessionUser() {
        insertUser(304, "DISABLED");
        insertSession("usr_11111111111111111111111111111111", "usr_22222222222222222222222222222222", 304, 1, false);
        MockHttpServletRequest request = bearerRequest("usr_11111111111111111111111111111111");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request));

        assertEquals("user session invalid", error.getMessage());
    }

    @Test
    void rejectsExpiredBearerSession() {
        insertUser(303, "ACTIVE");
        insertSession("usr_eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", "usr_ffffffffffffffffffffffffffffffff", 303, -1, false);
        MockHttpServletRequest request = bearerRequest("usr_eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request));

        assertEquals("user session invalid", error.getMessage());
    }

    @Test
    void rejectsRevokedBearerSession() {
        insertUser(305, "ACTIVE");
        insertSession("usr_33333333333333333333333333333333", "usr_44444444444444444444444444444444", 305, 1, true);
        MockHttpServletRequest request = bearerRequest("usr_33333333333333333333333333333333");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request));

        assertEquals("user session invalid", error.getMessage());
    }

    private void insertUser(long userId, String status) {
        jdbcTemplate.update("""
                INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status)
                VALUES (?, ?, ?, 'pbkdf2$hash', ?, ?)
                """,
                userId,
                "U-TEST-" + userId,
                "1390000" + String.format("%04d", userId),
                "测试用户" + userId,
                status);
    }

    private void insertSession(String accessToken, String refreshToken, long userId, int expiresInDays, boolean revoked) {
        jdbcTemplate.update("""
                INSERT INTO user_session (access_token, refresh_token, user_id, expires_at, revoked)
                VALUES (?, ?, ?, DATEADD('DAY', ?, CURRENT_TIMESTAMP), ?)
                """, accessToken, refreshToken, userId, expiresInDays, revoked);
    }

    private CurrentUserResolver devResolver(String... profiles) {
        return new CurrentUserResolver(jdbcTemplate, devEnvironment(profiles));
    }

    private HttpServletRequest legacyRequest(long userId, boolean devMode) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", String.valueOf(userId));
        if (devMode) {
            request.addHeader("X-Dev-Mode", "enabled");
        }
        return request;
    }

    private MockHttpServletRequest bearerRequest(String accessToken) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);
        return request;
    }

    private MockEnvironment devEnvironment(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }
}

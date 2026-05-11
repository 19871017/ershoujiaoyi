package com.secondhand.platform.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
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
    void resolvesCurrentUserFromValidPersistedBearerSession() {
        jdbcTemplate.update("""
                INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status)
                VALUES (101, 'U-SESSION-101', '13900001001', 'pbkdf2$hash', '测试用户101', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO user_session (access_token, refresh_token, user_id, expires_at, revoked)
                VALUES ('usr_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'usr_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 101, DATEADD('DAY', 1, CURRENT_TIMESTAMP), FALSE)
                """);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer usr_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        assertEquals(101L, resolver.resolve(request));
    }

    @Test
    void rejectsSpoofedUserHeaderWhenBearerSessionBelongsToAnotherUser() {
        jdbcTemplate.update("""
                INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status)
                VALUES (202, 'U-SESSION-202', '13900002002', 'pbkdf2$hash', '测试用户202', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO user_session (access_token, refresh_token, user_id, expires_at, revoked)
                VALUES ('usr_cccccccccccccccccccccccccccccccc', 'usr_dddddddddddddddddddddddddddddddd', 202, DATEADD('DAY', 1, CURRENT_TIMESTAMP), FALSE)
                """);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer usr_cccccccccccccccccccccccccccccccc");
        request.addHeader("X-User-Id", "999");

        assertEquals(202L, resolver.resolve(request));
    }

    @Test
    void rejectsExpiredOrRevokedBearerSession() {
        jdbcTemplate.update("""
                INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status)
                VALUES (303, 'U-SESSION-303', '13900003003', 'pbkdf2$hash', '测试用户303', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO user_session (access_token, refresh_token, user_id, expires_at, revoked)
                VALUES ('usr_eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee', 'usr_ffffffffffffffffffffffffffffffff', 303, DATEADD('DAY', -1, CURRENT_TIMESTAMP), FALSE)
                """);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer usr_eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request));

        assertEquals("user session invalid", error.getMessage());
    }
}

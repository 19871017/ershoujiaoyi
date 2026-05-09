package com.secondhand.platform.modules.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.secondhand.platform.modules.auth.AuthTokenResponse;
import com.secondhand.platform.modules.auth.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class AuthApplicationServiceTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private AuthApplicationService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        service = new AuthApplicationService(jdbcTemplate);
    }

    @Test
    void loginShouldCreateUserAccountAndProfileWhenMobileIsNew() {
        AuthTokenResponse response = service.login(login("13800138000", "pass-123456"));

        assertTrue(response.getAccessToken().startsWith("dev-access-"));
        assertTrue(response.getRefreshToken().startsWith("dev-refresh-"));
        assertFalse(response.getAccessToken().contains("13800138000"));
        assertEquals(1, count("user_account"));
        assertEquals(1, count("user_profile"));
        assertEquals("小原圈用户8000", jdbcTemplate.queryForObject(
                "SELECT nickname FROM user_account WHERE phone = ?", String.class, "13800138000"));
    }

    @Test
    void repeatedLoginShouldReuseExistingUserWithoutDuplicatingRows() {
        AuthTokenResponse first = service.login(login("13800138001", "pass-123456"));
        AuthTokenResponse second = service.login(login("13800138001", "pass-123456"));

        assertEquals(first.getAccessToken(), second.getAccessToken());
        assertEquals(1, count("user_account"));
        assertEquals(1, count("user_profile"));
    }

    @Test
    void loginShouldRejectWeakOrInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> service.login(login("12800138000", "pass-123456")));
        assertThrows(IllegalArgumentException.class, () -> service.login(login("13800138000", "123")));
    }

    private LoginRequest login(String mobile, String password) {
        LoginRequest request = new LoginRequest();
        request.setMobile(mobile);
        request.setPassword(password);
        return request;
    }

    private int count(String table) {
        Integer value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }
}

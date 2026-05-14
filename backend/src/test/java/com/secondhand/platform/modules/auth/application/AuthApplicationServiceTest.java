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
    void registerShouldCreateUserAccountAndProfileWhenMobileIsNew() {
        AuthTokenResponse response = service.register(login("13800138000", "pass-123456"), "203.0.113.1");

        assertTrue(response.getAccessToken().startsWith("usr_"));
        assertTrue(response.getRefreshToken().startsWith("usr_"));
        assertFalse(response.getAccessToken().contains("13800138000"));
        assertEquals(1, count("user_account"));
        assertEquals(1, count("user_profile"));
        assertEquals("小原圈用户8000", jdbcTemplate.queryForObject(
                "SELECT nickname FROM user_account WHERE phone = ?", String.class, "13800138000"));
    }

    @Test
    void loginShouldRejectUnknownMobileInsteadOfRegisteringImplicitly() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.login(login("13800138009", "pass-123456"), "203.0.113.2"));

        assertEquals("mobile or password invalid", error.getMessage());
        assertEquals(0, count("user_account"));
        assertEquals(0, count("user_profile"));
    }

    @Test
    void loginShouldStorePasswordWithPerUserSaltNotDeterministicSha256() {
        service.register(login("13800138002", "pass-123456"), "203.0.113.12");
        service.register(login("13800138003", "pass-123456"), "203.0.113.13");

        String firstHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user_account WHERE phone = ?", String.class, "13800138002");
        String secondHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user_account WHERE phone = ?", String.class, "13800138003");

        assertTrue(firstHash.startsWith("pbkdf2$"));
        assertTrue(secondHash.startsWith("pbkdf2$"));
        assertFalse(firstHash.contains("pass-123456"));
        assertFalse(secondHash.contains("pass-123456"));
        assertFalse(firstHash.equals(secondHash));
    }

    @Test
    void loginShouldIssuePersistedOpaqueSessionTokensInsteadOfDeterministicDevTokens() {
        AuthTokenResponse first = service.register(login("13800138004", "pass-123456"), "203.0.113.14");
        AuthTokenResponse second = service.login(login("13800138004", "pass-123456"));

        assertTrue(first.getAccessToken().matches("^usr_[a-f0-9]{32}$"));
        assertTrue(first.getRefreshToken().matches("^usr_[a-f0-9]{32}$"));
        assertFalse(first.getAccessToken().startsWith("dev-access-"));
        assertFalse(first.getRefreshToken().startsWith("dev-refresh-"));
        assertFalse(first.getAccessToken().equals(second.getAccessToken()));
        assertFalse(first.getRefreshToken().equals(second.getRefreshToken()));
        assertEquals(2, count("user_session"));
    }

    @Test
    void repeatedLoginShouldReuseExistingUserWithoutDuplicatingRows() {
        AuthTokenResponse first = service.register(login("13800138001", "pass-123456"), "203.0.113.15");
        AuthTokenResponse second = service.login(login("13800138001", "pass-123456"));

        assertFalse(first.getAccessToken().equals(second.getAccessToken()));
        assertEquals(1, count("user_account"));
        assertEquals(1, count("user_profile"));
    }

    @Test
    void newRegistrationShouldBeLimitedToOneAccountPerClientIpPerDay() {
        service.register(login("13800138011", "pass-123456"), "203.0.113.8");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.register(login("13800138012", "pass-123456"), "203.0.113.8"));

        assertEquals("daily registration limit exceeded", error.getMessage());
        assertEquals(1, count("user_account"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_config WHERE config_group = 'auth-registration-limit'",
                Integer.class));
    }

    @Test
    void ipRegistrationLimitShouldNotBlockExistingUserLoginOrDifferentIp() {
        service.register(login("13800138013", "pass-123456"), "203.0.113.9");

        AuthTokenResponse existingUserLogin = service.login(login("13800138013", "pass-123456"), "203.0.113.9");
        AuthTokenResponse differentIpRegistration = service.register(login("13800138014", "pass-123456"), "203.0.113.10");

        assertTrue(existingUserLogin.getAccessToken().startsWith("usr_"));
        assertTrue(differentIpRegistration.getAccessToken().startsWith("usr_"));
        assertEquals(2, count("user_account"));
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

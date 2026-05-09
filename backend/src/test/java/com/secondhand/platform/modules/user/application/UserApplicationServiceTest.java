package com.secondhand.platform.modules.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.secondhand.platform.modules.auth.LoginRequest;
import com.secondhand.platform.modules.auth.application.AuthApplicationService;
import com.secondhand.platform.modules.user.UserProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class UserApplicationServiceTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private UserApplicationService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        service = new UserApplicationService(jdbcTemplate);
    }

    @Test
    void currentUserProfileShouldReadPersistedAccountAndProfile() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.login(login("13800138002", "pass-123456"));
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138002");
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, city = ? WHERE user_id = ?", "VERIFIED", "广州", userId);

        UserProfileResponse profile = service.currentUserProfile(userId);

        assertEquals(userId, profile.getUserId());
        assertEquals("小原圈用户8002", profile.getNickname());
        assertEquals("VERIFIED", profile.getMainRole());
    }

    @Test
    void currentUserProfileShouldExposeVideoVerificationStatus() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.login(login("13800138088", "pass-123456"));
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138088");
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "APPROVED", true, userId);

        UserProfileResponse profile = service.currentUserProfile(userId);

        assertEquals("VERIFIED", profile.getMainRole());
        assertEquals("APPROVED", profile.getVideoIdentityStatus());
        assertEquals(true, profile.isVideoVerified());
    }

    @Test
    void publicProfileShouldExposeOnlyApprovedVideoVerification() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.login(login("13800138111", "pass-123456"));
        auth.login(login("13800138112", "pass-123456"));
        Long approvedUserId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138111");
        Long pendingUserId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138112");
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "APPROVED", true, approvedUserId);
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "PENDING", false, pendingUserId);

        UserProfileResponse approved = service.publicProfile(approvedUserId);
        UserProfileResponse pending = service.publicProfile(pendingUserId);

        assertEquals(true, approved.isVideoVerified());
        assertEquals("APPROVED", approved.getVideoIdentityStatus());
        assertEquals(false, pending.isVideoVerified());
        assertEquals("PENDING", pending.getVideoIdentityStatus());
    }

    @Test
    void currentUserProfileShouldRejectMissingUser() {
        assertThrows(IllegalArgumentException.class, () -> service.currentUserProfile(999L));
        assertThrows(IllegalArgumentException.class, () -> service.publicProfile(999L));
    }

    private LoginRequest login(String mobile, String password) {
        LoginRequest request = new LoginRequest();
        request.setMobile(mobile);
        request.setPassword(password);
        return request;
    }
}

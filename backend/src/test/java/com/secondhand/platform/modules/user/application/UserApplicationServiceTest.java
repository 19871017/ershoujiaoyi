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
    void publicProfileShouldExposeViewerScopedFollowStateAfterFollow() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.login(login("13800138221", "pass-123456"));
        auth.login(login("13800138222", "pass-123456"));
        Long viewerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138221");
        Long sellerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138222");

        UserProfileResponse before = service.publicProfile(sellerId, viewerId);
        service.followProfile(viewerId, sellerId);
        UserProfileResponse after = service.publicProfile(sellerId, viewerId);
        UserProfileResponse anonymous = service.publicProfile(sellerId);

        assertEquals(false, before.isFollowedByMe());
        assertEquals(true, after.isFollowedByMe());
        assertEquals(false, anonymous.isFollowedByMe());
        service.followProfile(viewerId, sellerId);
        Integer duplicateRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_follow WHERE follower_id = ? AND followed_id = ?",
                Integer.class,
                viewerId,
                sellerId
        );
        assertEquals(1, duplicateRows);
    }

    @Test
    void unfollowProfileShouldRemoveOnlyViewerScopedRelationshipIdempotently() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.login(login("13800138241", "pass-123456"));
        auth.login(login("13800138242", "pass-123456"));
        auth.login(login("13800138243", "pass-123456"));
        Long viewerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138241");
        Long sellerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138242");
        Long otherViewerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138243");
        service.followProfile(viewerId, sellerId);
        service.followProfile(otherViewerId, sellerId);

        UserProfileResponse afterUnfollow = service.unfollowProfile(viewerId, sellerId);
        UserProfileResponse repeated = service.unfollowProfile(viewerId, sellerId);
        Integer remainingRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_follow WHERE followed_id = ?",
                Integer.class,
                sellerId
        );

        assertEquals(false, afterUnfollow.isFollowedByMe());
        assertEquals(false, repeated.isFollowedByMe());
        assertEquals(1, remainingRows);
        assertEquals(true, service.publicProfile(sellerId, otherViewerId).isFollowedByMe());
    }

    @Test
    void followProfileShouldRejectSelfOrMissingUsers() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.login(login("13800138231", "pass-123456"));
        Long viewerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138231");

        assertThrows(IllegalArgumentException.class, () -> service.followProfile(viewerId, viewerId));
        assertThrows(IllegalArgumentException.class, () -> service.followProfile(viewerId, 999_999L));
        assertThrows(IllegalArgumentException.class, () -> service.unfollowProfile(viewerId, viewerId));
        assertThrows(IllegalArgumentException.class, () -> service.unfollowProfile(viewerId, 999_999L));
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

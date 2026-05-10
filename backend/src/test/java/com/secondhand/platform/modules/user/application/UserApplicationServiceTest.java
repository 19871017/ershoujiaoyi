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
    void accountSecurityShouldReturnMaskedPhoneAndEmptyBackendDeviceState() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.login(login("13800138666", "pass-123456"));
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138666");

        com.secondhand.platform.modules.user.AccountSecurityResponse security = service.accountSecurity(userId);

        assertEquals(userId, security.getUserId());
        assertEquals("138****8666", security.getMaskedPhone());
        assertEquals("--", security.getSecurityScore());
        assertEquals(0, security.getRecentDevices().size());
    }

    @Test
    void accountSecurityShouldRejectMissingOrInactiveUsersWithoutLeakingRawPhone() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.login(login("13800138667", "pass-123456"));
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138667");
        jdbcTemplate.update("UPDATE user_account SET status = ? WHERE id = ?", "DISABLED", userId);

        assertThrows(IllegalArgumentException.class, () -> service.accountSecurity(0L));
        assertThrows(IllegalArgumentException.class, () -> service.accountSecurity(userId));
    }

    @Test
    void currentUserProfileShouldReadPersistedAccountAndProfile() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.login(login("13800138002", "pass-123456"));
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138002");
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, main_role = ?, city = ? WHERE user_id = ?", "VERIFIED", "SELLER", "广州", userId);

        UserProfileResponse profile = service.currentUserProfile(userId);

        assertEquals(userId, profile.getUserId());
        assertEquals("小原圈用户8002", profile.getNickname());
        assertEquals("SELLER", profile.getMainRole());
    }

    @Test
    void updateProfileShouldPersistAllowedFieldsAndReturnServerState() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.login(login("13800138077", "pass-123456"));
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138077");
        com.secondhand.platform.modules.user.UpdateUserProfileRequest request = new com.secondhand.platform.modules.user.UpdateUserProfileRequest();
        request.setNickname("雨哥生产化小店");
        request.setMainRole("SELLER");
        request.setCity("杭州");
        request.setBio("只展示真实后端资料");

        UserProfileResponse updated = service.updateProfile(userId, request);

        assertEquals("雨哥生产化小店", updated.getNickname());
        assertEquals("SELLER", updated.getMainRole());
        assertEquals("杭州", updated.getCity());
        assertEquals("只展示真实后端资料", updated.getBio());
        assertEquals("雨哥生产化小店", jdbcTemplate.queryForObject("SELECT nickname FROM user_account WHERE id = ?", String.class, userId));
    }

    @Test
    void updateProfileShouldValidateInputAndKeepPersistedStateUnchanged() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.login(login("13800138078", "pass-123456"));
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138078");
        com.secondhand.platform.modules.user.UpdateUserProfileRequest request = new com.secondhand.platform.modules.user.UpdateUserProfileRequest();
        request.setNickname("   ");
        request.setMainRole("ADMIN");
        request.setCity("城市名称超过长度城市名称超过长度城市名称超过长度城市名称超过长度");
        request.setBio("bio");

        assertThrows(IllegalArgumentException.class, () -> service.updateProfile(userId, request));

        UserProfileResponse profile = service.currentUserProfile(userId);
        assertEquals("小原圈用户8078", profile.getNickname());
        assertEquals("BUYER", profile.getMainRole());
    }

    @Test
    void currentUserProfileShouldExposeVideoVerificationStatus() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.login(login("13800138088", "pass-123456"));
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138088");
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "APPROVED", true, userId);

        UserProfileResponse profile = service.currentUserProfile(userId);

        assertEquals("BUYER", profile.getMainRole());
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

    @Test
    void adminUserDetailShouldReturnMaskedPersistedUserProfileOnly() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.login(login("13800138331", "pass-123456"));
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138331");
        jdbcTemplate.update("UPDATE user_profile SET main_role = ?, city = ?, bio = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?",
                "SELLER", "上海", "后台用户资料", "APPROVED", true, userId);

        com.secondhand.platform.modules.user.AdminUserDetailResponse detail = service.adminUserDetail(userId);

        assertEquals(userId, detail.getUserId());
        assertEquals("小原圈用户8331", detail.getNickname());
        assertEquals("138****8331", detail.getMaskedPhone());
        assertEquals("SELLER", detail.getMainRole());
        assertEquals("APPROVED", detail.getVideoIdentityStatus());
        assertEquals(true, detail.isVideoVerified());
        assertEquals("ACTIVE", detail.getStatus());
    }

    @Test
    void adminUserDetailShouldRejectInvalidIdsAndInactiveUsers() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.login(login("13800138332", "pass-123456"));
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138332");
        jdbcTemplate.update("UPDATE user_account SET status = ? WHERE id = ?", "DISABLED", userId);

        assertThrows(IllegalArgumentException.class, () -> service.adminUserDetail(0L));
        assertThrows(IllegalArgumentException.class, () -> service.adminUserDetail(-1L));
        assertThrows(IllegalArgumentException.class, () -> service.adminUserDetail(userId));
    }

    @Test
    void listRankingsShouldExposeBackendSocialMetricsWithoutTrustMetricSubstitution() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.login(login("13800138551", "pass-123456"));
        auth.login(login("13800138552", "pass-123456"));
        auth.login(login("13800138553", "pass-123456"));
        Long viewerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138551");
        Long sellerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138552");
        Long followerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138553");
        jdbcTemplate.update("UPDATE user_profile SET gender = ?, main_role = ?, city = ?, bio = ? WHERE user_id = ?", "goddess", "SELLER", "成都", "后端榜单资料", sellerId);
        service.followProfile(viewerId, sellerId);
        service.followProfile(followerId, sellerId);

        java.util.List<com.secondhand.platform.modules.user.UserRankingResponse> rows = service.listRankings("goddess", 20, viewerId);

        com.secondhand.platform.modules.user.UserRankingResponse row = rows.stream().filter(item -> item.getUserId().equals(sellerId)).findFirst().orElseThrow();
        assertEquals(2, row.getFollowerCount());
        assertEquals(2, row.getPopularityScore());
        assertEquals(0, row.getSafetyScore());
        assertEquals(0, row.getGuardianScore());
        assertEquals(true, row.isFollowedByMe());
    }

    @Test
    void adminUserSearchShouldReturnMaskedActiveUsersByKeywordWithoutRawPhone() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.login(login("13800138441", "pass-123456"));
        auth.login(login("13800138442", "pass-123456"));
        Long sellerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138441");
        Long disabledId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138442");
        jdbcTemplate.update("UPDATE user_account SET nickname = ? WHERE id = ?", "后台检索用户", sellerId);
        jdbcTemplate.update("UPDATE user_account SET status = ? WHERE id = ?", "DISABLED", disabledId);
        jdbcTemplate.update("UPDATE user_profile SET main_role = ?, city = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?",
                "SELLER", "杭州", "APPROVED", true, sellerId);

        java.util.List<com.secondhand.platform.modules.user.AdminUserDetailResponse> rows = service.searchAdminUsers("后台检索", 20);
        java.util.List<com.secondhand.platform.modules.user.AdminUserDetailResponse> byMaskedPhone = service.searchAdminUsers("8441", 20);

        assertEquals(1, rows.size());
        assertEquals(sellerId, rows.get(0).getUserId());
        assertEquals("后台检索用户", rows.get(0).getNickname());
        assertEquals("138****8441", rows.get(0).getMaskedPhone());
        assertEquals("SELLER", rows.get(0).getMainRole());
        assertEquals("杭州", rows.get(0).getCity());
        assertEquals(true, rows.get(0).isVideoVerified());
        assertEquals(1, byMaskedPhone.size());
        assertEquals(sellerId, byMaskedPhone.get(0).getUserId());
    }

    @Test
    void adminUserSearchShouldFailClosedOnInvalidQueryOrLimit() {
        assertThrows(IllegalArgumentException.class, () -> service.searchAdminUsers("", 20));
        assertThrows(IllegalArgumentException.class, () -> service.searchAdminUsers("preview-user", 20));
        assertThrows(IllegalArgumentException.class, () -> service.searchAdminUsers("13800138441", 0));
        assertThrows(IllegalArgumentException.class, () -> service.searchAdminUsers("13800138441", 101));
    }

    private LoginRequest login(String mobile, String password) {
        LoginRequest request = new LoginRequest();
        request.setMobile(mobile);
        request.setPassword(password);
        return request;
    }
}

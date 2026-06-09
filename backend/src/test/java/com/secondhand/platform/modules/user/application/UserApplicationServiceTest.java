package com.secondhand.platform.modules.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.secondhand.platform.modules.auth.LoginRequest;
import com.secondhand.platform.modules.auth.application.AuthApplicationService;
import com.secondhand.platform.modules.media.application.MediaUploadTicketResponse;
import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
import com.secondhand.platform.modules.user.UpdateUserNoRequest;
import com.secondhand.platform.modules.user.UserProfileResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mock.web.MockMultipartFile;

class UserApplicationServiceTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private UserApplicationService service;
    @TempDir
    Path mediaRoot;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        service = new UserApplicationService(jdbcTemplate, new MediaUploadTicketService(jdbcTemplate, mediaRoot.toString()));
    }

    @Test
    void accountSecurityShouldReturnMaskedPhoneAndEmptyBackendDeviceState() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138666", "pass-123456"), "test-13800138666");
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
        auth.register(login("13800138667", "pass-123456"), "test-13800138667");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138667");
        jdbcTemplate.update("UPDATE user_account SET status = ? WHERE id = ?", "DISABLED", userId);

        assertThrows(IllegalArgumentException.class, () -> service.accountSecurity(0L));
        assertThrows(IllegalArgumentException.class, () -> service.accountSecurity(userId));
    }

    @Test
    void currentUserProfileShouldReadPersistedAccountAndProfile() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138002", "pass-123456"), "test-13800138002");
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
        auth.register(login("13800138077", "pass-123456"), "test-13800138077");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138077");
        jdbcTemplate.update("UPDATE user_account SET avatar_url = ? WHERE id = ?", "/uploads/avatar/existing.jpg", userId);
        com.secondhand.platform.modules.user.UpdateUserProfileRequest request = new com.secondhand.platform.modules.user.UpdateUserProfileRequest();
        request.setNickname("雨哥生产化小店");
        request.setCity("杭州");
        request.setBio("只展示真实后端资料");
        request.setGender("goddess");

        UserProfileResponse updated = service.updateProfile(userId, request);

        assertEquals("雨哥生产化小店", updated.getNickname());
        assertEquals("BUYER", updated.getMainRole());
        assertEquals("杭州", updated.getCity());
        assertEquals("只展示真实后端资料", updated.getBio());
        assertEquals("/uploads/avatar/existing.jpg", updated.getAvatarUrl());
        assertEquals("雨哥生产化小店", jdbcTemplate.queryForObject("SELECT nickname FROM user_account WHERE id = ?", String.class, userId));
    }

    @Test
    void updateProfileShouldValidateInputAndKeepPersistedStateUnchanged() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138078", "pass-123456"), "test-13800138078");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138078");
        com.secondhand.platform.modules.user.UpdateUserProfileRequest request = new com.secondhand.platform.modules.user.UpdateUserProfileRequest();
        request.setNickname("   ");
        request.setCity("城市名称超过长度城市名称超过长度城市名称超过长度城市名称超过长度");
        request.setBio("bio");

        assertThrows(IllegalArgumentException.class, () -> service.updateProfile(userId, request));

        UserProfileResponse profile = service.currentUserProfile(userId);
        assertEquals("小原圈用户8078", profile.getNickname());
        assertEquals("BUYER", profile.getMainRole());
    }

    @Test
    void updateUserNoShouldPersistOnceAndRecordChangeLog() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138079", "pass-123456"), "test-13800138079");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138079");
        String originalUserNo = jdbcTemplate.queryForObject("SELECT user_no FROM user_account WHERE id = ?", String.class, userId);
        UpdateUserNoRequest request = new UpdateUserNoRequest();
        request.setUserNo("Circle_8079");

        UserProfileResponse updated = service.updateUserNo(userId, request);

        assertEquals("Circle_8079", updated.getUserNo());
        assertEquals("Circle_8079", jdbcTemplate.queryForObject("SELECT user_no FROM user_account WHERE id = ?", String.class, userId));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_no_change_log WHERE user_id = ?", Integer.class, userId));
        assertEquals(originalUserNo, jdbcTemplate.queryForObject("SELECT old_user_no FROM user_no_change_log WHERE user_id = ?", String.class, userId));
        assertThrows(IllegalArgumentException.class, () -> service.updateUserNo(userId, userNoRequest("Circle_Second")));
    }

    @Test
    void updateUserNoShouldRejectInvalidReservedAndDuplicateValues() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138080", "pass-123456"), "test-13800138080");
        auth.register(login("13800138081", "pass-123456"), "test-13800138081");
        Long firstUserId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138080");
        Long secondUserId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138081");
        String firstOriginalUserNo = jdbcTemplate.queryForObject("SELECT user_no FROM user_account WHERE id = ?", String.class, firstUserId);
        String secondUserNo = jdbcTemplate.queryForObject("SELECT user_no FROM user_account WHERE id = ?", String.class, secondUserId);

        assertThrows(IllegalArgumentException.class, () -> service.updateUserNo(firstUserId, userNoRequest("1bad_name")));
        assertThrows(IllegalArgumentException.class, () -> service.updateUserNo(firstUserId, userNoRequest("demo_user")));
        assertThrows(IllegalArgumentException.class, () -> service.updateUserNo(firstUserId, userNoRequest(secondUserNo.toLowerCase(Locale.ROOT))));

        assertEquals(firstOriginalUserNo, jdbcTemplate.queryForObject("SELECT user_no FROM user_account WHERE id = ?", String.class, firstUserId));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_no_change_log WHERE user_id = ?", Integer.class, firstUserId));
    }

    @Test
    void updateUserNoShouldRejectInactiveUsers() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138082", "pass-123456"), "test-13800138082");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138082");
        jdbcTemplate.update("UPDATE user_account SET status = ? WHERE id = ?", "DISABLED", userId);

        assertThrows(IllegalArgumentException.class, () -> service.updateUserNo(userId, userNoRequest("Circle_8082")));
    }

    @Test
    void currentUserProfileShouldExposeVideoVerificationStatus() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138088", "pass-123456"), "test-13800138088");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138088");
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "SELLER", "APPROVED", true, userId);
        insertUploadedVideoIdentityAudit("AUDIT-VIDEO-CURRENT", userId, "/uploads/video-identity/current-approved.mp4", "APPROVED");

        UserProfileResponse profile = service.currentUserProfile(userId);

        assertEquals("SELLER", profile.getMainRole());
        assertEquals("APPROVED", profile.getVideoIdentityStatus());
        assertEquals(true, profile.isVideoVerified());
    }

    @Test
    void userProfileResponseShouldHidePendingVideoIdentityUrlByDefault() {
        UserProfileResponse profile = new UserProfileResponse(
                1L,
                "Circle_0001",
                "待审卖家",
                null,
                "SELLER",
                "goddess",
                "杭州",
                "待审核认证视频",
                "VERIFIED",
                "PENDING",
                false,
                "/uploads/video-identity/pending-default-hidden.mp4",
                java.util.List.of(),
                false,
                0,
                0,
                0,
                0
        );

        assertEquals("PENDING", profile.getVideoIdentityStatus());
        assertEquals(false, profile.isVideoVerified());
        assertNull(profile.getVideoIdentityUrl());
    }

    @Test
    void publicProfileShouldExposeOnlyApprovedVideoVerification() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138111", "pass-123456"), "test-13800138111");
        auth.register(login("13800138112", "pass-123456"), "test-13800138112");
        Long approvedUserId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138111");
        Long pendingUserId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138112");
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "SELLER", "APPROVED", true, approvedUserId);
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "SELLER", "PENDING", false, pendingUserId);
        insertUploadedVideoIdentityAudit("AUDIT-VIDEO-APPROVED", approvedUserId, "/uploads/video-identity/approved.mp4", "APPROVED");
        insertUploadedVideoIdentityAudit("AUDIT-VIDEO-PENDING", pendingUserId, "/uploads/video-identity/pending.mp4", "PENDING");

        UserProfileResponse approved = service.publicProfile(approvedUserId);
        UserProfileResponse pending = service.publicProfile(pendingUserId);
        UserProfileResponse pendingSelf = service.currentUserProfile(pendingUserId);

        assertEquals(true, approved.isVideoVerified());
        assertEquals("APPROVED", approved.getVideoIdentityStatus());
        assertEquals("/uploads/video-identity/approved.mp4", approved.getVideoIdentityUrl());
        assertEquals(false, pending.isVideoVerified());
        assertEquals("UNVERIFIED", pending.getVideoIdentityStatus());
        assertNull(pending.getVideoIdentityUrl());
        assertEquals(false, pendingSelf.isVideoVerified());
        assertEquals("PENDING", pendingSelf.getVideoIdentityStatus());
        assertEquals("/uploads/video-identity/pending.mp4", pendingSelf.getVideoIdentityUrl());
    }

    @Test
    void publicProfileShouldHideApprovedVideoIdentityWithoutUploadedTicket() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138119", "pass-123456"), "test-13800138119");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138119");
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "SELLER", "APPROVED", true, userId);
        insertVideoIdentityAudit("AUDIT-VIDEO-NO-TICKET", userId, "/uploads/video-identity/no-ticket.mp4", "APPROVED");

        UserProfileResponse profile = service.publicProfile(userId);

        assertEquals(false, profile.isVideoVerified());
        assertEquals("UNVERIFIED", profile.getVideoIdentityStatus());
        assertNull(profile.getVideoIdentityUrl());
    }

    @Test
    void publicProfileShouldHideRejectedAndDefaultVideoIdentityUrlsWhileSelfKeepsRejectedStatus() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138125", "pass-123456"), "test-13800138125");
        auth.register(login("13800138126", "pass-123456"), "test-13800138126");
        Long rejectedUserId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138125");
        Long defaultUserId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138126");
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "SELLER", "REJECTED", false, rejectedUserId);
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "SELLER", "UNVERIFIED", false, defaultUserId);
        insertVideoIdentityAudit("AUDIT-VIDEO-REJECTED-HIDDEN", rejectedUserId, "/uploads/video-identity/rejected-hidden.mp4", "REJECTED");
        insertVideoIdentityAudit("AUDIT-VIDEO-DEFAULT-HIDDEN", defaultUserId, "/uploads/video-identity/default-hidden.mp4", "UNVERIFIED");

        UserProfileResponse rejectedPublic = service.publicProfile(rejectedUserId);
        UserProfileResponse rejectedSelf = service.currentUserProfile(rejectedUserId);
        UserProfileResponse defaultPublic = service.publicProfile(defaultUserId);
        UserProfileResponse defaultSelf = service.currentUserProfile(defaultUserId);

        assertEquals("UNVERIFIED", rejectedPublic.getVideoIdentityStatus());
        assertEquals(false, rejectedPublic.isVideoVerified());
        assertNull(rejectedPublic.getVideoIdentityUrl());
        assertEquals("REJECTED", rejectedSelf.getVideoIdentityStatus());
        assertEquals(false, rejectedSelf.isVideoVerified());
        assertNull(rejectedSelf.getVideoIdentityUrl());
        assertEquals("UNVERIFIED", defaultPublic.getVideoIdentityStatus());
        assertNull(defaultPublic.getVideoIdentityUrl());
        assertEquals("UNVERIFIED", defaultSelf.getVideoIdentityStatus());
        assertNull(defaultSelf.getVideoIdentityUrl());
    }

    @Test
    void profileShouldHideNonCanonicalVideoIdentityAuditUrls() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138120", "pass-123456"), "test-13800138120");
        auth.register(login("13800138121", "pass-123456"), "test-13800138121");
        Long approvedUserId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138120");
        Long pendingUserId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138121");
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "SELLER", "APPROVED", true, approvedUserId);
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "SELLER", "PENDING", false, pendingUserId);
        insertVideoIdentityAudit("AUDIT-VIDEO-NON-CANONICAL-APPROVED", approvedUserId, "/uploads/community-image/not-video.jpg", "APPROVED");
        insertVideoIdentityAudit("AUDIT-VIDEO-NON-CANONICAL-PENDING", pendingUserId, "/uploads/product-image/not-video.jpg", "PENDING");

        UserProfileResponse approved = service.publicProfile(approvedUserId);
        UserProfileResponse pendingSelf = service.currentUserProfile(pendingUserId);

        assertEquals(false, approved.isVideoVerified());
        assertNull(approved.getVideoIdentityUrl());
        assertEquals(java.util.List.of(), approved.getShowcaseImageUrls());
        assertEquals("UNVERIFIED", pendingSelf.getVideoIdentityStatus());
        assertEquals(false, pendingSelf.isVideoVerified());
        assertNull(pendingSelf.getVideoIdentityUrl());
    }

    @Test
    void profileShouldHideMalformedCanonicalVideoIdentityAuditUrls() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138122", "pass-123456"), "test-13800138122");
        auth.register(login("13800138123", "pass-123456"), "test-13800138123");
        auth.register(login("13800138124", "pass-123456"), "test-13800138124");
        Long traversalUserId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138122");
        Long encodedUserId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138123");
        Long placeholderUserId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138124");
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "SELLER", "APPROVED", true, traversalUserId);
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "SELLER", "PENDING", false, encodedUserId);
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "SELLER", "PENDING", false, placeholderUserId);
        insertVideoIdentityAudit("AUDIT-VIDEO-TRAVERSAL", traversalUserId, "/uploads/video-identity/../community-image/leak.mp4", "APPROVED");
        insertVideoIdentityAudit("AUDIT-VIDEO-ENCODED", encodedUserId, "/uploads/video-identity/%2e%2e/secret.mp4", "PENDING");
        insertVideoIdentityAudit("AUDIT-VIDEO-PLACEHOLDER", placeholderUserId, "/uploads/video-identity/placeholder.mp4", "PENDING");

        UserProfileResponse traversal = service.publicProfile(traversalUserId);
        UserProfileResponse encoded = service.currentUserProfile(encodedUserId);
        UserProfileResponse placeholder = service.currentUserProfile(placeholderUserId);

        assertEquals(false, traversal.isVideoVerified());
        assertEquals("UNVERIFIED", traversal.getVideoIdentityStatus());
        assertNull(traversal.getVideoIdentityUrl());
        assertEquals("UNVERIFIED", encoded.getVideoIdentityStatus());
        assertNull(encoded.getVideoIdentityUrl());
        assertEquals("UNVERIFIED", placeholder.getVideoIdentityStatus());
        assertNull(placeholder.getVideoIdentityUrl());
    }

    @Test
    void publicProfileShouldHideApprovedVideoMediaForNonSellerRole() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138117", "pass-123456"), "test-13800138117");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138117");
        jdbcTemplate.update("UPDATE user_profile SET main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "BUYER", "APPROVED", true, userId);
        insertUploadedVideoIdentityAudit("AUDIT-VIDEO-BUYER-HIDDEN", userId, "/uploads/video-identity/buyer-hidden.mp4", "APPROVED");
        jdbcTemplate.update("INSERT INTO user_showcase_photo (user_id, image_url, sort_order) VALUES (?, ?, ?)", userId, "/uploads/community-image/1/buyer-hidden.jpg", 0);

        UserProfileResponse profile = service.publicProfile(userId);

        assertEquals(false, profile.isVideoVerified());
        assertNull(profile.getVideoIdentityUrl());
        assertEquals(java.util.List.of(), profile.getShowcaseImageUrls());
    }

    @Test
    void updateProfileShouldPersistShowcasePhotosFromUploadedCommunityImagesOnly() throws Exception {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138114", "pass-123456"), "test-13800138114");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138114");
        jdbcTemplate.update("UPDATE user_profile SET main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "SELLER", "APPROVED", true, userId);
        insertUploadedVideoIdentityAudit("AUDIT-VIDEO-SHOWCASE", userId, "/uploads/video-identity/approved-showcase.mp4", "APPROVED");
        MediaUploadTicketService mediaService = new MediaUploadTicketService(jdbcTemplate, System.getProperty("java.io.tmpdir"));
        byte[] firstBytes = imageBytes("jpeg");
        byte[] secondBytes = imageBytes("png");
        MediaUploadTicketResponse first = mediaService.issue(userId, "COMMUNITY_IMAGE", "image/jpeg", (long) firstBytes.length, "showcase-a.jpg");
        MediaUploadTicketResponse second = mediaService.issue(userId, "COMMUNITY_IMAGE", "image/png", (long) secondBytes.length, "showcase-b.png");
        mediaService.storeUploadedFile(userId, first.ticketNo(), first.uploadToken(), new MockMultipartFile("file", "showcase-a.jpg", "image/jpeg", firstBytes));
        mediaService.storeUploadedFile(userId, second.ticketNo(), second.uploadToken(), new MockMultipartFile("file", "showcase-b.png", "image/png", secondBytes));
        com.secondhand.platform.modules.user.UpdateUserProfileRequest request = new com.secondhand.platform.modules.user.UpdateUserProfileRequest();
        request.setNickname("认证商家照片秀");
        request.setGender("goddess");
        request.setShowcaseImageUrls(java.util.List.of(first.storageUrl(), second.storageUrl(), first.storageUrl()));

        UserProfileResponse updated = service.updateProfile(userId, request);

        assertEquals(java.util.List.of(first.storageUrl(), second.storageUrl()), updated.getShowcaseImageUrls());
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_showcase_photo WHERE user_id = ?", Integer.class, userId));
    }

    @Test
    void updateProfileShouldAllowKeepingPersistedShowcasePhotosAfterUploadTicketExpires() throws Exception {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138118", "pass-123456"), "test-13800138118");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138118");
        jdbcTemplate.update("UPDATE user_profile SET main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "SELLER", "APPROVED", true, userId);
        insertUploadedVideoIdentityAudit("AUDIT-VIDEO-SHOWCASE-EXPIRED", userId, "/uploads/video-identity/approved-showcase-expired.mp4", "APPROVED");
        MediaUploadTicketService mediaService = new MediaUploadTicketService(jdbcTemplate, System.getProperty("java.io.tmpdir"));
        byte[] firstBytes = imageBytes("jpeg");
        byte[] secondBytes = imageBytes("jpeg");
        MediaUploadTicketResponse first = mediaService.issue(userId, "COMMUNITY_IMAGE", "image/jpeg", (long) firstBytes.length, "showcase-keep-a.jpg");
        MediaUploadTicketResponse second = mediaService.issue(userId, "COMMUNITY_IMAGE", "image/jpeg", (long) secondBytes.length, "showcase-keep-b.jpg");
        mediaService.storeUploadedFile(userId, first.ticketNo(), first.uploadToken(), new MockMultipartFile("file", "showcase-keep-a.jpg", "image/jpeg", firstBytes));
        mediaService.storeUploadedFile(userId, second.ticketNo(), second.uploadToken(), new MockMultipartFile("file", "showcase-keep-b.jpg", "image/jpeg", secondBytes));
        com.secondhand.platform.modules.user.UpdateUserProfileRequest initial = new com.secondhand.platform.modules.user.UpdateUserProfileRequest();
        initial.setNickname("认证商家照片秀");
        initial.setGender("goddess");
        initial.setShowcaseImageUrls(java.util.List.of(first.storageUrl(), second.storageUrl()));
        service.updateProfile(userId, initial);
        jdbcTemplate.update("UPDATE media_upload_ticket SET expires_at = DATEADD('MINUTE', -1, CURRENT_TIMESTAMP) WHERE owner_user_id = ?", userId);
        com.secondhand.platform.modules.user.UpdateUserProfileRequest reduced = new com.secondhand.platform.modules.user.UpdateUserProfileRequest();
        reduced.setNickname("认证商家照片秀");
        reduced.setGender("goddess");
        reduced.setShowcaseImageUrls(java.util.List.of(second.storageUrl()));

        UserProfileResponse updated = service.updateProfile(userId, reduced);

        assertEquals(java.util.List.of(second.storageUrl()), updated.getShowcaseImageUrls());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_showcase_photo WHERE user_id = ?", Integer.class, userId));
    }

    @Test
    void updateProfileShouldRejectShowcasePhotosWithoutUploadedTicket() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138115", "pass-123456"), "test-13800138115");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138115");
        com.secondhand.platform.modules.user.UpdateUserProfileRequest request = new com.secondhand.platform.modules.user.UpdateUserProfileRequest();
        request.setNickname("认证商家照片秀");
        request.setGender("goddess");
        request.setShowcaseImageUrls(java.util.List.of("/uploads/community-image/1/free.jpg"));

        assertThrows(IllegalArgumentException.class, () -> service.updateProfile(userId, request));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_showcase_photo WHERE user_id = ?", Integer.class, userId));
    }

    @Test
    void publicProfileShouldHideShowcasePhotosUntilVideoVerified() throws Exception {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138116", "pass-123456"), "test-13800138116");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138116");
        MediaUploadTicketService mediaService = new MediaUploadTicketService(jdbcTemplate, System.getProperty("java.io.tmpdir"));
        byte[] photoBytes = imageBytes("jpeg");
        MediaUploadTicketResponse photo = mediaService.issue(userId, "COMMUNITY_IMAGE", "image/jpeg", (long) photoBytes.length, "showcase.jpg");
        mediaService.storeUploadedFile(userId, photo.ticketNo(), photo.uploadToken(), new MockMultipartFile("file", "showcase.jpg", "image/jpeg", photoBytes));
        jdbcTemplate.update("INSERT INTO user_showcase_photo (user_id, image_url, sort_order) VALUES (?, ?, ?)", userId, photo.storageUrl(), 0);

        assertEquals(java.util.List.of(), service.publicProfile(userId).getShowcaseImageUrls());
        jdbcTemplate.update("UPDATE user_profile SET main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "SELLER", "APPROVED", true, userId);
        insertUploadedVideoIdentityAudit("AUDIT-VIDEO-SHOWCASE-HIDE", userId, "/uploads/video-identity/approved-showcase-hide.mp4", "APPROVED");

        assertEquals(java.util.List.of(photo.storageUrl()), service.publicProfile(userId).getShowcaseImageUrls());
    }

    @Test
    void publicProfileShouldNotExposeApprovedVideoIdentityWhenAuditReasonIsNotStorageUrl() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138113", "pass-123456"), "test-13800138113");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138113");
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "SELLER", "APPROVED", true, userId);
        insertVideoIdentityAudit("AUDIT-VIDEO-TEXT-REASON", userId, "真人认证视频", "APPROVED");

        UserProfileResponse profile = service.publicProfile(userId);

        assertEquals(false, profile.isVideoVerified());
        assertEquals("UNVERIFIED", profile.getVideoIdentityStatus());
        assertNull(profile.getVideoIdentityUrl());
    }

    @Test
    void publicProfileShouldExposeViewerScopedFollowStateAfterFollow() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138221", "pass-123456"), "test-13800138221");
        auth.register(login("13800138222", "pass-123456"), "test-13800138222");
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
    void followProfileShouldNotifyFollowedUserOnce() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138271", "pass-123456"), "test-13800138271");
        auth.register(login("13800138272", "pass-123456"), "test-13800138272");
        Long viewerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138271");
        Long sellerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138272");
        jdbcTemplate.update("UPDATE user_account SET nickname = ? WHERE id = ?", "关注者", viewerId);

        service.followProfile(viewerId, sellerId);
        service.followProfile(viewerId, sellerId);

        var notices = new com.secondhand.platform.modules.notification.application.NotificationApplicationService(jdbcTemplate)
                .listNotifications(sellerId, "FOLLOW", 20);
        assertEquals(1, notices.size());
        assertEquals("你有新的关注", notices.get(0).title());
        assertEquals("关注者 关注了你", notices.get(0).description());
        assertEquals("/pages/user/public-profile/index?userId=" + viewerId, notices.get(0).targetUrl());
    }

    @Test
    void unfollowProfileShouldRemoveOnlyViewerScopedRelationshipIdempotently() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138241", "pass-123456"), "test-13800138241");
        auth.register(login("13800138242", "pass-123456"), "test-13800138242");
        auth.register(login("13800138243", "pass-123456"), "test-13800138243");
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
        auth.register(login("13800138231", "pass-123456"), "test-13800138231");
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
    void publicProfileShouldExposeFollowGiftAndConsumptionStats() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138261", "pass-123456"), "test-13800138261");
        auth.register(login("13800138262", "pass-123456"), "test-13800138262");
        auth.register(login("13800138263", "pass-123456"), "test-13800138263");
        Long profileUserId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138261");
        Long followerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138262");
        Long receiverId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138263");
        service.followProfile(followerId, profileUserId);
        service.followProfile(profileUserId, receiverId);
        jdbcTemplate.update("""
                INSERT INTO gift_order (gift_order_no, idempotency_key, sender_id, receiver_id, gift_id, gift_code, quantity, total_amount, platform_share, receiver_amount, debit_ledger_no, receiver_credit_ledger_no, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "GO-PROFILE-RECEIVED", "IDEM-GO-PROFILE-RECEIVED", followerId, profileUserId, 1L, "ROSE", 1, "19.90", "0.00", "19.90", "DL-GO-PROFILE-RECEIVED", "CL-GO-PROFILE-RECEIVED", "SUCCESS");
        jdbcTemplate.update("""
                INSERT INTO gift_order (gift_order_no, idempotency_key, sender_id, receiver_id, gift_id, gift_code, quantity, total_amount, platform_share, receiver_amount, debit_ledger_no, receiver_credit_ledger_no, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "GO-PROFILE-SENT", "IDEM-GO-PROFILE-SENT", profileUserId, receiverId, 1L, "COFFEE", 1, "6.50", "0.00", "6.50", "DL-GO-PROFILE-SENT", "CL-GO-PROFILE-SENT", "SUCCESS");
        jdbcTemplate.update("""
                INSERT INTO trade_order (order_no, product_id, goods_id, product_no, product_title, trade_rule_snapshot, buyer_id, seller_id, amount, order_status, accepted_trade_rule)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "TO-PROFILE-PAID", 1L, 1L, "PN-PROFILE-PAID", "真实消费订单", "server-order", profileUserId, receiverId, "88.80", "PAID", true);

        UserProfileResponse profile = service.publicProfile(profileUserId, followerId);

        assertEquals(1, profile.getFollowerCount());
        assertEquals(1, profile.getFollowingCount());
        assertEquals(19, profile.getSellerCharmScore());
        assertEquals(95, profile.getBuyerPowerScore());
        assertEquals(true, profile.isFollowedByMe());
    }

    @Test
    void adminUserDetailShouldReturnMaskedPersistedUserProfileOnly() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138331", "pass-123456"), "test-13800138331");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138331");
        jdbcTemplate.update("UPDATE user_profile SET main_role = ?, city = ?, bio = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?",
                "SELLER", "上海", "后台用户资料", "APPROVED", true, userId);
        insertUploadedVideoIdentityAudit("AUDIT-ADMIN-DETAIL-VIDEO", userId, "/uploads/video-identity/admin-detail.mp4", "APPROVED");

        com.secondhand.platform.modules.user.AdminUserDetailResponse detail = service.adminUserDetail(userId);

        assertEquals(userId, detail.getUserId());
        assertEquals("小原圈用户8331", detail.getNickname());
        assertEquals("138****8331", detail.getMaskedPhone());
        assertEquals("SELLER", detail.getMainRole());
        assertEquals("APPROVED", detail.getVideoIdentityStatus());
        assertEquals(true, detail.isVideoVerified());
        assertEquals("ACTIVE", detail.getStatus());
        assertEquals(0, detail.getOpsSummary().getOrderCount());
        assertNull(detail.getOpsSummary().getLastWithdrawalNo());
    }

    @Test
    void adminUserDetailShouldExposeOperationsSummaryForBackofficeReview() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138333", "pass-123456"), "test-13800138333");
        auth.register(login("13800138334", "pass-123456"), "test-13800138334");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138333");
        Long peerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138334");
        jdbcTemplate.update("""
                INSERT INTO trade_order (order_no, product_id, goods_id, product_no, product_title, trade_rule_snapshot, buyer_id, seller_id, amount, order_status, accepted_trade_rule)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "TO-ADMIN-USER-1", 1L, 1L, "PN-ADMIN-USER-1", "用户复盘订单1", "server-order", userId, peerId, "66.00", "PAID", true);
        jdbcTemplate.update("""
                INSERT INTO trade_order (order_no, product_id, goods_id, product_no, product_title, trade_rule_snapshot, buyer_id, seller_id, amount, order_status, accepted_trade_rule)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "TO-ADMIN-USER-2", 2L, 2L, "PN-ADMIN-USER-2", "用户复盘订单2", "server-order", peerId, userId, "88.00", "PENDING_PAY", true);
        jdbcTemplate.update("""
                INSERT INTO after_sales_record (after_sales_no, order_no, applicant_id, after_sales_type, refund_amount, reason, description, evidence_urls, after_sales_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "AS-ADMIN-USER-1", "TO-ADMIN-USER-1", userId, "REFUND_ONLY", "20.00", "商品问题", "真实售后", "[]", "PENDING_REVIEW");
        jdbcTemplate.update("""
                INSERT INTO report_record (report_no, reporter_id, target_type, target_id, reason_code, description, report_status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "RP-ADMIN-USER-1", peerId, "USER", String.valueOf(userId), "SPAM", "用户被举报", "PENDING");
        jdbcTemplate.update("""
                INSERT INTO withdrawal_record (withdrawal_no, user_id, amount, payment_method, account_name, account_no, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "WD-1770000000000-12345", userId, "30.00", "ALIPAY", "王*", "raw-account-should-not-leak", "PENDING");
        jdbcTemplate.update("""
                INSERT INTO im_conversation (conversation_no, owner_user_id, peer_user_id, conversation_type, last_seq, last_message_summary)
                VALUES (?, ?, ?, ?, ?, ?)
                """, "IC-ADMIN-USER-1", userId, peerId, "SINGLE", 3L, "最近消息");

        com.secondhand.platform.modules.user.AdminUserDetailResponse detail = service.adminUserDetail(userId);

        assertEquals(2, detail.getOpsSummary().getOrderCount());
        assertEquals(1, detail.getOpsSummary().getPaidOrderCount());
        assertEquals(1, detail.getOpsSummary().getAfterSalesCount());
        assertEquals(1, detail.getOpsSummary().getPendingAfterSalesCount());
        assertEquals(1, detail.getOpsSummary().getReportCount());
        assertEquals(1, detail.getOpsSummary().getPendingReportCount());
        assertEquals(1, detail.getOpsSummary().getWithdrawalCount());
        assertEquals(1, detail.getOpsSummary().getPendingWithdrawalCount());
        assertEquals(1, detail.getOpsSummary().getChatConversationCount());
        assertEquals("TO-ADMIN-USER-2", detail.getOpsSummary().getLastOrderNo());
        assertEquals("AS-ADMIN-USER-1", detail.getOpsSummary().getLastAfterSalesNo());
        assertEquals("WD-1770000000000-12345", detail.getOpsSummary().getLastWithdrawalNo());
        assertEquals(1L, detail.getOpsSummary().getLastChatConversationId());
        assertEquals("138****8333", detail.getMaskedPhone());
    }

    @Test
    void adminUserDetailShouldRejectInvalidIdsAndInactiveUsers() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138332", "pass-123456"), "test-13800138332");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138332");
        jdbcTemplate.update("UPDATE user_account SET status = ? WHERE id = ?", "DISABLED", userId);

        assertThrows(IllegalArgumentException.class, () -> service.adminUserDetail(0L));
        assertThrows(IllegalArgumentException.class, () -> service.adminUserDetail(-1L));
        assertThrows(IllegalArgumentException.class, () -> service.adminUserDetail(userId));
    }

    @Test
    void listRankingsShouldExposeBackendGiftScoreWithoutTrustMetricSubstitution() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138551", "pass-123456"), "test-13800138551");
        auth.register(login("13800138552", "pass-123456"), "test-13800138552");
        auth.register(login("13800138553", "pass-123456"), "test-13800138553");
        auth.register(login("13800138554", "pass-123456"), "test-13800138554");
        auth.register(login("13800138555", "pass-123456"), "test-13800138555");
        Long viewerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138551");
        Long sellerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138552");
        Long followerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138553");
        Long pendingSellerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138554");
        Long invalidVideoSellerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138555");
        jdbcTemplate.update("UPDATE user_profile SET gender = ?, main_role = ?, city = ?, bio = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "goddess", "SELLER", "成都", "后端榜单资料", "APPROVED", true, sellerId);
        jdbcTemplate.update("UPDATE user_profile SET gender = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "goddess", "SELLER", "PENDING", true, pendingSellerId);
        jdbcTemplate.update("UPDATE user_profile SET gender = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "goddess", "SELLER", "APPROVED", true, invalidVideoSellerId);
        insertUploadedVideoIdentityAudit("AUDIT-VIDEO-RANKING-APPROVED", sellerId, "/uploads/video-identity/ranking-approved.mp4", "APPROVED");
        insertVideoIdentityAudit("AUDIT-VIDEO-RANKING-INVALID", invalidVideoSellerId, "/uploads/video-identity/../placeholder.mp4", "APPROVED");
        service.followProfile(viewerId, sellerId);
        service.followProfile(followerId, sellerId);
        jdbcTemplate.update("""
                INSERT INTO gift_order (gift_order_no, idempotency_key, sender_id, receiver_id, gift_id, gift_code, quantity, total_amount, platform_share, receiver_amount, debit_ledger_no, receiver_credit_ledger_no, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "GO-METRIC-1", "IDEM-GO-METRIC-1", viewerId, sellerId, 1L, "ROSE", 1, "66.60", "0.00", "66.60", "DL-GO-METRIC-1", "CL-GO-METRIC-1", "SUCCESS");

        java.util.List<com.secondhand.platform.modules.user.UserRankingResponse> rows = service.listRankings("goddess", "all", 20, viewerId);

        com.secondhand.platform.modules.user.UserRankingResponse row = rows.stream().filter(item -> item.getUserId().equals(sellerId)).findFirst().orElseThrow();
        com.secondhand.platform.modules.user.UserRankingResponse pendingRow = rows.stream().filter(item -> item.getUserId().equals(pendingSellerId)).findFirst().orElseThrow();
        com.secondhand.platform.modules.user.UserRankingResponse invalidVideoRow = rows.stream().filter(item -> item.getUserId().equals(invalidVideoSellerId)).findFirst().orElseThrow();
        assertEquals(2, row.getFollowerCount());
        assertEquals(66, row.getGiftScore());
        assertEquals(66, row.getPopularityScore());
        assertEquals(66, row.getSafetyScore());
        assertEquals(66, row.getGuardianScore());
        assertEquals("APPROVED", row.getVideoIdentityStatus());
        assertEquals(true, row.isVideoVerified());
        assertEquals("UNVERIFIED", pendingRow.getVideoIdentityStatus());
        assertEquals(false, pendingRow.isVideoVerified());
        assertEquals("UNVERIFIED", invalidVideoRow.getVideoIdentityStatus());
        assertEquals(false, invalidVideoRow.isVideoVerified());
        assertEquals(true, row.isFollowedByMe());
    }

    @Test
    void adminUserSearchShouldReturnMaskedActiveUsersByKeywordWithoutRawPhone() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138441", "pass-123456"), "test-13800138441");
        auth.register(login("13800138442", "pass-123456"), "test-13800138442");
        Long sellerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138441");
        Long disabledId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138442");
        jdbcTemplate.update("UPDATE user_account SET nickname = ? WHERE id = ?", "后台检索用户", sellerId);
        jdbcTemplate.update("UPDATE user_account SET status = ? WHERE id = ?", "DISABLED", disabledId);
        jdbcTemplate.update("UPDATE user_profile SET main_role = ?, city = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?",
                "SELLER", "杭州", "APPROVED", true, sellerId);
        insertUploadedVideoIdentityAudit("AUDIT-ADMIN-SEARCH-VIDEO", sellerId, "/uploads/video-identity/admin-search.mp4", "APPROVED");

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
    void adminUserSearchAndDetailShouldRequireUploadedApprovedVideoIdentityForPublicBadge() {
        AuthApplicationService auth = new AuthApplicationService(jdbcTemplate);
        auth.register(login("13800138443", "pass-123456"), "test-13800138443");
        auth.register(login("13800138444", "pass-123456"), "test-13800138444");
        Long dirtySellerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138443");
        Long verifiedSellerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138444");
        jdbcTemplate.update("UPDATE user_account SET nickname = ? WHERE id = ?", "后台脏认证卖家", dirtySellerId);
        jdbcTemplate.update("UPDATE user_account SET nickname = ? WHERE id = ?", "后台真实认证卖家", verifiedSellerId);
        jdbcTemplate.update("UPDATE user_profile SET main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?",
                "SELLER", "APPROVED", true, dirtySellerId);
        jdbcTemplate.update("UPDATE user_profile SET main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?",
                "SELLER", "APPROVED", true, verifiedSellerId);
        insertUploadedVideoIdentityAudit("AUDIT-ADMIN-VIDEO-VERIFIED", verifiedSellerId, "/uploads/video-identity/admin-verified.mp4", "APPROVED");

        java.util.List<com.secondhand.platform.modules.user.AdminUserDetailResponse> dirtyRows = service.searchAdminUsers("后台脏认证", 20);
        java.util.List<com.secondhand.platform.modules.user.AdminUserDetailResponse> verifiedRows = service.searchAdminUsers("后台真实认证", 20);
        com.secondhand.platform.modules.user.AdminUserDetailResponse dirtyDetail = service.adminUserDetail(dirtySellerId);

        assertEquals(1, dirtyRows.size());
        assertEquals(false, dirtyRows.get(0).isVideoVerified());
        assertEquals(false, dirtyDetail.isVideoVerified());
        assertEquals(1, verifiedRows.size());
        assertEquals(true, verifiedRows.get(0).isVideoVerified());
    }

    @Test
    void adminUserSearchShouldFailClosedOnInvalidQueryOrLimit() {
        assertThrows(IllegalArgumentException.class, () -> service.searchAdminUsers("", 20));
        assertThrows(IllegalArgumentException.class, () -> service.searchAdminUsers("preview-user", 20));
        assertThrows(IllegalArgumentException.class, () -> service.searchAdminUsers("13800138441", 0));
        assertThrows(IllegalArgumentException.class, () -> service.searchAdminUsers("13800138441", 101));
    }

    private void insertVideoIdentityAudit(String auditNo, Long userId, String reason, String status) {
        jdbcTemplate.update("""
                INSERT INTO audit_record (audit_no, audit_type, user_id, target_type, target_id, reason, description, status, created_at, reviewed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, auditNo, "VIDEO_IDENTITY", userId, "VIDEO_IDENTITY", String.valueOf(userId), reason, "真人认证视频", status);
    }

    private void insertUploadedVideoIdentityAudit(String auditNo, Long userId, String reason, String status) {
        jdbcTemplate.update("""
                INSERT INTO media_upload_ticket (
                  ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at
                ) VALUES (?, ?, 'VIDEO_IDENTITY', 'identity.mp4', 'video/mp4', 1024, ?, 'hash', 'UPLOADED', CURRENT_TIMESTAMP, DATEADD('HOUR', -1, CURRENT_TIMESTAMP))
                """, "TICKET-" + auditNo, userId, reason);
        writeValidVideoIdentityFile(reason);
        insertVideoIdentityAudit(auditNo, userId, reason, status);
    }

    private void writeValidVideoIdentityFile(String storageUrl) {
        try {
            Path target = mediaRoot.resolve(storageUrl.substring(1)).normalize();
            Files.createDirectories(target.getParent());
            Files.write(target, minimalMp4WithDurationSeconds(10));
        } catch (Exception exception) {
            throw new IllegalStateException("test video identity media write failed", exception);
        }
    }

    private byte[] minimalMp4WithDurationSeconds(int durationSeconds) {
        byte[] ftyp = mp4Box("ftyp", concat(
                "isom".getBytes(StandardCharsets.US_ASCII),
                ByteBuffer.allocate(4).putInt(0).array(),
                "isom".getBytes(StandardCharsets.US_ASCII)
        ));
        ByteBuffer mvhdPayload = ByteBuffer.allocate(24);
        mvhdPayload.putInt(0);
        mvhdPayload.putInt(0);
        mvhdPayload.putInt(0);
        mvhdPayload.putInt(1000);
        mvhdPayload.putInt(durationSeconds * 1000);
        mvhdPayload.putInt(0);
        byte[] mvhd = mp4Box("mvhd", mvhdPayload.array());
        byte[] hdlr = mp4Box("hdlr", concat(
                ByteBuffer.allocate(8).putInt(0).putInt(0).array(),
                "vide".getBytes(StandardCharsets.US_ASCII),
                new byte[12]
        ));
        byte[] mdia = mp4Box("mdia", hdlr);
        byte[] trak = mp4Box("trak", mdia);
        byte[] moov = mp4Box("moov", concat(mvhd, trak));
        byte[] mdat = mp4Box("mdat", new byte[]{1});
        return concat(ftyp, moov, mdat);
    }

    private byte[] mp4Box(String type, byte[] content) {
        ByteBuffer buffer = ByteBuffer.allocate(8 + content.length);
        buffer.putInt(8 + content.length);
        buffer.put(type.getBytes(StandardCharsets.US_ASCII));
        buffer.put(content);
        return buffer.array();
    }

    private byte[] concat(byte[]... arrays) {
        int size = 0;
        for (byte[] array : arrays) {
            size += array.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(size);
        for (byte[] array : arrays) {
            buffer.put(array);
        }
        return buffer.array();
    }

    private LoginRequest login(String mobile, String password) {
        LoginRequest request = new LoginRequest();
        request.setMobile(mobile);
        request.setPassword(password);
        request.setGender("goddess");
        return request;
    }

    private UpdateUserNoRequest userNoRequest(String userNo) {
        UpdateUserNoRequest request = new UpdateUserNoRequest();
        request.setUserNo(userNo);
        return request;
    }

    private byte[] imageBytes(String format) throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}

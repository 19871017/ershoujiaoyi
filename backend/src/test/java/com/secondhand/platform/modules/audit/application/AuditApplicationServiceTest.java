package com.secondhand.platform.modules.audit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.secondhand.platform.modules.community.application.CommunityApplicationService;
import com.secondhand.platform.modules.media.application.MediaUploadTicketResponse;
import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
import com.secondhand.platform.modules.notification.application.NotificationApplicationService;
import com.secondhand.platform.modules.notification.application.NotificationItemResponse;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mock.web.MockMultipartFile;

class AuditApplicationServiceTest {
    @TempDir
    Path storageRoot;
    private EmbeddedDatabase database;
    private AuditApplicationService service;
    private MediaUploadTicketService mediaUploadTicketService;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        mediaUploadTicketService = new MediaUploadTicketService(jdbcTemplate, storageRoot.toString());
        service = new AuditApplicationService(jdbcTemplate, mediaUploadTicketService);
    }

    @Test
    void shouldCreateReportAuditRecordAsPending() {
        seedReportProduct(new JdbcTemplate(database), "PRODUCT-100001");
        String evidenceUrl = uploadedReportEvidence(1L, "report-proof.png");
        AuditRecordResponse response = service.submitReport(1L, "product", "PRODUCT-100001", "SPAM", "bad content", List.of(evidenceUrl));

        assertNotNull(response.auditNo());
        assertEquals(AuditApplicationService.AUDIT_TYPE_REPORT, response.auditType());
        assertEquals(1L, response.userId());
        assertEquals("PRODUCT", response.targetType());
        assertEquals("PRODUCT-100001", response.targetId());
        assertEquals("SPAM", response.reason());
        assertEquals("bad content", response.description());
        assertEquals(List.of(evidenceUrl), response.reportEvidenceUrls());
        assertEquals(AuditApplicationService.STATUS_PENDING, response.status());
        assertEquals(1, service.listAll().size());

        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        assertEquals(1, jdbcTemplate.queryForObject("select count(1) from report_record where report_no = ?", Integer.class, response.auditNo()));
        assertEquals("PENDING", jdbcTemplate.queryForObject("select report_status from report_record where report_no = ?", String.class, response.auditNo()));
        assertEquals(1L, jdbcTemplate.queryForObject("select reporter_id from report_record where report_no = ?", Long.class, response.auditNo()));
        assertEquals("PRODUCT", jdbcTemplate.queryForObject("select target_type from report_record where report_no = ?", String.class, response.auditNo()));
        assertEquals("PRODUCT-100001", jdbcTemplate.queryForObject("select target_id from report_record where report_no = ?", String.class, response.auditNo()));
        assertEquals("bad content", jdbcTemplate.queryForObject("select description from report_record where report_no = ?", String.class, response.auditNo()));
        assertEquals(evidenceUrl, jdbcTemplate.queryForObject("select evidence_urls from report_record where report_no = ?", String.class, response.auditNo()));
    }

    @Test
    void duplicatePendingReportShouldReuseExistingAuditInsteadOfFloodingQueue() {
        seedReportProduct(new JdbcTemplate(database), "PRODUCT-100001");
        String firstEvidenceUrl = uploadedReportEvidence(1L, "report-proof-one.png");
        String secondEvidenceUrl = uploadedReportEvidence(1L, "report-proof-two.png");

        AuditRecordResponse first = service.submitReport(1L, "product", "PRODUCT-100001", "SPAM", "bad content", List.of(firstEvidenceUrl));
        AuditRecordResponse duplicate = service.submitReport(1L, "PRODUCT", "PRODUCT-100001", "ABUSE", "again", List.of(secondEvidenceUrl));

        assertEquals(first.auditNo(), duplicate.auditNo());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        assertEquals(1, jdbcTemplate.queryForObject("select count(1) from audit_record where audit_type = ?", Integer.class, AuditApplicationService.AUDIT_TYPE_REPORT));
        assertEquals(1, jdbcTemplate.queryForObject("select count(1) from report_record where reporter_id = ? and target_type = ? and target_id = ?", Integer.class, 1L, "PRODUCT", "PRODUCT-100001"));
        assertEquals(firstEvidenceUrl, jdbcTemplate.queryForObject("select evidence_urls from report_record where report_no = ?", String.class, first.auditNo()));

        service.reject(first.auditNo(), "重复举报处理完成");
        AuditRecordResponse afterReviewed = service.submitReport(1L, "product", "PRODUCT-100001", "SPAM", "new pending", List.of(secondEvidenceUrl));

        assertTrue(!first.auditNo().equals(afterReviewed.auditNo()));
        assertEquals(2, jdbcTemplate.queryForObject("select count(1) from audit_record where audit_type = ?", Integer.class, AuditApplicationService.AUDIT_TYPE_REPORT));
        assertEquals(2, jdbcTemplate.queryForObject("select count(1) from report_record where reporter_id = ? and target_type = ? and target_id = ?", Integer.class, 1L, "PRODUCT", "PRODUCT-100001"));
    }

    @Test
    void reportEvidenceShouldUseStructuredColumnWithoutOverflowingDescription() {
        seedReportProduct(new JdbcTemplate(database), "PRODUCT-100001");
        List<String> evidenceUrls = List.of(
                uploadedReportEvidence(1L, "report-proof-one-with-long-name.png"),
                uploadedReportEvidence(1L, "report-proof-two-with-long-name.png"),
                uploadedReportEvidence(1L, "report-proof-three-with-long-name.png"),
                uploadedReportEvidence(1L, "report-proof-four-with-long-name.png"),
                uploadedReportEvidence(1L, "report-proof-five-with-long-name.png"),
                uploadedReportEvidence(1L, "report-proof-six-with-long-name.png")
        );

        AuditRecordResponse response = service.submitReport(1L, "product", "PRODUCT-100001", "SPAM", "商品描述明显异常", evidenceUrls);
        AuditRecordResponse adminDetail = service.getAdminDetail(response.auditNo());

        assertEquals("商品描述明显异常", adminDetail.description());
        assertEquals(evidenceUrls, adminDetail.reportEvidenceUrls());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        assertEquals("商品描述明显异常", jdbcTemplate.queryForObject("select description from audit_record where audit_no = ?", String.class, response.auditNo()));
        assertEquals("商品描述明显异常", jdbcTemplate.queryForObject("select description from report_record where report_no = ?", String.class, response.auditNo()));
        assertEquals(String.join("\n", evidenceUrls), jdbcTemplate.queryForObject("select evidence_urls from report_record where report_no = ?", String.class, response.auditNo()));
    }

    @Test
    void reportDescriptionShouldRejectOverlongTextBeforePersistence() {
        seedReportProduct(new JdbcTemplate(database), "PRODUCT-100001");

        assertEquals("report description max 512", assertThrows(IllegalArgumentException.class,
                () -> service.submitReport(1L, "product", "PRODUCT-100001", "SPAM", "A".repeat(513))).getMessage());
        assertEquals(0, new JdbcTemplate(database).queryForObject("select count(1) from audit_record", Integer.class));
    }

    @Test
    void shouldCreateWithdrawalAuditRecordAsPending() {
        AuditRecordResponse response = service.submitWithdrawal(2L, "WD-100001", "USER_WITHDRAWAL", "withdraw cash");

        assertNotNull(response.auditNo());
        assertEquals(AuditApplicationService.AUDIT_TYPE_WITHDRAWAL, response.auditType());
        assertEquals(2L, response.userId());
        assertEquals("WITHDRAWAL", response.targetType());
        assertEquals("WD-100001", response.targetId());
        assertEquals("USER_WITHDRAWAL", response.reason());
        assertEquals(AuditApplicationService.STATUS_PENDING, response.status());
    }

    @Test
    void videoIdentityAuditShouldMarkProfilePendingThenApproved() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("INSERT INTO user_account (user_no, phone, password_hash, nickname, status) VALUES (?,?,?,?,?)", "U-VIDEO-1", "13800138888", "hash", "视频卖家", "ACTIVE");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138888");
        jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, video_identity_status, video_verified) VALUES (?,?,?,?)", userId, "VERIFIED", "UNVERIFIED", false);

        String videoUrl = uploadedVideoUrl(userId, "u1.mp4");
        AuditRecordResponse created = service.submitVideoIdentity(userId, videoUrl, "真人认证视频");

        assertEquals(AuditApplicationService.AUDIT_TYPE_VIDEO_IDENTITY, created.auditType());
        assertEquals("VIDEO_IDENTITY", created.targetType());
        assertEquals(String.valueOf(userId), created.targetId());
        assertEquals("PENDING", jdbcTemplate.queryForObject("SELECT video_identity_status FROM user_profile WHERE user_id = ?", String.class, userId));
        assertEquals(false, jdbcTemplate.queryForObject("SELECT video_verified FROM user_profile WHERE user_id = ?", Boolean.class, userId));

        assertEquals("video identity evidence review required", assertThrows(IllegalStateException.class,
                () -> service.approve(created.auditNo(), "绕过后台视频复核")).getMessage());
        assertEquals("video identity evidence review required", assertThrows(IllegalStateException.class,
                () -> service.approveVideoIdentityAfterEvidenceReview(created.auditNo(), "未观看视频直接通过", 99L)).getMessage());

        markVideoIdentityEvidenceWatched(created.auditNo(), 99L);
        service.approveVideoIdentityAfterEvidenceReview(created.auditNo(), "视频本人一致", 99L);

        assertEquals("APPROVED", jdbcTemplate.queryForObject("SELECT video_identity_status FROM user_profile WHERE user_id = ?", String.class, userId));
        assertEquals(true, jdbcTemplate.queryForObject("SELECT video_verified FROM user_profile WHERE user_id = ?", Boolean.class, userId));
        assertEquals("SELLER", jdbcTemplate.queryForObject("SELECT main_role FROM user_profile WHERE user_id = ?", String.class, userId));
        AuditRecordResponse adminDetail = service.getAdminDetail(created.auditNo());
        assertEquals(videoUrl, adminDetail.videoEvidenceUrl());
        assertEquals(true, adminDetail.videoEvidenceVerified());
        List<NotificationItemResponse> notices = new NotificationApplicationService(jdbcTemplate).listNotifications(userId, "AUDIT", 20);
        assertEquals(1, notices.size());
        assertEquals("视频认证已通过", notices.get(0).title());
        assertEquals("/pages/user/identity/index", notices.get(0).targetUrl());
    }

    @Test
    void videoIdentityAuditShouldRejectDirtyUploadedMediaWithoutCreatingPendingAudit() throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("INSERT INTO user_account (user_no, phone, password_hash, nickname, status) VALUES (?,?,?,?,?)", "U-VIDEO-DIRTY", "13800138879", "hash", "脏视频卖家", "ACTIVE");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138879");
        jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, video_identity_status, video_verified) VALUES (?,?,?,?)", userId, "VERIFIED", "UNVERIFIED", false);
        MediaUploadTicketService media = serviceMedia();
        MediaUploadTicketResponse issued = media.issue(userId, "VIDEO_IDENTITY", "video/mp4", 64L, "dirty.mp4");
        Path target = storageRoot.resolve(issued.storageUrl().substring(1));
        Files.createDirectories(target.getParent());
        Files.writeString(target, "not-a-real-video");
        jdbcTemplate.update("update media_upload_ticket set status = 'UPLOADED' where ticket_no = ?", issued.ticketNo());

        assertEquals("video identity media invalid", assertThrows(IllegalArgumentException.class,
                () -> service.submitVideoIdentity(userId, issued.storageUrl(), "历史脏视频")).getMessage());

        assertEquals(0, jdbcTemplate.queryForObject("select count(1) from audit_record where audit_type = ?", Integer.class, AuditApplicationService.AUDIT_TYPE_VIDEO_IDENTITY));
        assertEquals("UNVERIFIED", jdbcTemplate.queryForObject("SELECT video_identity_status FROM user_profile WHERE user_id = ?", String.class, userId));
        assertEquals(false, jdbcTemplate.queryForObject("SELECT video_verified FROM user_profile WHERE user_id = ?", Boolean.class, userId));
    }

    @Test
    void videoIdentityApproveShouldRequireUploadedTicketAgain() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("INSERT INTO user_account (user_no, phone, password_hash, nickname, status) VALUES (?,?,?,?,?)", "U-VIDEO-TICKET", "13800138880", "hash", "脏审核卖家", "ACTIVE");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138880");
        jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, main_role, video_identity_status, video_verified) VALUES (?,?,?,?,?)", userId, "VERIFIED", "BUYER", "PENDING", false);
        jdbcTemplate.update("""
                INSERT INTO audit_record (audit_no, audit_type, user_id, target_type, target_id, reason, description, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, "AU-VIDEO-NO-TICKET", AuditApplicationService.AUDIT_TYPE_VIDEO_IDENTITY, userId, "VIDEO_IDENTITY", String.valueOf(userId), "/uploads/video-identity/missing-ticket.mp4", "历史脏数据", AuditApplicationService.STATUS_PENDING);

        assertEquals("video identity evidence review required", assertThrows(IllegalStateException.class,
                () -> service.approve("AU-VIDEO-NO-TICKET", "绕过后台视频复核")).getMessage());
        markVideoIdentityEvidenceWatched("AU-VIDEO-NO-TICKET", 99L);
        assertThrows(IllegalArgumentException.class,
                () -> service.approveVideoIdentityAfterEvidenceReview("AU-VIDEO-NO-TICKET", "不能通过无票据视频", 99L));

        AuditRecordResponse adminDetail = service.getAdminDetail("AU-VIDEO-NO-TICKET");
        assertEquals(null, adminDetail.videoEvidenceUrl());
        assertEquals(false, adminDetail.videoEvidenceVerified());
        assertEquals("PENDING", jdbcTemplate.queryForObject("SELECT video_identity_status FROM user_profile WHERE user_id = ?", String.class, userId));
        assertEquals(false, jdbcTemplate.queryForObject("SELECT video_verified FROM user_profile WHERE user_id = ?", Boolean.class, userId));
        assertEquals("BUYER", jdbcTemplate.queryForObject("SELECT main_role FROM user_profile WHERE user_id = ?", String.class, userId));
    }

    @Test
    void realNameIdentityAuditShouldMarkProfilePendingThenVerified() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("INSERT INTO user_account (user_no, phone, password_hash, nickname, status) VALUES (?,?,?,?,?)", "U-REAL-1", "13800138881", "hash", "实名用户", "ACTIVE");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138881");
        jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, video_identity_status, video_verified) VALUES (?,?,?,?)", userId, "UNVERIFIED", "UNVERIFIED", false);

        AuditRecordResponse created = service.submitRealNameIdentity(userId, "王小原", "6789");

        assertEquals(AuditApplicationService.AUDIT_TYPE_REAL_NAME_IDENTITY, created.auditType());
        assertEquals("REAL_NAME_IDENTITY", created.targetType());
        assertEquals(String.valueOf(userId), created.targetId());
        assertTrue(created.reason().contains("王**"));
        assertTrue(created.reason().contains("6789"));
        assertEquals("PENDING", jdbcTemplate.queryForObject("SELECT identity_status FROM user_profile WHERE user_id = ?", String.class, userId));

        service.approve(created.auditNo(), "实名资料一致");

        assertEquals("VERIFIED", jdbcTemplate.queryForObject("SELECT identity_status FROM user_profile WHERE user_id = ?", String.class, userId));
    }

    @Test
    void rejectedRealNameIdentityAuditShouldMarkProfileRejected() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("INSERT INTO user_account (user_no, phone, password_hash, nickname, status) VALUES (?,?,?,?,?)", "U-REAL-2", "13800138882", "hash", "实名拒绝用户", "ACTIVE");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138882");
        jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, video_identity_status, video_verified) VALUES (?,?,?,?)", userId, "UNVERIFIED", "UNVERIFIED", false);

        AuditRecordResponse created = service.submitRealNameIdentity(userId, "李小原", "1234");
        service.reject(created.auditNo(), "姓名不一致");

        assertEquals("REJECTED", jdbcTemplate.queryForObject("SELECT identity_status FROM user_profile WHERE user_id = ?", String.class, userId));
    }

    @Test
    void olderRealNameReviewShouldNotOverrideLatestIdentityStatus() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("INSERT INTO user_account (user_no, phone, password_hash, nickname, status) VALUES (?,?,?,?,?)", "U-REAL-3", "13800138883", "hash", "多次实名用户", "ACTIVE");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138883");
        jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, video_identity_status, video_verified) VALUES (?,?,?,?)", userId, "UNVERIFIED", "UNVERIFIED", false);

        AuditRecordResponse older = service.submitRealNameIdentity(userId, "赵小原", "1111");
        AuditRecordResponse latest = service.submitRealNameIdentity(userId, "赵小原", "2222");
        service.approve(latest.auditNo(), "最新实名通过");
        service.reject(older.auditNo(), "旧实名驳回");

        assertEquals("VERIFIED", jdbcTemplate.queryForObject("SELECT identity_status FROM user_profile WHERE user_id = ?", String.class, userId));
    }

    @Test
    void rejectedVideoIdentityAuditShouldNotExposeSellerAsVerifiedOrDemoteRole() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("INSERT INTO user_account (user_no, phone, password_hash, nickname, status) VALUES (?,?,?,?,?)", "U-VIDEO-2", "13800139999", "hash", "待审核卖家", "ACTIVE");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800139999");
        jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, main_role, video_identity_status, video_verified) VALUES (?,?,?,?,?)", userId, "VERIFIED", "SELLER", "UNVERIFIED", false);

        String videoUrl = uploadedVideoUrl(userId, "u2.mp4");
        AuditRecordResponse created = service.submitVideoIdentity(userId, videoUrl, "真人认证视频");
        service.reject(created.auditNo(), "画面不清晰");

        assertEquals("REJECTED", jdbcTemplate.queryForObject("SELECT video_identity_status FROM user_profile WHERE user_id = ?", String.class, userId));
        assertEquals(false, jdbcTemplate.queryForObject("SELECT video_verified FROM user_profile WHERE user_id = ?", Boolean.class, userId));
        assertEquals("SELLER", jdbcTemplate.queryForObject("SELECT main_role FROM user_profile WHERE user_id = ?", String.class, userId));
    }

    @Test
    void approvedVideoIdentityAuditShouldPreserveBothRole() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("INSERT INTO user_account (user_no, phone, password_hash, nickname, status) VALUES (?,?,?,?,?)", "U-VIDEO-BOTH", "13800139997", "hash", "双角色卖家", "ACTIVE");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800139997");
        jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, main_role, video_identity_status, video_verified) VALUES (?,?,?,?,?)", userId, "VERIFIED", "BOTH", "UNVERIFIED", false);

        AuditRecordResponse created = service.submitVideoIdentity(userId, uploadedVideoUrl(userId, "both.mp4"), "双角色认证视频");
        assertEquals("video identity evidence review required", assertThrows(IllegalStateException.class,
                () -> service.approve(created.auditNo(), "绕过后台视频复核")).getMessage());
        markVideoIdentityEvidenceWatched(created.auditNo(), 99L);
        service.approveVideoIdentityAfterEvidenceReview(created.auditNo(), "视频通过", 99L);

        assertEquals("APPROVED", jdbcTemplate.queryForObject("SELECT video_identity_status FROM user_profile WHERE user_id = ?", String.class, userId));
        assertEquals(true, jdbcTemplate.queryForObject("SELECT video_verified FROM user_profile WHERE user_id = ?", Boolean.class, userId));
        assertEquals("BOTH", jdbcTemplate.queryForObject("SELECT main_role FROM user_profile WHERE user_id = ?", String.class, userId));
    }

    @Test
    void olderVideoIdentityReviewShouldNotOverrideLatestSellerStatus() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("INSERT INTO user_account (user_no, phone, password_hash, nickname, status) VALUES (?,?,?,?,?)", "U-VIDEO-3", "13800139998", "hash", "多次认证卖家", "ACTIVE");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800139998");
        jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, main_role, video_identity_status, video_verified) VALUES (?,?,?,?,?)", userId, "VERIFIED", "BUYER", "UNVERIFIED", false);

        AuditRecordResponse older = service.submitVideoIdentity(userId, uploadedVideoUrl(userId, "older.mp4"), "旧视频");
        AuditRecordResponse latest = service.submitVideoIdentity(userId, uploadedVideoUrl(userId, "latest.mp4"), "新视频");
        assertEquals("video identity evidence review required", assertThrows(IllegalStateException.class,
                () -> service.approve(latest.auditNo(), "绕过后台视频复核")).getMessage());
        markVideoIdentityEvidenceWatched(latest.auditNo(), 99L);
        service.approveVideoIdentityAfterEvidenceReview(latest.auditNo(), "新视频通过", 99L);
        service.reject(older.auditNo(), "旧视频不清晰");

        assertEquals("APPROVED", jdbcTemplate.queryForObject("SELECT video_identity_status FROM user_profile WHERE user_id = ?", String.class, userId));
        assertEquals(true, jdbcTemplate.queryForObject("SELECT video_verified FROM user_profile WHERE user_id = ?", Boolean.class, userId));
        assertEquals("SELLER", jdbcTemplate.queryForObject("SELECT main_role FROM user_profile WHERE user_id = ?", String.class, userId));
    }

    @Test
    void videoIdentityAuditShouldRejectUnsafeVideoUrlAndMissingUser() {
        assertThrows(IllegalArgumentException.class, () -> service.submitVideoIdentity(404L, "/uploads/video-identity/404/missing.mp4", "missing user"));
        assertThrows(IllegalArgumentException.class, () -> service.submitVideoIdentity(1L, "local://video.mp4", "local video"));
        assertThrows(IllegalArgumentException.class, () -> service.submitVideoIdentity(1L, "https://cdn.example.com/blocked-preview/video.mp4", "blocked preview video"));
        assertThrows(IllegalArgumentException.class, () -> service.submitVideoIdentity(1L, "https://cdn.example.com/video-identity/free.mp4", "external video"));
    }

    @Test
    void realNameIdentityAuditShouldRejectInvalidInputAndMissingUser() {
        assertThrows(IllegalArgumentException.class, () -> service.submitRealNameIdentity(404L, "王小原", "1234"));
        assertThrows(IllegalArgumentException.class, () -> service.submitRealNameIdentity(1L, "测", "1234"));
        assertThrows(IllegalArgumentException.class, () -> service.submitRealNameIdentity(1L, "preview-user", "1234"));
        assertThrows(IllegalArgumentException.class, () -> service.submitRealNameIdentity(1L, "测试用户", "1234"));
        assertThrows(IllegalArgumentException.class, () -> service.submitRealNameIdentity(1L, "王小原1", "1234"));
        assertThrows(IllegalArgumentException.class, () -> service.submitRealNameIdentity(1L, "王小原", "123"));
        assertThrows(IllegalArgumentException.class, () -> service.submitRealNameIdentity(1L, "王小原", "abcd"));
    }

    @Test
    void getAdminAuditDetailShouldReturnSafePersistedRecordOnly() {
        seedReportConversation(new JdbcTemplate(database), "CHAT-100088", 5L, 2L);
        String evidenceUrl = uploadedReportEvidence(5L, "report-admin-proof.png");
        AuditRecordResponse created = service.submitReport(5L, "chat", "CHAT-100088", "HARASSMENT", "辱骂骚扰，凭证手机号 13800138000", List.of(evidenceUrl));

        AuditRecordResponse detail = service.getAdminDetail(created.auditNo());

        assertEquals(created.auditNo(), detail.auditNo());
        assertEquals("CHAT", detail.targetType());
        assertEquals("CHAT-100088", detail.targetId());
        assertTrue(detail.description().contains("138****8000"));
        assertEquals(List.of(evidenceUrl), detail.reportEvidenceUrls());
        assertThrows(IllegalArgumentException.class, () -> service.getAdminDetail("AUDIT-GOODS-001"));
        assertThrows(IllegalArgumentException.class, () -> service.getAdminDetail("preview-audit"));
    }

    @Test
    void approveShouldMovePendingAuditToApproved() {
        seedReportUser(new JdbcTemplate(database), 2L, "USER-100002", "被举报用户2");
        AuditRecordResponse created = service.submitReport(1L, "user", "2", "ABUSE", "abuse");

        AuditRecordResponse reviewed = service.approve(created.auditNo(), "approved ok");

        assertEquals(AuditApplicationService.STATUS_APPROVED, reviewed.status());
        assertEquals("approved ok", reviewed.reviewRemark());
        assertNotNull(reviewed.reviewedAt());
        assertEquals(AuditApplicationService.STATUS_APPROVED, service.get(created.auditNo()).status());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        assertEquals(AuditApplicationService.STATUS_APPROVED, jdbcTemplate.queryForObject("select report_status from report_record where report_no = ?", String.class, created.auditNo()));
        assertNotNull(jdbcTemplate.queryForObject("select handled_at from report_record where report_no = ?", java.sql.Timestamp.class, created.auditNo()));
        List<NotificationItemResponse> notices = new NotificationApplicationService(new JdbcTemplate(database)).listNotifications(1L, "AUDIT", 20);
        assertEquals(1, notices.size());
        assertEquals("举报已受理", notices.get(0).title());
        assertEquals("/pages/notification/index", notices.get(0).targetUrl());
    }

    @Test
    void rejectShouldMovePendingAuditToRejected() {
        AuditRecordResponse created = service.submitWithdrawal(1L, "WD-100001", "WITHDRAW", "missing info");

        AuditRecordResponse reviewed = service.reject(created.auditNo(), "reject reason");

        assertEquals(AuditApplicationService.STATUS_REJECTED, reviewed.status());
        assertEquals("reject reason", reviewed.reviewRemark());
        assertNotNull(reviewed.reviewedAt());
        assertEquals(AuditApplicationService.STATUS_REJECTED, service.get(created.auditNo()).status());
        assertEquals(0, new JdbcTemplate(database).queryForObject("select count(1) from report_record where report_no = ?", Integer.class, created.auditNo()));
        List<NotificationItemResponse> notices = new NotificationApplicationService(new JdbcTemplate(database)).listNotifications(1L, "AUDIT", 20);
        assertEquals(1, notices.size());
        assertEquals("提现审核未通过", notices.get(0).title());
        assertEquals("/pages/notification/index", notices.get(0).targetUrl());
    }

    @Test
    void approveAndRejectShouldPersistMaskedAdminAuditLogRows() {
        seedReportUser(new JdbcTemplate(database), 2L, "USER-100002", "被举报用户2");
        AuditRecordResponse reportAudit = service.submitReport(1L, "user", "2", "ABUSE", "涉及手机号 13800138000");
        AuditRecordResponse withdrawalAudit = service.submitWithdrawal(2L, "WD-20260510-8899", "WITHDRAW", "提现复核");

        service.approve(reportAudit.auditNo(), "同意处理 13800138001");
        service.reject(withdrawalAudit.auditNo(), "资料不符 13800138002");

        List<AdminAuditLogResponse> logs = service.listAdminAuditLogs(null, 10).stream()
                .filter(log -> log.action().startsWith("AUDIT_"))
                .toList();

        assertEquals(2, logs.size());
        AdminAuditLogResponse latest = logs.get(0);
        AdminAuditLogResponse first = logs.get(1);
        assertEquals("AUDIT_REJECT", latest.action());
        assertEquals("AUDIT", latest.targetType());
        assertEquals(withdrawalAudit.auditNo(), latest.targetId());
        assertEquals("SUCCESS", latest.result());
        assertEquals("资料不符 138****8002", latest.summary());
        assertEquals("AUDIT_APPROVE", first.action());
        assertEquals(reportAudit.auditNo(), first.targetId());
        assertEquals("同意处理 138****8001", first.summary());
    }

    @Test
    void reviewShouldPersistActualAdminOperatorIdNotTargetUserId() {
        seedReportUser(new JdbcTemplate(database), 12L, "USER-100012", "被举报用户12");
        AuditRecordResponse reportAudit = service.submitReport(11L, "user", "12", "ABUSE", "举报内容");

        service.approve(reportAudit.auditNo(), "管理员复核通过", 99L);

        List<AdminAuditLogResponse> logs = service.listAdminAuditLogs(null, 10).stream()
                .filter(log -> "AUDIT_APPROVE".equals(log.action()))
                .toList();
        assertEquals(1, logs.size());
        assertEquals(99L, logs.get(0).operatorId());
        assertEquals(reportAudit.auditNo(), logs.get(0).targetId());
    }

    @Test
    void approvingProductReportShouldOfflineReportedProductAndPersistDispositionLog() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("""
                insert into product_item (product_no,seller_id,title,category,price,product_status,audit_status,visible,trade_rule,created_at,updated_at)
                values (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, "PRODUCT-100777", 21L, "被举报商品", "女装", new BigDecimal("88.00"), "ACTIVE", "APPROVED", true, "offline-face-to-face-after-platform-order");
        AuditRecordResponse reportAudit = service.submitReport(11L, "product", "PRODUCT-100777", "SPAM", "商品违规");

        service.approve(reportAudit.auditNo(), "商品举报成立", 99L);

        assertEquals("OFFLINE", jdbcTemplate.queryForObject("select product_status from product_item where product_no = ?", String.class, "PRODUCT-100777"));
        assertEquals(false, jdbcTemplate.queryForObject("select visible from product_item where product_no = ?", Boolean.class, "PRODUCT-100777"));
        assertEquals("SUCCESS", jdbcTemplate.queryForObject("select result from admin_audit_log where action = ? and target_id = ?", String.class, "REPORT_DISPOSITION", "PRODUCT-100777"));
        assertEquals(99L, jdbcTemplate.queryForObject("select operator_id from admin_audit_log where action = ? and target_id = ?", Long.class, "REPORT_DISPOSITION", "PRODUCT-100777"));
    }

    @Test
    void approvingCommunityReportShouldBlockPostAndRejectingReportShouldNotMutateTarget() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("""
                insert into community_post (post_no,author_id,title,topic,content,status,created_at,updated_at)
                values (?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, "POST-11-1770000000001", 11L, "被举报动态", "生活日常", "违规内容", "PUBLISHED");
        jdbcTemplate.update("""
                insert into community_post (post_no,author_id,title,topic,content,status,created_at,updated_at)
                values (?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, "POST-11-1770000000002", 11L, "未成立举报动态", "生活日常", "正常内容", "PUBLISHED");
        AuditRecordResponse approvedReport = service.submitReport(12L, "community", "POST-11-1770000000001", "SPAM", "社区违规");
        AuditRecordResponse rejectedReport = service.submitReport(12L, "community", "POST-11-1770000000002", "SPAM", "误报");

        service.approve(approvedReport.auditNo(), "社区举报成立", 99L);
        service.reject(rejectedReport.auditNo(), "举报不成立", 99L);

        assertEquals("BLOCKED", jdbcTemplate.queryForObject("select status from community_post where post_no = ?", String.class, "POST-11-1770000000001"));
        assertEquals("PUBLISHED", jdbcTemplate.queryForObject("select status from community_post where post_no = ?", String.class, "POST-11-1770000000002"));
        assertEquals(AuditApplicationService.STATUS_APPROVED, jdbcTemplate.queryForObject("select report_status from report_record where report_no = ?", String.class, approvedReport.auditNo()));
        assertEquals(AuditApplicationService.STATUS_REJECTED, jdbcTemplate.queryForObject("select report_status from report_record where report_no = ?", String.class, rejectedReport.auditNo()));
        assertNotNull(jdbcTemplate.queryForObject("select handled_at from report_record where report_no = ?", java.sql.Timestamp.class, approvedReport.auditNo()));
        assertNotNull(jdbcTemplate.queryForObject("select handled_at from report_record where report_no = ?", java.sql.Timestamp.class, rejectedReport.auditNo()));
    }

    @Test
    void approvingCommunityCommentReportShouldHideOnlyThatComment() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        seedReportUser(jdbcTemplate, 11L, "USER-REPORT-11", "发帖人");
        seedReportUser(jdbcTemplate, 12L, "USER-REPORT-12", "评论人");
        jdbcTemplate.update("""
                insert into community_post (post_no,author_id,title,topic,content,status,comment_count,created_at,updated_at)
                values (?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, "POST-11-1770000000101", 11L, "评论举报动态", "生活日常", "只屏蔽被举报的评论。", "PUBLISHED", 2);
        Long postId = jdbcTemplate.queryForObject("select id from community_post where post_no = ?", Long.class, "POST-11-1770000000101");
        jdbcTemplate.update("""
                insert into community_comment (comment_no,post_id,author_id,content,status,created_at)
                values (?,?,?,?,?,CURRENT_TIMESTAMP)
                """, "CMT-12-1770000000101-AAAAAA1111", postId, 12L, "违规评论内容", "PUBLISHED");
        jdbcTemplate.update("""
                insert into community_comment (comment_no,post_id,author_id,content,status,created_at)
                values (?,?,?,?,?,CURRENT_TIMESTAMP)
                """, "CMT-12-1770000000102-BBBBBB2222", postId, 12L, "正常评论内容", "PUBLISHED");
        CommunityApplicationService communityService = new CommunityApplicationService(jdbcTemplate, new MediaUploadTicketService(jdbcTemplate));
        assertEquals(2, communityService.detail(postId, 11L).getComments().size());

        AuditRecordResponse report = service.submitReport(11L, "community_comment", "CMT-12-1770000000101-AAAAAA1111", "SPAM", "评论违规");
        service.approve(report.auditNo(), "评论举报成立", 99L);

        assertEquals("BLOCKED", jdbcTemplate.queryForObject("select status from community_comment where comment_no = ?", String.class, "CMT-12-1770000000101-AAAAAA1111"));
        assertEquals("PUBLISHED", jdbcTemplate.queryForObject("select status from community_comment where comment_no = ?", String.class, "CMT-12-1770000000102-BBBBBB2222"));
        assertEquals(1, jdbcTemplate.queryForObject("select comment_count from community_post where id = ?", Integer.class, postId));
        assertEquals(1, communityService.detail(postId, 11L).getComments().size());
        assertEquals("SUCCESS", jdbcTemplate.queryForObject("""
                select result
                from admin_audit_log
                where action = ? and target_type = ? and target_id = ?
                """, String.class, "REPORT_DISPOSITION", "COMMUNITY_COMMENT", "CMT-12-1770000000101-AAAAAA1111"));
        assertEquals("report target not found", assertThrows(IllegalArgumentException.class,
                () -> service.submitReport(11L, "community_comment", "CMT-12-1770000000101-AAAAAA1111", "SPAM", "重复举报已屏蔽评论")).getMessage());
    }

    @Test
    void approvedCommunityReportShouldHidePostFromPublicFeedAndDetailButKeepAdminTrace() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("""
                insert into user_account (id,user_no,phone,password_hash,nickname,status,created_at,updated_at)
                values (?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, 311L, "U-REPORT-POST", "13800139311", "hash", "被举报发帖人", "ACTIVE");
        jdbcTemplate.update("""
                insert into community_post (post_no,author_id,title,topic,content,status,created_at,updated_at)
                values (?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, "POST-311-1770000000311", 311L, "公开侧必须隐藏", "生活日常", "举报成立后公开列表和详情都不能再展示。", "PUBLISHED");
        Long postId = jdbcTemplate.queryForObject("select id from community_post where post_no = ?", Long.class, "POST-311-1770000000311");
        CommunityApplicationService communityService = new CommunityApplicationService(jdbcTemplate, new MediaUploadTicketService(jdbcTemplate));
        assertEquals(1, communityService.listPublishedPosts(20, 12L).size());

        AuditRecordResponse approvedReport = service.submitReport(12L, "community_post", "POST-311-1770000000311", "SPAM", "社区违规内容");
        service.approve(approvedReport.auditNo(), "社区举报成立", 99L);

        assertTrue(communityService.listPublishedPosts(20, 12L).isEmpty());
        assertEquals("post not found", assertThrows(IllegalArgumentException.class,
                () -> communityService.detail(postId, 12L)).getMessage());
        assertEquals("BLOCKED", communityService.adminDetail("POST-311-1770000000311").getStatus());
        assertEquals("SUCCESS", jdbcTemplate.queryForObject("""
                select result
                from admin_audit_log
                where action = ? and target_type = ? and target_id = ?
                """, String.class, "REPORT_DISPOSITION", "COMMUNITY_POST", "POST-311-1770000000311"));
    }

    @Test
    void reportShouldRejectMissingOrClosedTargetsWithoutPersistingAuditRows() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("""
                insert into community_post (post_no,author_id,title,topic,content,status,created_at,updated_at)
                values (?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, "POST-311-1770000000999", 311L, "已处理动态", "生活日常", "这条已被处理，不能继续创建举报。", "BLOCKED");
        jdbcTemplate.update("""
                insert into product_item (product_no,seller_id,title,category,price,product_status,audit_status,visible,trade_rule,created_at,updated_at)
                values (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, "PRODUCT-100999", 21L, "已成交商品", "女装", new BigDecimal("88.00"), "SOLD", "APPROVED", false, "offline-face-to-face-after-platform-order");
        int auditCountBefore = jdbcTemplate.queryForObject("select count(1) from audit_record", Integer.class);
        int reportCountBefore = jdbcTemplate.queryForObject("select count(1) from report_record", Integer.class);

        assertEquals("report target not found", assertThrows(IllegalArgumentException.class,
                () -> service.submitReport(12L, "community_post", "POST-311-1770000000999", "SPAM", "已处理动态")).getMessage());
        assertEquals("report target not found", assertThrows(IllegalArgumentException.class,
                () -> service.submitReport(12L, "community_post", "POST-311-1770000099999", "SPAM", "不存在动态")).getMessage());
        assertEquals("report target not found", assertThrows(IllegalArgumentException.class,
                () -> service.submitReport(12L, "product", "PRODUCT-100999", "SPAM", "已成交商品")).getMessage());
        assertEquals("report target not found", assertThrows(IllegalArgumentException.class,
                () -> service.submitReport(12L, "user", "USER-MISSING-0001", "ABUSE", "不存在用户")).getMessage());

        assertEquals(auditCountBefore, jdbcTemplate.queryForObject("select count(1) from audit_record", Integer.class));
        assertEquals(reportCountBefore, jdbcTemplate.queryForObject("select count(1) from report_record", Integer.class));
    }

    @Test
    void approvingUserReportShouldDisableReportedActiveUser() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("insert into user_account (id,user_no,phone,password_hash,nickname,status,created_at,updated_at) values (?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                222L, "USER-100222", "13800139222", "hash", "被举报用户", "ACTIVE");
        AuditRecordResponse reportAudit = service.submitReport(11L, "user", "222", "ABUSE", "用户违规");

        service.approve(reportAudit.auditNo(), "用户举报成立", 99L);

        assertEquals("DISABLED", jdbcTemplate.queryForObject("select status from user_account where id = ?", String.class, 222L));
    }

    @Test
    void repeatedReviewShouldBeRejected() {
        seedReportProduct(new JdbcTemplate(database), "PRODUCT-100001");
        AuditRecordResponse created = service.submitReport(1L, "product", "PRODUCT-100001", "SPAM", "bad content");
        service.approve(created.auditNo(), "approved");

        assertThrows(IllegalStateException.class, () -> service.approve(created.auditNo(), "again"));
        assertThrows(IllegalStateException.class, () -> service.reject(created.auditNo(), "change mind"));
    }

    @Test
    void reportShouldAcceptBackendOrderNumberAsOrderTarget() {
        seedReportOrder(new JdbcTemplate(database), "OD-100001");
        AuditRecordResponse created = service.submitReport(1L, "order", "OD-100001", "ORDER_RISK", "订单纠纷");

        assertEquals("ORDER", created.targetType());
        assertEquals("OD-100001", created.targetId());
        assertEquals(AuditApplicationService.STATUS_PENDING, created.status());
    }

    @Test
    void reportShouldAcceptAfterSalesNumberAsAfterSalesTarget() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        seedReportOrder(jdbcTemplate, "OD-100001");
        seedReportAfterSales(jdbcTemplate, "AS-100001", "OD-100001");
        AuditRecordResponse created = service.submitReport(1L, "after_sales", "AS-100001", "AFTER_SALES_RISK", "售后纠纷");

        assertEquals("AFTER_SALES", created.targetType());
        assertEquals("AS-100001", created.targetId());
        assertEquals(AuditApplicationService.STATUS_PENDING, created.status());
    }

    @Test
    void shouldRejectInvalidReportAndWithdrawalAuditCreation() {
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(0L, "product", "P-1", "SPAM", null));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, " ", "P-1", "SPAM", null));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "product", " ", "SPAM", null));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "product", "UNKNOWN", "SPAM", null));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "product", "preview-chat", "SPAM", null));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "product", "demo-product-1", "SPAM", null));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "product", "abc", "SPAM", null));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "product", "0", "SPAM", null));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "product", "P-1", "SPAM", null));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "user", "USER-abc", "SPAM", null));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "order", "ORDER-12", "SPAM", null));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "order", "OD-0", "SPAM", null));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "order", "OD-100000000000", "SPAM", null));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "after_sales", "123", "SPAM", null));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "after_sales", "AS-12", "SPAM", null));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "chat", "CHAT-12", "SPAM", null));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "report", "REPORT-12", "SPAM", null));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "product", "P-1", " ", null));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "product", "PRODUCT-100001", "SPAM", "bad", List.of("local://proof.png")));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "product", "PRODUCT-100001", "SPAM", "bad", List.of("https://cdn.example.com/proof.png")));
        assertThrows(IllegalArgumentException.class, () -> service.submitReport(1L, "product", "PRODUCT-100001", "SPAM", "bad", List.of("/uploads/evidence/report/2/free.png")));
        assertThrows(IllegalArgumentException.class, () -> service.submitWithdrawal(null, "WD-1", "WITHDRAW", null));
    }

    @Test
    void recordsShouldSurviveServiceRecreationWithSameDatabase() {
        seedReportConversation(new JdbcTemplate(database), "CHAT-100900", 9L, 2L);
        AuditRecordResponse created = service.submitReport(9L, "chat", "CHAT-100900", "HARASSMENT", "bad chat");

        AuditApplicationService reloaded = new AuditApplicationService(new JdbcTemplate(database));
        AuditRecordResponse loaded = reloaded.get(created.auditNo());
        List<AuditRecordResponse> all = reloaded.listAll();

        assertEquals(created.auditNo(), loaded.auditNo());
        assertEquals("CHAT", loaded.targetType());
        assertEquals("CHAT-100900", loaded.targetId());
        assertTrue(all.stream().anyMatch(item -> created.auditNo().equals(item.auditNo())));
    }

    @Test
    void adminAuditListShouldFilterReportsByStatusKeywordAndApplicantId() {
        seedReportProduct(new JdbcTemplate(database), "PRODUCT-100900");
        AuditRecordResponse report = service.submitReport(777L, "product", "PRODUCT-100900", "SPAM", "商品举报内容");
        service.submitWithdrawal(888L, "WD-100900", "WITHDRAW", "提现复核");

        List<AuditRecordResponse> reportRows = service.listAdminAudits("REPORT", "PENDING", "PRODUCT-100900", 20);
        List<AuditRecordResponse> applicantRows = service.listAdminAudits("ALL", "ALL", "777", 20);

        assertEquals(1, reportRows.size());
        assertEquals(report.auditNo(), reportRows.get(0).auditNo());
        assertEquals(1, applicantRows.size());
        assertEquals(report.auditNo(), applicantRows.get(0).auditNo());
    }

    @Test
    void adminAuditListShouldFilterRealNameIdentityByTypeAndKeyword() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("INSERT INTO user_account (user_no, phone, password_hash, nickname, status) VALUES (?,?,?,?,?)", "U-REAL-LIST", "13800138884", "hash", "实名列表用户", "ACTIVE");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800138884");
        jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, video_identity_status, video_verified) VALUES (?,?,?,?)", userId, "UNVERIFIED", "UNVERIFIED", false);
        AuditRecordResponse realName = service.submitRealNameIdentity(userId, "钱小原", "9988");
        seedReportProduct(jdbcTemplate, "PRODUCT-100901");
        service.submitReport(778L, "product", "PRODUCT-100901", "SPAM", "商品举报内容");

        List<AuditRecordResponse> rows = service.listAdminAudits("REAL_NAME_IDENTITY", "PENDING", "9988", 20);

        assertEquals(1, rows.size());
        assertEquals(realName.auditNo(), rows.get(0).auditNo());
        assertEquals(AuditApplicationService.AUDIT_TYPE_REAL_NAME_IDENTITY, rows.get(0).auditType());
    }

    @Test
    void adminAuditListShouldRejectInvalidFiltersFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> service.listAdminAudits("ROOT", "PENDING", null, 20));
        assertThrows(IllegalArgumentException.class, () -> service.listAdminAudits("REPORT", "PROCESSING", null, 20));
        assertThrows(IllegalArgumentException.class, () -> service.listAdminAudits("REPORT", "PENDING", "preview-report", 20));
        assertThrows(IllegalArgumentException.class, () -> service.listAdminAudits("REPORT", "PENDING", "A".repeat(65), 20));
        assertThrows(IllegalArgumentException.class, () -> service.listAdminAudits("REPORT", "PENDING", "0", 20));
        assertThrows(IllegalArgumentException.class, () -> service.listAdminAudits("REPORT", "PENDING", null, 101));
    }

    @Test
    void adminAuditLogsShouldReturnPersistedSafeSummariesWithPositiveCursor() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("insert into admin_audit_log (action,operator_id,target_type,target_id,result,summary,created_at) values (?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                "AUDIT_APPROVE", 7L, "AUDIT", "AU-20260510-0001", "SUCCESS", "处理用户 13800138000 的审核");
        Long logId = jdbcTemplate.queryForObject("select id from admin_audit_log where target_id = ?", Long.class, "AU-20260510-0001");

        List<AdminAuditLogResponse> firstPage = service.listAdminAuditLogs(null, 50);
        List<AdminAuditLogResponse> nextPage = service.listAdminAuditLogs(logId, 50);

        assertEquals(1, firstPage.size());
        assertEquals("AUDIT_APPROVE", firstPage.get(0).action());
        assertEquals("处理用户 138****8000 的审核", firstPage.get(0).summary());
        assertEquals(0, nextPage.size());
        assertThrows(IllegalArgumentException.class, () -> service.listAdminAuditLogs(0L, 50));
        assertThrows(IllegalArgumentException.class, () -> service.listAdminAuditLogs(null, 101));
    }

    @Test
    void adminAuditLogsShouldMaskBankCardLikeAccountNumbersInSummaries() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("insert into admin_audit_log (action,operator_id,target_type,target_id,result,summary,created_at) values (?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                "WITHDRAWAL_REVIEW", 8L, "WITHDRAWAL", "WD-20260510-9901", "APPROVED", "提现账号 6222020202020208088 已复核，手机号 13800138000");

        AdminAuditLogResponse log = service.listAdminAuditLogs(null, 10).get(0);

        assertEquals("提现账号 622202********8088 已复核，手机号 138****8000", log.summary());
    }

    @Test
    void adminDashboardSummaryShouldAggregatePersistedBackofficeCountsOnly() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("insert into audit_record (audit_no,audit_type,user_id,target_type,target_id,reason,description,status,created_at) values (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                "AU-DASH-20260510-0001", "REPORT", 1L, "PRODUCT", "PRODUCT-100001", "SPAM", "待审核", "PENDING");
        jdbcTemplate.update("insert into audit_record (audit_no,audit_type,user_id,target_type,target_id,reason,description,status,created_at) values (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                "AU-DASH-20260510-0002", "REPORT", 2L, "PRODUCT", "PRODUCT-100002", "SPAM", "已通过", "APPROVED");
        jdbcTemplate.update("insert into withdrawal_record (withdrawal_no,user_id,amount,payment_method,account_name,account_no,status,created_at) values (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                "WD-20260510-9001", 3L, new BigDecimal("88.00"), "ALIPAY", "王*", "13800138000", "PENDING");
        jdbcTemplate.update("insert into after_sales_record (after_sales_no,order_no,applicant_id,after_sales_type,refund_amount,reason,description,evidence_urls,after_sales_status,created_at) values (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                "AS-DASH-20260510-0001", "OD-DASH01", 4L, "REFUND_ONLY", new BigDecimal("12.00"), "尺码", "描述足够长", "/uploads/evidence/after-sales/4/proof.jpg", "PENDING_REVIEW");
        jdbcTemplate.update("insert into user_account (user_no,phone,password_hash,nickname,status,created_at) values (?,?,?,?,?,CURRENT_TIMESTAMP)",
                "U-DASH-1", "13800139001", "hash", "活跃用户", "ACTIVE");
        jdbcTemplate.update("insert into user_account (user_no,phone,password_hash,nickname,status,created_at) values (?,?,?,?,?,CURRENT_TIMESTAMP)",
                "U-DASH-2", "13800139002", "hash", "禁用用户", "DISABLED");
        jdbcTemplate.update("insert into trade_order (order_no,product_id,goods_id,product_no,product_title,trade_rule_snapshot,buyer_id,seller_id,amount,order_status,accepted_trade_rule,created_at) values (?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                "OD-DASH01", 10L, 10L, "PD-DASH01", "今日订单", "server-record", 4L, 5L, new BigDecimal("199.50"), "PAID", true);

        AdminDashboardSummary summary = service.getAdminDashboardSummary();

        assertEquals("dashboard-ready", summary.status());
        assertEquals(1, summary.pendingAudits());
        assertEquals(1, summary.approvedAudits());
        assertEquals(0, summary.rejectedAudits());
        assertEquals(1, summary.pendingWithdrawals());
        assertEquals(1, summary.pendingAfterSales());
        assertEquals(1, summary.activeUsers());
        assertEquals(1, summary.todayOrders());
        assertEquals(0, new BigDecimal("199.50").compareTo(summary.grossMerchandiseValue()));
    }

    private String uploadedVideoUrl(Long userId, String filename) {
        try {
            byte[] videoBytes = minimalMp4WithDurationSeconds(10);
            MediaUploadTicketResponse ticket = serviceMedia().issue(userId, "VIDEO_IDENTITY", "video/mp4", (long) videoBytes.length, filename);
            MockMultipartFile file = new MockMultipartFile("file", filename, "video/mp4", videoBytes);
            return serviceMedia().storeUploadedFile(userId, ticket.ticketNo(), ticket.uploadToken(), file).storageUrl();
        } catch (Exception exception) {
            throw new IllegalStateException("test video upload failed", exception);
        }
    }

    private String uploadedReportEvidence(Long userId, String filename) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        String storageUrl = serviceMedia().issue(userId, "REPORT_EVIDENCE", "image/png", 600_000L, filename).storageUrl();
        jdbcTemplate.update("update media_upload_ticket set status = 'UPLOADED' where owner_user_id = ? and storage_url = ?", userId, storageUrl);
        return storageUrl;
    }

    private MediaUploadTicketService serviceMedia() {
        return mediaUploadTicketService;
    }

    private void markVideoIdentityEvidenceWatched(String auditNo, Long operatorId) {
        new JdbcTemplate(database).update("""
                insert into admin_audit_log (action,operator_id,target_type,target_id,result,summary,created_at)
                values (?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """, "VIDEO_IDENTITY_MEDIA_WATCHED", operatorId, "AUDIT", auditNo, "SUCCESS", "测试已完成视频认证资料观看");
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

    private void seedReportUser(JdbcTemplate jdbcTemplate, Long id, String userNo, String nickname) {
        jdbcTemplate.update("""
                insert into user_account (id,user_no,phone,password_hash,nickname,status,created_at,updated_at)
                select ?, ?, ?, 'hash', ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                where not exists (select 1 from user_account where id = ?)
                """, id, userNo, "1380013" + String.format("%04d", id), nickname, id);
    }

    private void seedReportProduct(JdbcTemplate jdbcTemplate, String productNo) {
        jdbcTemplate.update("""
                insert into product_item (product_no,seller_id,title,category,price,product_status,audit_status,visible,trade_rule,created_at,updated_at)
                select ?, 21, ?, '女装', ?, 'ACTIVE', 'APPROVED', TRUE, 'offline-face-to-face-after-platform-order', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                where not exists (select 1 from product_item where product_no = ?)
                """, productNo, "举报目标商品" + productNo, new BigDecimal("88.00"), productNo);
    }

    private void seedReportOrder(JdbcTemplate jdbcTemplate, String orderNo) {
        jdbcTemplate.update("""
                insert into trade_order (order_no,product_id,goods_id,product_no,product_title,trade_rule_snapshot,buyer_id,seller_id,amount,order_status,accepted_trade_rule,created_at,updated_at)
                select ?, 100001, 100001, 'PRODUCT-100001', '举报目标订单商品', 'server-record', 1, 21, ?, 'PAID', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                where not exists (select 1 from trade_order where order_no = ?)
                """, orderNo, new BigDecimal("88.00"), orderNo);
    }

    private void seedReportAfterSales(JdbcTemplate jdbcTemplate, String afterSalesNo, String orderNo) {
        jdbcTemplate.update("""
                insert into after_sales_record (after_sales_no,order_no,applicant_id,after_sales_type,refund_amount,reason,description,evidence_urls,after_sales_status,created_at,updated_at)
                select ?, ?, 1, 'REFUND_ONLY', ?, '售后纠纷', '售后举报目标描述', '', 'PENDING_REVIEW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                where not exists (select 1 from after_sales_record where after_sales_no = ?)
                """, afterSalesNo, orderNo, new BigDecimal("12.00"), afterSalesNo);
    }

    private void seedReportConversation(JdbcTemplate jdbcTemplate, String conversationNo, Long ownerId, Long peerId) {
        seedReportUser(jdbcTemplate, ownerId, "USER-CHAT-" + ownerId, "聊天用户" + ownerId);
        seedReportUser(jdbcTemplate, peerId, "USER-CHAT-" + peerId, "聊天用户" + peerId);
        jdbcTemplate.update("""
                insert into im_conversation (conversation_no,owner_user_id,peer_user_id,conversation_type,last_seq,last_message_summary,created_at,updated_at)
                select ?, ?, ?, 'SINGLE', 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                where not exists (select 1 from im_conversation where conversation_no = ?)
                """, conversationNo, ownerId, peerId, conversationNo);
    }
}

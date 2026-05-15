package com.secondhand.platform.modules.audit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class AuditApplicationServiceTest {
    private EmbeddedDatabase database;
    private AuditApplicationService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        service = new AuditApplicationService(new JdbcTemplate(database));
    }

    @Test
    void shouldCreateReportAuditRecordAsPending() {
        String evidenceUrl = serviceMedia().issue(1L, "REPORT_EVIDENCE", "image/png", 600_000L, "report-proof.png").storageUrl();
        AuditRecordResponse response = service.submitReport(1L, "product", "PRODUCT-100001", "SPAM", "bad content", List.of(evidenceUrl));

        assertNotNull(response.auditNo());
        assertEquals(AuditApplicationService.AUDIT_TYPE_REPORT, response.auditType());
        assertEquals(1L, response.userId());
        assertEquals("PRODUCT", response.targetType());
        assertEquals("PRODUCT-100001", response.targetId());
        assertEquals("SPAM", response.reason());
        assertTrue(response.description().contains("bad content"));
        assertTrue(response.description().contains(evidenceUrl));
        assertEquals(AuditApplicationService.STATUS_PENDING, response.status());
        assertEquals(1, service.listAll().size());
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

        service.approve(created.auditNo(), "视频本人一致");

        assertEquals("APPROVED", jdbcTemplate.queryForObject("SELECT video_identity_status FROM user_profile WHERE user_id = ?", String.class, userId));
        assertEquals(true, jdbcTemplate.queryForObject("SELECT video_verified FROM user_profile WHERE user_id = ?", Boolean.class, userId));
    }

    @Test
    void rejectedVideoIdentityAuditShouldNotExposeSellerAsVerified() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("INSERT INTO user_account (user_no, phone, password_hash, nickname, status) VALUES (?,?,?,?,?)", "U-VIDEO-2", "13800139999", "hash", "待审核卖家", "ACTIVE");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800139999");
        jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, video_identity_status, video_verified) VALUES (?,?,?,?)", userId, "VERIFIED", "UNVERIFIED", false);

        String videoUrl = uploadedVideoUrl(userId, "u2.mp4");
        AuditRecordResponse created = service.submitVideoIdentity(userId, videoUrl, "真人认证视频");
        service.reject(created.auditNo(), "画面不清晰");

        assertEquals("REJECTED", jdbcTemplate.queryForObject("SELECT video_identity_status FROM user_profile WHERE user_id = ?", String.class, userId));
        assertEquals(false, jdbcTemplate.queryForObject("SELECT video_verified FROM user_profile WHERE user_id = ?", Boolean.class, userId));
    }

    @Test
    void videoIdentityAuditShouldRejectUnsafeVideoUrlAndMissingUser() {
        assertThrows(IllegalArgumentException.class, () -> service.submitVideoIdentity(404L, "/uploads/video-identity/404/missing.mp4", "missing user"));
        assertThrows(IllegalArgumentException.class, () -> service.submitVideoIdentity(1L, "local://video.mp4", "local video"));
        assertThrows(IllegalArgumentException.class, () -> service.submitVideoIdentity(1L, "https://cdn.example.com/blocked-preview/video.mp4", "blocked preview video"));
        assertThrows(IllegalArgumentException.class, () -> service.submitVideoIdentity(1L, "https://cdn.example.com/video-identity/free.mp4", "external video"));
    }

    @Test
    void getAdminAuditDetailShouldReturnSafePersistedRecordOnly() {
        String evidenceUrl = serviceMedia().issue(5L, "REPORT_EVIDENCE", "image/png", 600_000L, "report-admin-proof.png").storageUrl();
        AuditRecordResponse created = service.submitReport(5L, "chat", "CHAT-100088", "HARASSMENT", "辱骂骚扰，凭证手机号 13800138000", List.of(evidenceUrl));

        AuditRecordResponse detail = service.getAdminDetail(created.auditNo());

        assertEquals(created.auditNo(), detail.auditNo());
        assertEquals("CHAT", detail.targetType());
        assertEquals("CHAT-100088", detail.targetId());
        assertTrue(detail.description().contains("138****8000"));
        assertTrue(detail.description().contains(evidenceUrl));
        assertThrows(IllegalArgumentException.class, () -> service.getAdminDetail("AUDIT-GOODS-001"));
        assertThrows(IllegalArgumentException.class, () -> service.getAdminDetail("preview-audit"));
    }

    @Test
    void approveShouldMovePendingAuditToApproved() {
        AuditRecordResponse created = service.submitReport(1L, "user", "2", "ABUSE", "abuse");

        AuditRecordResponse reviewed = service.approve(created.auditNo(), "approved ok");

        assertEquals(AuditApplicationService.STATUS_APPROVED, reviewed.status());
        assertEquals("approved ok", reviewed.reviewRemark());
        assertNotNull(reviewed.reviewedAt());
        assertEquals(AuditApplicationService.STATUS_APPROVED, service.get(created.auditNo()).status());
    }

    @Test
    void rejectShouldMovePendingAuditToRejected() {
        AuditRecordResponse created = service.submitWithdrawal(1L, "WD-100001", "WITHDRAW", "missing info");

        AuditRecordResponse reviewed = service.reject(created.auditNo(), "reject reason");

        assertEquals(AuditApplicationService.STATUS_REJECTED, reviewed.status());
        assertEquals("reject reason", reviewed.reviewRemark());
        assertNotNull(reviewed.reviewedAt());
        assertEquals(AuditApplicationService.STATUS_REJECTED, service.get(created.auditNo()).status());
    }

    @Test
    void approveAndRejectShouldPersistMaskedAdminAuditLogRows() {
        AuditRecordResponse reportAudit = service.submitReport(1L, "user", "2", "ABUSE", "涉及手机号 13800138000");
        AuditRecordResponse withdrawalAudit = service.submitWithdrawal(2L, "WD-20260510-8899", "WITHDRAW", "提现复核");

        service.approve(reportAudit.auditNo(), "同意处理 13800138001");
        service.reject(withdrawalAudit.auditNo(), "资料不符 13800138002");

        List<AdminAuditLogResponse> logs = service.listAdminAuditLogs(null, 10);

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
        AuditRecordResponse reportAudit = service.submitReport(11L, "user", "12", "ABUSE", "举报内容");

        service.approve(reportAudit.auditNo(), "管理员复核通过", 99L);

        List<AdminAuditLogResponse> logs = service.listAdminAuditLogs(null, 10);
        assertEquals(1, logs.size());
        assertEquals(99L, logs.get(0).operatorId());
        assertEquals(reportAudit.auditNo(), logs.get(0).targetId());
    }

    @Test
    void repeatedReviewShouldBeRejected() {
        AuditRecordResponse created = service.submitReport(1L, "product", "PRODUCT-100001", "SPAM", "bad content");
        service.approve(created.auditNo(), "approved");

        assertThrows(IllegalStateException.class, () -> service.approve(created.auditNo(), "again"));
        assertThrows(IllegalStateException.class, () -> service.reject(created.auditNo(), "change mind"));
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
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        String videoUrl = serviceMedia().issue(userId, "VIDEO_IDENTITY", "video/mp4", 5_000_000L, filename).storageUrl();
        jdbcTemplate.update("update media_upload_ticket set status = 'UPLOADED' where storage_url = ?", videoUrl);
        return videoUrl;
    }

    private MediaUploadTicketService serviceMedia() {
        return new MediaUploadTicketService(new JdbcTemplate(database));
    }
}

package com.secondhand.platform.modules.audit.application;

import com.secondhand.platform.modules.notification.application.NotificationApplicationService;
import com.secondhand.platform.shared.contracts.user.IdentityType;
import java.sql.Timestamp;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditApplicationService {
    public static final String AUDIT_TYPE_WITHDRAWAL = "WITHDRAWAL";
    public static final String AUDIT_TYPE_REPORT = "REPORT";
    public static final String AUDIT_TYPE_VIDEO_IDENTITY = "VIDEO_IDENTITY";
    public static final String AUDIT_TYPE_REAL_NAME_IDENTITY = "REAL_NAME_IDENTITY";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    private static final String IDENTITY_STATUS_UNVERIFIED = "UNVERIFIED";
    private static final String IDENTITY_STATUS_PENDING = "PENDING";
    private static final String IDENTITY_STATUS_VERIFIED = "VERIFIED";
    private static final String IDENTITY_STATUS_REJECTED = "REJECTED";

    private final JdbcTemplate jdbcTemplate;
    private final com.secondhand.platform.modules.media.application.MediaUploadTicketService mediaUploadTicketService;
    private final NotificationApplicationService notificationApplicationService;

    public AuditApplicationService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new com.secondhand.platform.modules.media.application.MediaUploadTicketService(jdbcTemplate), new NotificationApplicationService(jdbcTemplate));
    }

    @Autowired
    public AuditApplicationService(JdbcTemplate jdbcTemplate, com.secondhand.platform.modules.media.application.MediaUploadTicketService mediaUploadTicketService) {
        this(jdbcTemplate, mediaUploadTicketService, new NotificationApplicationService(jdbcTemplate));
    }

    public AuditApplicationService(JdbcTemplate jdbcTemplate,
                                   com.secondhand.platform.modules.media.application.MediaUploadTicketService mediaUploadTicketService,
                                   NotificationApplicationService notificationApplicationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.mediaUploadTicketService = mediaUploadTicketService;
        this.notificationApplicationService = notificationApplicationService;
        ensureAuditSchemaCompatibility();
    }

    public AuditRecordResponse submitWithdrawal(Long userId, String withdrawalNo, String reason, String description) {
        return create(AUDIT_TYPE_WITHDRAWAL, userId, "WITHDRAWAL", requireText(withdrawalNo, "withdrawalNo required"), requireText(reason, "withdrawal reason required"), safeText(description));
    }

    @Transactional
    public AuditRecordResponse submitRealNameIdentity(Long userId, String realName, String idTail) {
        validateUserId(userId);
        String safeRealName = normalizeRealName(realName);
        String safeIdTail = normalizeIdTail(idTail);
        Integer existing = jdbcTemplate.queryForObject("select count(1) from user_profile where user_id = ?", Integer.class, userId);
        if (existing == null || existing == 0) {
            throw new IllegalArgumentException("user profile not found");
        }
        jdbcTemplate.update("update user_profile set identity_status = ?, updated_at = CURRENT_TIMESTAMP where user_id = ?", IDENTITY_STATUS_PENDING, userId);
        return create(
                AUDIT_TYPE_REAL_NAME_IDENTITY,
                userId,
                "REAL_NAME_IDENTITY",
                String.valueOf(userId),
                "实名：" + maskRealName(safeRealName) + " / 证件后四位：" + safeIdTail,
                "实名资料仅保存姓名与证件后四位，管理员按平台规则复核。"
        );
    }

    @Transactional
    public AuditRecordResponse submitVideoIdentity(Long userId, String videoUrl, String description) {
        validateUserId(userId);
        String safeVideoUrl = requireText(videoUrl, "video identity url required");
        if (safeVideoUrl.startsWith("local://")
                || safeVideoUrl.toLowerCase(Locale.ROOT).contains("placeholder")
                || safeVideoUrl.toLowerCase(Locale.ROOT).contains("blocked-preview")
                || safeVideoUrl.toLowerCase(Locale.ROOT).contains("preview")) {
            throw new IllegalArgumentException("video identity url invalid");
        }
        mediaUploadTicketService.requireUploadedVideoIdentityMedia(userId, safeVideoUrl);
        Integer existing = jdbcTemplate.queryForObject("select count(1) from user_profile where user_id = ?", Integer.class, userId);
        if (existing == null || existing == 0) {
            throw new IllegalArgumentException("user profile not found");
        }
        jdbcTemplate.update("update user_profile set video_identity_status = ?, video_verified = ?, updated_at = CURRENT_TIMESTAMP where user_id = ?", STATUS_PENDING, false, userId);
        return create(AUDIT_TYPE_VIDEO_IDENTITY, userId, "VIDEO_IDENTITY", String.valueOf(userId), safeVideoUrl, safeText(description));
    }

    @Transactional
    public AuditRecordResponse submitReport(Long userId, String targetType, String targetId, String reason, String description) {
        return submitReport(userId, targetType, targetId, reason, description, List.of());
    }

    @Transactional
    public AuditRecordResponse submitReport(Long userId, String targetType, String targetId, String reason, String description, List<String> evidenceUrls) {
        String safeTargetType = requireText(targetType, "report targetType required").toUpperCase(Locale.ROOT);
        String safeTargetId = requireText(targetId, "report targetId required");
        validateReportTargetId(safeTargetType, safeTargetId);
        requireReportTargetExists(safeTargetType, safeTargetId);
        String safeReason = requireText(reason, "report reason required");
        List<String> safeEvidenceUrls = normalizeReportEvidence(userId, evidenceUrls);
        String finalDescription = normalizeReportDescription(safeText(description), safeEvidenceUrls);
        AuditRecordResponse existingPending = findExistingPendingReport(userId, safeTargetType, safeTargetId);
        if (existingPending != null) {
            return existingPending;
        }
        AuditRecordResponse created = create(AUDIT_TYPE_REPORT, userId, safeTargetType, safeTargetId, safeReason, finalDescription);
        createReportRecord(created, finalDescription, safeEvidenceUrls);
        return get(created.auditNo());
    }

    @Transactional
    public AuditRecordResponse approve(String auditNo, String remark) {
        return review(auditNo, STATUS_APPROVED, remark, null, false);
    }

    @Transactional
    public AuditRecordResponse approve(String auditNo, String remark, Long operatorId) {
        return review(auditNo, STATUS_APPROVED, remark, operatorId, false);
    }

    @Transactional
    public AuditRecordResponse approveVideoIdentityAfterEvidenceReview(String auditNo, String remark, Long operatorId) {
        requireVideoIdentityEvidenceWatched(auditNo, operatorId);
        return review(auditNo, STATUS_APPROVED, remark, operatorId, true);
    }

    @Transactional
    public AuditRecordResponse reject(String auditNo, String remark) {
        return review(auditNo, STATUS_REJECTED, remark, null, false);
    }

    @Transactional
    public AuditRecordResponse reject(String auditNo, String remark, Long operatorId) {
        return review(auditNo, STATUS_REJECTED, remark, operatorId, false);
    }

    public AuditRecordResponse get(String auditNo) {
        return load(auditNo, false);
    }

    public AuditRecordResponse getAdminDetail(String auditNo) {
        String safeAuditNo = requireText(auditNo, "auditNo required");
        if (safeAuditNo.toLowerCase(Locale.ROOT).startsWith("preview") || safeAuditNo.startsWith("AUDIT-GOODS-")) {
            throw new IllegalArgumentException("audit record not found");
        }
        return load(safeAuditNo, true);
    }

    private AuditRecordResponse load(String auditNo, boolean maskSensitiveDescription) {
        String safeAuditNo = requireText(auditNo, "auditNo required");
        try {
            return jdbcTemplate.queryForObject(
                    "select audit_no,audit_type,user_id,target_type,target_id,reason,description,status,review_remark,created_at,reviewed_at from audit_record where audit_no = ?",
                    (rs, rowNum) -> buildAuditRecordResponse(
                            rs.getString("audit_no"),
                            rs.getString("audit_type"),
                            rs.getLong("user_id"),
                            rs.getString("target_type"),
                            rs.getString("target_id"),
                            rs.getString("reason"),
                            maskSensitiveDescription(rs.getString("description"), maskSensitiveDescription),
                            rs.getString("status"),
                            rs.getString("review_remark"),
                            toLocalDateTime(rs.getTimestamp("created_at")),
                            toLocalDateTime(rs.getTimestamp("reviewed_at")),
                            maskSensitiveDescription
                    ),
                    safeAuditNo
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("audit record not found");
        }
    }

    private AuditRecordResponse buildAuditRecordResponse(String auditNo,
                                                         String auditType,
                                                         Long userId,
                                                         String targetType,
                                                         String targetId,
                                                         String reason,
                                                         String description,
                                                         String status,
                                                         String reviewRemark,
                                                         LocalDateTime createdAt,
                                                         LocalDateTime reviewedAt,
                                                         boolean includeAdminEvidence) {
        String videoEvidenceUrl = null;
        boolean videoEvidenceVerified = false;
        if (includeAdminEvidence && AUDIT_TYPE_VIDEO_IDENTITY.equals(auditType)) {
            try {
                videoEvidenceUrl = mediaUploadTicketService.requireUploadedStorageUrl(userId, AUDIT_TYPE_VIDEO_IDENTITY, requireText(reason, "video identity url required")).storageUrl();
                mediaUploadTicketService.requireUploadedVideoIdentityMedia(userId, videoEvidenceUrl);
                videoEvidenceVerified = true;
            } catch (IllegalArgumentException ignored) {
                videoEvidenceUrl = null;
                videoEvidenceVerified = false;
            }
        }
        List<String> reportEvidenceUrls = AUDIT_TYPE_REPORT.equals(auditType) ? loadReportEvidenceUrls(auditNo, description) : List.of();
        return new AuditRecordResponse(
                auditNo,
                auditType,
                userId,
                targetType,
                targetId,
                reason,
                description,
                status,
                reviewRemark,
                createdAt,
                reviewedAt,
                videoEvidenceUrl,
                videoEvidenceVerified,
                reportEvidenceUrls
        );
    }

    public List<AuditRecordResponse> listAll() {
        return listAdminAudits(null, null, null, null);
    }

    public List<AuditRecordResponse> listAdminAudits(String auditType, String status, String keyword, Integer limit) {
        String safeAuditType = normalizeAdminAuditType(auditType);
        String safeStatus = normalizeAuditStatus(status);
        String safeKeyword = normalizeAdminAuditKeyword(keyword);
        int safeLimit = limit == null ? 50 : limit;
        if (safeLimit < 1 || safeLimit > 100) {
            throw new IllegalArgumentException("audit list limit invalid");
        }

        StringBuilder sql = new StringBuilder("""
                select audit_no,audit_type,user_id,target_type,target_id,reason,description,status,review_remark,created_at,reviewed_at
                from audit_record
                where 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (safeAuditType != null) {
            sql.append(" and audit_type = ?");
            args.add(safeAuditType);
        }
        if (safeStatus != null) {
            sql.append(" and status = ?");
            args.add(safeStatus);
        }
        if (safeKeyword != null) {
            sql.append("""
                     and (
                        lower(audit_no) like ?
                        or lower(target_type) like ?
                        or lower(target_id) like ?
                        or lower(reason) like ?
                        or lower(description) like ?
                """);
            String like = "%" + safeKeyword.toLowerCase(Locale.ROOT) + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            if (safeKeyword.matches("[1-9]\\d{0,18}")) {
                sql.append(" or user_id = ?");
                args.add(Long.parseLong(safeKeyword));
            }
            sql.append(")");
        }
        sql.append(" order by created_at desc, id desc limit ?");
        args.add(safeLimit);

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new AuditRecordResponse(
                        rs.getString("audit_no"),
                        rs.getString("audit_type"),
                        rs.getLong("user_id"),
                        rs.getString("target_type"),
                        rs.getString("target_id"),
                        rs.getString("reason"),
                        maskSensitiveDescription(rs.getString("description"), true),
                        rs.getString("status"),
                        rs.getString("review_remark"),
                        toLocalDateTime(rs.getTimestamp("created_at")),
                        toLocalDateTime(rs.getTimestamp("reviewed_at"))
                ),
                args.toArray()
        );
    }

    public List<AdminAuditLogResponse> listAdminAuditLogs(Long afterId, Integer limit) {
        int safeLimit = limit == null ? 50 : limit;
        if (safeLimit < 1 || safeLimit > 100) {
            throw new IllegalArgumentException("audit log limit invalid");
        }
        if (afterId != null && afterId <= 0) {
            throw new IllegalArgumentException("audit log cursor invalid");
        }
        if (afterId == null) {
            return queryAuditLogs("select id,action,operator_id,target_type,target_id,result,summary,created_at from admin_audit_log order by id desc limit ?", safeLimit);
        }
        return queryAuditLogs("select id,action,operator_id,target_type,target_id,result,summary,created_at from admin_audit_log where id > ? order by id asc limit ?", afterId, safeLimit);
    }

    public AdminDashboardSummary getAdminDashboardSummary() {
        Integer pendingAudits = countForSql("select count(1) from audit_record where status = ?", STATUS_PENDING);
        Integer approvedAudits = countForSql("select count(1) from audit_record where status = ?", STATUS_APPROVED);
        Integer rejectedAudits = countForSql("select count(1) from audit_record where status = ?", STATUS_REJECTED);
        Integer pendingWithdrawals = countForSql("select count(1) from withdrawal_record where status = ?", STATUS_PENDING);
        Integer pendingAfterSales = countForSql("select count(1) from after_sales_record where after_sales_status = ?", "PENDING_REVIEW");
        Integer activeUsers = countForSql("select count(1) from user_account where status = ?", "ACTIVE");
        Integer todayOrders = countForSql("select count(1) from trade_order where created_at >= CURRENT_DATE");
        java.math.BigDecimal gmv = jdbcTemplate.queryForObject(
                "select coalesce(sum(amount), 0) from trade_order where created_at >= CURRENT_DATE and order_status in ('PAID','SHIPPED','COMPLETED')",
                java.math.BigDecimal.class
        );
        return new AdminDashboardSummary(
                "dashboard-ready",
                pendingAudits == null ? 0 : pendingAudits,
                approvedAudits == null ? 0 : approvedAudits,
                rejectedAudits == null ? 0 : rejectedAudits,
                pendingWithdrawals == null ? 0 : pendingWithdrawals,
                pendingAfterSales == null ? 0 : pendingAfterSales,
                activeUsers == null ? 0 : activeUsers,
                todayOrders == null ? 0 : todayOrders,
                gmv == null ? java.math.BigDecimal.ZERO : gmv
        );
    }

    public void recordAdminOperation(String action, Long operatorId, String targetType, String targetId, String result, String summary) {
        String safeAction = requireText(action, "admin operation action required");
        String safeTargetType = requireText(targetType, "admin operation targetType required");
        String safeTargetId = requireText(targetId, "admin operation targetId required");
        String safeResult = requireText(result, "admin operation result required");
        long safeOperatorId = validateOperatorId(operatorId);
        jdbcTemplate.update("""
                insert into admin_audit_log (action,operator_id,target_type,target_id,result,summary,created_at)
                values (?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """,
                safeAction,
                safeOperatorId,
                safeTargetType,
                safeTargetId,
                safeResult,
                maskSensitiveDescription(safeText(summary), true)
        );
    }

    @Transactional
    public void approveLinkedPendingAudit(String targetType, String targetId, String remark, Long operatorId) {
        String safeTargetType = requireText(targetType, "audit targetType required").toUpperCase(Locale.ROOT);
        String safeTargetId = requireText(targetId, "audit targetId required");
        String auditNo;
        try {
            auditNo = jdbcTemplate.queryForObject("""
                    select audit_no from audit_record
                    where target_type = ? and target_id = ? and status = ?
                    order by created_at asc, id asc limit 1
                    """, String.class, safeTargetType, safeTargetId, STATUS_PENDING);
        } catch (EmptyResultDataAccessException e) {
            return;
        }
        approve(auditNo, remark, operatorId);
    }

    @Transactional
    public void rejectLinkedPendingAudit(String targetType, String targetId, String remark, Long operatorId) {
        String safeTargetType = requireText(targetType, "audit targetType required").toUpperCase(Locale.ROOT);
        String safeTargetId = requireText(targetId, "audit targetId required");
        String auditNo;
        try {
            auditNo = jdbcTemplate.queryForObject("""
                    select audit_no from audit_record
                    where target_type = ? and target_id = ? and status = ?
                    order by created_at asc, id asc limit 1
                    """, String.class, safeTargetType, safeTargetId, STATUS_PENDING);
        } catch (EmptyResultDataAccessException e) {
            return;
        }
        reject(auditNo, remark, operatorId);
    }

    @Transactional
    protected AuditRecordResponse create(String auditType, Long userId, String targetType, String targetId, String reason, String description) {
        validateUserId(userId);
        String auditNo = generateAuditNo(auditType);
        try {
            jdbcTemplate.update(
                    "insert into audit_record (audit_no,audit_type,user_id,target_type,target_id,reason,description,status,created_at) values (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                    auditNo,
                    auditType,
                    userId,
                    targetType,
                    targetId,
                    reason,
                    description,
                    STATUS_PENDING
            );
            return get(auditNo);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("audit record create conflict");
        }
    }

    private AuditRecordResponse review(String auditNo, String status, String remark, Long operatorId, boolean allowVideoIdentityApproval) {
        String safeAuditNo = requireText(auditNo, "auditNo required");
        AuditRecordResponse existing = get(safeAuditNo);
        if (STATUS_APPROVED.equals(status)
                && AUDIT_TYPE_VIDEO_IDENTITY.equals(existing.auditType())
                && STATUS_PENDING.equals(existing.status())
                && !allowVideoIdentityApproval) {
            throw new IllegalStateException("video identity evidence review required");
        }
        int changed = jdbcTemplate.update(
                "update audit_record set status = ?, review_remark = ?, reviewed_at = CURRENT_TIMESTAMP where audit_no = ? and status = ?",
                status,
                safeText(remark),
                safeAuditNo,
                STATUS_PENDING
        );
        if (changed == 1) {
            AuditRecordResponse reviewed = get(safeAuditNo);
            syncVideoIdentityStatus(reviewed, status);
            syncRealNameIdentityStatus(reviewed, status);
            syncReportRecordStatus(reviewed, status);
            syncReportDisposition(reviewed, status, operatorId);
            notifyAuditReviewed(reviewed, status);
            recordAdminAuditLog(reviewed, status, safeText(remark), operatorId);
            return reviewed;
        }
        if (!STATUS_PENDING.equals(existing.status())) {
            throw new IllegalStateException("audit record already reviewed");
        }
        throw new IllegalStateException("audit record review failed");
    }

    private void syncVideoIdentityStatus(AuditRecordResponse response, String status) {
        if (response == null || !AUDIT_TYPE_VIDEO_IDENTITY.equals(response.auditType())) {
            return;
        }
        long userId = Long.parseLong(response.targetId());
        if (!isLatestVideoIdentityAudit(response)) {
            return;
        }
        if (STATUS_APPROVED.equals(status)) {
            mediaUploadTicketService.requireUploadedVideoIdentityMedia(userId, requireText(response.reason(), "video identity url required"));
            jdbcTemplate.update("""
                    update user_profile
                    set main_role = case when main_role in (?, ?) then main_role else ? end,
                        video_identity_status = ?,
                        video_verified = ?,
                        updated_at = CURRENT_TIMESTAMP
                    where user_id = ?
                    """, IdentityType.SELLER.name(), "BOTH", IdentityType.SELLER.name(), STATUS_APPROVED, true, userId);
            return;
        }
        if (STATUS_REJECTED.equals(status)) {
            jdbcTemplate.update("update user_profile set video_identity_status = ?, video_verified = ?, updated_at = CURRENT_TIMESTAMP where user_id = ?", STATUS_REJECTED, false, userId);
        }
    }

    private void requireVideoIdentityEvidenceWatched(String auditNo, Long operatorId) {
        String safeAuditNo = requireText(auditNo, "auditNo required");
        long safeOperatorId = validateOperatorId(operatorId);
        Integer watchedLogs = jdbcTemplate.queryForObject("""
                select count(1)
                from admin_audit_log
                where action = ?
                  and operator_id = ?
                  and target_type = ?
                  and target_id = ?
                  and result = ?
                """, Integer.class, "VIDEO_IDENTITY_MEDIA_WATCHED", safeOperatorId, "AUDIT", safeAuditNo, "SUCCESS");
        if (watchedLogs == null || watchedLogs <= 0) {
            throw new IllegalStateException("video identity evidence review required");
        }
    }

    private boolean isLatestVideoIdentityAudit(AuditRecordResponse response) {
        Integer newerRows = jdbcTemplate.queryForObject("""
                select count(1)
                from audit_record
                where audit_type = ? and target_id = ? and id > (
                    select id from audit_record where audit_no = ?
                )
                """, Integer.class, AUDIT_TYPE_VIDEO_IDENTITY, response.targetId(), response.auditNo());
        return newerRows == null || newerRows == 0;
    }

    private void syncRealNameIdentityStatus(AuditRecordResponse response, String status) {
        if (response == null || !AUDIT_TYPE_REAL_NAME_IDENTITY.equals(response.auditType())) {
            return;
        }
        long userId = Long.parseLong(response.targetId());
        if (!isLatestRealNameIdentityAudit(response)) {
            return;
        }
        if (STATUS_APPROVED.equals(status)) {
            jdbcTemplate.update("update user_profile set identity_status = ?, updated_at = CURRENT_TIMESTAMP where user_id = ?", IDENTITY_STATUS_VERIFIED, userId);
            return;
        }
        if (STATUS_REJECTED.equals(status)) {
            jdbcTemplate.update("update user_profile set identity_status = ?, updated_at = CURRENT_TIMESTAMP where user_id = ?", IDENTITY_STATUS_REJECTED, userId);
        }
    }

    private boolean isLatestRealNameIdentityAudit(AuditRecordResponse response) {
        Integer newerRows = jdbcTemplate.queryForObject("""
                select count(1)
                from audit_record
                where audit_type = ? and target_id = ? and id > (
                    select id from audit_record where audit_no = ?
                )
                """, Integer.class, AUDIT_TYPE_REAL_NAME_IDENTITY, response.targetId(), response.auditNo());
        return newerRows == null || newerRows == 0;
    }

    private void syncReportDisposition(AuditRecordResponse response, String status, Long operatorId) {
        if (response == null || !AUDIT_TYPE_REPORT.equals(response.auditType()) || !STATUS_APPROVED.equals(status)) {
            return;
        }
        String targetType = requireText(response.targetType(), "report targetType required").toUpperCase(Locale.ROOT);
        String targetId = requireText(response.targetId(), "report targetId required");
        int changed = switch (targetType) {
            case "PRODUCT", "GOODS" -> offlineReportedProduct(targetId);
            case "COMMUNITY", "COMMUNITY_POST", "POST" -> blockReportedCommunityPost(targetId);
            case "COMMUNITY_COMMENT" -> blockReportedCommunityComment(targetId);
            case "USER" -> disableReportedUser(targetId);
            default -> 0;
        };
        recordReportDisposition(response, operatorId, targetType, targetId, changed);
    }

    private void createReportRecord(AuditRecordResponse response, String description, List<String> evidenceUrls) {
        if (response == null || !AUDIT_TYPE_REPORT.equals(response.auditType())) {
            return;
        }
        jdbcTemplate.update("""
                insert into report_record (report_no,reporter_id,target_type,target_id,reason_code,description,evidence_urls,report_status,created_at)
                values (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """,
                response.auditNo(),
                response.userId(),
                response.targetType(),
                response.targetId(),
                response.reason(),
                description,
                encodeEvidenceUrls(evidenceUrls),
                STATUS_PENDING
        );
    }

    private AuditRecordResponse findExistingPendingReport(Long userId, String targetType, String targetId) {
        try {
            String auditNo = jdbcTemplate.queryForObject("""
                    select r.report_no
                    from report_record r
                    join audit_record a on a.audit_no = r.report_no
                    where r.reporter_id = ?
                      and r.target_type = ?
                      and r.target_id = ?
                      and r.report_status = ?
                      and a.audit_type = ?
                      and a.status = ?
                    order by r.created_at asc, r.id asc
                    limit 1
                    """, String.class, userId, targetType, targetId, STATUS_PENDING, AUDIT_TYPE_REPORT, STATUS_PENDING);
            return get(auditNo);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private void syncReportRecordStatus(AuditRecordResponse response, String status) {
        if (response == null || !AUDIT_TYPE_REPORT.equals(response.auditType())) {
            return;
        }
        jdbcTemplate.update("""
                update report_record
                set report_status = ?, handled_at = CURRENT_TIMESTAMP
                where report_no = ? and report_status = ?
                """,
                status,
                response.auditNo(),
                STATUS_PENDING
        );
    }

    private int offlineReportedProduct(String targetId) {
        Long productId = parsePositiveLongOrNull(targetId);
        if (productId != null) {
            return jdbcTemplate.update("""
                    update product_item
                    set product_status = 'OFFLINE', visible = false, updated_at = CURRENT_TIMESTAMP
                    where id = ? and product_status <> 'SOLD'
                    """, productId);
        }
        return jdbcTemplate.update("""
                update product_item
                set product_status = 'OFFLINE', visible = false, updated_at = CURRENT_TIMESTAMP
                where product_no = ? and product_status <> 'SOLD'
                """, targetId);
    }

    private int blockReportedCommunityPost(String targetId) {
        Long postId = parsePositiveLongOrNull(targetId);
        if (postId != null) {
            return jdbcTemplate.update("""
                    update community_post
                    set status = 'BLOCKED', updated_at = CURRENT_TIMESTAMP
                    where id = ? and status = 'PUBLISHED'
                    """, postId);
        }
        return jdbcTemplate.update("""
                update community_post
                set status = 'BLOCKED', updated_at = CURRENT_TIMESTAMP
                where post_no = ? and status = 'PUBLISHED'
                """, targetId);
    }

    private int blockReportedCommunityComment(String targetId) {
        int changed = jdbcTemplate.update("""
                update community_comment
                set status = 'BLOCKED'
                where comment_no = ? and status = 'PUBLISHED'
                """, targetId);
        if (changed > 0) {
            jdbcTemplate.update("""
                    update community_post
                    set comment_count = (
                        select count(1)
                        from community_comment
                        where post_id = community_post.id and status = 'PUBLISHED'
                    ),
                    updated_at = CURRENT_TIMESTAMP
                    where id = (
                        select post_id
                        from community_comment
                        where comment_no = ?
                    )
                    """, targetId);
        }
        return changed;
    }

    private int disableReportedUser(String targetId) {
        Long userId = parsePositiveLongOrNull(targetId);
        if (userId != null) {
            return jdbcTemplate.update("""
                    update user_account
                    set status = 'DISABLED', updated_at = CURRENT_TIMESTAMP
                    where id = ? and status = 'ACTIVE'
                    """, userId);
        }
        return jdbcTemplate.update("""
                update user_account
                set status = 'DISABLED', updated_at = CURRENT_TIMESTAMP
                where user_no = ? and status = 'ACTIVE'
                """, targetId);
    }

    private void recordReportDisposition(AuditRecordResponse response, Long operatorId, String targetType, String targetId, int changed) {
        long safeOperatorId = operatorId == null ? response.userId() : operatorId;
        jdbcTemplate.update("""
                insert into admin_audit_log (action,operator_id,target_type,target_id,result,summary,created_at)
                values (?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """,
                "REPORT_DISPOSITION",
                safeOperatorId,
                targetType,
                targetId,
                changed > 0 ? "SUCCESS" : "NOOP",
                changed > 0 ? "举报通过后已执行业务处置" : "举报通过，目标无需自动处置或未匹配业务记录"
        );
    }

    private Long parsePositiveLongOrNull(String value) {
        if (value == null || !value.matches("[1-9]\\d{0,18}")) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void recordAdminAuditLog(AuditRecordResponse response, String status, String remark, Long operatorId) {
        if (response == null) {
            return;
        }
        String action = STATUS_APPROVED.equals(status) ? "AUDIT_APPROVE" : "AUDIT_REJECT";
        String summary = remark == null ? (STATUS_APPROVED.equals(status) ? "审核通过" : "审核驳回") : remark;
        long safeOperatorId = operatorId == null ? response.userId() : operatorId;
        jdbcTemplate.update("""
                insert into admin_audit_log (action,operator_id,target_type,target_id,result,summary,created_at)
                values (?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """,
                action,
                safeOperatorId,
                "AUDIT",
                response.auditNo(),
                "SUCCESS",
                summary
        );
    }

    private void notifyAuditReviewed(AuditRecordResponse response, String status) {
        if (response == null || response.userId() == null || response.userId() <= 0) {
            return;
        }
        String normalizedStatus = STATUS_APPROVED.equals(status) ? STATUS_APPROVED : STATUS_REJECTED;
        String targetUrl = notificationTargetUrl(response);
        notificationApplicationService.createNotification(
                response.userId(),
                "AUDIT",
                notificationTitle(response, normalizedStatus),
                notificationDescription(response, normalizedStatus),
                targetUrl
        );
    }

    private String notificationTitle(AuditRecordResponse response, String status) {
        String result = STATUS_APPROVED.equals(status) ? "已通过" : "未通过";
        return switch (response.auditType()) {
            case AUDIT_TYPE_VIDEO_IDENTITY -> "视频认证" + result;
            case AUDIT_TYPE_REAL_NAME_IDENTITY -> "实名认证" + result;
            case AUDIT_TYPE_REPORT -> STATUS_APPROVED.equals(status) ? "举报已受理" : "举报未成立";
            case AUDIT_TYPE_WITHDRAWAL -> "提现审核" + result;
            default -> "审核结果" + result;
        };
    }

    private String notificationDescription(AuditRecordResponse response, String status) {
        String suffix = response.reviewRemark() == null || response.reviewRemark().isBlank()
                ? "可进入对应页面查看最新状态。"
                : "处理说明：" + maskSensitiveDescription(response.reviewRemark(), true);
        return switch (response.auditType()) {
            case AUDIT_TYPE_VIDEO_IDENTITY -> STATUS_APPROVED.equals(status)
                    ? "视频认证已通过，认证卖家标识将按平台资料展示。" : "视频认证未通过，" + suffix;
            case AUDIT_TYPE_REAL_NAME_IDENTITY -> STATUS_APPROVED.equals(status)
                    ? "实名认证已通过，资料状态已更新。" : "实名认证未通过，" + suffix;
            case AUDIT_TYPE_REPORT -> STATUS_APPROVED.equals(status)
                    ? "你提交的举报已受理，平台已按规则处理。" : "你提交的举报暂未成立，" + suffix;
            case AUDIT_TYPE_WITHDRAWAL -> "提现审核" + (STATUS_APPROVED.equals(status) ? "已通过，" : "未通过，") + suffix;
            default -> "审核状态已更新，" + suffix;
        };
    }

    private String notificationTargetUrl(AuditRecordResponse response) {
        return switch (response.auditType()) {
            case AUDIT_TYPE_VIDEO_IDENTITY, AUDIT_TYPE_REAL_NAME_IDENTITY -> "/pages/user/identity/index";
            default -> "/pages/notification/index";
        };
    }

    private List<AdminAuditLogResponse> queryAuditLogs(String sql, Object... args) {
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new AdminAuditLogResponse(
                        rs.getLong("id"),
                        rs.getString("action"),
                        rs.getLong("operator_id"),
                        rs.getString("target_type"),
                        rs.getString("target_id"),
                        rs.getString("result"),
                        maskSensitiveDescription(rs.getString("summary"), true),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ),
                args
        );
    }

    private Integer countForSql(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private List<String> normalizeReportEvidence(Long userId, List<String> evidenceUrls) {
        validateUserId(userId);
        if (evidenceUrls == null || evidenceUrls.isEmpty()) {
            return List.of();
        }
        if (evidenceUrls.size() > 6) {
            throw new IllegalArgumentException("report evidence max 6");
        }
        return evidenceUrls.stream()
                .map(url -> requireText(url, "report evidence url required"))
                .peek(url -> rejectUnsafeEvidenceUrl(url))
                .peek(url -> mediaUploadTicketService.requireUploadedStorageUrl(userId, "REPORT_EVIDENCE", url))
                .distinct()
                .toList();
    }

    private String normalizeAdminAuditType(String auditType) {
        String safeType = safeText(auditType);
        if (safeType == null || "ALL".equalsIgnoreCase(safeType)) {
            return null;
        }
        String upper = safeType.toUpperCase(Locale.ROOT);
        if (!List.of(AUDIT_TYPE_REPORT, AUDIT_TYPE_WITHDRAWAL, AUDIT_TYPE_VIDEO_IDENTITY, AUDIT_TYPE_REAL_NAME_IDENTITY, "PRODUCT").contains(upper)) {
            throw new IllegalArgumentException("audit type invalid");
        }
        return upper;
    }

    private String normalizeAuditStatus(String status) {
        String safeStatus = safeText(status);
        if (safeStatus == null || "ALL".equalsIgnoreCase(safeStatus)) {
            return null;
        }
        String upper = safeStatus.toUpperCase(Locale.ROOT);
        if (!List.of(STATUS_PENDING, STATUS_APPROVED, STATUS_REJECTED).contains(upper)) {
            throw new IllegalArgumentException("audit status invalid");
        }
        return upper;
    }

    private String normalizeAdminAuditKeyword(String keyword) {
        String safeKeyword = safeText(keyword);
        if (safeKeyword == null) {
            return null;
        }
        String lower = safeKeyword.toLowerCase(Locale.ROOT);
        if (safeKeyword.length() > 64
                || lower.contains("preview")
                || lower.contains("demo")
                || lower.contains("mock")
                || lower.contains("sample")
                || lower.contains("placeholder")) {
            throw new IllegalArgumentException("audit keyword invalid");
        }
        if (safeKeyword.matches("\\d+") && !safeKeyword.matches("[1-9]\\d{0,18}")) {
            throw new IllegalArgumentException("audit keyword invalid");
        }
        return safeKeyword;
    }

    private void rejectUnsafeEvidenceUrl(String evidenceUrl) {
        String lower = evidenceUrl.toLowerCase(Locale.ROOT);
        if (lower.startsWith("local://") || lower.contains("placeholder") || lower.contains("preview") || !lower.startsWith("/uploads/")) {
            throw new IllegalArgumentException("report evidence url invalid");
        }
    }

    private void validateReportTargetId(String targetType, String targetId) {
        String lower = targetId.toLowerCase(Locale.ROOT);
        boolean validNumericId = !"AFTER_SALES".equals(targetType)
                && !"COMMUNITY_COMMENT".equals(targetType)
                && targetId.matches("[1-9]\\d{0,18}");
        boolean validTypedId = switch (targetType) {
            case "PRODUCT", "GOODS" -> targetId.matches("(PRODUCT|GOODS)-[A-Za-z0-9][A-Za-z0-9_-]{5,63}");
            case "COMMUNITY", "COMMUNITY_POST", "POST" -> targetId.matches("POST-[A-Za-z0-9][A-Za-z0-9_-]{5,63}");
            case "COMMUNITY_COMMENT" -> targetId.matches("CMT-[A-Za-z0-9][A-Za-z0-9_-]{5,63}");
            case "ORDER" -> targetId.matches("ORDER-[A-Za-z0-9][A-Za-z0-9_-]{5,63}") || targetId.matches("OD-[1-9][0-9]{0,9}");
            case "AFTER_SALES" -> targetId.matches("AS-[A-Za-z0-9][A-Za-z0-9_-]{5,63}");
            case "CHAT" -> targetId.matches("CHAT-[A-Za-z0-9][A-Za-z0-9_-]{5,63}");
            case "USER" -> targetId.matches("USER-[A-Za-z0-9][A-Za-z0-9_-]{5,63}");
            case "REPORT" -> targetId.matches("REPORT-[A-Za-z0-9][A-Za-z0-9_-]{5,63}");
            default -> false;
        };
        if ("UNKNOWN".equalsIgnoreCase(targetId)
                || lower.startsWith("preview")
                || lower.contains("demo")
                || lower.contains("sample")
                || lower.contains("mock")
                || lower.contains("placeholder")
                || !(validNumericId || validTypedId)) {
            throw new IllegalArgumentException("report targetId invalid");
        }
    }

    private void requireReportTargetExists(String targetType, String targetId) {
        Long numericId = parsePositiveLongOrNull(targetId);
        boolean exists = switch (targetType) {
            case "PRODUCT", "GOODS" -> numericId != null
                    ? existsForSql("select count(1) from product_item where id = ? and product_status <> ?", numericId, "SOLD")
                    : existsForSql("select count(1) from product_item where product_no = ? and product_status <> ?", targetId, "SOLD");
            case "COMMUNITY", "COMMUNITY_POST", "POST" -> numericId != null
                    ? existsForSql("select count(1) from community_post where id = ? and status = ?", numericId, "PUBLISHED")
                    : existsForSql("select count(1) from community_post where post_no = ? and status = ?", targetId, "PUBLISHED");
            case "COMMUNITY_COMMENT" -> existsForSql("""
                    select count(1)
                    from community_comment c
                    join community_post p on p.id = c.post_id
                    where c.comment_no = ?
                      and c.status = ?
                      and p.status = ?
                    """, targetId, "PUBLISHED", "PUBLISHED");
            case "USER" -> numericId != null
                    ? existsForSql("select count(1) from user_account where id = ? and status = ?", numericId, "ACTIVE")
                    : existsForSql("select count(1) from user_account where user_no = ? and status = ?", targetId, "ACTIVE");
            case "ORDER" -> numericId != null
                    ? existsForSql("select count(1) from trade_order where id = ?", numericId)
                    : existsForSql("select count(1) from trade_order where order_no = ?", targetId);
            case "AFTER_SALES" -> existsForSql("select count(1) from after_sales_record where after_sales_no = ?", targetId);
            case "CHAT" -> numericId != null
                    ? existsForSql("select count(1) from im_conversation where id = ?", numericId)
                    : existsForSql("select count(1) from im_conversation where conversation_no = ?", targetId);
            case "REPORT" -> existsForSql("select count(1) from report_record where report_no = ?", targetId);
            default -> false;
        };
        if (!exists) {
            throw new IllegalArgumentException("report target not found");
        }
    }

    private boolean existsForSql(String sql, Object... args) {
        Integer count = countForSql(sql, args);
        return count != null && count > 0;
    }

    private String normalizeReportDescription(String description, List<String> evidenceUrls) {
        String safeDescription = description == null ? null : description.trim();
        if (safeDescription == null || safeDescription.isBlank()) {
            return evidenceUrls == null || evidenceUrls.isEmpty() ? null : "举报凭证已通过平台上传票据提交。";
        }
        if (safeDescription.length() > 512) {
            throw new IllegalArgumentException("report description max 512");
        }
        return safeDescription;
    }

    private List<String> loadReportEvidenceUrls(String auditNo, String fallbackDescription) {
        List<String> rows = jdbcTemplate.query("""
                select evidence_urls
                from report_record
                where report_no = ?
                """, (rs, rowNum) -> rs.getString("evidence_urls"), auditNo);
        if (!rows.isEmpty()) {
            List<String> decoded = decodeEvidenceUrls(rows.get(0));
            if (!decoded.isEmpty()) {
                return decoded;
            }
        }
        return extractReportEvidenceUrls(fallbackDescription);
    }

    private String encodeEvidenceUrls(List<String> evidenceUrls) {
        if (evidenceUrls == null || evidenceUrls.isEmpty()) {
            return null;
        }
        return String.join("\n", evidenceUrls);
    }

    private List<String> decodeEvidenceUrls(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(encoded.split("\\n"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(this::isSafeReportEvidenceUrl)
                .distinct()
                .limit(6)
                .toList();
    }

    private List<String> extractReportEvidenceUrls(String description) {
        if (description == null || description.isBlank()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        String marker = "/uploads/report-evidence/";
        int index = 0;
        while (index >= 0 && index < description.length()) {
            int start = description.indexOf(marker, index);
            if (start < 0) {
                break;
            }
            int end = start;
            while (end < description.length()) {
                char ch = description.charAt(end);
                if (!(Character.isLetterOrDigit(ch) || ch == '/' || ch == '.' || ch == '_' || ch == '~' || ch == '%' || ch == '-')) {
                    break;
                }
                end += 1;
            }
            String candidate = description.substring(start, end);
            if (isSafeReportEvidenceUrl(candidate) && !urls.contains(candidate)) {
                urls.add(candidate);
            }
            index = end + 1;
        }
        return urls.size() > 6 ? urls.subList(0, 6) : urls;
    }

    private boolean isSafeReportEvidenceUrl(String value) {
        if (value == null) {
            return false;
        }
        String safeValue = value.trim();
        String lower = safeValue.toLowerCase(Locale.ROOT);
        if (!safeValue.startsWith("/uploads/report-evidence/")) {
            return false;
        }
        return !lower.contains("preview")
                && !lower.contains("demo")
                && !lower.contains("mock")
                && !lower.contains("sample")
                && !lower.contains("placeholder")
                && !lower.contains("%2e")
                && !lower.contains("%2f")
                && !lower.contains("%5c")
                && !safeValue.contains("\\")
                && !safeValue.contains("..")
                && !safeValue.contains("//");
    }

    private String maskSensitiveDescription(String description, boolean enabled) {
        if (!enabled || description == null) {
            return description;
        }
        return description
                .replaceAll("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)", "$1****$2")
                .replaceAll("(?<!\\d)(\\d{6})\\d{6,9}(\\d{4})(?!\\d)", "$1********$2");
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("audit userId required");
        }
    }

    private long validateOperatorId(Long operatorId) {
        if (operatorId == null || operatorId <= 0) {
            throw new IllegalArgumentException("admin operatorId required");
        }
        return operatorId;
    }

    private String requireText(String value, String message) {
        String safe = safeText(value);
        if (safe == null) {
            throw new IllegalArgumentException(message);
        }
        return safe;
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeRealName(String value) {
        String safe = requireText(value, "realName required");
        String lower = safe.toLowerCase(Locale.ROOT);
        if (safe.length() < 2
                || safe.length() > 24
                || lower.contains("preview")
                || lower.contains("demo")
                || lower.contains("mock")
                || lower.contains("sample")
                || lower.contains("placeholder")
                || lower.contains("测试")
                || safe.matches(".*[0-9@#￥$%^&*_+=<>/\\\\].*")) {
            throw new IllegalArgumentException("realName invalid");
        }
        return safe;
    }

    private String normalizeIdTail(String value) {
        String safe = requireText(value, "idTail required");
        if (!safe.matches("\\d{4}")) {
            throw new IllegalArgumentException("idTail invalid");
        }
        return safe;
    }

    private String maskRealName(String value) {
        if (value == null || value.isBlank()) {
            return "*";
        }
        String safe = value.trim();
        if (safe.length() <= 1) {
            return "*";
        }
        return safe.charAt(0) + "*".repeat(Math.min(3, safe.length() - 1));
    }

    private String generateAuditNo(String auditType) {
        return "AU-" + auditType.substring(0, Math.min(3, auditType.length())) + '-' + System.currentTimeMillis() + '-' + Math.abs((int) (Math.random() * 100000));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private void ensureAuditSchemaCompatibility() {
        ensureColumn("report_record", "evidence_urls", "ALTER TABLE report_record ADD COLUMN evidence_urls TEXT");
    }

    private void ensureColumn(String tableName, String columnName, String alterSql) {
        try {
            if (!columnExists(tableName, columnName)) {
                jdbcTemplate.execute(alterSql);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("audit schema compatibility check failed: " + tableName + "." + columnName, ex);
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        if (jdbcTemplate.getDataSource() == null) {
            throw new SQLException("dataSource unavailable");
        }
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return columnExists(metaData, tableName, columnName)
                    || columnExists(metaData, tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT))
                    || columnExists(metaData, tableName.toLowerCase(Locale.ROOT), columnName.toLowerCase(Locale.ROOT));
        }
    }

    private boolean columnExists(DatabaseMetaData metaData, String tableName, String columnName) throws SQLException {
        try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
            return columns.next();
        }
    }
}

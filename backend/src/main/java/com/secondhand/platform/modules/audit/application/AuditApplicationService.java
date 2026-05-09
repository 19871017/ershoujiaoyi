package com.secondhand.platform.modules.audit.application;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    private final JdbcTemplate jdbcTemplate;
    private final com.secondhand.platform.modules.media.application.MediaUploadTicketService mediaUploadTicketService;

    public AuditApplicationService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new com.secondhand.platform.modules.media.application.MediaUploadTicketService(jdbcTemplate));
    }

    @Autowired
    public AuditApplicationService(JdbcTemplate jdbcTemplate, com.secondhand.platform.modules.media.application.MediaUploadTicketService mediaUploadTicketService) {
        this.jdbcTemplate = jdbcTemplate;
        this.mediaUploadTicketService = mediaUploadTicketService;
    }

    public AuditRecordResponse submitWithdrawal(Long userId, String withdrawalNo, String reason, String description) {
        return create(AUDIT_TYPE_WITHDRAWAL, userId, "WITHDRAWAL", requireText(withdrawalNo, "withdrawalNo required"), requireText(reason, "withdrawal reason required"), safeText(description));
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
        mediaUploadTicketService.requireIssuedStorageUrl(userId, AUDIT_TYPE_VIDEO_IDENTITY, safeVideoUrl);
        Integer existing = jdbcTemplate.queryForObject("select count(1) from user_profile where user_id = ?", Integer.class, userId);
        if (existing == null || existing == 0) {
            throw new IllegalArgumentException("user profile not found");
        }
        jdbcTemplate.update("update user_profile set video_identity_status = ?, video_verified = ?, updated_at = CURRENT_TIMESTAMP where user_id = ?", STATUS_PENDING, false, userId);
        return create(AUDIT_TYPE_VIDEO_IDENTITY, userId, "VIDEO_IDENTITY", String.valueOf(userId), safeVideoUrl, safeText(description));
    }

    public AuditRecordResponse submitReport(Long userId, String targetType, String targetId, String reason, String description) {
        return submitReport(userId, targetType, targetId, reason, description, List.of());
    }

    public AuditRecordResponse submitReport(Long userId, String targetType, String targetId, String reason, String description, List<String> evidenceUrls) {
        String safeTargetType = requireText(targetType, "report targetType required").toUpperCase(Locale.ROOT);
        String safeTargetId = requireText(targetId, "report targetId required");
        validateReportTargetId(safeTargetType, safeTargetId);
        String safeReason = requireText(reason, "report reason required");
        List<String> safeEvidenceUrls = normalizeReportEvidence(userId, evidenceUrls);
        String finalDescription = appendEvidenceUrls(safeText(description), safeEvidenceUrls);
        return create(AUDIT_TYPE_REPORT, userId, safeTargetType, safeTargetId, safeReason, finalDescription);
    }

    @Transactional
    public AuditRecordResponse approve(String auditNo, String remark) {
        return review(auditNo, STATUS_APPROVED, remark);
    }

    @Transactional
    public AuditRecordResponse reject(String auditNo, String remark) {
        return review(auditNo, STATUS_REJECTED, remark);
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
                    (rs, rowNum) -> new AuditRecordResponse(
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
                            toLocalDateTime(rs.getTimestamp("reviewed_at"))
                    ),
                    safeAuditNo
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("audit record not found");
        }
    }

    public List<AuditRecordResponse> listAll() {
        return jdbcTemplate.query(
                "select audit_no,audit_type,user_id,target_type,target_id,reason,description,status,review_remark,created_at,reviewed_at from audit_record order by created_at desc, id desc",
                (rs, rowNum) -> new AuditRecordResponse(
                        rs.getString("audit_no"),
                        rs.getString("audit_type"),
                        rs.getLong("user_id"),
                        rs.getString("target_type"),
                        rs.getString("target_id"),
                        rs.getString("reason"),
                        rs.getString("description"),
                        rs.getString("status"),
                        rs.getString("review_remark"),
                        toLocalDateTime(rs.getTimestamp("created_at")),
                        toLocalDateTime(rs.getTimestamp("reviewed_at"))
                )
        );
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

    private AuditRecordResponse review(String auditNo, String status, String remark) {
        String safeAuditNo = requireText(auditNo, "auditNo required");
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
            return reviewed;
        }
        AuditRecordResponse existing = get(safeAuditNo);
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
        if (STATUS_APPROVED.equals(status)) {
            jdbcTemplate.update("update user_profile set video_identity_status = ?, video_verified = ?, updated_at = CURRENT_TIMESTAMP where user_id = ?", STATUS_APPROVED, true, userId);
            return;
        }
        if (STATUS_REJECTED.equals(status)) {
            jdbcTemplate.update("update user_profile set video_identity_status = ?, video_verified = ?, updated_at = CURRENT_TIMESTAMP where user_id = ?", STATUS_REJECTED, false, userId);
        }
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
                .peek(url -> mediaUploadTicketService.requireIssuedStorageUrl(userId, "REPORT_EVIDENCE", url))
                .distinct()
                .toList();
    }

    private void rejectUnsafeEvidenceUrl(String evidenceUrl) {
        String lower = evidenceUrl.toLowerCase(Locale.ROOT);
        if (lower.startsWith("local://") || lower.contains("placeholder") || lower.contains("preview") || !lower.startsWith("/uploads/")) {
            throw new IllegalArgumentException("report evidence url invalid");
        }
    }

    private void validateReportTargetId(String targetType, String targetId) {
        String lower = targetId.toLowerCase(Locale.ROOT);
        boolean validNumericId = targetId.matches("[1-9]\\d{0,18}");
        boolean validTypedId = switch (targetType) {
            case "PRODUCT", "GOODS" -> targetId.matches("(PRODUCT|GOODS)-[A-Za-z0-9][A-Za-z0-9_-]{5,63}");
            case "ORDER" -> targetId.matches("ORDER-[A-Za-z0-9][A-Za-z0-9_-]{5,63}");
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

    private String appendEvidenceUrls(String description, List<String> evidenceUrls) {
        if (evidenceUrls == null || evidenceUrls.isEmpty()) {
            return description;
        }
        String prefix = description == null ? "" : description + "\n";
        return prefix + "举报凭证：" + String.join(",", evidenceUrls);
    }

    private String maskSensitiveDescription(String description, boolean enabled) {
        if (!enabled || description == null) {
            return description;
        }
        return description.replaceAll("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)", "$1****$2");
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("audit userId required");
        }
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

    private String generateAuditNo(String auditType) {
        return "AU-" + auditType.substring(0, Math.min(3, auditType.length())) + '-' + System.currentTimeMillis() + '-' + Math.abs((int) (Math.random() * 100000));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}

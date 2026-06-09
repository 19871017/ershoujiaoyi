package com.secondhand.platform.modules.audit.application;

import java.time.LocalDateTime;
import java.util.List;

public record AuditRecordResponse(
        String auditNo,
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
        String videoEvidenceUrl,
        boolean videoEvidenceVerified,
        List<String> reportEvidenceUrls
) {
    public AuditRecordResponse(String auditNo,
                               String auditType,
                               Long userId,
                               String targetType,
                               String targetId,
                               String reason,
                               String description,
                               String status,
                               String reviewRemark,
                               LocalDateTime createdAt,
                               LocalDateTime reviewedAt) {
        this(auditNo, auditType, userId, targetType, targetId, reason, description, status, reviewRemark, createdAt, reviewedAt, null, false, List.of());
    }

    public AuditRecordResponse(String auditNo,
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
                               String videoEvidenceUrl,
                               boolean videoEvidenceVerified) {
        this(auditNo, auditType, userId, targetType, targetId, reason, description, status, reviewRemark, createdAt, reviewedAt, videoEvidenceUrl, videoEvidenceVerified, List.of());
    }
}

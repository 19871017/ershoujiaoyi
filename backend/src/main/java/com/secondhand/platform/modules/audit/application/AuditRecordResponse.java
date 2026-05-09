package com.secondhand.platform.modules.audit.application;

import java.time.LocalDateTime;

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
        LocalDateTime reviewedAt
) {
}

package com.secondhand.platform.modules.audit.application;

import java.time.LocalDateTime;

public record AdminAuditLogResponse(
        Long logId,
        String action,
        Long operatorId,
        String targetType,
        String targetId,
        String result,
        String summary,
        LocalDateTime createdAt
) {
}

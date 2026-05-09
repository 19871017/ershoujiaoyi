package com.secondhand.platform.modules.notification.application;

import java.time.Instant;

public record NotificationItemResponse(
        String notificationNo,
        Long userId,
        String type,
        String title,
        String description,
        String targetUrl,
        boolean read,
        Instant createdAt,
        Instant readAt
) {
}

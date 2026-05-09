package com.secondhand.platform.modules.media.application;

import java.time.LocalDateTime;

public record MediaUploadTicketResponse(
        String ticketNo,
        Long ownerUserId,
        String scene,
        String contentType,
        Long fileSize,
        String storageUrl,
        String uploadToken,
        String status,
        LocalDateTime expiresAt
) {
}

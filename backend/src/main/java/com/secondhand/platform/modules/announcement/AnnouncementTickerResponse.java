package com.secondhand.platform.modules.announcement;

public record AnnouncementTickerResponse(
    Boolean enabled,
    String text,
    String icon,
    String targetUrl,
    String updatedAt
) {
}

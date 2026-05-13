package com.secondhand.platform.modules.home;

public record HomeBannerResponse(
    Long id,
    String kicker,
    String title,
    String description,
    String cta,
    String imageUrl,
    String action,
    Integer sortOrder,
    Boolean enabled,
    String sizeHint,
    String updatedAt
) {
}

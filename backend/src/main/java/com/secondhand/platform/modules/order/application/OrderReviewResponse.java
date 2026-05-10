package com.secondhand.platform.modules.order.application;

public record OrderReviewResponse(
        String reviewNo,
        String orderNo,
        Long reviewerId,
        Long revieweeId,
        Integer descriptionScore,
        Integer serviceScore,
        Integer shippingScore,
        String content,
        String createdAt
) {
}

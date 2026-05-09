package com.secondhand.platform.modules.product;

import java.math.BigDecimal;

public class ProductListItemResponse {
    private final Long productId;
    private final String productNo;
    private final String title;
    private final BigDecimal price;
    private final String coverImageUrl;
    private final String status;
    private final String auditState;
    private final Boolean visible;
    private final String createdAt;

    public ProductListItemResponse(Long productId, String productNo, String title, BigDecimal price, String coverImageUrl,
            String status, String auditState, Boolean visible, String createdAt) {
        this.productId = productId;
        this.productNo = productNo;
        this.title = title;
        this.price = price;
        this.coverImageUrl = coverImageUrl;
        this.status = status;
        this.auditState = auditState;
        this.visible = visible;
        this.createdAt = createdAt;
    }

    public Long getProductId() { return productId; }
    public String getProductNo() { return productNo; }
    public String getTitle() { return title; }
    public BigDecimal getPrice() { return price; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public String getStatus() { return status; }
    public String getAuditState() { return auditState; }
    public Boolean getVisible() { return visible; }
    public String getCreatedAt() { return createdAt; }
}

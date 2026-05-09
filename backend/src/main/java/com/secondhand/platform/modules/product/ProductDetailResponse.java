package com.secondhand.platform.modules.product;

import java.math.BigDecimal;
import java.util.List;

public class ProductDetailResponse {
    private final Long productId;
    private final String productNo;
    private final String title;
    private final String description;
    private final BigDecimal price;
    private final List<String> imageUrls;
    private final String status;
    private final String auditState;
    private final Boolean visible;
    private final String tradeRule;
    private final String createdAt;

    public ProductDetailResponse(Long productId, String productNo, String title, String description, BigDecimal price,
            List<String> imageUrls, String status, String auditState, Boolean visible, String tradeRule,
            String createdAt) {
        this.productId = productId;
        this.productNo = productNo;
        this.title = title;
        this.description = description;
        this.price = price;
        this.imageUrls = List.copyOf(imageUrls);
        this.status = status;
        this.auditState = auditState;
        this.visible = visible;
        this.tradeRule = tradeRule;
        this.createdAt = createdAt;
    }

    public Long getProductId() { return productId; }
    public String getProductNo() { return productNo; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public List<String> getImageUrls() { return imageUrls; }
    public String getStatus() { return status; }
    public String getAuditState() { return auditState; }
    public Boolean getVisible() { return visible; }
    public String getTradeRule() { return tradeRule; }
    public String getCreatedAt() { return createdAt; }
}

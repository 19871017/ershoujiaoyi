package com.secondhand.platform.modules.product;

import java.math.BigDecimal;

public class CreateProductResponse {
    private final Long productId;
    private final String productNo;
    private final String title;
    private final BigDecimal price;
    private final String status;
    private final String auditState;
    private final Boolean visible;
    private final String tradeRule;
    private final String createdAt;

    public CreateProductResponse(Long productId, String productNo, String title, BigDecimal price, String status,
            String auditState, Boolean visible, String tradeRule, String createdAt) {
        this.productId = productId;
        this.productNo = productNo;
        this.title = title;
        this.price = price;
        this.status = status;
        this.auditState = auditState;
        this.visible = visible;
        this.tradeRule = tradeRule;
        this.createdAt = createdAt;
    }

    public Long getProductId() { return productId; }
    public String getProductNo() { return productNo; }
    public String getTitle() { return title; }
    public BigDecimal getPrice() { return price; }
    public String getStatus() { return status; }
    public String getAuditState() { return auditState; }
    public Boolean getVisible() { return visible; }
    public String getTradeRule() { return tradeRule; }
    public String getCreatedAt() { return createdAt; }
}

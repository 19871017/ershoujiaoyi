package com.secondhand.platform.modules.product;

import java.math.BigDecimal;
import java.util.List;

public class UpdateProductResponse {
    private Long productId;
    private String productNo;
    private String title;
    private String description;
    private BigDecimal price;
    private List<String> imageUrls;
    private String status;
    private String auditState;
    private Boolean visible;
    private String tradeRule;

    public UpdateProductResponse(Long productId, String productNo, String title, String description, BigDecimal price,
                                 List<String> imageUrls, String status, String auditState, Boolean visible, String tradeRule) {
        this.productId = productId;
        this.productNo = productNo;
        this.title = title;
        this.description = description;
        this.price = price;
        this.imageUrls = imageUrls;
        this.status = status;
        this.auditState = auditState;
        this.visible = visible;
        this.tradeRule = tradeRule;
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
}

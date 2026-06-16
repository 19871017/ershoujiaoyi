package com.secondhand.platform.modules.product;

import java.math.BigDecimal;

public class ProductListItemResponse {
    private final Long productId;
    private final String productNo;
    private final Long sellerId;
    private final String sellerNickname;
    private final String sellerAvatarUrl;
    private final String sellerGender;
    private final String title;
    private final String category;
    private final String sellerCity;
    private final Boolean sellerVideoVerified;
    private final BigDecimal price;
    private final BigDecimal sellerPrice;
    private final BigDecimal platformMarkupRate;
    private final BigDecimal platformMarkupAmount;
    private final String coverImageUrl;
    private final String status;
    private final String auditState;
    private final Boolean visible;
    private final String createdAt;

    public ProductListItemResponse(Long productId, String productNo, String title, BigDecimal price, String coverImageUrl,
            String status, String auditState, Boolean visible, String createdAt) {
        this(productId, productNo, null, null, null, null, title, null, null, null, price, price, BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(2), coverImageUrl, status, auditState, visible, createdAt);
    }

    public ProductListItemResponse(Long productId, String productNo, Long sellerId, String sellerNickname, String sellerAvatarUrl,
            String sellerGender, String title, String category, String sellerCity, Boolean sellerVideoVerified, BigDecimal price, String coverImageUrl,
            String status, String auditState, Boolean visible, String createdAt) {
        this(productId, productNo, sellerId, sellerNickname, sellerAvatarUrl, sellerGender, title, category, sellerCity,
                sellerVideoVerified, price, price, BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(2), coverImageUrl, status,
                auditState, visible, createdAt);
    }

    public ProductListItemResponse(Long productId, String productNo, Long sellerId, String sellerNickname, String sellerAvatarUrl,
            String sellerGender, String title, String category, String sellerCity, Boolean sellerVideoVerified, BigDecimal price,
            BigDecimal sellerPrice, BigDecimal platformMarkupRate, BigDecimal platformMarkupAmount, String coverImageUrl,
            String status, String auditState, Boolean visible, String createdAt) {
        this.productId = productId;
        this.productNo = productNo;
        this.sellerId = sellerId;
        this.sellerNickname = sellerNickname;
        this.sellerAvatarUrl = sellerAvatarUrl;
        this.sellerGender = sellerGender;
        this.title = title;
        this.category = category;
        this.sellerCity = sellerCity;
        this.sellerVideoVerified = sellerVideoVerified;
        this.price = price;
        this.sellerPrice = sellerPrice;
        this.platformMarkupRate = platformMarkupRate;
        this.platformMarkupAmount = platformMarkupAmount;
        this.coverImageUrl = coverImageUrl;
        this.status = status;
        this.auditState = auditState;
        this.visible = visible;
        this.createdAt = createdAt;
    }

    public Long getProductId() { return productId; }
    public String getProductNo() { return productNo; }
    public Long getSellerId() { return sellerId; }
    public String getSellerNickname() { return sellerNickname; }
    public String getSellerAvatarUrl() { return sellerAvatarUrl; }
    public String getSellerGender() { return sellerGender; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getSellerCity() { return sellerCity; }
    public Boolean getSellerVideoVerified() { return sellerVideoVerified; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getSellerPrice() { return sellerPrice; }
    public BigDecimal getPlatformMarkupRate() { return platformMarkupRate; }
    public BigDecimal getPlatformMarkupAmount() { return platformMarkupAmount; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public String getStatus() { return status; }
    public String getAuditState() { return auditState; }
    public Boolean getVisible() { return visible; }
    public String getCreatedAt() { return createdAt; }
}

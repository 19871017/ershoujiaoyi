package com.secondhand.platform.modules.product.application;

import java.math.BigDecimal;

public class ProductSnapshot {
    private final Long productId;
    private final String productNo;
    private final String title;
    private final BigDecimal price;
    private final BigDecimal sellerPrice;
    private final BigDecimal platformMarkupRate;
    private final BigDecimal platformMarkupAmount;
    private final String tradeRule;
    private final Long sellerId;

    public ProductSnapshot(Long productId, String productNo, String title, BigDecimal price, String tradeRule, Long sellerId) {
        this(productId, productNo, title, price, price, BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(2), tradeRule, sellerId);
    }

    public ProductSnapshot(Long productId, String productNo, String title, BigDecimal buyerPrice, BigDecimal sellerPrice,
            BigDecimal platformMarkupRate, BigDecimal platformMarkupAmount, String tradeRule, Long sellerId) {
        this.productId = productId;
        this.productNo = productNo;
        this.title = title;
        this.price = buyerPrice;
        this.sellerPrice = sellerPrice;
        this.platformMarkupRate = platformMarkupRate;
        this.platformMarkupAmount = platformMarkupAmount;
        this.tradeRule = tradeRule;
        this.sellerId = sellerId;
    }

    public Long getProductId() { return productId; }
    public String getProductNo() { return productNo; }
    public String getTitle() { return title; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getBuyerPrice() { return price; }
    public BigDecimal getSellerPrice() { return sellerPrice; }
    public BigDecimal getPlatformMarkupRate() { return platformMarkupRate; }
    public BigDecimal getPlatformMarkupAmount() { return platformMarkupAmount; }
    public String getTradeRule() { return tradeRule; }
    public Long getSellerId() { return sellerId; }
}

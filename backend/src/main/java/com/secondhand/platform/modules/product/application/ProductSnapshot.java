package com.secondhand.platform.modules.product.application;

import java.math.BigDecimal;

public class ProductSnapshot {
    private final Long productId;
    private final String productNo;
    private final String title;
    private final BigDecimal price;
    private final String tradeRule;

    public ProductSnapshot(Long productId, String productNo, String title, BigDecimal price, String tradeRule) {
        this.productId = productId;
        this.productNo = productNo;
        this.title = title;
        this.price = price;
        this.tradeRule = tradeRule;
    }

    public Long getProductId() { return productId; }
    public String getProductNo() { return productNo; }
    public String getTitle() { return title; }
    public BigDecimal getPrice() { return price; }
    public String getTradeRule() { return tradeRule; }
}

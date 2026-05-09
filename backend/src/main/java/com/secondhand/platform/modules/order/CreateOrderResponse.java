package com.secondhand.platform.modules.order;

import java.math.BigDecimal;

public class CreateOrderResponse {
    private final String orderNo;
    private final Long buyerId;
    private final Long goodsId;
    private final Long productId;
    private final String productNo;
    private final String productTitle;
    private final BigDecimal productPrice;
    private final String tradeRuleSnapshot;
    private final String status;
    private final Boolean acceptedTradeRule;
    private final String createdAt;

    public CreateOrderResponse(String orderNo, Long buyerId, Long goodsId, Long productId, String productNo,
            String productTitle, BigDecimal productPrice, String tradeRuleSnapshot, String status,
            Boolean acceptedTradeRule, String createdAt) {
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        this.goodsId = goodsId;
        this.productId = productId;
        this.productNo = productNo;
        this.productTitle = productTitle;
        this.productPrice = productPrice;
        this.tradeRuleSnapshot = tradeRuleSnapshot;
        this.status = status;
        this.acceptedTradeRule = acceptedTradeRule;
        this.createdAt = createdAt;
    }

    public String getOrderNo() { return orderNo; }
    public Long getBuyerId() { return buyerId; }
    public Long getGoodsId() { return goodsId; }
    public Long getProductId() { return productId; }
    public String getProductNo() { return productNo; }
    public String getProductTitle() { return productTitle; }
    public BigDecimal getProductPrice() { return productPrice; }
    public String getTradeRuleSnapshot() { return tradeRuleSnapshot; }
    public String getStatus() { return status; }
    public Boolean getAcceptedTradeRule() { return acceptedTradeRule; }
    public String getCreatedAt() { return createdAt; }
}

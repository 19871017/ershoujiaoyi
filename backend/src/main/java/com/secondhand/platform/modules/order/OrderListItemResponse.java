package com.secondhand.platform.modules.order;

import java.math.BigDecimal;

public class OrderListItemResponse {
    private String orderNo;
    private Long buyerId;
    private Long sellerId;
    private Long productId;
    private Long goodsId;
    private String productNo;
    private String productTitle;
    private BigDecimal amount;
    private String tradeRuleSnapshot;
    private String status;
    private String role;
    private String counterpartyName;
    private String afterSalesNo;
    private String afterSalesStatus;
    private String createdAt;

    public OrderListItemResponse(String orderNo, Long buyerId, Long sellerId, Long productId, Long goodsId, String productNo,
                                 String productTitle, BigDecimal amount, String tradeRuleSnapshot, String status,
                                 String role, String counterpartyName, String afterSalesNo, String afterSalesStatus, String createdAt) {
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.productId = productId;
        this.goodsId = goodsId;
        this.productNo = productNo;
        this.productTitle = productTitle;
        this.amount = amount;
        this.tradeRuleSnapshot = tradeRuleSnapshot;
        this.status = status;
        this.role = role;
        this.counterpartyName = counterpartyName;
        this.afterSalesNo = afterSalesNo;
        this.afterSalesStatus = afterSalesStatus;
        this.createdAt = createdAt;
    }

    public String getOrderNo() { return orderNo; }
    public Long getBuyerId() { return buyerId; }
    public Long getSellerId() { return sellerId; }
    public Long getProductId() { return productId; }
    public Long getGoodsId() { return goodsId; }
    public String getProductNo() { return productNo; }
    public String getProductTitle() { return productTitle; }
    public BigDecimal getAmount() { return amount; }
    public String getTradeRuleSnapshot() { return tradeRuleSnapshot; }
    public String getStatus() { return status; }
    public String getRole() { return role; }
    public String getCounterpartyName() { return counterpartyName; }
    public String getAfterSalesNo() { return afterSalesNo; }
    public String getAfterSalesStatus() { return afterSalesStatus; }
    public String getCreatedAt() { return createdAt; }
}

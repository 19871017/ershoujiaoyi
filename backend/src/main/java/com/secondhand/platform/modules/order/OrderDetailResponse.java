package com.secondhand.platform.modules.order;

import java.math.BigDecimal;

public class OrderDetailResponse {
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
    private String counterpartyName;
    private String afterSalesNo;
    private String afterSalesStatus;
    private String shippingType;
    private String shippingCompany;
    private String trackingNo;
    private String shippingRemark;
    private String createdAt;
    private String paidAt;
    private String shippedAt;
    private String completedAt;

    public OrderDetailResponse(String orderNo, Long buyerId, Long sellerId, Long productId, Long goodsId, String productNo,
                               String productTitle, BigDecimal amount, String tradeRuleSnapshot, String status,
                               String counterpartyName, String afterSalesNo, String afterSalesStatus, String shippingType,
                               String shippingCompany, String trackingNo, String shippingRemark, String createdAt,
                               String paidAt, String shippedAt, String completedAt) {
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
        this.counterpartyName = counterpartyName;
        this.afterSalesNo = afterSalesNo;
        this.afterSalesStatus = afterSalesStatus;
        this.shippingType = shippingType;
        this.shippingCompany = shippingCompany;
        this.trackingNo = trackingNo;
        this.shippingRemark = shippingRemark;
        this.createdAt = createdAt;
        this.paidAt = paidAt;
        this.shippedAt = shippedAt;
        this.completedAt = completedAt;
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
    public String getCounterpartyName() { return counterpartyName; }
    public String getAfterSalesNo() { return afterSalesNo; }
    public String getAfterSalesStatus() { return afterSalesStatus; }
    public String getShippingType() { return shippingType; }
    public String getShippingCompany() { return shippingCompany; }
    public String getTrackingNo() { return trackingNo; }
    public String getShippingRemark() { return shippingRemark; }
    public String getCreatedAt() { return createdAt; }
    public String getPaidAt() { return paidAt; }
    public String getShippedAt() { return shippedAt; }
    public String getCompletedAt() { return completedAt; }
}

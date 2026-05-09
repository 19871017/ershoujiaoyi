package com.secondhand.platform.modules.order;

import java.math.BigDecimal;

public class PayOrderResponse {
    private final String orderNo;
    private final Long buyerId;
    private final Long goodsId;
    private final Long productId;
    private final String productNo;
    private final String productTitle;
    private final BigDecimal amount;
    private final String status;
    private final String ledgerNo;
    private final String balanceType;
    private final BigDecimal balanceBefore;
    private final BigDecimal balanceAfter;
    private final String paidAt;
    private final boolean idempotentReplay;

    public PayOrderResponse(String orderNo, Long buyerId, Long goodsId, Long productId, String productNo,
            String productTitle, BigDecimal amount, String status, String ledgerNo, String balanceType,
            BigDecimal balanceBefore, BigDecimal balanceAfter, String paidAt, boolean idempotentReplay) {
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        this.goodsId = goodsId;
        this.productId = productId;
        this.productNo = productNo;
        this.productTitle = productTitle;
        this.amount = amount;
        this.status = status;
        this.ledgerNo = ledgerNo;
        this.balanceType = balanceType;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.paidAt = paidAt;
        this.idempotentReplay = idempotentReplay;
    }

    public String getOrderNo() { return orderNo; }
    public Long getBuyerId() { return buyerId; }
    public Long getGoodsId() { return goodsId; }
    public Long getProductId() { return productId; }
    public String getProductNo() { return productNo; }
    public String getProductTitle() { return productTitle; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getLedgerNo() { return ledgerNo; }
    public String getBalanceType() { return balanceType; }
    public BigDecimal getBalanceBefore() { return balanceBefore; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getPaidAt() { return paidAt; }
    public boolean isIdempotentReplay() { return idempotentReplay; }
}

package com.secondhand.platform.modules.wallet_ledger.application;

import java.math.BigDecimal;

public class CreditCommand {
    private Long userId;
    private String idempotencyKey;
    private String bizType;
    private String bizNo;
    private String balanceType;
    private BigDecimal amount;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public String getBizNo() { return bizNo; }
    public void setBizNo(String bizNo) { this.bizNo = bizNo; }
    public String getBalanceType() { return balanceType; }
    public void setBalanceType(String balanceType) { this.balanceType = balanceType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}

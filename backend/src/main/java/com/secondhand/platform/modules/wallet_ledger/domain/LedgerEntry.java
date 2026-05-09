package com.secondhand.platform.modules.wallet_ledger.domain;

import com.secondhand.platform.shared.kernel.BaseEntity;
import java.math.BigDecimal;

public class LedgerEntry extends BaseEntity {
    private String ledgerNo;
    private String idempotencyKey;
    private Long userId;
    private String bizType;
    private String bizNo;
    private String direction;
    private String balanceType;
    private BigDecimal amount;

    public String getLedgerNo() { return ledgerNo; }
    public void setLedgerNo(String ledgerNo) { this.ledgerNo = ledgerNo; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public String getBizNo() { return bizNo; }
    public void setBizNo(String bizNo) { this.bizNo = bizNo; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getBalanceType() { return balanceType; }
    public void setBalanceType(String balanceType) { this.balanceType = balanceType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}

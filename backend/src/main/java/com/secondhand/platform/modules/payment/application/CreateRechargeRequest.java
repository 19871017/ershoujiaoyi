package com.secondhand.platform.modules.payment.application;

import java.math.BigDecimal;

public class CreateRechargeRequest {
    private BigDecimal amount;
    private String channel;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
}

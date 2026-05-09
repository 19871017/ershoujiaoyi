package com.secondhand.platform.modules.wallet_ledger;

import java.math.BigDecimal;

public class CreateWithdrawalRequest {
    private BigDecimal amount;
    private String paymentMethod;
    private String accountName;
    private String accountNo;
    private String remark;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}

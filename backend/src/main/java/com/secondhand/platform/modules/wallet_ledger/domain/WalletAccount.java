package com.secondhand.platform.modules.wallet_ledger.domain;

import com.secondhand.platform.shared.kernel.BaseEntity;
import java.math.BigDecimal;

public class WalletAccount extends BaseEntity {
    private Long userId;
    private BigDecimal rechargeBalance = BigDecimal.ZERO;
    private BigDecimal incomeBalance = BigDecimal.ZERO;
    private BigDecimal frozenBalance = BigDecimal.ZERO;
    private BigDecimal withdrawableBalance = BigDecimal.ZERO;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public BigDecimal getRechargeBalance() { return rechargeBalance; }
    public void setRechargeBalance(BigDecimal rechargeBalance) { this.rechargeBalance = rechargeBalance; }
    public BigDecimal getIncomeBalance() { return incomeBalance; }
    public void setIncomeBalance(BigDecimal incomeBalance) { this.incomeBalance = incomeBalance; }
    public BigDecimal getFrozenBalance() { return frozenBalance; }
    public void setFrozenBalance(BigDecimal frozenBalance) { this.frozenBalance = frozenBalance; }
    public BigDecimal getWithdrawableBalance() { return withdrawableBalance; }
    public void setWithdrawableBalance(BigDecimal withdrawableBalance) { this.withdrawableBalance = withdrawableBalance; }
}

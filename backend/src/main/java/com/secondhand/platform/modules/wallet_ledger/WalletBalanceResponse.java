package com.secondhand.platform.modules.wallet_ledger;

import java.math.BigDecimal;

public class WalletBalanceResponse {
    private BigDecimal rechargeBalance;
    private BigDecimal incomeBalance;
    private BigDecimal frozenBalance;
    private BigDecimal withdrawableBalance;

    public WalletBalanceResponse(BigDecimal rechargeBalance, BigDecimal incomeBalance, BigDecimal frozenBalance, BigDecimal withdrawableBalance) {
        this.rechargeBalance = rechargeBalance;
        this.incomeBalance = incomeBalance;
        this.frozenBalance = frozenBalance;
        this.withdrawableBalance = withdrawableBalance;
    }

    public BigDecimal getRechargeBalance() {
        return rechargeBalance;
    }

    public BigDecimal getIncomeBalance() {
        return incomeBalance;
    }

    public BigDecimal getFrozenBalance() {
        return frozenBalance;
    }

    public BigDecimal getWithdrawableBalance() {
        return withdrawableBalance;
    }
}

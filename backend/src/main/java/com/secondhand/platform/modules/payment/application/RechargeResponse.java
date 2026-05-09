package com.secondhand.platform.modules.payment.application;

import com.secondhand.platform.modules.wallet_ledger.application.LedgerTransactionResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RechargeResponse(
        Long userId,
        String rechargeNo,
        BigDecimal amount,
        String channel,
        String status,
        String ledgerNo,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        LocalDateTime createdAt,
        boolean idempotentReplay
) {
    static RechargeResponse pending(Long userId, String rechargeNo, BigDecimal amount, String channel, LocalDateTime createdAt) {
        return new RechargeResponse(userId, rechargeNo, amount, channel, "PENDING", null, null, null, createdAt, false);
    }

    RechargeResponse paid(LedgerTransactionResponse ledger) {
        return new RechargeResponse(
                userId,
                rechargeNo,
                amount,
                channel,
                "PAID",
                ledger.ledgerNo(),
                ledger.balanceBefore(),
                ledger.balanceAfter(),
                createdAt,
                ledger.idempotentReplay()
        );
    }
}

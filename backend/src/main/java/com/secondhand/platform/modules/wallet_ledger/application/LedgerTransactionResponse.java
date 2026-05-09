package com.secondhand.platform.modules.wallet_ledger.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LedgerTransactionResponse(
        String ledgerNo,
        Long userId,
        String direction,
        String bizType,
        String bizNo,
        String balanceType,
        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        LocalDateTime createdAt,
        boolean idempotentReplay
) {
    public LedgerTransactionResponse asIdempotentReplay() {
        return new LedgerTransactionResponse(
                ledgerNo,
                userId,
                direction,
                bizType,
                bizNo,
                balanceType,
                amount,
                balanceBefore,
                balanceAfter,
                createdAt,
                true
        );
    }
}

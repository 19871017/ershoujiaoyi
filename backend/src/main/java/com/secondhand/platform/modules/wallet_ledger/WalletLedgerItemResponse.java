package com.secondhand.platform.modules.wallet_ledger;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletLedgerItemResponse(
        String ledgerNo,
        String direction,
        BigDecimal amount,
        String balanceType,
        String businessType,
        String businessId,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String status,
        String remark,
        LocalDateTime createdAt
) {
}

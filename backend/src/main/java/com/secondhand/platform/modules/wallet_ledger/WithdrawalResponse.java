package com.secondhand.platform.modules.wallet_ledger;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WithdrawalResponse(
        String withdrawalNo,
        String auditNo,
        Long userId,
        BigDecimal amount,
        String paymentMethod,
        String accountName,
        String accountNo,
        String accountVerifyStatus,
        String status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt
) {
}

package com.secondhand.platform.modules.wallet_ledger;

public record PayoutAccountResponse(
        Long payoutAccountId,
        String paymentMethod,
        String accountName,
        String maskedAccountNo,
        String verifyStatus
) {
}

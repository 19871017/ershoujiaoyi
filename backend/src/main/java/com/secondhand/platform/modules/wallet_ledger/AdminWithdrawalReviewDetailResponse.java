package com.secondhand.platform.modules.wallet_ledger;

import java.util.List;

public record AdminWithdrawalReviewDetailResponse(
        WithdrawalResponse withdrawal,
        UserSnapshot user,
        WalletBalanceResponse balance,
        List<WalletLedgerItemResponse> recentLedgers
) {
    public record UserSnapshot(
            Long userId,
            String userNo,
            String nickname,
            String status,
            String identityStatus,
            String mainRole,
            String city,
            String videoIdentityStatus,
            Boolean videoVerified
    ) {
    }
}

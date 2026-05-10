package com.secondhand.platform.modules.wallet_ledger;

import com.secondhand.platform.modules.audit.application.AuditApplicationService;
import com.secondhand.platform.modules.audit.application.AuditRecordResponse;
import com.secondhand.platform.modules.wallet_ledger.application.WalletLedgerService;
import com.secondhand.platform.shared.kernel.Result;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletLedgerController {
    private final WalletLedgerService walletLedgerService;
    private final AuditApplicationService auditApplicationService;
    private final CurrentUserResolver currentUserResolver;

    public WalletLedgerController(WalletLedgerService walletLedgerService,
                                  AuditApplicationService auditApplicationService,
                                  CurrentUserResolver currentUserResolver) {
        this.walletLedgerService = walletLedgerService;
        this.auditApplicationService = auditApplicationService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/balance")
    public Result<WalletBalanceResponse> balance(HttpServletRequest request) {
        return Result.ok(walletLedgerService.getBalance(currentUserResolver.resolve(request)));
    }

    @GetMapping("/ledger")
    public Result<List<WalletLedgerItemResponse>> ledger(HttpServletRequest request) {
        return Result.ok(walletLedgerService.listLedger(currentUserResolver.resolve(request)));
    }

    @GetMapping("/payout-account")
    public Result<PayoutAccountResponse> payoutAccount(HttpServletRequest request) {
        return Result.ok(walletLedgerService.getActivePayoutAccount(currentUserResolver.resolve(request)));
    }

    @PostMapping("/payout-account")
    public Result<PayoutAccountResponse> bindPayoutAccount(@RequestBody PayoutAccountRequest body,
                                                           HttpServletRequest request) {
        Long userId = currentUserResolver.resolve(request);
        walletLedgerService.bindPayoutAccount(userId, body);
        return Result.ok(walletLedgerService.getActivePayoutAccount(userId));
    }

    @PostMapping("/withdrawals")
    public Result<WithdrawalResponse> createWithdrawal(@RequestBody CreateWithdrawalRequest body,
                                                       HttpServletRequest request) {
        Long userId = currentUserResolver.resolve(request);
        WithdrawalResponse pendingWithdrawal = walletLedgerService.createWithdrawal(userId, body, null);
        try {
            AuditRecordResponse audit = auditApplicationService.submitWithdrawal(
                    userId,
                    pendingWithdrawal.withdrawalNo(),
                    "用户提现申请",
                    "提现申请进入人工审核，当前阶段不触发真实出款"
            );
            WithdrawalResponse withdrawal = walletLedgerService.attachWithdrawalAudit(pendingWithdrawal.withdrawalNo(), audit.auditNo());
            return Result.ok(withdrawal);
        } catch (RuntimeException ex) {
            walletLedgerService.cancelWithdrawalCreation(pendingWithdrawal.withdrawalNo());
            throw ex;
        }
    }

    @GetMapping("/withdrawals")
    public Result<List<WithdrawalResponse>> withdrawals(HttpServletRequest request) {
        return Result.ok(walletLedgerService.listWithdrawals(currentUserResolver.resolve(request)));
    }
}

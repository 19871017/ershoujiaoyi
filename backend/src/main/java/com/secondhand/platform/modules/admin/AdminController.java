package com.secondhand.platform.modules.admin;

import com.secondhand.platform.modules.audit.application.AuditApplicationService;
import com.secondhand.platform.modules.audit.application.AuditRecordResponse;
import com.secondhand.platform.modules.location.AdminUpdateLocationConfigRequest;
import com.secondhand.platform.modules.location.LocationApplicationService;
import com.secondhand.platform.modules.location.LocationConfigResponse;
import com.secondhand.platform.modules.wallet_ledger.WithdrawalResponse;
import com.secondhand.platform.modules.wallet_ledger.application.WalletLedgerService;
import com.secondhand.platform.shared.kernel.Result;
import com.secondhand.platform.shared.web.AdminAccessGuard;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AuditApplicationService auditApplicationService;
    private final WalletLedgerService walletLedgerService;
    private final LocationApplicationService locationApplicationService;
    private final AdminAccessGuard adminAccessGuard;

    public AdminController(AuditApplicationService auditApplicationService,
                           WalletLedgerService walletLedgerService,
                           LocationApplicationService locationApplicationService,
                           AdminAccessGuard adminAccessGuard) {
        this.auditApplicationService = auditApplicationService;
        this.walletLedgerService = walletLedgerService;
        this.locationApplicationService = locationApplicationService;
        this.adminAccessGuard = adminAccessGuard;
    }

    @GetMapping("/dashboard")
    public Result<String> dashboard(HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request);
        return Result.ok("dashboard-ready");
    }

    @GetMapping("/audit")
    public Result<List<AuditRecordResponse>> auditList(HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request);
        return Result.ok(auditApplicationService.listAll());
    }

    @GetMapping("/audit/{auditNo}")
    public Result<AuditRecordResponse> auditDetail(@PathVariable String auditNo, HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request);
        return Result.ok(auditApplicationService.getAdminDetail(auditNo));
    }

    @GetMapping("/location/config")
    public Result<LocationConfigResponse> locationConfig(HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request);
        return Result.ok(locationApplicationService.getConfig());
    }

    @PostMapping("/location/config")
    public Result<LocationConfigResponse> updateLocationConfig(@RequestBody(required = false) AdminUpdateLocationConfigRequest body,
                                                               HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request);
        return Result.ok(locationApplicationService.adminUpdateConfig(body));
    }

    @GetMapping("/withdrawals/{withdrawalNo}")
    public Result<WithdrawalResponse> withdrawalDetail(@PathVariable String withdrawalNo, HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request);
        return Result.ok(walletLedgerService.getAdminWithdrawal(withdrawalNo));
    }

    @PostMapping("/audit/{auditNo}/approve")
    public Result<AuditRecordResponse> approveAudit(@PathVariable String auditNo,
                                                    @RequestBody(required = false) AuditReviewRequest body,
                                                    HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request);
        AuditRecordResponse response = auditApplicationService.approve(auditNo, body == null ? null : body.getRemark());
        syncWithdrawalStatus(response, "APPROVED");
        return Result.ok(response);
    }

    @PostMapping("/audit/{auditNo}/reject")
    public Result<AuditRecordResponse> rejectAudit(@PathVariable String auditNo,
                                                   @RequestBody(required = false) AuditReviewRequest body,
                                                   HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request);
        AuditRecordResponse response = auditApplicationService.reject(auditNo, body == null ? null : body.getRemark());
        syncWithdrawalStatus(response, "REJECTED");
        return Result.ok(response);
    }

    private void syncWithdrawalStatus(AuditRecordResponse response, String status) {
        if (response != null && AuditApplicationService.AUDIT_TYPE_WITHDRAWAL.equals(response.auditType())) {
            walletLedgerService.markWithdrawalReviewed(response.targetId(), status);
        }
    }
}

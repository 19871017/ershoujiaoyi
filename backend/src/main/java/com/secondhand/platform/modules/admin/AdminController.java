package com.secondhand.platform.modules.admin;

import com.secondhand.platform.modules.aftersales.AfterSalesResponse;
import com.secondhand.platform.modules.aftersales.application.AfterSalesApplicationService;
import com.secondhand.platform.modules.audit.application.AuditApplicationService;
import com.secondhand.platform.modules.audit.application.AdminAuditLogResponse;
import com.secondhand.platform.modules.audit.application.AdminDashboardSummary;
import com.secondhand.platform.modules.audit.application.AuditRecordResponse;
import com.secondhand.platform.modules.location.AdminUpdateLocationConfigRequest;
import com.secondhand.platform.modules.location.LocationApplicationService;
import com.secondhand.platform.modules.location.LocationConfigResponse;
import com.secondhand.platform.modules.order.OrderDetailResponse;
import com.secondhand.platform.modules.order.OrderListItemResponse;
import com.secondhand.platform.modules.order.application.OrderApplicationService;
import com.secondhand.platform.modules.product.CreateProductResponse;
import com.secondhand.platform.modules.product.application.ProductApplicationService;
import com.secondhand.platform.modules.user.AdminUserDetailResponse;
import com.secondhand.platform.modules.user.application.UserApplicationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AuditApplicationService auditApplicationService;
    private final WalletLedgerService walletLedgerService;
    private final LocationApplicationService locationApplicationService;
    private final AfterSalesApplicationService afterSalesApplicationService;
    private final OrderApplicationService orderApplicationService;
    private final ProductApplicationService productApplicationService;
    private final UserApplicationService userApplicationService;
    private final AdminAccessGuard adminAccessGuard;

    public AdminController(AuditApplicationService auditApplicationService,
                           WalletLedgerService walletLedgerService,
                           LocationApplicationService locationApplicationService,
                           AfterSalesApplicationService afterSalesApplicationService,
                           OrderApplicationService orderApplicationService,
                           ProductApplicationService productApplicationService,
                           UserApplicationService userApplicationService,
                           AdminAccessGuard adminAccessGuard) {
        this.auditApplicationService = auditApplicationService;
        this.walletLedgerService = walletLedgerService;
        this.locationApplicationService = locationApplicationService;
        this.afterSalesApplicationService = afterSalesApplicationService;
        this.orderApplicationService = orderApplicationService;
        this.productApplicationService = productApplicationService;
        this.userApplicationService = userApplicationService;
        this.adminAccessGuard = adminAccessGuard;
    }

    @GetMapping("/dashboard")
    public Result<AdminDashboardSummary> dashboard(HttpServletRequest request) {
        adminAccessGuard.requireAdminSession(request);
        return Result.ok(auditApplicationService.getAdminDashboardSummary());
    }

    @GetMapping("/orders")
    public Result<List<OrderListItemResponse>> orderList(@RequestParam(required = false) String status,
                                                         @RequestParam(defaultValue = "20") Integer limit,
                                                         HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "order:read");
        return Result.ok(orderApplicationService.adminListOrders(status, limit));
    }

    @GetMapping("/orders/{orderNo}")
    public Result<OrderDetailResponse> orderDetail(@PathVariable String orderNo, HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "order:read");
        return Result.ok(orderApplicationService.adminDetailOrder(orderNo));
    }

    @PostMapping("/products/{productId}/approve")
    public Result<CreateProductResponse> approveProduct(@PathVariable Long productId, HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "audit:review");
        productApplicationService.approveForSale(productId);
        auditApplicationService.approveLinkedPendingAudit("PRODUCT", String.valueOf(productId), "后台商品审核通过", adminUserId);
        return Result.ok(productApplicationService.createResponse(productId));
    }

    @GetMapping("/users/{userId}")
    public Result<AdminUserDetailResponse> userDetail(@PathVariable Long userId, HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "user:read");
        return Result.ok(userApplicationService.adminUserDetail(userId));
    }

    @GetMapping("/users")
    public Result<List<AdminUserDetailResponse>> userSearch(@RequestParam String keyword,
                                                            @RequestParam(defaultValue = "20") Integer limit,
                                                            HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "user:read");
        return Result.ok(userApplicationService.searchAdminUsers(keyword, limit == null ? 20 : limit));
    }

    @GetMapping("/audit")
    public Result<List<AuditRecordResponse>> auditList(HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "audit:read");
        return Result.ok(auditApplicationService.listAll());
    }

    @GetMapping("/audit-logs")
    public Result<List<AdminAuditLogResponse>> auditLogs(@RequestParam(required = false) Long afterId,
                                                         @RequestParam(required = false) Integer limit,
                                                         HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "audit:log");
        return Result.ok(auditApplicationService.listAdminAuditLogs(afterId, limit));
    }

    @GetMapping("/audit/{auditNo}")
    public Result<AuditRecordResponse> auditDetail(@PathVariable String auditNo, HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "audit:read");
        return Result.ok(auditApplicationService.getAdminDetail(auditNo));
    }

    @GetMapping("/location/config")
    public Result<LocationConfigResponse> locationConfig(HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "system:config");
        return Result.ok(locationApplicationService.getConfig());
    }

    @PostMapping("/location/config")
    public Result<LocationConfigResponse> updateLocationConfig(@RequestBody(required = false) AdminUpdateLocationConfigRequest body,
                                                               HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "system:config");
        LocationConfigResponse response = locationApplicationService.adminUpdateConfig(body);
        auditApplicationService.recordAdminOperation(
                "LOCATION_CONFIG_UPDATE",
                adminUserId,
                "SYSTEM_CONFIG",
                "location",
                "SUCCESS",
                "位置配置已更新：provider=" + response.provider() + ", enabled=" + response.enabled()
        );
        return Result.ok(response);
    }

    @GetMapping("/withdrawals")
    public Result<List<WithdrawalResponse>> withdrawalList(@RequestParam(required = false) String status,
                                                           @RequestParam(defaultValue = "20") Integer limit,
                                                           HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "finance:read");
        return Result.ok(walletLedgerService.listAdminWithdrawals(status, limit));
    }

    @GetMapping("/withdrawals/{withdrawalNo}")
    public Result<WithdrawalResponse> withdrawalDetail(@PathVariable String withdrawalNo, HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "finance:read");
        return Result.ok(walletLedgerService.getAdminWithdrawal(withdrawalNo));
    }

    @GetMapping("/after-sales")
    public Result<List<AfterSalesResponse>> afterSalesList(@RequestParam(required = false) String status,
                                                           @RequestParam(defaultValue = "20") Integer limit,
                                                           HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "after-sales:read");
        return Result.ok(afterSalesApplicationService.listAdminAfterSales(status, limit));
    }

    @GetMapping("/after-sales/{afterSalesNo}")
    public Result<AfterSalesResponse> afterSalesDetail(@PathVariable String afterSalesNo, HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "after-sales:read");
        return Result.ok(afterSalesApplicationService.getAdminDetail(afterSalesNo));
    }

    @PostMapping("/after-sales/{afterSalesNo}/approve")
    public Result<AfterSalesResponse> approveAfterSales(@PathVariable String afterSalesNo,
                                                        @RequestBody(required = false) AdminAfterSalesReviewRequest body,
                                                        HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "after-sales:review");
        return Result.ok(afterSalesApplicationService.adminReview(afterSalesNo, "APPROVED", adminUserId, body == null ? null : body.getRemark()));
    }

    @PostMapping("/after-sales/{afterSalesNo}/reject")
    public Result<AfterSalesResponse> rejectAfterSales(@PathVariable String afterSalesNo,
                                                       @RequestBody(required = false) AdminAfterSalesReviewRequest body,
                                                       HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "after-sales:review");
        return Result.ok(afterSalesApplicationService.adminReview(afterSalesNo, "REJECTED", adminUserId, body == null ? null : body.getRemark()));
    }

    @PostMapping("/audit/{auditNo}/approve")
    public Result<AuditRecordResponse> approveAudit(@PathVariable String auditNo,
                                                    @RequestBody(required = false) AuditReviewRequest body,
                                                    HttpServletRequest request) {
        long adminUserId = requireReviewPermissionForAudit(auditNo, request);
        AuditRecordResponse response = auditApplicationService.approve(auditNo, body == null ? null : body.getRemark(), adminUserId);
        syncWithdrawalStatus(response, "APPROVED", adminUserId);
        return Result.ok(response);
    }

    @PostMapping("/audit/{auditNo}/reject")
    public Result<AuditRecordResponse> rejectAudit(@PathVariable String auditNo,
                                                   @RequestBody(required = false) AuditReviewRequest body,
                                                   HttpServletRequest request) {
        long adminUserId = requireReviewPermissionForAudit(auditNo, request);
        AuditRecordResponse response = auditApplicationService.reject(auditNo, body == null ? null : body.getRemark(), adminUserId);
        syncWithdrawalStatus(response, "REJECTED", adminUserId);
        return Result.ok(response);
    }

    private long requireReviewPermissionForAudit(String auditNo, HttpServletRequest request) {
        AuditRecordResponse detail = auditApplicationService.getAdminDetail(auditNo);
        if (AuditApplicationService.AUDIT_TYPE_WITHDRAWAL.equals(detail.auditType())) {
            return adminAccessGuard.requireAdmin(request, "finance:review");
        }
        return adminAccessGuard.requireAdmin(request, "audit:review");
    }

    private void syncWithdrawalStatus(AuditRecordResponse response, String status, long adminUserId) {
        if (response != null && AuditApplicationService.AUDIT_TYPE_WITHDRAWAL.equals(response.auditType())) {
            walletLedgerService.markWithdrawalReviewed(response.targetId(), status);
            auditApplicationService.recordAdminOperation(
                    "WITHDRAWAL_REVIEW",
                    adminUserId,
                    "WITHDRAWAL",
                    response.targetId(),
                    status,
                    "提现审核状态已更新：" + status
            );
        }
    }
}

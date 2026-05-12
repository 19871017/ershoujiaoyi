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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private static final Set<String> ASSIGNABLE_OPERATOR_PERMISSIONS = Set.of(
            "audit:read",
            "audit:review",
            "finance:read",
            "finance:review",
            "user:read",
            "user:risk-control",
            "order:read",
            "after-sales:read",
            "after-sales:review",
            "system:config",
            "audit:log",
            "operator:grant"
    );

    private final AuditApplicationService auditApplicationService;
    private final WalletLedgerService walletLedgerService;
    private final LocationApplicationService locationApplicationService;
    private final AfterSalesApplicationService afterSalesApplicationService;
    private final OrderApplicationService orderApplicationService;
    private final ProductApplicationService productApplicationService;
    private final UserApplicationService userApplicationService;
    private final AdminAccessGuard adminAccessGuard;
    private final JdbcTemplate jdbcTemplate;

    public AdminController(AuditApplicationService auditApplicationService,
                           WalletLedgerService walletLedgerService,
                           LocationApplicationService locationApplicationService,
                           AfterSalesApplicationService afterSalesApplicationService,
                           OrderApplicationService orderApplicationService,
                           ProductApplicationService productApplicationService,
                           UserApplicationService userApplicationService,
                           AdminAccessGuard adminAccessGuard,
                           JdbcTemplate jdbcTemplate) {
        this.auditApplicationService = auditApplicationService;
        this.walletLedgerService = walletLedgerService;
        this.locationApplicationService = locationApplicationService;
        this.afterSalesApplicationService = afterSalesApplicationService;
        this.orderApplicationService = orderApplicationService;
        this.productApplicationService = productApplicationService;
        this.userApplicationService = userApplicationService;
        this.adminAccessGuard = adminAccessGuard;
        this.jdbcTemplate = jdbcTemplate;
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

    @GetMapping("/operators/{userId}/permissions")
    public Result<AdminOperatorPermissionResponse> operatorPermissions(@PathVariable Long userId, HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "operator:grant");
        return Result.ok(loadOperatorPermissions(userId));
    }

    @PostMapping("/operators/{userId}/permissions")
    public Result<AdminOperatorPermissionResponse> updateOperatorPermissions(@PathVariable Long userId,
                                                                            @RequestBody(required = false) AdminOperatorPermissionUpdateRequest body,
                                                                            HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "operator:grant");
        List<String> permissions = normalizeAssignablePermissions(body == null ? null : body.getPermissions());
        ensureActiveOperator(userId);
        jdbcReplaceOperatorPermissions(userId, permissions);
        auditApplicationService.recordAdminOperation(
                "OPERATOR_PERMISSION_GRANT",
                adminUserId,
                "ADMIN_OPERATOR",
                String.valueOf(userId),
                "SUCCESS",
                "运营经理权限已更新：" + String.join(",", permissions)
        );
        return Result.ok(loadOperatorPermissions(userId));
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

    private AdminOperatorPermissionResponse loadOperatorPermissions(Long userId) {
        OperatorRow row = findActiveOperator(userId);
        return new AdminOperatorPermissionResponse(row.userId(), row.userNo(), row.nickname(), row.status(), listOperatorPermissions(row.userId()));
    }

    private void ensureActiveOperator(Long userId) {
        findActiveOperator(userId);
    }

    private OperatorRow findActiveOperator(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("operator userId required");
        }
        List<OperatorRow> rows = jdbcQueryOperator(userId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("operator not found");
        }
        return rows.get(0);
    }

    private List<OperatorRow> jdbcQueryOperator(Long userId) {
        return jdbcTemplate.query("""
                SELECT id, user_no, nickname, status
                FROM user_account
                WHERE id = ? AND status = 'ACTIVE'
                """, (rs, rowNum) -> new OperatorRow(
                rs.getLong("id"),
                rs.getString("user_no"),
                rs.getString("nickname"),
                rs.getString("status")
        ), userId);
    }

    private List<String> listOperatorPermissions(Long userId) {
        return jdbcTemplate.query("""
                SELECT permission_code
                FROM admin_user_permission
                WHERE user_id = ? AND enabled = TRUE
                ORDER BY permission_code
                """, (rs, rowNum) -> rs.getString("permission_code"), userId)
                .stream()
                .filter(ASSIGNABLE_OPERATOR_PERMISSIONS::contains)
                .toList();
    }

    private List<String> normalizeAssignablePermissions(List<String> permissions) {
        if (permissions == null) {
            throw new IllegalArgumentException("permissions required");
        }
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String permission : permissions) {
            if (permission == null || permission.isBlank()) {
                throw new IllegalArgumentException("permission invalid");
            }
            String safePermission = permission.trim();
            if (!ASSIGNABLE_OPERATOR_PERMISSIONS.contains(safePermission)) {
                throw new IllegalArgumentException("permission invalid");
            }
            if (seen.add(safePermission)) {
                normalized.add(safePermission);
            }
        }
        return List.copyOf(normalized);
    }

    private void jdbcReplaceOperatorPermissions(Long userId, List<String> permissions) {
        jdbcTemplate.update("""
                UPDATE admin_user_permission
                SET enabled = FALSE, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND permission_code IN (
                    'audit:read',
                    'audit:review',
                    'finance:read',
                    'finance:review',
                    'user:read',
                    'user:risk-control',
                    'order:read',
                    'after-sales:read',
                    'after-sales:review',
                    'system:config',
                    'audit:log',
                    'operator:grant'
                )
                """, userId);
        for (String permission : permissions) {
            int updated = jdbcTemplate.update("""
                    UPDATE admin_user_permission
                    SET enabled = TRUE, updated_at = CURRENT_TIMESTAMP
                    WHERE user_id = ? AND permission_code = ?
                    """, userId, permission);
            if (updated == 0) {
                jdbcTemplate.update("""
                        INSERT INTO admin_user_permission (user_id, permission_code, enabled, created_at, updated_at)
                        VALUES (?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """, userId, permission);
            }
        }
    }

    private record OperatorRow(Long userId, String userNo, String nickname, String status) {
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

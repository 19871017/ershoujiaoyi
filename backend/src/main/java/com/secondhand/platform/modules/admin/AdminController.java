package com.secondhand.platform.modules.admin;

import com.secondhand.platform.modules.aftersales.AfterSalesResponse;
import com.secondhand.platform.modules.aftersales.application.AfterSalesApplicationService;
import com.secondhand.platform.modules.announcement.AdminUpdateAnnouncementTickerRequest;
import com.secondhand.platform.modules.announcement.AnnouncementApplicationService;
import com.secondhand.platform.modules.announcement.AnnouncementTickerResponse;
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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    private static final Set<String> BLOCKED_ADMIN_SEARCH_KEYWORDS = Set.of(
            "preview",
            "demo",
            "mock",
            "sample",
            "placeholder"
    );

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
    private final AnnouncementApplicationService announcementApplicationService;
    private final LocationApplicationService locationApplicationService;
    private final AfterSalesApplicationService afterSalesApplicationService;
    private final OrderApplicationService orderApplicationService;
    private final ProductApplicationService productApplicationService;
    private final UserApplicationService userApplicationService;
    private final com.secondhand.platform.modules.home.HomeBannerApplicationService homeBannerApplicationService;
    private final AdminAccessGuard adminAccessGuard;
    private final JdbcTemplate jdbcTemplate;

    public AdminController(AuditApplicationService auditApplicationService,
                           WalletLedgerService walletLedgerService,
                           AnnouncementApplicationService announcementApplicationService,
                           LocationApplicationService locationApplicationService,
                           AfterSalesApplicationService afterSalesApplicationService,
                           OrderApplicationService orderApplicationService,
                           ProductApplicationService productApplicationService,
                           UserApplicationService userApplicationService,
                           com.secondhand.platform.modules.home.HomeBannerApplicationService homeBannerApplicationService,
                           AdminAccessGuard adminAccessGuard,
                           JdbcTemplate jdbcTemplate) {
        this.auditApplicationService = auditApplicationService;
        this.walletLedgerService = walletLedgerService;
        this.announcementApplicationService = announcementApplicationService;
        this.locationApplicationService = locationApplicationService;
        this.afterSalesApplicationService = afterSalesApplicationService;
        this.orderApplicationService = orderApplicationService;
        this.productApplicationService = productApplicationService;
        this.userApplicationService = userApplicationService;
        this.homeBannerApplicationService = homeBannerApplicationService;
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
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(defaultValue = "20") Integer limit,
                                                         HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "order:read");
        return Result.ok(orderApplicationService.adminListOrders(status, keyword, limit));
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
    public Result<List<AuditRecordResponse>> auditList(@RequestParam(required = false) String auditType,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Integer limit,
                                                       HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "audit:read");
        return Result.ok(auditApplicationService.listAdminAudits(auditType, status, keyword, limit));
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

    @GetMapping("/announcements/ticker")
    public Result<AnnouncementTickerResponse> announcementTicker(HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "system:config");
        return Result.ok(announcementApplicationService.getTicker());
    }

    @GetMapping("/home/banners")
    public Result<List<com.secondhand.platform.modules.home.HomeBannerResponse>> homeBanners(HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "system:config");
        return Result.ok(homeBannerApplicationService.adminList());
    }

    @PostMapping("/home/banners")
    public Result<com.secondhand.platform.modules.home.HomeBannerResponse> createHomeBanner(@RequestBody(required = false) com.secondhand.platform.modules.home.AdminHomeBannerRequest body,
                                                                                           HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "system:config");
        var response = homeBannerApplicationService.adminCreate(body);
        auditApplicationService.recordAdminOperation(
                "HOME_BANNER_CREATE",
                adminUserId,
                "SYSTEM_CONFIG",
                "home-banner-" + response.id(),
                "SUCCESS",
                "首页轮播图已新增：" + response.title()
        );
        return Result.ok(response);
    }

    @PostMapping("/home/banners/{bannerId}")
    public Result<com.secondhand.platform.modules.home.HomeBannerResponse> updateHomeBanner(@PathVariable Long bannerId,
                                                                                           @RequestBody(required = false) com.secondhand.platform.modules.home.AdminHomeBannerRequest body,
                                                                                           HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "system:config");
        var response = homeBannerApplicationService.adminUpdate(bannerId, body);
        auditApplicationService.recordAdminOperation(
                "HOME_BANNER_UPDATE",
                adminUserId,
                "SYSTEM_CONFIG",
                "home-banner-" + response.id(),
                "SUCCESS",
                "首页轮播图已更新：" + response.title()
        );
        return Result.ok(response);
    }

    @PostMapping("/home/banners/{bannerId}/delete")
    public Result<com.secondhand.platform.modules.home.HomeBannerResponse> deleteHomeBanner(@PathVariable Long bannerId,
                                                                                           HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "system:config");
        var response = homeBannerApplicationService.adminDelete(bannerId);
        auditApplicationService.recordAdminOperation(
                "HOME_BANNER_DELETE",
                adminUserId,
                "SYSTEM_CONFIG",
                "home-banner-" + response.id(),
                "SUCCESS",
                "首页轮播图已删除：" + response.title()
        );
        return Result.ok(response);
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

    @PostMapping("/announcements/ticker")
    public Result<AnnouncementTickerResponse> updateAnnouncementTicker(@RequestBody(required = false) AdminUpdateAnnouncementTickerRequest body,
                                                                       HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "system:config");
        AnnouncementTickerResponse response = announcementApplicationService.adminUpdateTicker(body);
        auditApplicationService.recordAdminOperation(
                "ANNOUNCEMENT_TICKER_UPDATE",
                adminUserId,
                "SYSTEM_CONFIG",
                "announcement-ticker",
                "SUCCESS",
                "全局跑马灯公告已更新：enabled=" + response.enabled()
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
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(defaultValue = "20") Integer limit,
                                                           HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "after-sales:read");
        return Result.ok(afterSalesApplicationService.listAdminAfterSales(status, keyword, limit));
    }

    @GetMapping("/after-sales/{afterSalesNo}")
    public Result<AfterSalesResponse> afterSalesDetail(@PathVariable String afterSalesNo, HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "after-sales:read");
        return Result.ok(afterSalesApplicationService.getAdminDetail(afterSalesNo));
    }

    @GetMapping("/chat/conversations")
    public Result<List<AdminChatConversationTraceResponse>> chatConversationList(@RequestParam(required = false) Long conversationId,
                                                                                @RequestParam(required = false) Long userId,
                                                                                @RequestParam(required = false) String keyword,
                                                                                @RequestParam(defaultValue = "20") Integer limit,
                                                                                HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "audit:read");
        return Result.ok(queryAdminChatConversations(conversationId, userId, keyword, limit));
    }

    @GetMapping("/chat/conversations/{conversationId}/messages")
    public Result<AdminChatConversationMessageTraceResponse> chatConversationMessages(@PathVariable Long conversationId,
                                                                                     @RequestParam(defaultValue = "100") Integer limit,
                                                                                     HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "audit:read");
        long safeConversationId = requirePositiveId(conversationId, "conversationId invalid");
        int safeLimit = normalizeLimit(limit, 100, 200, "chat message limit invalid");
        List<AdminChatConversationTraceResponse> conversations = queryAdminChatConversations(safeConversationId, null, null, 1);
        if (conversations.isEmpty()) {
            throw new IllegalArgumentException("conversation not found");
        }
        List<AdminChatMessageTraceResponse> messages = jdbcTemplate.query("""
                SELECT *
                FROM (
                    SELECT id, message_no, conversation_id, conversation_no, server_seq, client_msg_id,
                           sender_id, receiver_id, message_type, content_json, created_at
                    FROM im_message
                    WHERE conversation_id = ?
                    ORDER BY server_seq DESC
                    LIMIT ?
                ) recent_messages
                ORDER BY server_seq ASC
                """, (rs, rowNum) -> new AdminChatMessageTraceResponse(
                rs.getLong("id"),
                rs.getString("message_no"),
                rs.getLong("conversation_id"),
                rs.getString("conversation_no"),
                rs.getLong("server_seq"),
                rs.getString("client_msg_id"),
                rs.getLong("sender_id"),
                rs.getLong("receiver_id"),
                rs.getString("message_type"),
                rs.getString("content_json"),
                toLocalDateTime(rs.getTimestamp("created_at"))
        ), safeConversationId, safeLimit);
        return Result.ok(new AdminChatConversationMessageTraceResponse(conversations.get(0), messages));
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

    private List<AdminChatConversationTraceResponse> queryAdminChatConversations(Long conversationId, Long userId, String keyword, Integer limit) {
        Long safeConversationId = conversationId == null ? null : requirePositiveId(conversationId, "conversationId invalid");
        Long safeUserId = userId == null ? null : requirePositiveId(userId, "userId invalid");
        String safeKeyword = normalizeAdminSearchKeyword(keyword);
        int safeLimit = normalizeLimit(limit, 20, 100, "chat conversation limit invalid");
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.conversation_no, c.owner_user_id, c.peer_user_id, c.conversation_type,
                       c.last_seq, c.last_message_summary, c.created_at, c.updated_at,
                       owner.user_no AS owner_user_no,
                       owner.nickname AS owner_nickname,
                       owner.avatar_url AS owner_avatar_url,
                       owner.status AS owner_status,
                       owner_profile.gender AS owner_gender,
                       owner_profile.city AS owner_city,
                       COALESCE(owner_profile.main_role, 'BUYER') AS owner_main_role,
                       COALESCE(owner_profile.video_verified, FALSE) AS owner_video_verified,
                       peer.user_no AS peer_user_no,
                       peer.nickname AS peer_nickname,
                       peer.avatar_url AS peer_avatar_url,
                       peer.status AS peer_status,
                       peer_profile.gender AS peer_gender,
                       peer_profile.city AS peer_city,
                       COALESCE(peer_profile.main_role, 'BUYER') AS peer_main_role,
                       COALESCE(peer_profile.video_verified, FALSE) AS peer_video_verified
                FROM im_conversation c
                LEFT JOIN user_account owner ON owner.id = c.owner_user_id
                LEFT JOIN user_profile owner_profile ON owner_profile.user_id = owner.id
                LEFT JOIN user_account peer ON peer.id = c.peer_user_id
                LEFT JOIN user_profile peer_profile ON peer_profile.user_id = peer.id
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (safeConversationId != null) {
            sql.append(" AND c.id = ?");
            args.add(safeConversationId);
        }
        if (safeUserId != null) {
            sql.append(" AND (c.owner_user_id = ? OR c.peer_user_id = ?)");
            args.add(safeUserId);
            args.add(safeUserId);
        }
        if (safeKeyword != null) {
            String likeKeyword = "%" + safeKeyword + "%";
            sql.append("""
                     AND (
                        c.conversation_no = ?
                        OR c.last_message_summary LIKE ?
                        OR owner.user_no = ?
                        OR peer.user_no = ?
                        OR owner.nickname LIKE ?
                        OR peer.nickname LIKE ?
                """);
            Collections.addAll(args, safeKeyword, likeKeyword, safeKeyword, safeKeyword, likeKeyword, likeKeyword);
            if (safeKeyword.matches("^[1-9]\\d{0,18}$")) {
                long numericKeyword = Long.parseLong(safeKeyword);
                sql.append("""
                        OR c.id = ?
                        OR c.owner_user_id = ?
                        OR c.peer_user_id = ?
                """);
                Collections.addAll(args, numericKeyword, numericKeyword, numericKeyword);
            }
            sql.append(")");
        }
        sql.append(" ORDER BY c.updated_at DESC, c.id DESC LIMIT ?");
        args.add(safeLimit);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new AdminChatConversationTraceResponse(
                rs.getLong("id"),
                rs.getString("conversation_no"),
                rs.getString("conversation_type"),
                rs.getLong("last_seq"),
                rs.getString("last_message_summary"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at")),
                new AdminChatParticipantTraceResponse(
                        rs.getLong("owner_user_id"),
                        rs.getString("owner_user_no"),
                        rs.getString("owner_nickname"),
                        rs.getString("owner_avatar_url"),
                        rs.getString("owner_status"),
                        rs.getString("owner_gender"),
                        rs.getString("owner_city"),
                        rs.getString("owner_main_role"),
                        rs.getBoolean("owner_video_verified")
                ),
                new AdminChatParticipantTraceResponse(
                        rs.getLong("peer_user_id"),
                        rs.getString("peer_user_no"),
                        rs.getString("peer_nickname"),
                        rs.getString("peer_avatar_url"),
                        rs.getString("peer_status"),
                        rs.getString("peer_gender"),
                        rs.getString("peer_city"),
                        rs.getString("peer_main_role"),
                        rs.getBoolean("peer_video_verified")
                )
        ), args.toArray());
    }

    private Long requirePositiveId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private int normalizeLimit(Integer limit, int defaultLimit, int maxLimit, String message) {
        int normalized = limit == null ? defaultLimit : limit;
        if (normalized <= 0 || normalized > maxLimit) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeAdminSearchKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("keyword invalid");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (BLOCKED_ADMIN_SEARCH_KEYWORDS.stream().anyMatch(lower::contains)) {
            throw new IllegalArgumentException("keyword invalid");
        }
        if (normalized.matches("^\\d+$") && !normalized.matches("^[1-9]\\d{0,18}$")) {
            throw new IllegalArgumentException("keyword invalid");
        }
        return normalized;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
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

    public record AdminChatParticipantTraceResponse(Long userId,
                                                    String userNo,
                                                    String nickname,
                                                    String avatarUrl,
                                                    String status,
                                                    String gender,
                                                    String city,
                                                    String mainRole,
                                                    Boolean videoVerified) {
    }

    public record AdminChatConversationTraceResponse(Long conversationId,
                                                     String conversationNo,
                                                     String conversationType,
                                                     Long lastSeq,
                                                     String lastMessageSummary,
                                                     LocalDateTime createdAt,
                                                     LocalDateTime updatedAt,
                                                     AdminChatParticipantTraceResponse owner,
                                                     AdminChatParticipantTraceResponse peer) {
    }

    public record AdminChatMessageTraceResponse(Long messageId,
                                                String messageNo,
                                                Long conversationId,
                                                String conversationNo,
                                                Long serverSeq,
                                                String clientMsgId,
                                                Long senderId,
                                                Long receiverId,
                                                String messageType,
                                                String contentJson,
                                                LocalDateTime createdAt) {
    }

    public record AdminChatConversationMessageTraceResponse(AdminChatConversationTraceResponse conversation,
                                                            List<AdminChatMessageTraceResponse> messages) {
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

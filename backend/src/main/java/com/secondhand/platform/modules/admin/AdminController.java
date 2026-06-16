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
import com.secondhand.platform.modules.community.application.CommunityApplicationService;
import com.secondhand.platform.modules.community.application.CommunityPostDetailResponse;
import com.secondhand.platform.modules.community.application.CommunityPostResponse;
import com.secondhand.platform.modules.location.AdminUpdateLocationConfigRequest;
import com.secondhand.platform.modules.location.LocationApplicationService;
import com.secondhand.platform.modules.location.LocationConfigResponse;
import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
import com.secondhand.platform.modules.media.application.VideoIdentityMediaInspector;
import com.secondhand.platform.modules.order.OrderDetailResponse;
import com.secondhand.platform.modules.order.OrderListItemResponse;
import com.secondhand.platform.modules.order.application.OrderApplicationService;
import com.secondhand.platform.modules.payment.AdminPaymentChannelConfigRequest;
import com.secondhand.platform.modules.payment.AdminPaymentChannelConfigResponse;
import com.secondhand.platform.modules.payment.application.DefaultPaymentGatewayClient;
import com.secondhand.platform.modules.payment.application.PaymentApplicationService;
import com.secondhand.platform.modules.product.CreateProductResponse;
import com.secondhand.platform.modules.product.ProductDetailResponse;
import com.secondhand.platform.modules.product.ProductListItemResponse;
import com.secondhand.platform.modules.product.UpdateProductResponse;
import com.secondhand.platform.modules.product.application.AdminProductPricingConfigRequest;
import com.secondhand.platform.modules.product.application.AdminProductPricingConfigResponse;
import com.secondhand.platform.modules.product.application.ProductApplicationService;
import com.secondhand.platform.modules.user.AdminUserDetailResponse;
import com.secondhand.platform.modules.user.application.UserApplicationService;
import com.secondhand.platform.modules.wallet_ledger.AdminWithdrawalReviewDetailResponse;
import com.secondhand.platform.modules.wallet_ledger.WithdrawalResponse;
import com.secondhand.platform.modules.wallet_ledger.application.WalletLedgerService;
import com.secondhand.platform.shared.kernel.Result;
import com.secondhand.platform.shared.web.AdminAccessGuard;
import com.secondhand.platform.shared.web.MediaPathGuard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private static final double VIDEO_IDENTITY_WATCH_RATIO_THRESHOLD = 0.8;
    private static final Set<String> ALLOWED_VIDEO_IDENTITY_CONTENT_TYPES = Set.of("video/mp4", "video/quicktime", "video/x-m4v");
    private static final double VIDEO_IDENTITY_WATCH_PROGRESS_GRACE_SECONDS = 0.75;
    private static final double VIDEO_IDENTITY_DURATION_MISMATCH_GRACE_SECONDS = 1.0;
    private static final String DEFAULT_GOD_AVATAR_URL = "/assets/profile/default-avatar-god.png";
    private static final String DEFAULT_GODDESS_AVATAR_URL = "/assets/profile/default-avatar-goddess.png";
    private static final Set<String> BLOCKED_ADMIN_SEARCH_KEYWORDS = Set.of(
            "preview",
            "demo",
            "mock",
            "sample",
            "placeholder"
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() {
    };

    private static final Set<String> ASSIGNABLE_OPERATOR_PERMISSIONS = Set.of(
            "audit:read",
            "audit:review",
            "chat:trace",
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
    private final PaymentApplicationService paymentApplicationService;
    private final CommunityApplicationService communityApplicationService;
    private final UserApplicationService userApplicationService;
    private final com.secondhand.platform.modules.home.HomeBannerApplicationService homeBannerApplicationService;
    private final AdminAccessGuard adminAccessGuard;
    private final JdbcTemplate jdbcTemplate;
    private final MediaUploadTicketService mediaUploadTicketService;
    private final Path mediaStorageRoot;

    @Autowired
    public AdminController(AuditApplicationService auditApplicationService,
                           WalletLedgerService walletLedgerService,
                           AnnouncementApplicationService announcementApplicationService,
                           LocationApplicationService locationApplicationService,
                           AfterSalesApplicationService afterSalesApplicationService,
                           OrderApplicationService orderApplicationService,
                           ProductApplicationService productApplicationService,
                           PaymentApplicationService paymentApplicationService,
                           CommunityApplicationService communityApplicationService,
                           UserApplicationService userApplicationService,
                           com.secondhand.platform.modules.home.HomeBannerApplicationService homeBannerApplicationService,
                           AdminAccessGuard adminAccessGuard,
                           JdbcTemplate jdbcTemplate,
                           MediaUploadTicketService mediaUploadTicketService,
                           @Value("${media.storage-root:${java.io.tmpdir}}") String mediaStorageRoot) {
        this.auditApplicationService = auditApplicationService;
        this.walletLedgerService = walletLedgerService;
        this.announcementApplicationService = announcementApplicationService;
        this.locationApplicationService = locationApplicationService;
        this.afterSalesApplicationService = afterSalesApplicationService;
        this.orderApplicationService = orderApplicationService;
        this.productApplicationService = productApplicationService;
        this.paymentApplicationService = paymentApplicationService;
        this.communityApplicationService = communityApplicationService;
        this.userApplicationService = userApplicationService;
        this.homeBannerApplicationService = homeBannerApplicationService;
        this.adminAccessGuard = adminAccessGuard;
        this.jdbcTemplate = jdbcTemplate;
        this.mediaUploadTicketService = mediaUploadTicketService;
        this.mediaStorageRoot = resolveMediaStorageRoot(mediaStorageRoot);
    }

    public AdminController(AuditApplicationService auditApplicationService,
                           WalletLedgerService walletLedgerService,
                           AnnouncementApplicationService announcementApplicationService,
                           LocationApplicationService locationApplicationService,
                           AfterSalesApplicationService afterSalesApplicationService,
                           OrderApplicationService orderApplicationService,
                           ProductApplicationService productApplicationService,
                           CommunityApplicationService communityApplicationService,
                           UserApplicationService userApplicationService,
                           com.secondhand.platform.modules.home.HomeBannerApplicationService homeBannerApplicationService,
                           AdminAccessGuard adminAccessGuard,
                           JdbcTemplate jdbcTemplate) {
        this(auditApplicationService,
                walletLedgerService,
                announcementApplicationService,
                locationApplicationService,
                afterSalesApplicationService,
                orderApplicationService,
                productApplicationService,
                new PaymentApplicationService(walletLedgerService, jdbcTemplate, orderApplicationService, new DefaultPaymentGatewayClient()),
                communityApplicationService,
                userApplicationService,
                homeBannerApplicationService,
                adminAccessGuard,
                jdbcTemplate,
                new MediaUploadTicketService(jdbcTemplate),
                System.getProperty("java.io.tmpdir"));
    }

    public AdminController(AuditApplicationService auditApplicationService,
                           WalletLedgerService walletLedgerService,
                           AnnouncementApplicationService announcementApplicationService,
                           LocationApplicationService locationApplicationService,
                           AfterSalesApplicationService afterSalesApplicationService,
                           OrderApplicationService orderApplicationService,
                           ProductApplicationService productApplicationService,
                           CommunityApplicationService communityApplicationService,
                           UserApplicationService userApplicationService,
                           com.secondhand.platform.modules.home.HomeBannerApplicationService homeBannerApplicationService,
                           AdminAccessGuard adminAccessGuard,
                           JdbcTemplate jdbcTemplate,
                           MediaUploadTicketService mediaUploadTicketService,
                           String mediaStorageRoot) {
        this(auditApplicationService,
                walletLedgerService,
                announcementApplicationService,
                locationApplicationService,
                afterSalesApplicationService,
                orderApplicationService,
                productApplicationService,
                new PaymentApplicationService(walletLedgerService, jdbcTemplate, orderApplicationService, new DefaultPaymentGatewayClient()),
                communityApplicationService,
                userApplicationService,
                homeBannerApplicationService,
                adminAccessGuard,
                jdbcTemplate,
                mediaUploadTicketService,
                mediaStorageRoot);
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
        String auditNo = productApplicationService.requirePendingProductAuditNo(productId);
        productApplicationService.approveForSale(productId);
        auditApplicationService.approve(auditNo, "后台商品审核通过", adminUserId);
        return Result.ok(productApplicationService.createResponse(productId));
    }

    @PostMapping("/products/{productId}/reject")
    public Result<CreateProductResponse> rejectProduct(@PathVariable Long productId, HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "audit:review");
        String auditNo = productApplicationService.requirePendingProductAuditNo(productId);
        productApplicationService.rejectForSale(productId);
        auditApplicationService.reject(auditNo, "后台商品审核拒绝", adminUserId);
        return Result.ok(productApplicationService.createResponse(productId));
    }

    @PostMapping("/products/{productId}/offline")
    public Result<UpdateProductResponse> offlineProduct(@PathVariable Long productId,
                                                        @RequestBody AdminProductOfflineRequest body,
                                                        HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "audit:review");
        String reason = body == null || body.getReason() == null ? null : body.getReason().trim();
        UpdateProductResponse response = productApplicationService.adminOfflineProduct(productId, reason);
        auditApplicationService.recordAdminOperation(
                "PRODUCT_OFFLINE",
                adminUserId,
                "PRODUCT",
                String.valueOf(productId),
                "SUCCESS",
                reason
        );
        return Result.ok(response);
    }

    @PostMapping("/products/{productId}/hide")
    public Result<UpdateProductResponse> hideProduct(@PathVariable Long productId,
                                                     @RequestBody AdminProductOfflineRequest body,
                                                     HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "audit:review");
        String reason = body == null || body.getReason() == null ? null : body.getReason().trim();
        UpdateProductResponse response = productApplicationService.adminHideProduct(productId, reason);
        auditApplicationService.recordAdminOperation(
                "PRODUCT_HIDE",
                adminUserId,
                "PRODUCT",
                String.valueOf(productId),
                "SUCCESS",
                reason
        );
        return Result.ok(response);
    }

    @PostMapping("/products/{productId}/delete")
    public Result<UpdateProductResponse> deleteProduct(@PathVariable Long productId,
                                                       @RequestBody AdminProductOfflineRequest body,
                                                       HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "audit:review");
        String reason = body == null || body.getReason() == null ? null : body.getReason().trim();
        UpdateProductResponse response = productApplicationService.adminDeleteProduct(productId, reason);
        rejectPendingProductAuditIfPresent(productId, adminUserId);
        auditApplicationService.recordAdminOperation(
                "PRODUCT_DELETE",
                adminUserId,
                "PRODUCT",
                String.valueOf(productId),
                "SUCCESS",
                reason
        );
        return Result.ok(response);
    }

    @GetMapping("/products")
    public Result<List<ProductListItemResponse>> productList(@RequestParam(required = false) String status,
                                                             @RequestParam(required = false) String auditStatus,
                                                             @RequestParam(required = false) String keyword,
                                                             @RequestParam(defaultValue = "20") Integer limit,
                                                             HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "audit:read");
        return Result.ok(productApplicationService.adminListProducts(status, auditStatus, keyword, limit));
    }

    @GetMapping("/products/{productId}")
    public Result<ProductDetailResponse> productDetail(@PathVariable Long productId, HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "audit:read");
        return Result.ok(productApplicationService.adminDetailProduct(productId));
    }

    @GetMapping("/community/posts")
    public Result<List<CommunityPostResponse>> communityPostList(@RequestParam(required = false) String keyword,
                                                                 @RequestParam(required = false) Long authorId,
                                                                 @RequestParam(defaultValue = "20") Integer limit,
                                                                 HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "audit:read");
        List<CommunityPostResponse> rows = communityApplicationService.adminListPosts(keyword, authorId, limit);
        auditApplicationService.recordAdminOperation(
                "COMMUNITY_TRACE_LIST",
                adminUserId,
                "COMMUNITY_POST_LIST",
                communityTraceListTargetId(authorId, keyword, limit),
                "SUCCESS",
                "查询社区追溯列表 rows=" + rows.size()
        );
        return Result.ok(rows);
    }

    @GetMapping("/community/posts/{postId}")
    public Result<CommunityPostDetailResponse> communityPostDetail(@PathVariable String postId, HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "audit:read");
        CommunityPostDetailResponse response = communityApplicationService.adminDetail(postId);
        auditApplicationService.recordAdminOperation(
                "COMMUNITY_TRACE_VIEW",
                adminUserId,
                "COMMUNITY_POST",
                String.valueOf(response.getPostId()),
                "SUCCESS",
                "查看社区追溯 postNo=" + response.getPostNo() + " comments=" + response.getComments().size()
        );
        return Result.ok(response);
    }

    @PostMapping("/community/posts/{postId}/block")
    public Result<CommunityPostDetailResponse> blockCommunityPost(@PathVariable String postId,
                                                                  @RequestBody(required = false) AdminCommunityModerationRequest body,
                                                                  HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "audit:review");
        String reason = body == null || body.getReason() == null ? null : body.getReason().trim();
        CommunityPostDetailResponse response = communityApplicationService.adminBlockPost(postId, reason);
        auditApplicationService.recordAdminOperation(
                "COMMUNITY_POST_BLOCK",
                adminUserId,
                "COMMUNITY_POST",
                String.valueOf(response.getPostId()),
                "SUCCESS",
                reason
        );
        return Result.ok(response);
    }

    @PostMapping("/community/posts/{postId}/restore")
    public Result<CommunityPostDetailResponse> restoreCommunityPost(@PathVariable String postId,
                                                                    @RequestBody(required = false) AdminCommunityModerationRequest body,
                                                                    HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "audit:review");
        String reason = body == null || body.getReason() == null ? null : body.getReason().trim();
        CommunityPostDetailResponse response = communityApplicationService.adminRestorePost(postId, reason);
        auditApplicationService.recordAdminOperation(
                "COMMUNITY_POST_RESTORE",
                adminUserId,
                "COMMUNITY_POST",
                String.valueOf(response.getPostId()),
                "SUCCESS",
                reason
        );
        return Result.ok(response);
    }

    @PostMapping("/community/comments/{commentNo}/block")
    public Result<CommunityPostDetailResponse> blockCommunityComment(@PathVariable String commentNo,
                                                                     @RequestBody(required = false) AdminCommunityModerationRequest body,
                                                                     HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "audit:review");
        String reason = body == null || body.getReason() == null ? null : body.getReason().trim();
        CommunityPostDetailResponse response = communityApplicationService.adminBlockComment(commentNo, reason);
        auditApplicationService.recordAdminOperation(
                "COMMUNITY_COMMENT_BLOCK",
                adminUserId,
                "COMMUNITY_COMMENT",
                commentNo.trim(),
                "SUCCESS",
                "postId=" + response.getPostId() + "; " + reason
        );
        return Result.ok(response);
    }

    @PostMapping("/community/comments/{commentNo}/restore")
    public Result<CommunityPostDetailResponse> restoreCommunityComment(@PathVariable String commentNo,
                                                                       @RequestBody(required = false) AdminCommunityModerationRequest body,
                                                                       HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "audit:review");
        String reason = body == null || body.getReason() == null ? null : body.getReason().trim();
        CommunityPostDetailResponse response = communityApplicationService.adminRestoreComment(commentNo, reason);
        auditApplicationService.recordAdminOperation(
                "COMMUNITY_COMMENT_RESTORE",
                adminUserId,
                "COMMUNITY_COMMENT",
                commentNo.trim(),
                "SUCCESS",
                "postId=" + response.getPostId() + "; " + reason
        );
        return Result.ok(response);
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

    @GetMapping("/payment/config")
    public Result<List<AdminPaymentChannelConfigResponse>> paymentConfig(HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "system:config");
        return Result.ok(paymentApplicationService.adminListChannelConfigs());
    }

    @GetMapping("/product-pricing/config")
    public Result<AdminProductPricingConfigResponse> productPricingConfig(HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "system:config");
        return Result.ok(productApplicationService.adminPricingConfig());
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

    @PostMapping("/payment/config/{channel}")
    public Result<AdminPaymentChannelConfigResponse> updatePaymentConfig(@PathVariable String channel,
                                                                         @RequestBody(required = false) AdminPaymentChannelConfigRequest body,
                                                                         HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "system:config");
        AdminPaymentChannelConfigResponse response = paymentApplicationService.adminUpdateChannelConfig(channel, body);
        auditApplicationService.recordAdminOperation(
                "PAYMENT_CONFIG_UPDATE",
                adminUserId,
                "SYSTEM_CONFIG",
                "payment:" + response.channel(),
                "SUCCESS",
                "更新支付配置 channel=" + response.channel() + " enabled=" + response.enabled() + " configured=" + response.configured()
        );
        return Result.ok(response);
    }

    @PostMapping("/product-pricing/config")
    public Result<AdminProductPricingConfigResponse> updateProductPricingConfig(@RequestBody(required = false) AdminProductPricingConfigRequest body,
                                                                                HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "system:config");
        AdminProductPricingConfigResponse response = productApplicationService.adminUpdatePricingConfig(body);
        auditApplicationService.recordAdminOperation(
                "PRODUCT_PRICING_CONFIG_UPDATE",
                adminUserId,
                "SYSTEM_CONFIG",
                "product-pricing",
                "SUCCESS",
                "商品加价比例已更新：markupRate=" + response.markupRate()
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
    public Result<AdminWithdrawalReviewDetailResponse> withdrawalDetail(@PathVariable String withdrawalNo, HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request, "finance:read");
        return Result.ok(walletLedgerService.getAdminWithdrawalReviewDetail(withdrawalNo));
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
        adminAccessGuard.requireAdmin(request, "chat:trace");
        return Result.ok(queryAdminChatConversations(conversationId, userId, keyword, limit));
    }

    @GetMapping("/chat/conversations/{conversationId}/messages")
    public Result<AdminChatConversationMessageTraceResponse> chatConversationMessages(@PathVariable Long conversationId,
                                                                                     @RequestParam(defaultValue = "100") Integer limit,
                                                                                     @RequestParam(required = false) Long beforeSeq,
                                                                                     HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "chat:trace");
        long safeConversationId = requirePositiveId(conversationId, "conversationId invalid");
        Long safeBeforeSeq = beforeSeq == null ? null : requirePositiveId(beforeSeq, "beforeSeq invalid");
        int safeLimit = normalizeLimit(limit, 100, 200, "chat message limit invalid");
        List<AdminChatConversationTraceResponse> conversations = queryAdminChatConversations(safeConversationId, null, null, 1);
        if (conversations.isEmpty()) {
            throw new IllegalArgumentException("conversation not found");
        }
        List<Object> args = new ArrayList<>();
        args.add(safeConversationId);
        String beforeSeqFilter = "";
        if (safeBeforeSeq != null) {
            beforeSeqFilter = " AND server_seq < ?";
            args.add(safeBeforeSeq);
        }
        args.add(safeLimit + 1);
        List<AdminChatMessageTraceResponse> fetchedMessages = jdbcTemplate.query("""
                SELECT *
                FROM (
                    SELECT id, message_no, conversation_id, conversation_no, server_seq, client_msg_id,
                           sender_id, receiver_id, message_type, content_json, revoked, revoked_at, created_at
                    FROM im_message
                    WHERE conversation_id = ?
                    """ + beforeSeqFilter + """
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
                rs.getBoolean("revoked"),
                toLocalDateTime(rs.getTimestamp("revoked_at")),
                toLocalDateTime(rs.getTimestamp("created_at"))
        ), args.toArray());
        boolean hasMore = fetchedMessages.size() > safeLimit;
        List<AdminChatMessageTraceResponse> messages = hasMore
                ? List.copyOf(fetchedMessages.subList(1, fetchedMessages.size()))
                : List.copyOf(fetchedMessages);
        Long oldestSeq = messages.isEmpty() ? null : messages.get(0).serverSeq();
        Long nextBeforeSeq = hasMore ? oldestSeq : null;
        auditApplicationService.recordAdminOperation(
                "CHAT_TRACE_VIEW",
                adminUserId,
                "CHAT_CONVERSATION",
                String.valueOf(safeConversationId),
                "SUCCESS",
                "查看私聊追溯 limit=" + safeLimit + (safeBeforeSeq == null ? "" : " beforeSeq=" + safeBeforeSeq) + " messages=" + messages.size()
        );
        return Result.ok(new AdminChatConversationMessageTraceResponse(conversations.get(0), messages, hasMore, oldestSeq, nextBeforeSeq));
    }

    @GetMapping("/chat/media")
    public ResponseEntity<Resource> chatTraceMedia(@RequestParam("conversationId") Long conversationId,
                                                   @RequestParam("messageId") Long messageId,
                                                   @RequestParam("messageNo") String messageNo,
                                                   @RequestParam("url") String storageUrl,
                                                   HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "chat:trace");
        long safeConversationId = requirePositiveId(conversationId, "conversationId invalid");
        long safeMessageId = requirePositiveId(messageId, "messageId invalid");
        String safeMessageNo = requireText(messageNo, "messageNo required");
        String safeStorageUrl = normalizeChatTraceMediaUrl(storageUrl);
        ChatTraceMediaCandidate mediaCandidate = assertChatTraceMediaBoundToMessage(safeConversationId, safeMessageId, safeMessageNo, safeStorageUrl);
        mediaUploadTicketService.requireUploadedStorageUrl(mediaCandidate.senderId(), sceneForChatTraceMedia(mediaCandidate.messageType()), safeStorageUrl);
        Path mediaPath = verifiedChatTraceMediaPathFor(safeStorageUrl);
        auditApplicationService.recordAdminOperation(
                "CHAT_TRACE_MEDIA_VIEW",
                adminUserId,
                "CHAT_MEDIA",
                safeConversationId + ":" + safeMessageId + ":" + safeMessageNo,
                "SUCCESS",
                "读取私聊追溯媒体 conversationId=" + safeConversationId + " messageId=" + safeMessageId + " messageNo=" + safeMessageNo
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(resolveChatTraceMediaContentType(safeStorageUrl)))
                .contentLength(fileSize(mediaPath))
                .body(new FileSystemResource(mediaPath));
    }

    @GetMapping("/audit/{auditNo}/video-evidence")
    public ResponseEntity<Resource> auditVideoEvidence(@PathVariable String auditNo, HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "audit:read");
        AuditRecordResponse detail = auditApplicationService.getAdminDetail(auditNo);
        if (!AuditApplicationService.AUDIT_TYPE_VIDEO_IDENTITY.equals(detail.auditType())
                || detail.videoEvidenceVerified() != true
                || detail.videoEvidenceUrl() == null) {
            throw new IllegalArgumentException("video identity media not found");
        }
        String safeStorageUrl = normalizeVideoIdentityMediaUrl(detail.videoEvidenceUrl());
        Path mediaPath = verifiedVideoIdentityMediaPathFor(safeStorageUrl);
        auditApplicationService.recordAdminOperation(
                "VIDEO_IDENTITY_MEDIA_VIEW",
                adminUserId,
                "AUDIT",
                detail.auditNo(),
                "SUCCESS",
                "读取视频认证审核资料 auditNo=" + detail.auditNo()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(resolveVideoIdentityMediaContentType(safeStorageUrl)))
                .contentLength(fileSize(mediaPath))
                .body(new FileSystemResource(mediaPath));
    }

    @PostMapping("/audit/{auditNo}/video-evidence/progress")
    public Result<Void> auditVideoEvidenceProgress(@PathVariable String auditNo,
                                                   @RequestBody(required = false) VideoIdentityWatchProgressRequest body,
                                                   HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "audit:review");
        adminAccessGuard.requireAdmin(request, "audit:read");
        AuditRecordResponse detail = auditApplicationService.getAdminDetail(auditNo);
        if (!AuditApplicationService.AUDIT_TYPE_VIDEO_IDENTITY.equals(detail.auditType())
                || !AuditApplicationService.STATUS_PENDING.equals(detail.status())
                || detail.videoEvidenceVerified() != true
                || detail.videoEvidenceUrl() == null) {
            throw new IllegalArgumentException("video identity media not found");
        }
        String safeStorageUrl = normalizeVideoIdentityMediaUrl(detail.videoEvidenceUrl());
        Path mediaPath = verifiedVideoIdentityMediaPathFor(safeStorageUrl);
        if (hasVideoIdentityEvidenceLog(detail, adminUserId, "VIDEO_IDENTITY_MEDIA_WATCHED")) {
            return Result.ok(null);
        }
        LocalDateTime viewedAt = requireVideoIdentityEvidenceLogTime(detail, adminUserId, "VIDEO_IDENTITY_MEDIA_VIEW");
        double serverDurationSeconds = VideoIdentityMediaInspector.readDurationSeconds(mediaPath);
        VideoIdentityWatchProgress progress = normalizeVideoIdentityWatchProgress(body, serverDurationSeconds);
        requireVideoIdentityWatchElapsed(viewedAt, progress.requiredElapsedSeconds());
        auditApplicationService.recordAdminOperation(
                "VIDEO_IDENTITY_MEDIA_WATCHED",
                adminUserId,
                "AUDIT",
                detail.auditNo(),
                "SUCCESS",
                "视频认证资料观看进度已达标 progress=" + Math.round(progress.ratio() * 100) + "% elapsed>=" + Math.round(progress.requiredElapsedSeconds()) + "s"
        );
        return Result.ok(null);
    }

    @GetMapping("/audit/{auditNo}/report-evidence")
    public ResponseEntity<Resource> auditReportEvidence(@PathVariable String auditNo,
                                                        @RequestParam("url") String storageUrl,
                                                        HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "audit:read");
        AuditRecordResponse detail = auditApplicationService.getAdminDetail(auditNo);
        if (!AuditApplicationService.AUDIT_TYPE_REPORT.equals(detail.auditType())) {
            throw new IllegalArgumentException("report evidence not found");
        }
        String safeStorageUrl = normalizeReportEvidenceMediaUrl(storageUrl);
        assertEvidenceUrlBoundToReportAudit(detail, safeStorageUrl);
        mediaUploadTicketService.requireUploadedStorageUrl(detail.userId(), "REPORT_EVIDENCE", safeStorageUrl);
        Path mediaPath = verifiedSensitiveEvidenceMediaPathFor(safeStorageUrl, "report-evidence", "report evidence not found");
        auditApplicationService.recordAdminOperation(
                "REPORT_EVIDENCE_MEDIA_VIEW",
                adminUserId,
                "AUDIT",
                detail.auditNo(),
                "SUCCESS",
                "读取举报凭证 index=" + evidenceIndex(reportEvidenceUrls(detail), safeStorageUrl)
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(evidenceMediaContentType(safeStorageUrl)))
                .contentLength(fileSize(mediaPath))
                .body(new FileSystemResource(mediaPath));
    }

    @GetMapping("/after-sales/{afterSalesNo}/evidence")
    public ResponseEntity<Resource> afterSalesEvidence(@PathVariable String afterSalesNo,
                                                       @RequestParam("url") String storageUrl,
                                                       HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "after-sales:read");
        AfterSalesResponse detail = afterSalesApplicationService.getAdminDetail(afterSalesNo);
        String safeStorageUrl = normalizeAfterSalesEvidenceMediaUrl(storageUrl);
        List<String> evidenceUrls = detail.getEvidenceUrls() == null ? List.of() : detail.getEvidenceUrls();
        if (!evidenceUrls.contains(safeStorageUrl)) {
            throw new IllegalArgumentException("after-sales evidence not found");
        }
        mediaUploadTicketService.requireUploadedStorageUrl(detail.getApplicantId(), "AFTER_SALES_EVIDENCE", safeStorageUrl);
        Path mediaPath = verifiedSensitiveEvidenceMediaPathFor(safeStorageUrl, "evidence/after-sales", "after-sales evidence not found");
        auditApplicationService.recordAdminOperation(
                "AFTER_SALES_EVIDENCE_MEDIA_VIEW",
                adminUserId,
                "AFTER_SALES",
                detail.getAfterSalesNo(),
                "SUCCESS",
                "读取售后凭证 index=" + evidenceIndex(evidenceUrls, safeStorageUrl)
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(evidenceMediaContentType(safeStorageUrl)))
                .contentLength(fileSize(mediaPath))
                .body(new FileSystemResource(mediaPath));
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
        AuditRecordResponse detail = auditApplicationService.getAdminDetail(auditNo);
        requireVideoIdentityEvidenceViewedBeforeApproval(detail, adminUserId);
        syncProductStatus(detail, "APPROVED");
        AuditRecordResponse response = AuditApplicationService.AUDIT_TYPE_VIDEO_IDENTITY.equals(detail.auditType())
                ? auditApplicationService.approveVideoIdentityAfterEvidenceReview(auditNo, body == null ? null : body.getRemark(), adminUserId)
                : auditApplicationService.approve(auditNo, body == null ? null : body.getRemark(), adminUserId);
        syncWithdrawalStatus(response, "APPROVED", adminUserId);
        return Result.ok(response);
    }

    @PostMapping("/audit/{auditNo}/reject")
    public Result<AuditRecordResponse> rejectAudit(@PathVariable String auditNo,
                                                   @RequestBody(required = false) AuditReviewRequest body,
                                                   HttpServletRequest request) {
        long adminUserId = requireReviewPermissionForAudit(auditNo, request);
        AuditRecordResponse detail = auditApplicationService.getAdminDetail(auditNo);
        syncProductStatus(detail, "REJECTED");
        AuditRecordResponse response = auditApplicationService.reject(auditNo, body == null ? null : body.getRemark(), adminUserId);
        syncWithdrawalStatus(response, "REJECTED", adminUserId);
        return Result.ok(response);
    }

    private long requireReviewPermissionForAudit(String auditNo, HttpServletRequest request) {
        long adminUserId = adminAccessGuard.requireAdmin(request, "audit:review");
        AuditRecordResponse detail = auditApplicationService.getAdminDetail(auditNo);
        if (AuditApplicationService.AUDIT_TYPE_WITHDRAWAL.equals(detail.auditType())) {
            adminAccessGuard.requireAdmin(request, "finance:review");
        }
        return adminUserId;
    }

    private void requireVideoIdentityEvidenceViewedBeforeApproval(AuditRecordResponse detail, long adminUserId) {
        if (detail == null
                || !AuditApplicationService.AUDIT_TYPE_VIDEO_IDENTITY.equals(detail.auditType())
                || !AuditApplicationService.STATUS_PENDING.equals(detail.status())) {
            return;
        }
        requireVideoIdentityEvidenceLog(detail, adminUserId, "VIDEO_IDENTITY_MEDIA_WATCHED");
    }

    private void requireVideoIdentityEvidenceLog(AuditRecordResponse detail, long adminUserId, String action) {
        requireVideoIdentityEvidenceLogTime(detail, adminUserId, action);
    }

    private boolean hasVideoIdentityEvidenceLog(AuditRecordResponse detail, long adminUserId, String action) {
        Integer logCount = jdbcTemplate.queryForObject("""
                select count(1)
                from admin_audit_log
                where action = ?
                  and operator_id = ?
                  and target_type = ?
                  and target_id = ?
                  and result = ?
                """, Integer.class, action, adminUserId, "AUDIT", detail.auditNo(), "SUCCESS");
        return logCount != null && logCount > 0;
    }

    private LocalDateTime requireVideoIdentityEvidenceLogTime(AuditRecordResponse detail, long adminUserId, String action) {
        Timestamp latestLogAt = jdbcTemplate.queryForObject("""
                select max(created_at)
                from admin_audit_log
                where action = ?
                  and operator_id = ?
                  and target_type = ?
                  and target_id = ?
                  and result = ?
                """, Timestamp.class, action, adminUserId, "AUDIT", detail.auditNo(), "SUCCESS");
        if (latestLogAt == null) {
            throw new IllegalStateException("video identity evidence review required");
        }
        return latestLogAt.toLocalDateTime();
    }

    private VideoIdentityWatchProgress normalizeVideoIdentityWatchProgress(VideoIdentityWatchProgressRequest body, double serverDurationSeconds) {
        if (body == null) {
            throw new IllegalArgumentException("video watch progress invalid");
        }
        double safeServerDurationSeconds = finitePositive(serverDurationSeconds, "video identity duration unavailable");
        double durationSeconds = finitePositive(body.durationSeconds(), "video watch progress invalid");
        double currentTimeSeconds = finiteNonNegative(body.currentTimeSeconds(), "video watch progress invalid");
        double durationMismatchGrace = Math.max(VIDEO_IDENTITY_DURATION_MISMATCH_GRACE_SECONDS, safeServerDurationSeconds * 0.05);
        if (Math.abs(durationSeconds - safeServerDurationSeconds) > durationMismatchGrace) {
            throw new IllegalArgumentException("video watch progress invalid");
        }
        if (currentTimeSeconds > safeServerDurationSeconds + VIDEO_IDENTITY_WATCH_PROGRESS_GRACE_SECONDS) {
            throw new IllegalArgumentException("video watch progress invalid");
        }
        double actualRatio = Math.min(Math.max(currentTimeSeconds / safeServerDurationSeconds, 0), 1);
        if (body.watchedRatio() != null) {
            double reportedRatio = body.watchedRatio();
            if (!Double.isFinite(reportedRatio) || reportedRatio < 0 || reportedRatio > 1.05 || reportedRatio > actualRatio + 0.05) {
                throw new IllegalArgumentException("video watch progress invalid");
            }
        }
        boolean ended = Boolean.TRUE.equals(body.ended()) && currentTimeSeconds + VIDEO_IDENTITY_WATCH_PROGRESS_GRACE_SECONDS >= safeServerDurationSeconds;
        if (!ended && actualRatio < VIDEO_IDENTITY_WATCH_RATIO_THRESHOLD) {
            throw new IllegalArgumentException("video watch progress insufficient");
        }
        double safeRatio = ended ? 1 : actualRatio;
        double requiredElapsedSeconds = Math.max(1, safeServerDurationSeconds * VIDEO_IDENTITY_WATCH_RATIO_THRESHOLD - VIDEO_IDENTITY_WATCH_PROGRESS_GRACE_SECONDS);
        return new VideoIdentityWatchProgress(Math.min(safeRatio, 1), requiredElapsedSeconds);
    }

    private void requireVideoIdentityWatchElapsed(LocalDateTime viewedAt, double requiredElapsedSeconds) {
        double elapsedSeconds = Duration.between(viewedAt, LocalDateTime.now()).toMillis() / 1000.0;
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds + VIDEO_IDENTITY_WATCH_PROGRESS_GRACE_SECONDS < requiredElapsedSeconds) {
            throw new IllegalArgumentException("video watch progress not elapsed");
        }
    }

    private double finitePositive(Double value, String message) {
        if (value == null || !Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private double finiteNonNegative(Double value, String message) {
        if (value == null || !Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String normalizeChatTraceMediaUrl(String storageUrl) {
        String safeUrl = requireText(storageUrl, "chat media url required");
        String lower = safeUrl.toLowerCase(Locale.ROOT);
        if (!(safeUrl.startsWith("/uploads/chat-image/") || safeUrl.startsWith("/uploads/chat-voice/") || safeUrl.startsWith("/uploads/chat-video/"))
                || lower.startsWith("http:")
                || lower.startsWith("https:")
                || lower.startsWith("data:")
                || lower.startsWith("blob:")
                || lower.contains("preview")
                || lower.contains("demo")
                || lower.contains("mock")
                || lower.contains("sample")
                || lower.contains("placeholder")
                || lower.contains("%2e")
                || lower.contains("%2f")
                || lower.contains("%5c")
                || safeUrl.contains("\\")
                || safeUrl.contains("..")
                || safeUrl.contains("//")) {
            throw new IllegalArgumentException("chat media url invalid");
        }
        return safeUrl;
    }

    private String normalizeVideoIdentityMediaUrl(String storageUrl) {
        String safeUrl = requireText(storageUrl, "video identity media url required");
        String lower = safeUrl.toLowerCase(Locale.ROOT);
        if (!safeUrl.startsWith("/uploads/video-identity/")
                || lower.startsWith("http:")
                || lower.startsWith("https:")
                || lower.startsWith("data:")
                || lower.startsWith("blob:")
                || lower.contains("preview")
                || lower.contains("demo")
                || lower.contains("mock")
                || lower.contains("sample")
                || lower.contains("placeholder")
                || lower.contains("%2e")
                || lower.contains("%2f")
                || lower.contains("%5c")
                || safeUrl.contains("\\")
                || safeUrl.contains("..")
                || safeUrl.contains("//")) {
            throw new IllegalArgumentException("video identity media url invalid");
        }
        String relativePath = safeUrl.substring("/uploads/video-identity/".length());
        if (relativePath.isBlank() || !relativePath.contains("/")) {
            throw new IllegalArgumentException("video identity media url invalid");
        }
        for (String segment : relativePath.split("/")) {
            if (segment.isBlank()) {
                throw new IllegalArgumentException("video identity media url invalid");
            }
        }
        return safeUrl;
    }

    private String normalizeReportEvidenceMediaUrl(String storageUrl) {
        return normalizeSensitiveEvidenceMediaUrl(storageUrl, "/uploads/report-evidence/", "report evidence url invalid");
    }

    private String normalizeAfterSalesEvidenceMediaUrl(String storageUrl) {
        return normalizeSensitiveEvidenceMediaUrl(storageUrl, "/uploads/evidence/after-sales/", "after-sales evidence url invalid");
    }

    private String normalizeSensitiveEvidenceMediaUrl(String storageUrl, String prefix, String message) {
        String safeUrl = requireText(storageUrl, message);
        String lower = safeUrl.toLowerCase(Locale.ROOT);
        if (!safeUrl.startsWith(prefix)
                || lower.startsWith("http:")
                || lower.startsWith("https:")
                || lower.startsWith("data:")
                || lower.startsWith("blob:")
                || lower.contains("preview")
                || lower.contains("demo")
                || lower.contains("mock")
                || lower.contains("sample")
                || lower.contains("placeholder")
                || lower.contains("%2e")
                || lower.contains("%2f")
                || lower.contains("%5c")
                || safeUrl.contains("\\")
                || safeUrl.contains("..")
                || safeUrl.contains("//")) {
            throw new IllegalArgumentException(message);
        }
        String relativePath = safeUrl.substring(prefix.length());
        if (relativePath.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        for (String segment : relativePath.split("/")) {
            if (segment.isBlank()) {
                throw new IllegalArgumentException(message);
            }
        }
        return safeUrl;
    }

    private Path storagePathForChatTraceMedia(String storageUrl) {
        Path uploadsRoot = mediaStorageRoot.resolve("uploads").normalize();
        Path target = mediaStorageRoot.resolve(storageUrl.substring(1)).normalize();
        if (!target.startsWith(uploadsRoot)) {
            throw new IllegalArgumentException("chat media url invalid");
        }
        return target;
    }

    private Path storagePathForVideoIdentityMedia(String storageUrl) {
        Path videoIdentityRoot = mediaStorageRoot.resolve("uploads/video-identity").normalize();
        Path target = mediaStorageRoot.resolve(storageUrl.substring(1)).normalize();
        if (!target.startsWith(videoIdentityRoot)) {
            throw new IllegalArgumentException("video identity media url invalid");
        }
        return target;
    }

    private Path storagePathForSensitiveEvidenceMedia(String storageUrl, String relativeRoot) {
        Path evidenceRoot = mediaStorageRoot.resolve("uploads").resolve(relativeRoot).normalize();
        Path target = mediaStorageRoot.resolve(storageUrl.substring(1)).normalize();
        if (!target.startsWith(evidenceRoot)) {
            throw new IllegalArgumentException("evidence media url invalid");
        }
        return target;
    }

    private Path verifiedChatTraceMediaPathFor(String storageUrl) {
        return MediaPathGuard.requireRegularFileInside(
                storagePathForChatTraceMedia(storageUrl),
                mediaStorageRoot.resolve("uploads"),
                "chat media url invalid",
                "chat media not found"
        );
    }

    private Path verifiedVideoIdentityMediaPathFor(String storageUrl) {
        return MediaPathGuard.requireRegularFileInside(
                storagePathForVideoIdentityMedia(storageUrl),
                mediaStorageRoot.resolve("uploads/video-identity"),
                "video identity media url invalid",
                "video identity media not found"
        );
    }

    private Path verifiedSensitiveEvidenceMediaPathFor(String storageUrl, String relativeRoot, String notFoundMessage) {
        return MediaPathGuard.requireRegularFileInside(
                storagePathForSensitiveEvidenceMedia(storageUrl, relativeRoot),
                mediaStorageRoot.resolve("uploads").resolve(relativeRoot),
                "evidence media url invalid",
                notFoundMessage
        );
    }

    private String resolveChatTraceMediaContentType(String storageUrl) {
        String expectedScene = sceneForChatTraceStorageUrl(storageUrl);
        List<String> rows = jdbcTemplate.query("""
                SELECT content_type
                FROM media_upload_ticket
                WHERE storage_url = ?
                  AND scene = ?
                  AND status = 'UPLOADED'
                LIMIT 1
                """, (rs, rowNum) -> rs.getString("content_type"), storageUrl, expectedScene);
        if (!rows.isEmpty() && rows.get(0) != null && !rows.get(0).isBlank()) {
            return rows.get(0).trim().toLowerCase(Locale.ROOT);
        }
        return chatTraceMediaContentType(storageUrl);
    }

    private String chatTraceMediaContentType(String storageUrl) {
        String lower = storageUrl.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".mp4") || lower.endsWith(".m4a")) {
            return storageUrl.startsWith("/uploads/chat-video/") ? "video/mp4" : "audio/mp4";
        }
        if (lower.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (lower.endsWith(".m4v")) {
            return "video/x-m4v";
        }
        if (lower.endsWith(".aac")) {
            return "audio/aac";
        }
        if (lower.endsWith(".mp3") || lower.endsWith(".mpeg")) {
            return "audio/mpeg";
        }
        if (lower.endsWith(".wav")) {
            return "audio/wav";
        }
        if (storageUrl.startsWith("/uploads/chat-video/")) {
            return lower.endsWith(".webm") ? "video/webm" : "video/mp4";
        }
        return storageUrl.startsWith("/uploads/chat-voice/") ? "audio/webm" : "application/octet-stream";
    }

    private String resolveVideoIdentityMediaContentType(String storageUrl) {
        List<String> rows = jdbcTemplate.query("""
                SELECT content_type
                FROM media_upload_ticket
                WHERE storage_url = ?
                  AND scene = 'VIDEO_IDENTITY'
                  AND status = 'UPLOADED'
                LIMIT 1
                """, (rs, rowNum) -> rs.getString("content_type"), storageUrl);
        if (!rows.isEmpty() && rows.get(0) != null && !rows.get(0).isBlank()) {
            String contentType = rows.get(0).trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_VIDEO_IDENTITY_CONTENT_TYPES.contains(contentType)) {
                throw new IllegalArgumentException("video identity contentType unsupported");
            }
            return contentType;
        }
        return videoIdentityMediaContentType(storageUrl);
    }

    private String videoIdentityMediaContentType(String storageUrl) {
        String lower = storageUrl.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (lower.endsWith(".m4v")) {
            return "video/x-m4v";
        }
        return "video/mp4";
    }

    private String evidenceMediaContentType(String storageUrl) {
        String lower = storageUrl.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        return "application/octet-stream";
    }

    private void assertEvidenceUrlBoundToReportAudit(AuditRecordResponse detail, String storageUrl) {
        if (!reportEvidenceUrls(detail).contains(storageUrl)) {
            throw new IllegalArgumentException("report evidence not found");
        }
    }

    private List<String> reportEvidenceUrls(AuditRecordResponse detail) {
        if (detail == null) {
            return List.of();
        }
        List<String> structured = detail.reportEvidenceUrls() == null ? List.of() : detail.reportEvidenceUrls();
        if (!structured.isEmpty()) {
            return structured;
        }
        return extractReportEvidenceUrls(detail.description());
    }

    private List<String> extractReportEvidenceUrls(String description) {
        if (description == null || description.isBlank()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        String marker = "/uploads/report-evidence/";
        int index = 0;
        while (index >= 0 && index < description.length()) {
            int start = description.indexOf(marker, index);
            if (start < 0) {
                break;
            }
            int end = start;
            while (end < description.length()) {
                char ch = description.charAt(end);
                if (!(Character.isLetterOrDigit(ch) || ch == '/' || ch == '.' || ch == '_' || ch == '~' || ch == '%' || ch == '-')) {
                    break;
                }
                end += 1;
            }
            String candidate = description.substring(start, end);
            try {
                String safeUrl = normalizeReportEvidenceMediaUrl(candidate);
                if (!urls.contains(safeUrl)) {
                    urls.add(safeUrl);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed evidence references in historical audit descriptions.
            }
            index = end + 1;
        }
        return urls;
    }

    private int evidenceIndex(List<String> urls, String storageUrl) {
        int index = urls.indexOf(storageUrl);
        return index < 0 ? -1 : index + 1;
    }

    private Path resolveMediaStorageRoot(String configuredRoot) {
        if (configuredRoot == null || configuredRoot.isBlank()) {
            throw new IllegalStateException("media.storage-root required");
        }
        return Path.of(configuredRoot).toAbsolutePath().normalize();
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new IllegalArgumentException("chat media not found", exception);
        }
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
                       CASE WHEN owner_profile.video_identity_status = 'APPROVED'
                            AND COALESCE(owner_profile.video_verified, FALSE) = TRUE
                            AND COALESCE(owner_profile.main_role, 'BUYER') IN ('SELLER', 'BOTH')
                            AND EXISTS (
                                SELECT 1
                                FROM audit_record owner_video_audit
                                JOIN media_upload_ticket owner_video_ticket
                                  ON owner_video_ticket.owner_user_id = owner.id
                                 AND owner_video_ticket.scene = 'VIDEO_IDENTITY'
                                 AND owner_video_ticket.status = 'UPLOADED'
                                 AND owner_video_ticket.storage_url = owner_video_audit.reason
                                WHERE owner_video_audit.audit_type = 'VIDEO_IDENTITY'
                                  AND owner_video_audit.user_id = owner.id
                                  AND owner_video_audit.target_id = CONCAT('', owner.id)
                                  AND owner_video_audit.status = 'APPROVED'
                                  AND owner_video_audit.reason LIKE '/uploads/video-identity/%'
                            )
                            THEN TRUE ELSE FALSE END AS owner_video_verified,
                       peer.user_no AS peer_user_no,
                       peer.nickname AS peer_nickname,
                       peer.avatar_url AS peer_avatar_url,
                       peer.status AS peer_status,
                       peer_profile.gender AS peer_gender,
                       peer_profile.city AS peer_city,
                       COALESCE(peer_profile.main_role, 'BUYER') AS peer_main_role,
                       CASE WHEN peer_profile.video_identity_status = 'APPROVED'
                            AND COALESCE(peer_profile.video_verified, FALSE) = TRUE
                            AND COALESCE(peer_profile.main_role, 'BUYER') IN ('SELLER', 'BOTH')
                            AND EXISTS (
                                SELECT 1
                                FROM audit_record peer_video_audit
                                JOIN media_upload_ticket peer_video_ticket
                                  ON peer_video_ticket.owner_user_id = peer.id
                                 AND peer_video_ticket.scene = 'VIDEO_IDENTITY'
                                 AND peer_video_ticket.status = 'UPLOADED'
                                 AND peer_video_ticket.storage_url = peer_video_audit.reason
                                WHERE peer_video_audit.audit_type = 'VIDEO_IDENTITY'
                                  AND peer_video_audit.user_id = peer.id
                                  AND peer_video_audit.target_id = CONCAT('', peer.id)
                                  AND peer_video_audit.status = 'APPROVED'
                                  AND peer_video_audit.reason LIKE '/uploads/video-identity/%'
                            )
                            THEN TRUE ELSE FALSE END AS peer_video_verified
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
                        defaultAvatarUrl(rs.getString("owner_avatar_url"), rs.getString("owner_gender")),
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
                        defaultAvatarUrl(rs.getString("peer_avatar_url"), rs.getString("peer_gender")),
                        rs.getString("peer_status"),
                        rs.getString("peer_gender"),
                        rs.getString("peer_city"),
                        rs.getString("peer_main_role"),
                        rs.getBoolean("peer_video_verified")
                )
        ), args.toArray());
    }

    private String defaultAvatarUrl(String avatarUrl, String gender) {
        String normalizedAvatar = avatarUrl == null ? null : avatarUrl.trim();
        if (normalizedAvatar != null && !normalizedAvatar.isBlank()) {
            return normalizedAvatar;
        }
        String normalizedGender = gender == null ? "" : gender.trim().toLowerCase(Locale.ROOT);
        return normalizedGender.equals("god") || normalizedGender.equals("男") ? DEFAULT_GOD_AVATAR_URL : DEFAULT_GODDESS_AVATAR_URL;
    }

    private Long requirePositiveId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String communityTraceListTargetId(Long authorId, String keyword, Integer limit) {
        String safeKeyword = keyword == null || keyword.isBlank() ? "-" : keyword.trim();
        if (safeKeyword.length() > 24) {
            safeKeyword = safeKeyword.substring(0, 24);
        }
        return "author=" + (authorId == null ? "-" : authorId) + ";keyword=" + safeKeyword + ";limit=" + (limit == null ? "-" : limit);
    }

    private ChatTraceMediaCandidate assertChatTraceMediaBoundToMessage(long conversationId, long messageId, String messageNo, String storageUrl) {
        List<ChatTraceMediaCandidate> candidates = jdbcTemplate.query("""
                        select id, message_no, conversation_id, sender_id, message_type, content_json
                          from im_message
                         where conversation_id = ?
                           and id = ?
                           and message_no = ?
                           and message_type in ('IMAGE', 'VOICE', 'VIDEO')
                         limit 1
                        """,
                (rs, rowNum) -> new ChatTraceMediaCandidate(
                        rs.getLong("id"),
                        rs.getString("message_no"),
                        rs.getLong("conversation_id"),
                        rs.getLong("sender_id"),
                        rs.getString("message_type"),
                        rs.getString("content_json")
                ),
                conversationId,
                messageId,
                messageNo
        );
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("chat media message invalid");
        }
        ChatTraceMediaCandidate candidate = candidates.get(0);
        String boundUrl = extractChatTraceMediaUrl(candidate.messageType(), candidate.contentJson());
        if (!storageUrl.equals(boundUrl)) {
            throw new IllegalArgumentException("chat media message invalid");
        }
        return candidate;
    }

    private String sceneForChatTraceMedia(String messageType) {
        if ("VOICE".equals(messageType)) {
            return "CHAT_VOICE";
        }
        if ("VIDEO".equals(messageType)) {
            return "CHAT_VIDEO";
        }
        if ("IMAGE".equals(messageType)) {
            return "CHAT_IMAGE";
        }
        throw new IllegalArgumentException("chat media message invalid");
    }

    private String sceneForChatTraceStorageUrl(String storageUrl) {
        if (storageUrl.startsWith("/uploads/chat-voice/")) {
            return "CHAT_VOICE";
        }
        if (storageUrl.startsWith("/uploads/chat-video/")) {
            return "CHAT_VIDEO";
        }
        return "CHAT_IMAGE";
    }

    private String extractChatTraceMediaUrl(String messageType, String contentJson) {
        Map<String, Object> payload = parseChatTraceMediaPayload(contentJson, 0);
        String rawUrl = "";
        if ("VOICE".equals(messageType)) {
            rawUrl = firstText(payload.get("url"), payload.get("audioUrl"), payload.get("voiceUrl"));
        } else if ("VIDEO".equals(messageType)) {
            rawUrl = firstText(payload.get("url"), payload.get("videoUrl"));
        } else if ("IMAGE".equals(messageType)) {
            rawUrl = firstText(payload.get("url"));
        }
        if (rawUrl.isBlank()) {
            throw new IllegalArgumentException("chat media message invalid");
        }
        String safeUrl = normalizeChatTraceMediaUrl(rawUrl);
        if ("VOICE".equals(messageType) && !safeUrl.startsWith("/uploads/chat-voice/")) {
            throw new IllegalArgumentException("chat media message invalid");
        }
        if ("IMAGE".equals(messageType) && !safeUrl.startsWith("/uploads/chat-image/")) {
            throw new IllegalArgumentException("chat media message invalid");
        }
        if ("VIDEO".equals(messageType) && !safeUrl.startsWith("/uploads/chat-video/")) {
            throw new IllegalArgumentException("chat media message invalid");
        }
        return safeUrl;
    }

    private Map<String, Object> parseChatTraceMediaPayload(String contentJson, int depth) {
        if (depth > 2) {
            throw new IllegalArgumentException("chat media message invalid");
        }
        String content = requireText(contentJson, "chat media message invalid");
        try {
            Object value = OBJECT_MAPPER.readValue(content, Object.class);
            if (value instanceof Map<?, ?> map) {
                return OBJECT_MAPPER.convertValue(map, STRING_OBJECT_MAP);
            }
            if (value instanceof String nested) {
                return parseChatTraceMediaPayload(nested, depth + 1);
            }
        } catch (IllegalArgumentException | JsonProcessingException ex) {
            String legacy = content.replace("\\\"", "\"");
            if (!legacy.equals(content)) {
                return parseChatTraceMediaPayload(legacy, depth + 1);
            }
        }
        throw new IllegalArgumentException("chat media message invalid");
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value instanceof String text && !text.trim().isBlank()) {
                return text.trim();
            }
        }
        return "";
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
                    'chat:trace',
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
                                                Boolean revoked,
                                                LocalDateTime revokedAt,
                                                LocalDateTime createdAt) {
    }

    public record AdminChatConversationMessageTraceResponse(AdminChatConversationTraceResponse conversation,
                                                            List<AdminChatMessageTraceResponse> messages,
                                                            Boolean hasMore,
                                                            Long oldestSeq,
                                                            Long nextBeforeSeq) {
    }

    public record VideoIdentityWatchProgressRequest(Double durationSeconds,
                                                    Double currentTimeSeconds,
                                                    Double watchedRatio,
                                                    Boolean ended) {
    }

    private record VideoIdentityWatchProgress(double ratio, double requiredElapsedSeconds) {
    }

    private record ChatTraceMediaCandidate(Long messageId,
                                           String messageNo,
                                           Long conversationId,
                                           Long senderId,
                                           String messageType,
                                           String contentJson) {
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

    private void syncProductStatus(AuditRecordResponse response, String status) {
        if (response == null || !"PRODUCT".equals(response.auditType()) || !"PRODUCT".equals(response.targetType())) {
            return;
        }
        if (!AuditApplicationService.STATUS_PENDING.equals(response.status())) {
            throw new IllegalStateException("audit record already reviewed");
        }
        Long productId = requirePositiveId(parseLong(response.targetId()), "productId invalid");
        if ("APPROVED".equals(status)) {
            productApplicationService.approveForSale(productId);
            return;
        }
        if ("REJECTED".equals(status)) {
            productApplicationService.rejectForSale(productId);
        }
    }

    private void rejectPendingProductAuditIfPresent(Long productId, long adminUserId) {
        try {
            String auditNo = productApplicationService.requirePendingProductAuditNo(productId);
            auditApplicationService.reject(auditNo, "后台商品运营删除", adminUserId);
        } catch (IllegalArgumentException ignored) {
            // No pending product audit exists for already-reviewed products; the admin operation log above is the trace.
        }
    }

    private Long parseLong(String value) {
        if (value == null || !value.matches("^[1-9]\\d{0,18}$")) {
            throw new IllegalArgumentException("productId invalid");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("productId invalid", ex);
        }
    }
}

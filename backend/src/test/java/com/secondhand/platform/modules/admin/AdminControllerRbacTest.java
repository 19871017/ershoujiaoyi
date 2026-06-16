package com.secondhand.platform.modules.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.secondhand.platform.modules.aftersales.application.AfterSalesApplicationService;
import com.secondhand.platform.modules.announcement.AnnouncementApplicationService;
import com.secondhand.platform.modules.audit.application.AuditApplicationService;
import com.secondhand.platform.modules.wallet_ledger.CreateWithdrawalRequest;
import com.secondhand.platform.modules.wallet_ledger.PayoutAccountRequest;
import com.secondhand.platform.modules.wallet_ledger.WithdrawalResponse;
import com.secondhand.platform.modules.wallet_ledger.application.CreditCommand;
import com.secondhand.platform.modules.location.LocationApplicationService;
import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
import com.secondhand.platform.modules.order.application.OrderApplicationService;
import com.secondhand.platform.modules.product.CreateProductResponse;
import com.secondhand.platform.modules.product.application.CreateProductRequest;
import com.secondhand.platform.modules.product.application.ProductApplicationService;
import com.secondhand.platform.modules.user.application.UserApplicationService;
import com.secondhand.platform.modules.wallet_ledger.application.WalletLedgerService;
import com.secondhand.platform.shared.web.AdminAccessGuard;
import com.secondhand.platform.shared.web.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminControllerRbacTest {
    private JdbcTemplate jdbcTemplate;
    private MockMvc mvc;
    private AuditApplicationService auditApplicationService;
    private WalletLedgerService walletLedgerService;
    private ProductApplicationService productApplicationService;
    private OrderApplicationService orderApplicationService;
    @TempDir
    Path mediaRoot;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);

        walletLedgerService = new WalletLedgerService(jdbcTemplate);
        MediaUploadTicketService mediaUploadTicketService = new MediaUploadTicketService(jdbcTemplate, mediaRoot.toString());
        auditApplicationService = new AuditApplicationService(jdbcTemplate, mediaUploadTicketService);
        productApplicationService = new ProductApplicationService(jdbcTemplate, mediaUploadTicketService);
        orderApplicationService = new OrderApplicationService(productApplicationService, walletLedgerService, jdbcTemplate);
        AdminController controller = new AdminController(
                auditApplicationService,
                walletLedgerService,
                new AnnouncementApplicationService(jdbcTemplate),
                new LocationApplicationService(new com.secondhand.platform.modules.location.BaiduReverseGeocodeClient(), "", jdbcTemplate),
                new AfterSalesApplicationService(jdbcTemplate, mediaUploadTicketService),
                orderApplicationService,
                productApplicationService,
                new com.secondhand.platform.modules.community.application.CommunityApplicationService(jdbcTemplate, mediaUploadTicketService),
                new UserApplicationService(jdbcTemplate, mediaUploadTicketService),
                new com.secondhand.platform.modules.home.HomeBannerApplicationService(jdbcTemplate),
                new AdminAccessGuard(jdbcTemplate),
                jdbcTemplate,
                mediaUploadTicketService,
                mediaRoot.toString()
        );
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void adminDashboardRequiresPersistedAuditReadPermissionNotOnlyAdminModeHeader() throws Exception {
        mvc.perform(get("/api/admin/dashboard")
                        .header("X-Admin-Mode", "enabled")
                        .header("X-User-Id", "7"))
                .andExpect(status().isForbidden());

        createActiveUser(7L);
        grantPermission(7L, "audit:read");

        mvc.perform(get("/api/admin/dashboard")
                        .header("X-Admin-Mode", "enabled")
                        .header("X-User-Id", "7"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/dashboard")
                        .header("X-User-Id", "7")
                        .header("X-Admin-Session", issueAdminSession(7L)))
                .andExpect(status().isOk());
    }

    @Test
    void adminDashboardRequiresServerIssuedAdminSessionWithPersistedAuditReadPermission() throws Exception {
        createActiveUser(9L);
        grantPermission(9L, "audit:read");

        mvc.perform(get("/api/admin/dashboard")
                        .header("X-User-Id", "9"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/dashboard")
                        .header("X-User-Id", "9")
                        .header("X-Admin-Session", issueAdminSession(9L)))
                .andExpect(status().isOk());
    }

    @Test
    void adminDashboardRejectsPersistedLegacyNonOpaqueSessionIds() throws Exception {
        createActiveUser(10L);
        grantPermission(10L, "audit:read");
        jdbcTemplate.update("""
                insert into admin_session (session_id, user_id, expires_at, revoked, created_at)
                values (?, ?, DATEADD('HOUR', 1, CURRENT_TIMESTAMP), false, CURRENT_TIMESTAMP)
                """, "test-session-legacy-10", 10L);

        mvc.perform(get("/api/admin/dashboard")
                        .header("X-User-Id", "10")
                        .header("X-Admin-Session", "test-session-legacy-10"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDashboardRequiresActivePersistedAdminAccountWithPermission() throws Exception {
        grantPermission(11L, "audit:read");

        mvc.perform(get("/api/admin/dashboard")
                        .header("X-User-Id", "11"))
                .andExpect(status().isForbidden());

        createActiveUser(11L);

        mvc.perform(get("/api/admin/dashboard")
                        .header("X-User-Id", "11")
                        .header("X-Admin-Session", issueAdminSession(11L)))
                .andExpect(status().isOk());
    }

    @Test
    void adminDashboardRequiresOnlyValidServerIssuedAdminSessionBecauseMetricsArePermissionFiltered() throws Exception {
        createActiveUser(13L);

        mvc.perform(get("/api/admin/dashboard")
                        .header("X-User-Id", "13"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/dashboard")
                        .header("X-User-Id", "13")
                        .header("X-Admin-Session", issueAdminSession(13L)))
                .andExpect(status().isOk());
    }

    @Test
    void adminDashboardRejectsSessionIssuedForDifferentOperator() throws Exception {
        createActiveUser(131L);
        createActiveUser(132L);
        grantPermission(131L, "audit:read");
        grantPermission(132L, "audit:read");

        mvc.perform(get("/api/admin/dashboard")
                        .header("X-User-Id", "132")
                        .header("X-Admin-Session", issueAdminSession(131L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDashboardRejectsSessionWhenOperatorAccountIsNotActive() throws Exception {
        createInactiveUser(14L);

        mvc.perform(get("/api/admin/dashboard")
                        .header("X-User-Id", "14")
                        .header("X-Admin-Session", issueAdminSession(14L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDashboardAcceptsServerIssuedAdminSessionUnderProductionProfile() throws Exception {
        createActiveUser(15L);
        grantPermission(15L, "audit:read");

        mvc.perform(get("/api/admin/dashboard")
                        .header("X-User-Id", "15")
                        .header("X-Admin-Session", issueAdminSession(15L)))
                .andExpect(status().isOk());
    }

    @Test
    void adminLocationConfigUpdatePersistsOperatorAuditLog() throws Exception {
        createActiveUser(21L);
        grantPermission(21L, "system:config");

        mvc.perform(post("/api/admin/location/config")
                        .header("X-User-Id", "21")
                        .header("X-Admin-Session", issueAdminSession(21L))
                        .contentType("application/json")
                        .content("{\"defaultCity\":\"杭州\",\"coordinateType\":\"gcj02ll\"}"))
                .andExpect(status().isOk());

        Integer count = jdbcTemplate.queryForObject("""
                select count(1)
                from admin_audit_log
                where action = 'LOCATION_CONFIG_UPDATE'
                  and operator_id = 21
                  and target_type = 'SYSTEM_CONFIG'
                  and target_id = 'location'
                  and result = 'SUCCESS'
                  and summary not like '%SECRET%'
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }

    @Test
    void adminProductPricingConfigRequiresSystemConfigAndPersistsAuditLog() throws Exception {
        createActiveUser(22L);
        grantPermission(22L, "audit:read");

        mvc.perform(get("/api/admin/product-pricing/config")
                        .header("X-User-Id", "22")
                        .header("X-Admin-Session", issueAdminSession(22L)))
                .andExpect(status().isForbidden());

        grantPermission(22L, "system:config");

        mvc.perform(get("/api/admin/product-pricing/config")
                        .header("X-User-Id", "22")
                        .header("X-Admin-Session", issueAdminSession(22L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.markupRate").value(0.3000));

        mvc.perform(post("/api/admin/product-pricing/config")
                        .header("X-User-Id", "22")
                        .header("X-Admin-Session", issueAdminSession(22L))
                        .contentType("application/json")
                        .content("{\"markupRate\":0.25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.markupRate").value(0.2500));

        mvc.perform(post("/api/admin/product-pricing/config")
                        .header("X-User-Id", "22")
                        .header("X-Admin-Session", issueAdminSession(22L))
                        .contentType("application/json")
                        .content("{\"markupRate\":0.12345}"))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("0.2500"),
                jdbcTemplate.queryForObject("select config_value from system_config where config_key = 'product.pricing.markup_rate'", BigDecimal.class));
        Integer count = jdbcTemplate.queryForObject("""
                select count(1)
                from admin_audit_log
                where action = 'PRODUCT_PRICING_CONFIG_UPDATE'
                  and operator_id = 22
                  and target_type = 'SYSTEM_CONFIG'
                  and target_id = 'product-pricing'
                  and result = 'SUCCESS'
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }

    @Test
    void adminWithdrawalAuditApprovalPersistsWithdrawalOperationAuditLog() throws Exception {
        createActiveUser(31L);
        grantPermission(31L, "audit:review");
        grantPermission(31L, "finance:review");
        createActiveUser(41L);
        walletLedgerService.credit(credit(41L, "withdraw-seed", "WITHDRAWABLE", "90.00"));
        WithdrawalResponse withdrawal = walletLedgerService.createWithdrawal(41L, withdrawal("40.00"), "AU-WD-REVIEW-1");
        jdbcTemplate.update("""
                insert into audit_record (audit_no,audit_type,user_id,target_type,target_id,reason,description,status,created_at)
                values (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """, "AU-WD-REVIEW-1", AuditApplicationService.AUDIT_TYPE_WITHDRAWAL, 41L, "WITHDRAWAL", withdrawal.withdrawalNo(), "提现审核", "提现复核", AuditApplicationService.STATUS_PENDING);

        mvc.perform(post("/api/admin/audit/AU-WD-REVIEW-1/approve")
                        .header("X-User-Id", "31")
                        .header("X-Admin-Session", issueAdminSession(31L))
                        .contentType("application/json")
                        .content("{\"remark\":\"finance ok\"}"))
                .andExpect(status().isOk());

        Integer count = jdbcTemplate.queryForObject("""
                select count(1)
                from admin_audit_log
                where action = 'WITHDRAWAL_REVIEW'
                  and operator_id = 31
                  and target_type = 'WITHDRAWAL'
                  and target_id = ?
                  and result = 'APPROVED'
                  and summary not like '%6222020202020208088%'
                """, Integer.class, withdrawal.withdrawalNo());
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }

    @Test
    void adminWithdrawalAuditApprovalRequiresFinanceReviewPermission() throws Exception {
        createActiveUser(32L);
        grantPermission(32L, "audit:review");
        grantPermission(32L, "finance:read");
        createActiveUser(42L);
        walletLedgerService.credit(credit(42L, "withdraw-finance-seed", "WITHDRAWABLE", "90.00"));
        WithdrawalResponse withdrawal = walletLedgerService.createWithdrawal(42L, withdrawalForUser(42L, "40.00"), "AU-WD-FINANCE-1");
        jdbcTemplate.update("""
                insert into audit_record (audit_no,audit_type,user_id,target_type,target_id,reason,description,status,created_at)
                values (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """, "AU-WD-FINANCE-1", AuditApplicationService.AUDIT_TYPE_WITHDRAWAL, 42L, "WITHDRAWAL", withdrawal.withdrawalNo(), "提现审核", "提现复核", AuditApplicationService.STATUS_PENDING);

        mvc.perform(post("/api/admin/audit/AU-WD-FINANCE-1/approve")
                        .header("X-User-Id", "32")
                        .header("X-Admin-Session", issueAdminSession(32L))
                        .contentType("application/json")
                        .content("{\"remark\":\"audit reviewer should not approve withdrawals\"}"))
                .andExpect(status().isForbidden());

        grantPermission(32L, "finance:review");

        mvc.perform(post("/api/admin/audit/AU-WD-FINANCE-1/approve")
                        .header("X-User-Id", "32")
                        .header("X-Admin-Session", issueAdminSession(32L))
                        .contentType("application/json")
                        .content("{\"remark\":\"finance ok\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void adminAuditApprovalRequiresReviewPermissionBeforeLookingUpAuditNo() throws Exception {
        createActiveUser(35L);
        grantPermission(35L, "audit:read");
        createActiveUser(45L);
        jdbcTemplate.update("insert into user_profile (user_id, identity_status, video_identity_status, video_verified) values (?,?,?,?)", 45L, "UNVERIFIED", "UNVERIFIED", false);
        var realNameAudit = auditApplicationService.submitRealNameIdentity(45L, "陈小原", "110105199001010045");

        mvc.perform(post("/api/admin/audit/" + realNameAudit.auditNo() + "/approve")
                        .header("X-User-Id", "35")
                        .header("X-Admin-Session", issueAdminSession(35L))
                        .contentType("application/json")
                        .content("{\"remark\":\"should be forbidden before detail lookup\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/admin/audit/AU-NOT-EXISTS-35/approve")
                        .header("X-User-Id", "35")
                        .header("X-Admin-Session", issueAdminSession(35L))
                        .contentType("application/json")
                        .content("{\"remark\":\"should not reveal existence\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminWithdrawalDetailRequiresFinanceReadAndReturnsReviewContext() throws Exception {
        createActiveUser(33L);
        createActiveUser(43L);
        walletLedgerService.credit(credit(43L, "withdraw-detail-seed", "WITHDRAWABLE", "120.00"));
        WithdrawalResponse withdrawal = walletLedgerService.createWithdrawal(43L, withdrawalForUser(43L, "60.00"), "AU-WD-DETAIL-1");

        mvc.perform(get("/api/admin/withdrawals/" + withdrawal.withdrawalNo())
                        .header("X-User-Id", "33")
                        .header("X-Admin-Session", issueAdminSession(33L)))
                .andExpect(status().isForbidden());

        grantPermission(33L, "finance:read");

        mvc.perform(get("/api/admin/withdrawals/" + withdrawal.withdrawalNo())
                        .header("X-User-Id", "33")
                        .header("X-Admin-Session", issueAdminSession(33L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawal.withdrawalNo").value(withdrawal.withdrawalNo()))
                .andExpect(jsonPath("$.data.withdrawal.maskedAccountNo").value("6222 **** **** 8088"))
                .andExpect(jsonPath("$.data.withdrawal.accountNo").doesNotExist())
                .andExpect(jsonPath("$.data.user.userId").value(43))
                .andExpect(jsonPath("$.data.user.nickname").value("管理员43"))
                .andExpect(jsonPath("$.data.user.identityStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.balance.withdrawableBalance").value(60.00))
                .andExpect(jsonPath("$.data.balance.frozenBalance").value(60.00))
                .andExpect(jsonPath("$.data.recentLedgers[0].businessType").value("WITHDRAW_FREEZE"));
    }

    @Test
    void adminRealNameAuditApprovalRequiresAuditReviewAndUpdatesIdentityStatus() throws Exception {
        createActiveUser(34L);
        grantPermission(34L, "audit:read");
        createActiveUser(44L);
        jdbcTemplate.update("insert into user_profile (user_id, identity_status, video_identity_status, video_verified) values (?,?,?,?)", 44L, "UNVERIFIED", "UNVERIFIED", false);
        var realNameAudit = auditApplicationService.submitRealNameIdentity(44L, "孙小原", "110105199001010053");

        mvc.perform(post("/api/admin/audit/" + realNameAudit.auditNo() + "/approve")
                        .header("X-User-Id", "34")
                        .header("X-Admin-Session", issueAdminSession(34L))
                        .contentType("application/json")
                        .content("{\"remark\":\"实名一致\"}"))
                .andExpect(status().isForbidden());

        grantPermission(34L, "audit:review");

        mvc.perform(post("/api/admin/audit/" + realNameAudit.auditNo() + "/approve")
                        .header("X-User-Id", "34")
                        .header("X-Admin-Session", issueAdminSession(34L))
                        .contentType("application/json")
                        .content("{\"remark\":\"实名一致\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditType").value(AuditApplicationService.AUDIT_TYPE_REAL_NAME_IDENTITY))
                .andExpect(jsonPath("$.data.status").value(AuditApplicationService.STATUS_APPROVED));

        org.junit.jupiter.api.Assertions.assertEquals("VERIFIED", jdbcTemplate.queryForObject("select identity_status from user_profile where user_id = ?", String.class, 44L));
    }

    @Test
    void adminAfterSalesListRequiresAfterSalesReadPermission() throws Exception {
        createActiveUser(51L);
        grantPermission(51L, "audit:read");

        mvc.perform(get("/api/admin/after-sales")
                        .header("X-User-Id", "51")
                        .header("X-Admin-Session", issueAdminSession(51L))
                        .param("status", "PENDING_REVIEW")
                        .param("keyword", "AS-ADMIN-20260510-0001")
                        .param("limit", "20"))
                .andExpect(status().isForbidden());

        grantPermission(51L, "after-sales:read");

        mvc.perform(get("/api/admin/after-sales")
                        .header("X-User-Id", "51")
                        .header("X-Admin-Session", issueAdminSession(51L))
                        .param("status", "PENDING_REVIEW")
                        .param("keyword", "AS-ADMIN-20260510-0001")
                        .param("limit", "20"))
                .andExpect(status().isOk());
    }

    @Test
    void adminProductApproveRequiresAuditReviewAndPublishesProductForSale() throws Exception {
        CreateProductResponse product = productApplicationService.createProduct(61L, productRequest(61L, "后台待审商品", "12.34"));
        createActiveUser(62L);

        mvc.perform(post("/api/admin/products/" + product.getProductId() + "/approve")
                        .header("X-User-Id", "62")
                        .header("X-Admin-Session", issueAdminSession(62L)))
                .andExpect(status().isForbidden());

        grantPermission(62L, "audit:review");

        mvc.perform(post("/api/admin/products/" + product.getProductId() + "/approve")
                        .header("X-User-Id", "62")
                        .header("X-Admin-Session", issueAdminSession(62L)))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(1, productApplicationService.listProducts().size());
        org.junit.jupiter.api.Assertions.assertEquals(AuditApplicationService.STATUS_APPROVED,
                jdbcTemplate.queryForObject("select status from audit_record where audit_type = 'PRODUCT' and target_id = ?", String.class, String.valueOf(product.getProductId())));
    }

    @Test
    void adminProductListRequiresAuditReadAndReturnsPendingProducts() throws Exception {
        CreateProductResponse product = productApplicationService.createProduct(67L, productRequest(67L, "后台列表待审商品", "19.99"));
        createActiveUser(68L);

        mvc.perform(get("/api/admin/products")
                        .header("X-User-Id", "68")
                        .header("X-Admin-Session", issueAdminSession(68L))
                        .param("auditStatus", "PENDING")
                        .param("keyword", "后台列表待审商品")
                        .param("limit", "20"))
                .andExpect(status().isForbidden());

        grantPermission(68L, "audit:read");

        mvc.perform(get("/api/admin/products")
                        .header("X-User-Id", "68")
                        .header("X-Admin-Session", issueAdminSession(68L))
                        .param("status", "PENDING_AUDIT")
                        .param("auditStatus", "PENDING")
                        .param("keyword", "后台列表待审商品")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productId").value(product.getProductId()))
                .andExpect(jsonPath("$.data[0].title").value("后台列表待审商品"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_AUDIT"))
                .andExpect(jsonPath("$.data[0].auditState").value("PENDING"))
                .andExpect(jsonPath("$.data[0].visible").value(false));

        mvc.perform(get("/api/admin/products/" + product.getProductId())
                        .header("X-User-Id", "68")
                        .header("X-Admin-Session", issueAdminSession(68L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(product.getProductId()))
                .andExpect(jsonPath("$.data.sellerId").value(67))
                .andExpect(jsonPath("$.data.imageUrls.length()").value(1));

        mvc.perform(get("/api/admin/products")
                        .header("X-User-Id", "68")
                        .header("X-Admin-Session", issueAdminSession(68L))
                        .param("keyword", "preview-product")
                        .param("limit", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminProductApproveReviewsLinkedAuditRecordWithActingOperator() throws Exception {
        CreateProductResponse product = productApplicationService.createProduct(63L, productRequest(63L, "后台商品审核联动", "45.67"));
        String auditNo = jdbcTemplate.queryForObject("select audit_no from audit_record where audit_type = 'PRODUCT' and target_id = ?", String.class, String.valueOf(product.getProductId()));
        createActiveUser(64L);
        grantPermission(64L, "audit:review");

        mvc.perform(post("/api/admin/products/" + product.getProductId() + "/approve")
                        .header("X-User-Id", "64")
                        .header("X-Admin-Session", issueAdminSession(64L)))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(AuditApplicationService.STATUS_APPROVED,
                jdbcTemplate.queryForObject("select status from audit_record where audit_no = ?", String.class, auditNo));
        org.junit.jupiter.api.Assertions.assertEquals(64L,
                jdbcTemplate.queryForObject("select operator_id from admin_audit_log where target_id = ?", Long.class, auditNo));
    }

    @Test
    void adminAuditProductApprovalKeepsProductAndAuditRecordLinked() throws Exception {
        CreateProductResponse product = productApplicationService.createProduct(69L, productRequest(69L, "审核工作台商品通过", "31.20"));
        String auditNo = jdbcTemplate.queryForObject("select audit_no from audit_record where audit_type = 'PRODUCT' and target_id = ?", String.class, String.valueOf(product.getProductId()));
        createActiveUser(70L);
        grantPermission(70L, "audit:review");

        mvc.perform(post("/api/admin/audit/" + auditNo + "/approve")
                        .header("X-User-Id", "70")
                        .header("X-Admin-Session", issueAdminSession(70L))
                        .contentType("application/json")
                        .content("{\"remark\":\"商品可上架\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(AuditApplicationService.STATUS_APPROVED));

        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE",
                jdbcTemplate.queryForObject("select product_status from product_item where id = ?", String.class, product.getProductId()));
        org.junit.jupiter.api.Assertions.assertEquals("APPROVED",
                jdbcTemplate.queryForObject("select audit_status from product_item where id = ?", String.class, product.getProductId()));
        org.junit.jupiter.api.Assertions.assertTrue(
                jdbcTemplate.queryForObject("select visible from product_item where id = ?", Boolean.class, product.getProductId()));
    }

    @Test
    void adminProductRejectRequiresAuditReviewAndRejectsLinkedAuditRecord() throws Exception {
        CreateProductResponse product = productApplicationService.createProduct(65L, productRequest(65L, "后台商品审核拒绝", "56.78"));
        String auditNo = jdbcTemplate.queryForObject("select audit_no from audit_record where audit_type = 'PRODUCT' and target_id = ?", String.class, String.valueOf(product.getProductId()));
        createActiveUser(66L);

        mvc.perform(post("/api/admin/products/" + product.getProductId() + "/reject")
                        .header("X-User-Id", "66")
                        .header("X-Admin-Session", issueAdminSession(66L)))
                .andExpect(status().isForbidden());

        grantPermission(66L, "audit:review");

        mvc.perform(post("/api/admin/products/" + product.getProductId() + "/reject")
                        .header("X-User-Id", "66")
                        .header("X-Admin-Session", issueAdminSession(66L)))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(AuditApplicationService.STATUS_REJECTED,
                jdbcTemplate.queryForObject("select status from audit_record where audit_no = ?", String.class, auditNo));
        org.junit.jupiter.api.Assertions.assertEquals("REJECTED",
                jdbcTemplate.queryForObject("select audit_status from product_item where id = ?", String.class, product.getProductId()));
        org.junit.jupiter.api.Assertions.assertFalse(
                jdbcTemplate.queryForObject("select visible from product_item where id = ?", Boolean.class, product.getProductId()));
        org.junit.jupiter.api.Assertions.assertEquals(66L,
                jdbcTemplate.queryForObject("select operator_id from admin_audit_log where target_id = ?", Long.class, auditNo));
    }

    @Test
    void adminProductOfflineRequiresAuditReviewAndPersistsOperationLog() throws Exception {
        CreateProductResponse product = productApplicationService.createProduct(171L, productRequest(171L, "运营下架在售商品", "56.78"));
        productApplicationService.approveForSale(product.getProductId());
        createActiveUser(172L);

        mvc.perform(post("/api/admin/products/" + product.getProductId() + "/offline")
                        .header("X-User-Id", "172")
                        .header("X-Admin-Session", issueAdminSession(172L))
                        .contentType("application/json")
                        .content("{\"reason\":\"商品图片违规\"}"))
                .andExpect(status().isForbidden());

        grantPermission(172L, "audit:review");

        mvc.perform(post("/api/admin/products/" + product.getProductId() + "/offline")
                        .header("X-User-Id", "172")
                        .header("X-Admin-Session", issueAdminSession(172L))
                        .contentType("application/json")
                        .content("{\"reason\":\"商品图片违规\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(product.getProductId()))
                .andExpect(jsonPath("$.data.status").value("OFFLINE"))
                .andExpect(jsonPath("$.data.auditState").value("REJECTED"))
                .andExpect(jsonPath("$.data.visible").value(false));

        org.junit.jupiter.api.Assertions.assertEquals("OFFLINE",
                jdbcTemplate.queryForObject("select product_status from product_item where id = ?", String.class, product.getProductId()));
        org.junit.jupiter.api.Assertions.assertEquals("REJECTED",
                jdbcTemplate.queryForObject("select audit_status from product_item where id = ?", String.class, product.getProductId()));
        org.junit.jupiter.api.Assertions.assertEquals(172L,
                jdbcTemplate.queryForObject("select operator_id from admin_audit_log where action = ? and target_id = ?", Long.class, "PRODUCT_OFFLINE", String.valueOf(product.getProductId())));
        org.junit.jupiter.api.Assertions.assertEquals("商品图片违规",
                jdbcTemplate.queryForObject("select summary from admin_audit_log where action = ? and target_id = ?", String.class, "PRODUCT_OFFLINE", String.valueOf(product.getProductId())));

        mvc.perform(post("/api/admin/products/" + product.getProductId() + "/offline")
                        .header("X-User-Id", "172")
                        .header("X-Admin-Session", issueAdminSession(172L))
                        .contentType("application/json")
                        .content("{\"reason\":\"preview offline\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminProductHideRequiresAuditReviewAndKeepsAuditApprovedForTrace() throws Exception {
        CreateProductResponse product = productApplicationService.createProduct(173L, productRequest(173L, "运营隐藏在售商品", "56.78"));
        productApplicationService.approveForSale(product.getProductId());
        createActiveUser(174L);

        mvc.perform(post("/api/admin/products/" + product.getProductId() + "/hide")
                        .header("X-User-Id", "174")
                        .header("X-Admin-Session", issueAdminSession(174L))
                        .contentType("application/json")
                        .content("{\"reason\":\"重复发布先隐藏\"}"))
                .andExpect(status().isForbidden());

        grantPermission(174L, "audit:review");

        mvc.perform(post("/api/admin/products/" + product.getProductId() + "/hide")
                        .header("X-User-Id", "174")
                        .header("X-Admin-Session", issueAdminSession(174L))
                        .contentType("application/json")
                        .content("{\"reason\":\"重复发布先隐藏\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(product.getProductId()))
                .andExpect(jsonPath("$.data.status").value("OFFLINE"))
                .andExpect(jsonPath("$.data.auditState").value("APPROVED"))
                .andExpect(jsonPath("$.data.visible").value(false));

        org.junit.jupiter.api.Assertions.assertEquals("OFFLINE",
                jdbcTemplate.queryForObject("select product_status from product_item where id = ?", String.class, product.getProductId()));
        org.junit.jupiter.api.Assertions.assertEquals("APPROVED",
                jdbcTemplate.queryForObject("select audit_status from product_item where id = ?", String.class, product.getProductId()));
        org.junit.jupiter.api.Assertions.assertFalse(
                jdbcTemplate.queryForObject("select visible from product_item where id = ?", Boolean.class, product.getProductId()));
        org.junit.jupiter.api.Assertions.assertEquals(174L,
                jdbcTemplate.queryForObject("select operator_id from admin_audit_log where action = ? and target_id = ?", Long.class, "PRODUCT_HIDE", String.valueOf(product.getProductId())));
    }

    @Test
    void adminProductDeleteRequiresAuditReviewAndSoftDeletesWithoutBreakingTrace() throws Exception {
        CreateProductResponse product = productApplicationService.createProduct(175L, productRequest(175L, "运营删除待审商品", "36.78"));
        createActiveUser(176L);

        mvc.perform(post("/api/admin/products/" + product.getProductId() + "/delete")
                        .header("X-User-Id", "176")
                        .header("X-Admin-Session", issueAdminSession(176L))
                        .contentType("application/json")
                        .content("{\"reason\":\"严重违规删除\"}"))
                .andExpect(status().isForbidden());

        grantPermission(176L, "audit:review");
        grantPermission(176L, "audit:read");

        mvc.perform(post("/api/admin/products/" + product.getProductId() + "/delete")
                        .header("X-User-Id", "176")
                        .header("X-Admin-Session", issueAdminSession(176L))
                        .contentType("application/json")
                        .content("{\"reason\":\"严重违规删除\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(product.getProductId()))
                .andExpect(jsonPath("$.data.status").value("DELETED"))
                .andExpect(jsonPath("$.data.visible").value(false));

        org.junit.jupiter.api.Assertions.assertEquals("DELETED",
                jdbcTemplate.queryForObject("select product_status from product_item where id = ?", String.class, product.getProductId()));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                productApplicationService.adminListProducts("DELETED", "ALL", String.valueOf(product.getProductId()), 20).size());
        org.junit.jupiter.api.Assertions.assertFalse(
                jdbcTemplate.queryForObject("select visible from product_item where id = ?", Boolean.class, product.getProductId()));
        org.junit.jupiter.api.Assertions.assertEquals(176L,
                jdbcTemplate.queryForObject("select operator_id from admin_audit_log where action = ? and target_id = ?", Long.class, "PRODUCT_DELETE", String.valueOf(product.getProductId())));

        mvc.perform(get("/api/admin/products/" + product.getProductId())
                        .header("X-User-Id", "176")
                        .header("X-Admin-Session", issueAdminSession(176L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELETED"));
    }

    @Test
    void adminAuditListMasksSensitiveDescriptionBeforeDtoResponse() throws Exception {
        createActiveUser(72L);
        grantPermission(72L, "audit:read");
        jdbcTemplate.update("""
                insert into audit_record (audit_no,audit_type,user_id,target_type,target_id,reason,description,status,created_at)
                values (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """, "AU-MASK-20260511-0001", AuditApplicationService.AUDIT_TYPE_WITHDRAWAL, 42L, "WITHDRAWAL", "WD-MASK-1", "提现审核", "用户手机号13912345678，银行卡6222020202020208088", AuditApplicationService.STATUS_PENDING);

        mvc.perform(get("/api/admin/audit")
                        .header("X-User-Id", "72")
                        .header("X-Admin-Session", issueAdminSession(72L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].description").value("用户手机号139****5678，银行卡622202********8088"));
    }

    @Test
    void adminAuditListFiltersReportsOnlyWithAuditReadPermission() throws Exception {
        createActiveUser(73L);
        jdbcTemplate.update("""
                insert into audit_record (audit_no,audit_type,user_id,target_type,target_id,reason,description,status,created_at)
                values (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """, "AU-REP-1770000000000-7301", AuditApplicationService.AUDIT_TYPE_REPORT, 73L, "PRODUCT", "PRODUCT-730001", "SPAM", "举报商品", AuditApplicationService.STATUS_PENDING);
        jdbcTemplate.update("""
                insert into audit_record (audit_no,audit_type,user_id,target_type,target_id,reason,description,status,created_at)
                values (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """, "AU-WIT-1770000000000-7302", AuditApplicationService.AUDIT_TYPE_WITHDRAWAL, 73L, "WITHDRAWAL", "WD-730001", "提现审核", "提现复核", AuditApplicationService.STATUS_PENDING);

        mvc.perform(get("/api/admin/audit")
                        .header("X-User-Id", "73")
                        .header("X-Admin-Session", issueAdminSession(73L))
                        .param("auditType", "REPORT")
                        .param("status", "PENDING")
                        .param("keyword", "PRODUCT-730001")
                        .param("limit", "20"))
                .andExpect(status().isForbidden());

        grantPermission(73L, "audit:read");

        mvc.perform(get("/api/admin/audit")
                        .header("X-User-Id", "73")
                        .header("X-Admin-Session", issueAdminSession(73L))
                        .param("auditType", "REPORT")
                        .param("status", "PENDING")
                        .param("keyword", "PRODUCT-730001")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].targetId").value("PRODUCT-730001"));
    }

    @Test
    void adminChatTraceRequiresChatTracePermissionAndReturnsParticipantProfiles() throws Exception {
        createActiveUser(141L);
        createChatTraceFixture();
        jdbcTemplate.update("update user_account set avatar_url = null where id in (?, ?)", 141L, 142L);

        mvc.perform(get("/api/admin/chat/conversations")
                        .header("X-User-Id", "141")
                        .header("X-Admin-Session", issueAdminSession(141L))
                        .param("keyword", "CHAT-141-142")
                        .param("limit", "20"))
                .andExpect(status().isForbidden());

        grantPermission(141L, "audit:read");

        mvc.perform(get("/api/admin/chat/conversations")
                        .header("X-User-Id", "141")
                        .header("X-Admin-Session", issueAdminSession(141L))
                        .param("keyword", "CHAT-141-142")
                        .param("limit", "20"))
                .andExpect(status().isForbidden());

        grantPermission(141L, "chat:trace");

        mvc.perform(get("/api/admin/chat/conversations")
                        .header("X-User-Id", "141")
                        .header("X-Admin-Session", issueAdminSession(141L))
                        .param("keyword", "CHAT-141-142")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].conversationNo").value("CHAT-141-142"))
                .andExpect(jsonPath("$.data[0].owner.nickname").value("私聊用户141"))
                .andExpect(jsonPath("$.data[0].owner.avatarUrl").value("/assets/profile/default-avatar-goddess.png"))
                .andExpect(jsonPath("$.data[0].owner.videoVerified").value(false))
                .andExpect(jsonPath("$.data[0].peer.avatarUrl").value("/assets/profile/default-avatar-god.png"))
                .andExpect(jsonPath("$.data[0].peer.city").value("上海"));

        mvc.perform(get("/api/admin/chat/conversations")
                        .header("X-User-Id", "141")
                        .header("X-Admin-Session", issueAdminSession(141L))
                        .param("userId", "142")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].peer.userId").value(142));
    }

    @Test
    void adminChatTraceParticipantVideoBadgeRequiresUploadedApprovedSellerVideoIdentity() throws Exception {
        createActiveUser(145L);
        grantPermission(145L, "chat:trace");
        upsertChatUser(146L, "U-CHAT-146", "真实认证卖家146", "女", "广州", "SELLER", true);
        upsertChatUser(147L, "U-CHAT-147", "私聊买家147", "男", "深圳", "BUYER", false);
        insertUploadedVideoIdentityForAdminTrace(146L, "/uploads/video-identity/chat-trace-approved-146.mp4");
        jdbcTemplate.update("""
                insert into im_conversation (conversation_no, owner_user_id, peer_user_id, conversation_type, last_seq, last_message_summary, created_at, updated_at)
                values (?, ?, ?, 'SINGLE', 1, '认证口径测试', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "CHAT-146-147", 146L, 147L);

        mvc.perform(get("/api/admin/chat/conversations")
                        .header("X-User-Id", "145")
                        .header("X-Admin-Session", issueAdminSession(145L))
                        .param("keyword", "CHAT-146-147")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].owner.videoVerified").value(true))
                .andExpect(jsonPath("$.data[0].peer.videoVerified").value(false));
    }

    @Test
    void adminChatTraceMessagesIncludeVoiceContentAndRejectUnsafeFilters() throws Exception {
        createActiveUser(143L);
        grantPermission(143L, "chat:trace");
        Long conversationId = createChatTraceFixture();

        mvc.perform(get("/api/admin/chat/conversations/" + conversationId + "/messages")
                        .header("X-User-Id", "143")
                        .header("X-Admin-Session", issueAdminSession(143L))
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversation.conversationNo").value("CHAT-141-142"))
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.oldestSeq").value(1))
                .andExpect(jsonPath("$.data.nextBeforeSeq").doesNotExist())
                .andExpect(jsonPath("$.data.messages[0].revoked").value(false))
                .andExpect(jsonPath("$.data.messages[1].messageType").value("VOICE"))
                .andExpect(jsonPath("$.data.messages[1].revoked").value(true))
                .andExpect(jsonPath("$.data.messages[1].revokedAt").exists())
                .andExpect(jsonPath("$.data.messages[1].contentJson").value(org.hamcrest.Matchers.containsString("/uploads/chat-voice/141/trace.webm")));

        mvc.perform(get("/api/admin/chat/conversations")
                        .header("X-User-Id", "143")
                        .header("X-Admin-Session", issueAdminSession(143L))
                        .param("keyword", "preview-chat")
                        .param("limit", "20"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/admin/chat/conversations")
                        .header("X-User-Id", "143")
                        .header("X-Admin-Session", issueAdminSession(143L))
                .param("userId", "0")
                        .param("limit", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminChatTraceMediaRequiresChatTracePermissionAndServesOnlyCanonicalChatUploads() throws Exception {
        createActiveUser(148L);
        Long conversationId = createChatTraceFixture();
        Long voiceMessageId = jdbcTemplate.queryForObject("select id from im_message where message_no = ?", Long.class, "MSG-TRACE-2");
        Path voiceFile = mediaRoot.resolve("uploads/chat-voice/141/trace.webm");
        Files.createDirectories(voiceFile.getParent());
        Files.write(voiceFile, "WEBM-TRACE".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mvc.perform(get("/api/admin/chat/media")
                        .header("X-User-Id", "148")
                        .header("X-Admin-Session", issueAdminSession(148L))
                        .param("conversationId", String.valueOf(conversationId))
                        .param("messageId", String.valueOf(voiceMessageId))
                        .param("messageNo", "MSG-TRACE-2")
                        .param("url", "/uploads/chat-voice/141/trace.webm"))
                .andExpect(status().isForbidden());

        grantPermission(148L, "audit:read");

        mvc.perform(get("/api/admin/chat/media")
                        .header("X-User-Id", "148")
                        .header("X-Admin-Session", issueAdminSession(148L))
                        .param("conversationId", String.valueOf(conversationId))
                        .param("messageId", String.valueOf(voiceMessageId))
                        .param("messageNo", "MSG-TRACE-2")
                        .param("url", "/uploads/chat-voice/141/trace.webm"))
                .andExpect(status().isForbidden());

        grantPermission(148L, "chat:trace");

        jdbcTemplate.update("update media_upload_ticket set status = 'ISSUED' where storage_url = ?", "/uploads/chat-voice/141/trace.webm");
        mvc.perform(get("/api/admin/chat/media")
                        .header("X-User-Id", "148")
                        .header("X-Admin-Session", issueAdminSession(148L))
                        .param("conversationId", String.valueOf(conversationId))
                        .param("messageId", String.valueOf(voiceMessageId))
                        .param("messageNo", "MSG-TRACE-2")
                        .param("url", "/uploads/chat-voice/141/trace.webm"))
                .andExpect(status().isBadRequest());
        jdbcTemplate.update("update media_upload_ticket set status = 'UPLOADED' where storage_url = ?", "/uploads/chat-voice/141/trace.webm");

        mvc.perform(get("/api/admin/chat/media")
                        .header("X-User-Id", "148")
                        .header("X-Admin-Session", issueAdminSession(148L))
                        .param("conversationId", String.valueOf(conversationId))
                        .param("messageId", String.valueOf(voiceMessageId))
                        .param("messageNo", "MSG-TRACE-2")
                        .param("url", "/uploads/chat-voice/141/trace.webm"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", org.hamcrest.Matchers.startsWith("audio/webm")));

        Files.delete(voiceFile);
        createSymlinkToOutsideFile(voiceFile, "OUTSIDE-CHAT-TRACE");
        mvc.perform(get("/api/admin/chat/media")
                        .header("X-User-Id", "148")
                        .header("X-Admin-Session", issueAdminSession(148L))
                        .param("conversationId", String.valueOf(conversationId))
                        .param("messageId", String.valueOf(voiceMessageId))
                        .param("messageNo", "MSG-TRACE-2")
                        .param("url", "/uploads/chat-voice/141/trace.webm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        Files.delete(voiceFile);
        Files.write(voiceFile, "WEBM-TRACE".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mvc.perform(get("/api/admin/chat/media")
                        .header("X-User-Id", "148")
                        .header("X-Admin-Session", issueAdminSession(148L))
                        .param("conversationId", String.valueOf(conversationId))
                        .param("messageId", String.valueOf(voiceMessageId))
                        .param("messageNo", "MSG-TRACE-2")
                        .param("url", "/uploads/chat-voice/141/other.webm"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/admin/chat/media")
                        .header("X-User-Id", "148")
                        .header("X-Admin-Session", issueAdminSession(148L))
                        .param("conversationId", String.valueOf(conversationId))
                        .param("messageId", String.valueOf(voiceMessageId))
                        .param("messageNo", "MSG-TRACE-1")
                        .param("url", "/uploads/chat-voice/141/trace.webm"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/admin/chat/media")
                        .header("X-User-Id", "148")
                        .header("X-Admin-Session", issueAdminSession(148L))
                        .param("conversationId", String.valueOf(conversationId))
                        .param("messageId", String.valueOf(voiceMessageId))
                        .param("messageNo", "MSG-TRACE-2")
                        .param("url", "/uploads/chat-voice/141/%2e%2e/secret.webm"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/admin/chat/media")
                        .header("X-User-Id", "148")
                        .header("X-Admin-Session", issueAdminSession(148L))
                        .param("conversationId", String.valueOf(conversationId))
                        .param("messageId", String.valueOf(voiceMessageId))
                        .param("messageNo", "MSG-TRACE-2")
                        .param("url", "/uploads/video-identity/141/public.mp4"))
                .andExpect(status().isBadRequest());

        Integer logCount = jdbcTemplate.queryForObject("""
                select count(1)
                from admin_audit_log
                where action = 'CHAT_TRACE_MEDIA_VIEW'
                  and operator_id = 148
                  and target_id = ?
                  and summary not like '%/uploads/%'
                """, Integer.class, conversationId + ":" + voiceMessageId + ":MSG-TRACE-2");
        org.junit.jupiter.api.Assertions.assertEquals(1, logCount);
    }

    @Test
    void adminChatTraceMediaUsesUploadedTicketContentTypeInsteadOfUrlSuffix() throws Exception {
        createActiveUser(155L);
        grantPermission(155L, "chat:trace");
        Long conversationId = createChatTraceFixture();
        jdbcTemplate.update("""
                insert into im_message (message_no, conversation_id, conversation_no, server_seq, client_msg_id, client_key, sender_id, receiver_id, message_type, content_json, created_at, updated_at)
                values (?, ?, ?, 3, ?, ?, ?, ?, 'VOICE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "MSG-TRACE-VOICE-MP3-SUFFIX", conversationId, "CHAT-141-142", "trace-voice-mp3-suffix", conversationId + ":141:trace-voice-mp3-suffix", 141L, 142L,
                "{\"url\":\"/uploads/chat-voice/141/trace-webm-suffix.mp3\",\"durationMs\":1800,\"sizeBytes\":4096,\"mimeType\":\"audio/webm\"}");
        jdbcTemplate.update("""
                merge into media_upload_ticket (ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at)
                key(ticket_no)
                values (?, ?, 'CHAT_VOICE', ?, 'audio/webm', 4096, ?, 'hash', 'UPLOADED', CURRENT_TIMESTAMP, DATEADD('DAY', 1, CURRENT_TIMESTAMP))
                """, "TICKET-CHAT-VOICE-MP3-SUFFIX", 141L, "trace-webm-suffix.mp3", "/uploads/chat-voice/141/trace-webm-suffix.mp3");
        Long voiceMessageId = jdbcTemplate.queryForObject("select id from im_message where message_no = ?", Long.class, "MSG-TRACE-VOICE-MP3-SUFFIX");
        Path voiceFile = mediaRoot.resolve("uploads/chat-voice/141/trace-webm-suffix.mp3");
        Files.createDirectories(voiceFile.getParent());
        Files.write(voiceFile, "WEBM-BY-CONTENT-TYPE".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mvc.perform(get("/api/admin/chat/media")
                        .header("X-User-Id", "155")
                        .header("X-Admin-Session", issueAdminSession(155L))
                        .param("conversationId", String.valueOf(conversationId))
                        .param("messageId", String.valueOf(voiceMessageId))
                        .param("messageNo", "MSG-TRACE-VOICE-MP3-SUFFIX")
                        .param("url", "/uploads/chat-voice/141/trace-webm-suffix.mp3"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", org.hamcrest.Matchers.startsWith("audio/webm")));
    }

    @Test
    void adminVideoIdentityEvidenceAuditLogDoesNotExposeRawMediaPath() throws Exception {
        createActiveUser(153L);
        createActiveUser(154L);
        grantPermission(153L, "audit:read");
        String videoUrl = "/uploads/video-identity/154/identity.mp4";
        Path videoFile = mediaRoot.resolve("uploads/video-identity/154/identity.mp4");
        Files.createDirectories(videoFile.getParent());
        Files.write(videoFile, minimalMp4WithDurationSeconds(10));
        insertUploadedVideoIdentityForAdminTrace(154L, videoUrl);

        mvc.perform(get("/api/admin/audit/AU-CHAT-TRACE-VIDEO-154/video-evidence")
                        .header("X-User-Id", "153")
                        .header("X-Admin-Session", issueAdminSession(153L)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", org.hamcrest.Matchers.startsWith("video/mp4")));

        Files.delete(videoFile);
        createSymlinkToOutsideFile(videoFile, "OUTSIDE-VIDEO-EVIDENCE");
        mvc.perform(get("/api/admin/audit/AU-CHAT-TRACE-VIDEO-154/video-evidence")
                        .header("X-User-Id", "153")
                        .header("X-Admin-Session", issueAdminSession(153L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        Files.delete(videoFile);
        Files.write(videoFile, minimalMp4WithDurationSeconds(10));

        Integer logCount = jdbcTemplate.queryForObject("""
                select count(1)
                from admin_audit_log
                where action = 'VIDEO_IDENTITY_MEDIA_VIEW'
                  and operator_id = 153
                  and target_id = 'AU-CHAT-TRACE-VIDEO-154'
                  and summary not like '%/uploads/%'
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, logCount);
    }

    @Test
    void adminVideoIdentityEvidenceUsesUploadedTicketContentType() throws Exception {
        createActiveUser(156L);
        createActiveUser(157L);
        grantPermission(156L, "audit:read");
        String videoUrl = "/uploads/video-identity/157/identity-mov.mov";
        Path videoFile = mediaRoot.resolve("uploads/video-identity/157/identity-mov.mov");
        Files.createDirectories(videoFile.getParent());
        Files.write(videoFile, minimalMp4WithDurationSeconds(10));
        insertUploadedVideoIdentityForAdminTrace(157L, videoUrl, "video/quicktime");

        mvc.perform(get("/api/admin/audit/AU-CHAT-TRACE-VIDEO-157/video-evidence")
                        .header("X-User-Id", "156")
                        .header("X-Admin-Session", issueAdminSession(156L)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", org.hamcrest.Matchers.startsWith("video/quicktime")));
    }

    @Test
    void adminVideoIdentityEvidenceRejectsDirtyUploadedTicketContentType() throws Exception {
        createActiveUser(162L);
        createActiveUser(163L);
        grantPermission(162L, "audit:read");
        String videoUrl = "/uploads/video-identity/163/identity-dirty.mp4";
        Path videoFile = mediaRoot.resolve("uploads/video-identity/163/identity-dirty.mp4");
        Files.createDirectories(videoFile.getParent());
        Files.write(videoFile, minimalMp4WithDurationSeconds(10));
        insertUploadedVideoIdentityForAdminTrace(163L, videoUrl, "text/plain");

        mvc.perform(get("/api/admin/audit/AU-CHAT-TRACE-VIDEO-163/video-evidence")
                .header("X-User-Id", "162")
                .header("X-Admin-Session", issueAdminSession(162L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bad request"));
    }

    @Test
    void adminVideoIdentityApprovalRequiresSameAdminToWatchEvidenceProgressFirst() throws Exception {
        createActiveUser(158L);
        createActiveUser(159L);
        createActiveUser(160L);
        createActiveUser(161L);
        grantPermission(158L, "audit:read");
        grantPermission(158L, "audit:review");
        grantPermission(160L, "audit:review");
        grantPermission(160L, "audit:read");
        grantPermission(161L, "audit:read");
        String videoUrl = "/uploads/video-identity/159/pending-identity.mp4";
        Path videoFile = mediaRoot.resolve("uploads/video-identity/159/pending-identity.mp4");
        Files.createDirectories(videoFile.getParent());
        Files.write(videoFile, minimalMp4WithDurationSeconds(10));
        insertPendingUploadedVideoIdentityAudit(159L, "AU-VIDEO-PENDING-159", videoUrl, "video/mp4");

        mvc.perform(post("/api/admin/audit/AU-VIDEO-PENDING-159/approve")
                        .header("X-User-Id", "158")
                        .header("X-Admin-Session", issueAdminSession(158L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("video identity evidence review required"));

        mvc.perform(get("/api/admin/audit/AU-VIDEO-PENDING-159/video-evidence")
                        .header("X-User-Id", "161")
                        .header("X-Admin-Session", issueAdminSession(161L)))
                .andExpect(status().isOk());

        markLatestVideoIdentityMediaViewAsElapsed("AU-VIDEO-PENDING-159", 161L, 9);

        mvc.perform(post("/api/admin/audit/AU-VIDEO-PENDING-159/video-evidence/progress")
                        .header("X-User-Id", "161")
                        .header("X-Admin-Session", issueAdminSession(161L))
                        .contentType("application/json")
                        .content("{\"durationSeconds\":10,\"currentTimeSeconds\":9,\"watchedRatio\":0.9}"))
                .andExpect(status().isForbidden());

        Integer readOnlyWatchedLogs = jdbcTemplate.queryForObject("""
                select count(1)
                from admin_audit_log
                where action = 'VIDEO_IDENTITY_MEDIA_WATCHED'
                  and operator_id = 161
                  and target_id = 'AU-VIDEO-PENDING-159'
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, readOnlyWatchedLogs);

        mvc.perform(get("/api/admin/audit/AU-VIDEO-PENDING-159/video-evidence")
                        .header("X-User-Id", "160")
                        .header("X-Admin-Session", issueAdminSession(160L)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/admin/audit/AU-VIDEO-PENDING-159/video-evidence/progress")
                        .header("X-User-Id", "160")
                        .header("X-Admin-Session", issueAdminSession(160L))
                        .contentType("application/json")
                        .content("{\"durationSeconds\":10,\"currentTimeSeconds\":9,\"watchedRatio\":0.9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("video watch progress not elapsed"));

        markLatestVideoIdentityMediaViewAsElapsed("AU-VIDEO-PENDING-159", 160L, 9);

        mvc.perform(post("/api/admin/audit/AU-VIDEO-PENDING-159/video-evidence/progress")
                        .header("X-User-Id", "160")
                        .header("X-Admin-Session", issueAdminSession(160L))
                        .contentType("application/json")
                        .content("{\"durationSeconds\":1,\"currentTimeSeconds\":1,\"watchedRatio\":1,\"ended\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("video watch progress invalid"));

        mvc.perform(post("/api/admin/audit/AU-VIDEO-PENDING-159/video-evidence/progress")
                        .header("X-User-Id", "160")
                        .header("X-Admin-Session", issueAdminSession(160L))
                        .contentType("application/json")
                        .content("{\"durationSeconds\":10,\"currentTimeSeconds\":9,\"watchedRatio\":0.9}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/admin/audit/AU-VIDEO-PENDING-159/approve")
                        .header("X-User-Id", "158")
                        .header("X-Admin-Session", issueAdminSession(158L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("video identity evidence review required"));

        mvc.perform(get("/api/admin/audit/AU-VIDEO-PENDING-159/video-evidence")
                        .header("X-User-Id", "158")
                        .header("X-Admin-Session", issueAdminSession(158L)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/admin/audit/AU-VIDEO-PENDING-159/video-evidence/progress")
                        .header("X-User-Id", "158")
                        .header("X-Admin-Session", issueAdminSession(158L))
                        .contentType("application/json")
                        .content("{\"durationSeconds\":10,\"currentTimeSeconds\":7,\"watchedRatio\":0.7}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("video watch progress insufficient"));

        mvc.perform(post("/api/admin/audit/AU-VIDEO-PENDING-159/approve")
                        .header("X-User-Id", "158")
                        .header("X-Admin-Session", issueAdminSession(158L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("video identity evidence review required"));

        mvc.perform(post("/api/admin/audit/AU-VIDEO-PENDING-159/video-evidence/progress")
                        .header("X-User-Id", "158")
                        .header("X-Admin-Session", issueAdminSession(158L))
                        .contentType("application/json")
                        .content("{\"durationSeconds\":10,\"currentTimeSeconds\":8,\"watchedRatio\":0.8}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("video watch progress not elapsed"));

        mvc.perform(post("/api/admin/audit/AU-VIDEO-PENDING-159/video-evidence/progress")
                        .header("X-User-Id", "158")
                        .header("X-Admin-Session", issueAdminSession(158L))
                        .contentType("application/json")
                        .content("{\"durationSeconds\":10,\"currentTimeSeconds\":4,\"watchedRatio\":0.9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("video watch progress invalid"));

        markLatestVideoIdentityMediaViewAsElapsed("AU-VIDEO-PENDING-159", 158L, 8);

        mvc.perform(post("/api/admin/audit/AU-VIDEO-PENDING-159/video-evidence/progress")
                        .header("X-User-Id", "158")
                        .header("X-Admin-Session", issueAdminSession(158L))
                        .contentType("application/json")
                        .content("{\"durationSeconds\":10,\"currentTimeSeconds\":8,\"watchedRatio\":0.8}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/admin/audit/AU-VIDEO-PENDING-159/approve")
                        .header("X-User-Id", "158")
                        .header("X-Admin-Session", issueAdminSession(158L))
                        .contentType("application/json")
                        .content("{\"remark\":\"本人视频一致\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(AuditApplicationService.STATUS_APPROVED));

        Integer watchedLogs = jdbcTemplate.queryForObject("""
                select count(1)
                from admin_audit_log
                where action = 'VIDEO_IDENTITY_MEDIA_WATCHED'
                  and operator_id = 158
                  and target_id = 'AU-VIDEO-PENDING-159'
                  and summary not like '%/uploads/%'
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, watchedLogs);
    }

    @Test
    void adminVideoIdentityRejectDoesNotRequireEvidenceView() throws Exception {
        createActiveUser(163L);
        createActiveUser(164L);
        grantPermission(163L, "audit:review");
        String videoUrl = "/uploads/video-identity/164/pending-reject-identity.mp4";
        insertPendingUploadedVideoIdentityAudit(164L, "AU-VIDEO-PENDING-164", videoUrl, "video/mp4");

        mvc.perform(post("/api/admin/audit/AU-VIDEO-PENDING-164/reject")
                        .header("X-User-Id", "163")
                        .header("X-Admin-Session", issueAdminSession(163L))
                        .contentType("application/json")
                        .content("{\"remark\":\"画面不清晰\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(AuditApplicationService.STATUS_REJECTED));
    }

    @Test
    void adminChatTraceMessagesSupportBeforeSeqPaginationAndPersistViewAuditLog() throws Exception {
        createActiveUser(144L);
        grantPermission(144L, "chat:trace");
        Long conversationId = createChatTraceFixture();
        jdbcTemplate.update("update im_conversation set last_seq = 4, last_message_summary = ? where id = ?", "第4条", conversationId);
        jdbcTemplate.update("""
                insert into im_message (message_no, conversation_id, conversation_no, server_seq, client_msg_id, client_key, sender_id, receiver_id, message_type, content_json, created_at, updated_at)
                values (?, ?, ?, 3, ?, ?, ?, ?, 'TEXT', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "MSG-TRACE-3", conversationId, "CHAT-141-142", "trace-text-3", conversationId + ":142:trace-text-3", 142L, 141L, "{\"text\":\"第三条\"}");
        jdbcTemplate.update("""
                insert into im_message (message_no, conversation_id, conversation_no, server_seq, client_msg_id, client_key, sender_id, receiver_id, message_type, content_json, created_at, updated_at)
                values (?, ?, ?, 4, ?, ?, ?, ?, 'TEXT', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "MSG-TRACE-4", conversationId, "CHAT-141-142", "trace-text-4", conversationId + ":141:trace-text-4", 141L, 142L, "{\"text\":\"第四条\"}");

        mvc.perform(get("/api/admin/chat/conversations/" + conversationId + "/messages")
                        .header("X-User-Id", "144")
                        .header("X-Admin-Session", issueAdminSession(144L))
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[0].serverSeq").value(3))
                .andExpect(jsonPath("$.data.messages[1].serverSeq").value(4))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.oldestSeq").value(3))
                .andExpect(jsonPath("$.data.nextBeforeSeq").value(3));

        mvc.perform(get("/api/admin/chat/conversations/" + conversationId + "/messages")
                        .header("X-User-Id", "144")
                        .header("X-Admin-Session", issueAdminSession(144L))
                        .param("limit", "2")
                        .param("beforeSeq", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[0].serverSeq").value(1))
                .andExpect(jsonPath("$.data.messages[1].serverSeq").value(2))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.oldestSeq").value(1))
                .andExpect(jsonPath("$.data.nextBeforeSeq").doesNotExist());

        Integer viewLogs = jdbcTemplate.queryForObject("""
                select count(1)
                from admin_audit_log
                where action = 'CHAT_TRACE_VIEW'
                  and operator_id = 144
                  and target_type = 'CHAT_CONVERSATION'
                  and target_id = ?
                  and result = 'SUCCESS'
                  and summary not like '%你好，后台可追溯%'
                """, Integer.class, String.valueOf(conversationId));
        org.junit.jupiter.api.Assertions.assertEquals(2, viewLogs);

        mvc.perform(get("/api/admin/chat/conversations/" + conversationId + "/messages")
                        .header("X-User-Id", "144")
                        .header("X-Admin-Session", issueAdminSession(144L))
                        .param("limit", "2")
                        .param("beforeSeq", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminChatTraceMessagesWithoutChatTracePermissionDoesNotPersistViewAuditLog() throws Exception {
        createActiveUser(145L);
        Long conversationId = createChatTraceFixture();

        mvc.perform(get("/api/admin/chat/conversations/" + conversationId + "/messages")
                        .header("X-User-Id", "145")
                        .header("X-Admin-Session", issueAdminSession(145L))
                        .param("limit", "20"))
                .andExpect(status().isForbidden());

        Integer viewLogs = jdbcTemplate.queryForObject("""
                select count(1)
                from admin_audit_log
                where action = 'CHAT_TRACE_VIEW'
                  and operator_id = 145
                  and target_id = ?
                """, Integer.class, String.valueOf(conversationId));
        org.junit.jupiter.api.Assertions.assertEquals(0, viewLogs);
    }

    @Test
    void adminReportEvidenceRequiresAuditReadAndBoundAuditUrl() throws Exception {
        createActiveUser(149L);
        String reportEvidenceUrl = "/uploads/report-evidence/200/report-proof.png";
        Path reportEvidenceFile = mediaRoot.resolve("uploads/report-evidence/200/report-proof.png");
        Files.createDirectories(reportEvidenceFile.getParent());
        Files.write(reportEvidenceFile, "PNG-REPORT".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jdbcTemplate.update("""
                insert into audit_record (audit_no,audit_type,user_id,target_type,target_id,reason,description,status,created_at)
                values (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """, "AU-REPORT-EVIDENCE-1", AuditApplicationService.AUDIT_TYPE_REPORT, 200L, "COMMUNITY_POST", "POST-200", "举报", "举报凭证：" + reportEvidenceUrl, AuditApplicationService.STATUS_PENDING);
        insertUploadedMediaTicket("TICKET-REPORT-EVIDENCE-1", 200L, "REPORT_EVIDENCE", "report-proof.png", "image/png", reportEvidenceUrl);

        mvc.perform(get("/api/admin/audit/AU-REPORT-EVIDENCE-1/report-evidence")
                        .header("X-User-Id", "149")
                        .header("X-Admin-Session", issueAdminSession(149L))
                        .param("url", reportEvidenceUrl))
                .andExpect(status().isForbidden());

        grantPermission(149L, "audit:read");

        jdbcTemplate.update("update media_upload_ticket set status = 'ISSUED' where storage_url = ?", reportEvidenceUrl);
        mvc.perform(get("/api/admin/audit/AU-REPORT-EVIDENCE-1/report-evidence")
                        .header("X-User-Id", "149")
                        .header("X-Admin-Session", issueAdminSession(149L))
                        .param("url", reportEvidenceUrl))
                .andExpect(status().isBadRequest());
        jdbcTemplate.update("update media_upload_ticket set status = 'UPLOADED' where storage_url = ?", reportEvidenceUrl);

        mvc.perform(get("/api/admin/audit/AU-REPORT-EVIDENCE-1/report-evidence")
                        .header("X-User-Id", "149")
                        .header("X-Admin-Session", issueAdminSession(149L))
                        .param("url", reportEvidenceUrl))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", org.hamcrest.Matchers.startsWith("image/png")));

        Files.delete(reportEvidenceFile);
        createSymlinkToOutsideFile(reportEvidenceFile, "OUTSIDE-REPORT-EVIDENCE");
        mvc.perform(get("/api/admin/audit/AU-REPORT-EVIDENCE-1/report-evidence")
                        .header("X-User-Id", "149")
                        .header("X-Admin-Session", issueAdminSession(149L))
                        .param("url", reportEvidenceUrl))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        Files.delete(reportEvidenceFile);
        Files.write(reportEvidenceFile, "PNG-REPORT".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mvc.perform(get("/api/admin/audit/AU-REPORT-EVIDENCE-1/report-evidence")
                        .header("X-User-Id", "149")
                        .header("X-Admin-Session", issueAdminSession(149L))
                        .param("url", "/uploads/report-evidence/200/other.png"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/admin/audit/AU-REPORT-EVIDENCE-1/report-evidence")
                        .header("X-User-Id", "149")
                        .header("X-Admin-Session", issueAdminSession(149L))
                        .param("url", "/uploads/report-evidence/200/%2e%2e/secret.png"))
                .andExpect(status().isBadRequest());

        Integer logCount = jdbcTemplate.queryForObject("""
                select count(1)
                from admin_audit_log
                where action = 'REPORT_EVIDENCE_MEDIA_VIEW'
                  and operator_id = 149
                  and target_id = 'AU-REPORT-EVIDENCE-1'
                  and summary not like '%/uploads/%'
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, logCount);
    }

    @Test
    void adminAfterSalesEvidenceRequiresAfterSalesReadAndBoundRecordUrl() throws Exception {
        createActiveUser(150L);
        createActiveUser(201L);
        createActiveUser(202L);
        String afterSalesEvidenceUrl = "/uploads/evidence/after-sales/201/as-proof.jpg";
        Path afterSalesEvidenceFile = mediaRoot.resolve("uploads/evidence/after-sales/201/as-proof.jpg");
        Files.createDirectories(afterSalesEvidenceFile.getParent());
        Files.write(afterSalesEvidenceFile, "JPG-AFTER-SALES".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jdbcTemplate.update("""
                insert into trade_order (order_no, product_id, goods_id, product_no, product_title, trade_rule_snapshot, buyer_id, seller_id, amount, order_status, accepted_trade_rule, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PAID', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "ORDER-AS-EVIDENCE-1", 1L, 1L, "P-AS-EVIDENCE-1", "售后凭证商品", "{}", 201L, 202L, new BigDecimal("28.00"));
        jdbcTemplate.update("""
                insert into after_sales_record (after_sales_no, order_no, applicant_id, after_sales_type, refund_amount, reason, description, evidence_urls, after_sales_status, created_at, updated_at)
                values (?, ?, ?, 'REFUND', ?, '商品问题', '售后凭证测试', ?, 'PENDING_REVIEW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "AS-ADMIN-20260608-0001", "ORDER-AS-EVIDENCE-1", 201L, new BigDecimal("8.00"), afterSalesEvidenceUrl);
        insertUploadedMediaTicket("TICKET-AFTER-SALES-EVIDENCE-1", 201L, "AFTER_SALES_EVIDENCE", "as-proof.jpg", "image/jpeg", afterSalesEvidenceUrl);

        mvc.perform(get("/api/admin/after-sales/AS-ADMIN-20260608-0001/evidence")
                        .header("X-User-Id", "150")
                        .header("X-Admin-Session", issueAdminSession(150L))
                        .param("url", afterSalesEvidenceUrl))
                .andExpect(status().isForbidden());

        grantPermission(150L, "after-sales:read");

        jdbcTemplate.update("update media_upload_ticket set owner_user_id = ? where storage_url = ?", 202L, afterSalesEvidenceUrl);
        mvc.perform(get("/api/admin/after-sales/AS-ADMIN-20260608-0001/evidence")
                        .header("X-User-Id", "150")
                        .header("X-Admin-Session", issueAdminSession(150L))
                        .param("url", afterSalesEvidenceUrl))
                .andExpect(status().isBadRequest());
        jdbcTemplate.update("update media_upload_ticket set owner_user_id = ? where storage_url = ?", 201L, afterSalesEvidenceUrl);

        mvc.perform(get("/api/admin/after-sales/AS-ADMIN-20260608-0001/evidence")
                        .header("X-User-Id", "150")
                        .header("X-Admin-Session", issueAdminSession(150L))
                        .param("url", afterSalesEvidenceUrl))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", org.hamcrest.Matchers.startsWith("image/jpeg")));

        Files.delete(afterSalesEvidenceFile);
        createSymlinkToOutsideFile(afterSalesEvidenceFile, "OUTSIDE-AFTER-SALES-EVIDENCE");
        mvc.perform(get("/api/admin/after-sales/AS-ADMIN-20260608-0001/evidence")
                        .header("X-User-Id", "150")
                        .header("X-Admin-Session", issueAdminSession(150L))
                        .param("url", afterSalesEvidenceUrl))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        Files.delete(afterSalesEvidenceFile);
        Files.write(afterSalesEvidenceFile, "JPG-AFTER-SALES".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mvc.perform(get("/api/admin/after-sales/AS-ADMIN-20260608-0001/evidence")
                        .header("X-User-Id", "150")
                        .header("X-Admin-Session", issueAdminSession(150L))
                        .param("url", "/uploads/evidence/after-sales/201/other.jpg"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/admin/after-sales/AS-ADMIN-20260608-0001/evidence")
                        .header("X-User-Id", "150")
                        .header("X-Admin-Session", issueAdminSession(150L))
                        .param("url", "/uploads/evidence/after-sales/201/..%2fsecret.jpg"))
                .andExpect(status().isBadRequest());

        Integer logCount = jdbcTemplate.queryForObject("""
                select count(1)
                from admin_audit_log
                where action = 'AFTER_SALES_EVIDENCE_MEDIA_VIEW'
                  and operator_id = 150
                  and target_id = 'AS-ADMIN-20260608-0001'
                  and summary not like '%/uploads/%'
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, logCount);
    }

    @Test
    void adminCommunityTraceRequiresAuditReadAndReturnsPostComments() throws Exception {
        createActiveUser(151L);
        Long postId = createCommunityTraceFixture();

        mvc.perform(get("/api/admin/community/posts")
                        .header("X-User-Id", "151")
                        .header("X-Admin-Session", issueAdminSession(151L))
                        .param("keyword", "社区追溯标题")
                        .param("limit", "20"))
                .andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertEquals(0,
                jdbcTemplate.queryForObject("select count(1) from admin_audit_log where action = ? and operator_id = ?", Integer.class, "COMMUNITY_TRACE_LIST", 151L));

        grantPermission(151L, "audit:read");

        mvc.perform(get("/api/admin/community/posts")
                        .header("X-User-Id", "151")
                        .header("X-Admin-Session", issueAdminSession(151L))
                        .param("keyword", "社区追溯标题")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].postId").value(postId))
                .andExpect(jsonPath("$.data[0].authorName").value("社区作者161"));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbcTemplate.queryForObject("select count(1) from admin_audit_log where action = ? and operator_id = ? and target_type = ? and target_id = ?", Integer.class, "COMMUNITY_TRACE_LIST", 151L, "COMMUNITY_POST_LIST", "author=-;keyword=社区追溯标题;limit=20"));

        mvc.perform(get("/api/admin/community/posts/" + postId)
                        .header("X-User-Id", "151")
                        .header("X-Admin-Session", issueAdminSession(151L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("社区追溯标题"))
                .andExpect(jsonPath("$.data.comments.length()").value(1))
                .andExpect(jsonPath("$.data.comments[0].authorName").value("社区评论者162"));

        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbcTemplate.queryForObject("select count(1) from admin_audit_log where action = ? and operator_id = ? and target_type = ? and target_id = ?", Integer.class, "COMMUNITY_TRACE_VIEW", 151L, "COMMUNITY_POST", String.valueOf(postId)));
    }

    @Test
    void adminCommunityTraceRejectsUnsafeKeywordAndAuthorId() throws Exception {
        createActiveUser(152L);
        grantPermission(152L, "audit:read");
        createCommunityTraceFixture();

        mvc.perform(get("/api/admin/community/posts")
                        .header("X-User-Id", "152")
                        .header("X-Admin-Session", issueAdminSession(152L))
                        .param("keyword", "preview-post")
                        .param("limit", "20"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/admin/community/posts")
                        .header("X-User-Id", "152")
                        .header("X-Admin-Session", issueAdminSession(152L))
                        .param("authorId", "0")
                        .param("limit", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCommunityPostBlockRequiresAuditReviewAndWritesAuditLog() throws Exception {
        createActiveUser(153L);
        grantPermission(153L, "audit:read");
        Long postId = createCommunityTraceFixture();

        mvc.perform(post("/api/admin/community/posts/" + postId + "/block")
                        .header("X-User-Id", "153")
                        .header("X-Admin-Session", issueAdminSession(153L))
                        .contentType("application/json")
                        .content("{\"reason\":\"社区内容违规\"}"))
                .andExpect(status().isForbidden());

        grantPermission(153L, "audit:review");

        mvc.perform(post("/api/admin/community/posts/" + postId + "/block")
                        .header("X-User-Id", "153")
                        .header("X-Admin-Session", issueAdminSession(153L))
                        .contentType("application/json")
                        .content("{\"reason\":\"preview reason\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/admin/community/posts/" + postId + "/block")
                        .header("X-User-Id", "153")
                        .header("X-Admin-Session", issueAdminSession(153L))
                        .contentType("application/json")
                        .content("{\"reason\":\"社区内容违规\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(postId))
                .andExpect(jsonPath("$.data.status").value("BLOCKED"));

        org.junit.jupiter.api.Assertions.assertEquals("BLOCKED",
                jdbcTemplate.queryForObject("select status from community_post where id = ?", String.class, postId));
        org.junit.jupiter.api.Assertions.assertEquals(153L,
                jdbcTemplate.queryForObject("select operator_id from admin_audit_log where action = ? and target_id = ?", Long.class, "COMMUNITY_POST_BLOCK", String.valueOf(postId)));
        org.junit.jupiter.api.Assertions.assertEquals("社区内容违规",
                jdbcTemplate.queryForObject("select summary from admin_audit_log where action = ? and target_id = ?", String.class, "COMMUNITY_POST_BLOCK", String.valueOf(postId)));

        mvc.perform(post("/api/admin/community/posts/" + postId + "/restore")
                        .header("X-User-Id", "153")
                        .header("X-Admin-Session", issueAdminSession(153L))
                        .contentType("application/json")
                        .content("{\"reason\":\"误封恢复帖子\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(postId))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        org.junit.jupiter.api.Assertions.assertEquals("PUBLISHED",
                jdbcTemplate.queryForObject("select status from community_post where id = ?", String.class, postId));
        org.junit.jupiter.api.Assertions.assertEquals(153L,
                jdbcTemplate.queryForObject("select operator_id from admin_audit_log where action = ? and target_id = ?", Long.class, "COMMUNITY_POST_RESTORE", String.valueOf(postId)));
    }

    @Test
    void adminCommunityCommentBlockRequiresAuditReviewRefreshesCountAndWritesAuditLog() throws Exception {
        createActiveUser(154L);
        grantPermission(154L, "audit:read");
        Long postId = createCommunityTraceFixture();

        mvc.perform(post("/api/admin/community/comments/CMT-162-1770000000000/block")
                        .header("X-User-Id", "154")
                        .header("X-Admin-Session", issueAdminSession(154L))
                        .contentType("application/json")
                        .content("{\"reason\":\"评论内容违规\"}"))
                .andExpect(status().isForbidden());

        grantPermission(154L, "audit:review");

        mvc.perform(post("/api/admin/community/comments/CMT-162-1770000000000/block")
                        .header("X-User-Id", "154")
                        .header("X-Admin-Session", issueAdminSession(154L))
                        .contentType("application/json")
                        .content("{\"reason\":\"评论内容违规\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(postId))
                .andExpect(jsonPath("$.data.commentCount").value(0))
                .andExpect(jsonPath("$.data.comments[0].commentNo").value("CMT-162-1770000000000"))
                .andExpect(jsonPath("$.data.comments[0].status").value("BLOCKED"));

        org.junit.jupiter.api.Assertions.assertEquals("BLOCKED",
                jdbcTemplate.queryForObject("select status from community_comment where comment_no = ?", String.class, "CMT-162-1770000000000"));
        org.junit.jupiter.api.Assertions.assertEquals(0,
                jdbcTemplate.queryForObject("select comment_count from community_post where id = ?", Integer.class, postId));
        org.junit.jupiter.api.Assertions.assertEquals(154L,
                jdbcTemplate.queryForObject("select operator_id from admin_audit_log where action = ? and target_id = ?", Long.class, "COMMUNITY_COMMENT_BLOCK", "CMT-162-1770000000000"));

        mvc.perform(post("/api/admin/community/comments/CMT-162-1770000000000/restore")
                        .header("X-User-Id", "154")
                        .header("X-Admin-Session", issueAdminSession(154L))
                        .contentType("application/json")
                        .content("{\"reason\":\"误封恢复评论\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(postId))
                .andExpect(jsonPath("$.data.commentCount").value(1))
                .andExpect(jsonPath("$.data.comments[0].commentNo").value("CMT-162-1770000000000"))
                .andExpect(jsonPath("$.data.comments[0].status").value("PUBLISHED"));

        org.junit.jupiter.api.Assertions.assertEquals("PUBLISHED",
                jdbcTemplate.queryForObject("select status from community_comment where comment_no = ?", String.class, "CMT-162-1770000000000"));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbcTemplate.queryForObject("select comment_count from community_post where id = ?", Integer.class, postId));
        org.junit.jupiter.api.Assertions.assertEquals(154L,
                jdbcTemplate.queryForObject("select operator_id from admin_audit_log where action = ? and target_id = ?", Long.class, "COMMUNITY_COMMENT_RESTORE", "CMT-162-1770000000000"));
    }

    @Test
    void adminOperatorPermissionGrantRequiresOperatorGrantPermissionAndWritesAuditLog() throws Exception {
        createActiveUser(91L);
        createActiveUser(92L);
        grantPermission(91L, "audit:read");

        mvc.perform(post("/api/admin/operators/92/permissions")
                        .header("X-User-Id", "91")
                        .header("X-Admin-Session", issueAdminSession(91L))
                        .contentType("application/json")
                        .content("{\"permissions\":[\"audit:read\",\"finance:read\"]}"))
                .andExpect(status().isForbidden());

        grantPermission(91L, "operator:grant");

        mvc.perform(post("/api/admin/operators/92/permissions")
                        .header("X-User-Id", "91")
                        .header("X-Admin-Session", issueAdminSession(91L))
                        .contentType("application/json")
                        .content("{\"permissions\":[\"audit:read\",\"finance:read\",\"audit:read\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(92))
                .andExpect(jsonPath("$.data.permissions.length()").value(2));

        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbcTemplate.queryForObject("select count(*) from admin_user_permission where user_id = ? and permission_code = ? and enabled = true", Integer.class, 92L, "audit:read"));
        org.junit.jupiter.api.Assertions.assertEquals(91L,
                jdbcTemplate.queryForObject("select operator_id from admin_audit_log where action = ? and target_id = ?", Long.class, "OPERATOR_PERMISSION_GRANT", "92"));
    }

    @Test
    void adminOperatorPermissionGrantRejectsUnknownPermissionCodesFailClosed() throws Exception {
        createActiveUser(93L);
        createActiveUser(94L);
        grantPermission(93L, "operator:grant");

        mvc.perform(post("/api/admin/operators/94/permissions")
                        .header("X-User-Id", "93")
                        .header("X-Admin-Session", issueAdminSession(93L))
                        .contentType("application/json")
                        .content("{\"permissions\":[\"root:all\"]}"))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertEquals(0,
                jdbcTemplate.queryForObject("select count(*) from admin_user_permission where user_id = ?", Integer.class, 94L));
    }

    @Test
    void adminOperatorPermissionGrantAllowsClearingAllAssignablePermissionsWithAuditLog() throws Exception {
        createActiveUser(95L);
        createActiveUser(96L);
        grantPermission(95L, "operator:grant");
        grantPermission(96L, "audit:read");
        grantPermission(96L, "finance:read");

        mvc.perform(post("/api/admin/operators/96/permissions")
                        .header("X-User-Id", "95")
                        .header("X-Admin-Session", issueAdminSession(95L))
                        .contentType("application/json")
                        .content("{\"permissions\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(96))
                .andExpect(jsonPath("$.data.permissions.length()").value(0));

        org.junit.jupiter.api.Assertions.assertEquals(0,
                jdbcTemplate.queryForObject("select count(*) from admin_user_permission where user_id = ? and enabled = true", Integer.class, 96L));
        org.junit.jupiter.api.Assertions.assertEquals(95L,
                jdbcTemplate.queryForObject("select operator_id from admin_audit_log where action = ? and target_id = ?", Long.class, "OPERATOR_PERMISSION_GRANT", "96"));
    }

    @Test
    void adminOrderListUsesPersistedOrderServiceWithOrderReadPermission() throws Exception {
        createActiveUser(71L);
        grantPermission(71L, "order:read");
        CreateProductResponse product = productApplicationService.createProduct(81L, productRequest(81L, "后台订单闭环商品", "66.00"));
        productApplicationService.approveForSale(product.getProductId());
        var order = orderApplicationService.createOrder(orderRequest(product.getProductId()), 82L);

        mvc.perform(get("/api/admin/orders")
                        .header("X-User-Id", "71")
                        .header("X-Admin-Session", issueAdminSession(71L))
                        .param("status", "ALL")
                        .param("keyword", order.getOrderNo())
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderNo").value(order.getOrderNo()))
                .andExpect(jsonPath("$.data[0].buyerId").value(82));
    }

    @Test
    void adminHomeBannerRequiresSystemConfigPermissionAndPersistsAuditLog() throws Exception {
        createActiveUser(121L);
        grantPermission(121L, "audit:read");

        mvc.perform(post("/api/admin/home/banners/1")
                        .header("X-User-Id", "121")
                        .header("X-Admin-Session", issueAdminSession(121L))
                        .contentType("application/json")
                        .content("{\"kicker\":\"首页运营\",\"title\":\"真实后台轮播\",\"description\":\"运营后台可随时替换首页轮播图。\",\"cta\":\"去看看\",\"imageUrl\":\"/uploads/home/real-banner.jpg\",\"action\":\"closet\",\"sortOrder\":10,\"enabled\":true}"))
                .andExpect(status().isForbidden());

        grantPermission(121L, "system:config");

        mvc.perform(post("/api/admin/home/banners/1")
                        .header("X-User-Id", "121")
                        .header("X-Admin-Session", issueAdminSession(121L))
                        .contentType("application/json")
                        .content("{\"kicker\":\"首页运营\",\"title\":\"真实后台轮播\",\"description\":\"运营后台可随时替换首页轮播图。\",\"cta\":\"去看看\",\"imageUrl\":\"/uploads/home/real-banner.jpg\",\"action\":\"closet\",\"sortOrder\":10,\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("真实后台轮播"))
                .andExpect(jsonPath("$.data.sizeHint").value(org.hamcrest.Matchers.containsString("750×300px")));

        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbcTemplate.queryForObject("select count(1) from admin_audit_log where action = ? and operator_id = ? and target_id = ?", Integer.class, "HOME_BANNER_UPDATE", 121L, "home-banner-1"));
    }

    @Test
    void adminHomeBannerRejectsPlaceholderOrExternalImageUrls() throws Exception {
        createActiveUser(122L);
        grantPermission(122L, "system:config");

        mvc.perform(post("/api/admin/home/banners")
                        .header("X-User-Id", "122")
                        .header("X-Admin-Session", issueAdminSession(122L))
                        .contentType("application/json")
                        .content("{\"kicker\":\"首页运营\",\"title\":\"外部轮播\",\"description\":\"运营后台轮播。\",\"cta\":\"去看看\",\"imageUrl\":\"https://example.com/banner.jpg\",\"action\":\"closet\",\"sortOrder\":40,\"enabled\":true}"))
                .andExpect(status().isBadRequest());
    }

    private void grantPermission(Long userId, String permission) {
        jdbcTemplate.update("""
                insert into admin_user_permission (user_id, permission_code, enabled, created_at, updated_at)
                values (?, ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId, permission);
    }

    private void createActiveUser(Long userId) {
        jdbcTemplate.update("""
                insert into user_account (id, user_no, phone, password_hash, nickname, status, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId, "U-ADMIN-" + userId, "1390000" + userId, "hash", "管理员" + userId);
    }

    private void createInactiveUser(Long userId) {
        jdbcTemplate.update("""
                insert into user_account (id, user_no, phone, password_hash, nickname, status, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'DISABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId, "U-ADMIN-INACTIVE-" + userId, "1391000" + userId, "hash", "停用管理员" + userId);
    }

    private Long createChatTraceFixture() {
        upsertChatUser(141L, "U-CHAT-141", "私聊用户141", "女", "杭州", "SELLER", true);
        upsertChatUser(142L, "U-CHAT-142", "私聊用户142", "男", "上海", "BUYER", false);
        Integer existing = jdbcTemplate.queryForObject("select count(1) from im_conversation where conversation_no = ?", Integer.class, "CHAT-141-142");
        if (existing != null && existing > 0) {
            return jdbcTemplate.queryForObject("select id from im_conversation where conversation_no = ?", Long.class, "CHAT-141-142");
        }
        jdbcTemplate.update("""
                insert into im_conversation (conversation_no, owner_user_id, peer_user_id, conversation_type, last_seq, last_message_summary, created_at, updated_at)
                values (?, ?, ?, 'SINGLE', 2, '[语音]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "CHAT-141-142", 141L, 142L);
        Long conversationId = jdbcTemplate.queryForObject("select id from im_conversation where conversation_no = ?", Long.class, "CHAT-141-142");
        jdbcTemplate.update("""
                insert into im_message (message_no, conversation_id, conversation_no, server_seq, client_msg_id, client_key, sender_id, receiver_id, message_type, content_json, created_at, updated_at)
                values (?, ?, ?, 1, ?, ?, ?, ?, 'TEXT', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "MSG-TRACE-1", conversationId, "CHAT-141-142", "trace-text-1", conversationId + ":141:trace-text-1", 141L, 142L, "{\"text\":\"你好，后台可追溯\"}");
        jdbcTemplate.update("""
                insert into im_message (message_no, conversation_id, conversation_no, server_seq, client_msg_id, client_key, sender_id, receiver_id, message_type, content_json, revoked, revoked_at, created_at, updated_at)
                values (?, ?, ?, 2, ?, ?, ?, ?, 'VOICE', ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "MSG-TRACE-2", conversationId, "CHAT-141-142", "trace-voice-1", conversationId + ":141:trace-voice-1", 141L, 142L,
                "{\"url\":\"/uploads/chat-voice/141/trace.webm\",\"durationMs\":1800,\"sizeBytes\":4096,\"mimeType\":\"audio/webm\"}");
        jdbcTemplate.update("""
                merge into media_upload_ticket (ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at)
                key(ticket_no)
                values (?, ?, 'CHAT_VOICE', ?, 'audio/webm', 4096, ?, 'hash', 'UPLOADED', CURRENT_TIMESTAMP, DATEADD('DAY', 1, CURRENT_TIMESTAMP))
                """, "TICKET-CHAT-VOICE-TRACE-1", 141L, "trace.webm", "/uploads/chat-voice/141/trace.webm");
        return conversationId;
    }

    private Long createCommunityTraceFixture() {
        createActiveUser(161L);
        createActiveUser(162L);
        jdbcTemplate.update("""
                insert into community_post (post_no, author_id, title, topic, content, image_urls, status, like_count, comment_count, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, 'PUBLISHED', 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "POST-161-1770000000000", 161L, "社区追溯标题", "生活日常", "社区追溯内容用于后台核查", "/uploads/community-image/161/a.jpg");
        Long postId = jdbcTemplate.queryForObject("select id from community_post where post_no = ?", Long.class, "POST-161-1770000000000");
        jdbcTemplate.update("""
                insert into community_comment (comment_no, post_id, author_id, content, status, created_at)
                values (?, ?, ?, ?, 'PUBLISHED', CURRENT_TIMESTAMP)
                """, "CMT-162-1770000000000", postId, 162L, "社区评论可追溯");
        jdbcTemplate.update("update user_account set nickname = ? where id = ?", "社区作者161", 161L);
        jdbcTemplate.update("update user_account set nickname = ? where id = ?", "社区评论者162", 162L);
        return postId;
    }

    private void upsertChatUser(Long userId, String userNo, String nickname, String gender, String city, String mainRole, boolean videoVerified) {
        Integer accountRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account WHERE id = ?", Integer.class, userId);
        if (accountRows == null || accountRows == 0) {
            jdbcTemplate.update("""
                    insert into user_account (id, user_no, phone, password_hash, nickname, avatar_url, status, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, userId, userNo, "1389000" + userId, "hash", nickname, "/uploads/avatar/" + userId + ".jpg");
        } else {
            jdbcTemplate.update("""
                    update user_account
                    set user_no = ?, nickname = ?, avatar_url = ?, status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
                    where id = ?
                    """, userNo, nickname, "/uploads/avatar/" + userId + ".jpg", userId);
        }
        Integer profileRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_profile WHERE user_id = ?", Integer.class, userId);
        if (profileRows == null || profileRows == 0) {
            jdbcTemplate.update("""
                    insert into user_profile (user_id, gender, city, identity_status, main_role, video_identity_status, video_verified)
                    values (?, ?, ?, 'VERIFIED', ?, ?, ?)
                    """, userId, gender, city, mainRole, videoVerified ? "APPROVED" : "UNVERIFIED", videoVerified);
        } else {
            jdbcTemplate.update("""
                    update user_profile
                    set gender = ?, city = ?, identity_status = 'VERIFIED', main_role = ?, video_identity_status = ?, video_verified = ?, updated_at = CURRENT_TIMESTAMP
                    where user_id = ?
                    """, gender, city, mainRole, videoVerified ? "APPROVED" : "UNVERIFIED", videoVerified, userId);
        }
    }

    private void insertUploadedVideoIdentityForAdminTrace(Long userId, String videoUrl) {
        insertUploadedVideoIdentityForAdminTrace(userId, videoUrl, "video/mp4");
    }

    private void insertUploadedVideoIdentityForAdminTrace(Long userId, String videoUrl, String contentType) {
        insertUploadedMediaTicket("TICKET-CHAT-TRACE-VIDEO-" + userId, userId, "VIDEO_IDENTITY", "chat-trace-" + userId + ".mp4", contentType, videoUrl);
        jdbcTemplate.update("""
                merge into audit_record (audit_no, audit_type, user_id, target_type, target_id, reason, description, status, created_at, reviewed_at)
                key(audit_no)
                values (?, 'VIDEO_IDENTITY', ?, 'USER', ?, ?, '后台私聊追溯认证测试', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "AU-CHAT-TRACE-VIDEO-" + userId, userId, String.valueOf(userId), videoUrl);
    }

    private void createSymlinkToOutsideFile(Path link, String content) throws Exception {
        Path outsideFile = Files.createTempFile("xiaoyuanquan-admin-media-outside", ".txt");
        Files.writeString(outsideFile, content);
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, outsideFile);
    }

    private void insertPendingUploadedVideoIdentityAudit(Long userId, String auditNo, String videoUrl, String contentType) {
        jdbcTemplate.update("""
                merge into media_upload_ticket (ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at)
                key(ticket_no)
                values (?, ?, 'VIDEO_IDENTITY', ?, ?, 1024, ?, 'hash', 'UPLOADED', CURRENT_TIMESTAMP, DATEADD('DAY', 1, CURRENT_TIMESTAMP))
                """, "TICKET-PENDING-VIDEO-" + userId, userId, "pending-" + userId + ".mp4", contentType, videoUrl);
        jdbcTemplate.update("""
                merge into user_profile (user_id, identity_status, main_role, video_identity_status, video_verified, created_at, updated_at)
                key(user_id)
                values (?, 'VERIFIED', 'BUYER', 'PENDING', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId);
        jdbcTemplate.update("""
                merge into audit_record (audit_no, audit_type, user_id, target_type, target_id, reason, description, status, created_at)
                key(audit_no)
                values (?, 'VIDEO_IDENTITY', ?, 'VIDEO_IDENTITY', ?, ?, '待审核视频认证', 'PENDING', CURRENT_TIMESTAMP)
                """, auditNo, userId, String.valueOf(userId), videoUrl);
    }

    private void insertUploadedMediaTicket(String ticketNo, Long ownerUserId, String scene, String filename, String contentType, String storageUrl) {
        jdbcTemplate.update("""
                merge into media_upload_ticket (ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at)
                key(ticket_no)
                values (?, ?, ?, ?, ?, 4096, ?, 'hash', 'UPLOADED', CURRENT_TIMESTAMP, DATEADD('DAY', 1, CURRENT_TIMESTAMP))
                """, ticketNo, ownerUserId, scene, filename, contentType, storageUrl);
    }

    private void markLatestVideoIdentityMediaViewAsElapsed(String auditNo, Long operatorId, int elapsedSeconds) {
        int updated = jdbcTemplate.update("""
                update admin_audit_log
                set created_at = DATEADD('SECOND', ?, CURRENT_TIMESTAMP)
                where id = (
                    select id
                    from admin_audit_log
                    where action = 'VIDEO_IDENTITY_MEDIA_VIEW'
                      and operator_id = ?
                      and target_type = 'AUDIT'
                      and target_id = ?
                      and result = 'SUCCESS'
                    order by created_at desc, id desc
                    limit 1
                )
                """, -Math.max(1, elapsedSeconds), operatorId, auditNo);
        org.junit.jupiter.api.Assertions.assertEquals(1, updated);
    }

    private byte[] minimalMp4WithDurationSeconds(int durationSeconds) {
        byte[] ftyp = mp4Box("ftyp", concat(
                "isom".getBytes(StandardCharsets.US_ASCII),
                ByteBuffer.allocate(4).putInt(0).array(),
                "isom".getBytes(StandardCharsets.US_ASCII)
        ));
        ByteBuffer mvhdPayload = ByteBuffer.allocate(24);
        mvhdPayload.putInt(0);
        mvhdPayload.putInt(0);
        mvhdPayload.putInt(0);
        mvhdPayload.putInt(1000);
        mvhdPayload.putInt(Math.max(1, durationSeconds) * 1000);
        mvhdPayload.putInt(0);
        byte[] mvhd = mp4Box("mvhd", mvhdPayload.array());
        byte[] hdlr = mp4Box("hdlr", concat(
                ByteBuffer.allocate(8).putInt(0).putInt(0).array(),
                "vide".getBytes(StandardCharsets.US_ASCII),
                new byte[12]
        ));
        byte[] mdia = mp4Box("mdia", hdlr);
        byte[] trak = mp4Box("trak", mdia);
        byte[] moov = mp4Box("moov", concat(mvhd, trak));
        byte[] mdat = mp4Box("mdat", new byte[]{1});
        return concat(ftyp, moov, mdat);
    }

    private byte[] mp4Box(String type, byte[] content) {
        ByteBuffer buffer = ByteBuffer.allocate(8 + content.length);
        buffer.putInt(8 + content.length);
        buffer.put(type.getBytes(StandardCharsets.US_ASCII));
        buffer.put(content);
        return buffer.array();
    }

    private byte[] concat(byte[]... arrays) {
        int size = 0;
        for (byte[] array : arrays) {
            size += array.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(size);
        for (byte[] array : arrays) {
            buffer.put(array);
        }
        return buffer.array();
    }

    private void upsertSellerProfile(Long userId) {
        Integer accountRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account WHERE id = ?", Integer.class, userId);
        if (accountRows == null || accountRows == 0) {
            createActiveUser(userId);
        }
        String videoUrl = "/uploads/video-identity/admin-seller-" + userId + ".webm";
        jdbcTemplate.update("""
                merge into media_upload_ticket (ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at)
                key(ticket_no)
                values (?, ?, 'VIDEO_IDENTITY', ?, 'video/webm', 1024, ?, 'hash', 'UPLOADED', CURRENT_TIMESTAMP, DATEADD('DAY', 1, CURRENT_TIMESTAMP))
                """, "TICKET-ADMIN-SELLER-" + userId, userId, "seller-" + userId + ".webm", videoUrl);
        jdbcTemplate.update("""
                merge into audit_record (audit_no, audit_type, user_id, target_type, target_id, reason, description, status, created_at, reviewed_at)
                key(audit_no)
                values (?, 'VIDEO_IDENTITY', ?, 'USER', ?, ?, '后台测试卖家视频认证', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "AU-ADMIN-SELLER-VIDEO-" + userId, userId, String.valueOf(userId), videoUrl);
        Integer profileRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_profile WHERE user_id = ?", Integer.class, userId);
        if (profileRows == null || profileRows == 0) {
            jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, main_role, video_identity_status, video_verified) VALUES (?, ?, ?, ?, ?)", userId, "VERIFIED", "SELLER", "APPROVED", true);
            return;
        }
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "SELLER", "APPROVED", true, userId);
    }

    private String issueAdminSession(Long userId) {
        String sessionId = "adm_" + String.format("%032x", Math.abs((userId + ":" + System.nanoTime()).hashCode()));
        jdbcTemplate.update("""
                insert into admin_session (session_id, user_id, expires_at, revoked, created_at)
                values (?, ?, DATEADD('HOUR', 1, CURRENT_TIMESTAMP), false, CURRENT_TIMESTAMP)
                """, sessionId, userId);
        return sessionId;
    }

    private CreditCommand credit(Long userId, String key, String balanceType, String amount) {
        CreditCommand command = new CreditCommand();
        command.setUserId(userId);
        command.setIdempotencyKey(key);
        command.setBizType("TEST");
        command.setBizNo(key);
        command.setBalanceType(balanceType);
        command.setAmount(new BigDecimal(amount));
        return command;
    }

    private CreateWithdrawalRequest withdrawal(String amount) {
        return withdrawalForUser(41L, amount);
    }

    private CreateWithdrawalRequest withdrawalForUser(Long userId, String amount) {
        Long payoutAccountId = walletLedgerService.bindPayoutAccount(userId, payoutAccount("ALIPAY", "Alice", "6222020202020208088"));
        upsertVerifiedIdentity(userId);
        CreateWithdrawalRequest request = new CreateWithdrawalRequest();
        request.setAmount(new BigDecimal(amount));
        request.setPayoutAccountId(payoutAccountId);
        request.setRemark("提现申请");
        return request;
    }

    private PayoutAccountRequest payoutAccount(String paymentMethod, String accountName, String accountNo) {
        PayoutAccountRequest request = new PayoutAccountRequest();
        request.setPaymentMethod(paymentMethod);
        request.setAccountName(accountName);
        request.setAccountNo(accountNo);
        return request;
    }

    private void upsertVerifiedIdentity(Long userId) {
        Integer profileRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_profile WHERE user_id = ?", Integer.class, userId);
        if (profileRows == null || profileRows == 0) {
            jdbcTemplate.update(
                    "INSERT INTO user_profile (user_id, identity_status, main_role, video_identity_status, video_verified) VALUES (?, 'VERIFIED', 'BUYER', 'UNVERIFIED', false)",
                    userId
            );
            return;
        }
        jdbcTemplate.update("UPDATE user_profile SET identity_status = 'VERIFIED', updated_at = CURRENT_TIMESTAMP WHERE user_id = ?", userId);
    }

    private CreateProductRequest productRequest(String title, String price) {
        return productRequest(61L, title, price);
    }

    private CreateProductRequest productRequest(Long sellerId, String title, String price) {
        upsertSellerProfile(sellerId);
        CreateProductRequest request = new CreateProductRequest();
        request.setTitle(title);
        request.setDescription("admin product approval test");
        request.setPrice(new BigDecimal(price));
        String uploaded = new MediaUploadTicketService(jdbcTemplate)
                .issue(sellerId, "PRODUCT_IMAGE", "image/jpeg", 300_000L, title + ".jpg")
                .storageUrl();
        jdbcTemplate.update("UPDATE media_upload_ticket SET status = 'UPLOADED' WHERE owner_user_id = ? AND storage_url = ?", sellerId, uploaded);
        request.setImageUrls(java.util.List.of(uploaded));
        return request;
    }

    private com.secondhand.platform.modules.order.application.CreateOrderRequest orderRequest(Long productId) {
        com.secondhand.platform.modules.order.application.CreateOrderRequest request = new com.secondhand.platform.modules.order.application.CreateOrderRequest();
        request.setGoodsId(productId);
        request.setAcceptedTradeRule(true);
        return request;
    }
}

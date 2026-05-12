package com.secondhand.platform.modules.admin;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.secondhand.platform.modules.aftersales.application.AfterSalesApplicationService;
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
import com.secondhand.platform.shared.web.CurrentUserResolver;
import com.secondhand.platform.shared.web.GlobalExceptionHandler;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);

        auditApplicationService = new AuditApplicationService(jdbcTemplate);
        walletLedgerService = new WalletLedgerService(jdbcTemplate);
        productApplicationService = new ProductApplicationService(jdbcTemplate, new MediaUploadTicketService(jdbcTemplate));
        orderApplicationService = new OrderApplicationService(productApplicationService, walletLedgerService, jdbcTemplate);

        AdminController controller = new AdminController(
                auditApplicationService,
                walletLedgerService,
                new LocationApplicationService(new com.secondhand.platform.modules.location.BaiduReverseGeocodeClient(), "", jdbcTemplate),
                mock(AfterSalesApplicationService.class),
                orderApplicationService,
                productApplicationService,
                mock(UserApplicationService.class),
                new AdminAccessGuard(new CurrentUserResolver(), jdbcTemplate),
                jdbcTemplate
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
    void adminDashboardRejectsSessionWhenOperatorAccountIsNotActive() throws Exception {
        createInactiveUser(14L);

        mvc.perform(get("/api/admin/dashboard")
                        .header("X-User-Id", "14")
                        .header("X-Admin-Session", issueAdminSession(14L)))
                .andExpect(status().isForbidden());
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
    void adminAfterSalesListRequiresAfterSalesReadPermission() throws Exception {
        createActiveUser(51L);
        grantPermission(51L, "audit:read");

        mvc.perform(get("/api/admin/after-sales")
                        .header("X-User-Id", "51")
                        .header("X-Admin-Session", issueAdminSession(51L))
                        .param("status", "PENDING_REVIEW")
                        .param("limit", "20"))
                .andExpect(status().isForbidden());

        grantPermission(51L, "after-sales:read");

        mvc.perform(get("/api/admin/after-sales")
                        .header("X-User-Id", "51")
                        .header("X-Admin-Session", issueAdminSession(51L))
                        .param("status", "PENDING_REVIEW")
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
    }

    @Test
    void adminProductApproveReviewsLinkedAuditRecordWithActingOperator() throws Exception {
        CreateProductResponse product = productApplicationService.createProduct(63L, productRequest(63L, "后台商品审核联动", "45.67"));
        jdbcTemplate.update("""
                insert into audit_record (audit_no,audit_type,user_id,target_type,target_id,reason,description,status,created_at)
                values (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """, "AU-20260511-6301", "PRODUCT", 63L, "PRODUCT", String.valueOf(product.getProductId()), "商品待审核", "后台商品审核联动", AuditApplicationService.STATUS_PENDING);
        createActiveUser(64L);
        grantPermission(64L, "audit:review");

        mvc.perform(post("/api/admin/products/" + product.getProductId() + "/approve")
                        .header("X-User-Id", "64")
                        .header("X-Admin-Session", issueAdminSession(64L)))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(AuditApplicationService.STATUS_APPROVED,
                jdbcTemplate.queryForObject("select status from audit_record where audit_no = ?", String.class, "AU-20260511-6301"));
        org.junit.jupiter.api.Assertions.assertEquals(64L,
                jdbcTemplate.queryForObject("select operator_id from admin_audit_log where target_id = ?", Long.class, "AU-20260511-6301"));
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
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderNo").value(order.getOrderNo()))
                .andExpect(jsonPath("$.data[0].buyerId").value(82));
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

    private CreateProductRequest productRequest(String title, String price) {
        return productRequest(61L, title, price);
    }

    private CreateProductRequest productRequest(Long sellerId, String title, String price) {
        CreateProductRequest request = new CreateProductRequest();
        request.setTitle(title);
        request.setDescription("admin product approval test");
        request.setPrice(new BigDecimal(price));
        String issued = new MediaUploadTicketService(jdbcTemplate)
                .issue(sellerId, "PRODUCT_IMAGE", "image/jpeg", 300_000L, title + ".jpg")
                .storageUrl();
        request.setImageUrls(java.util.List.of(issued));
        return request;
    }

    private com.secondhand.platform.modules.order.application.CreateOrderRequest orderRequest(Long productId) {
        com.secondhand.platform.modules.order.application.CreateOrderRequest request = new com.secondhand.platform.modules.order.application.CreateOrderRequest();
        request.setGoodsId(productId);
        request.setAcceptedTradeRule(true);
        return request;
    }
}

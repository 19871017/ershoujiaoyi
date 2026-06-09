package com.secondhand.platform.modules.audit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.platform.modules.audit.application.AuditApplicationService;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuditControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void legacyAuditSubmitEndpointMustNotReturnFakeSuccess() throws Exception {
        EmbeddedDatabase database = database();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        AuditController controller = new AuditController(
                new AuditApplicationService(jdbcTemplate),
                devCurrentUserResolver(jdbcTemplate)
        );
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(post("/api/audit/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reportEndpointPersistsRealAuditRecord() throws Exception {
        EmbeddedDatabase database = database();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        AuditController controller = new AuditController(
                new AuditApplicationService(jdbcTemplate),
                devCurrentUserResolver(jdbcTemplate)
        );
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        createActiveUser(jdbcTemplate, 1L);
        seedReportProduct(jdbcTemplate, "PRODUCT-100001");
        ReportRequest request = new ReportRequest();
        request.setTargetType("PRODUCT");
        request.setTargetId("PRODUCT-100001");
        request.setReason("SPAM");
        request.setDescription("商品描述明显异常");

        mvc.perform(post("/api/audit/reports")
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditType").value(AuditApplicationService.AUDIT_TYPE_REPORT))
                .andExpect(jsonPath("$.data.status").value(AuditApplicationService.STATUS_PENDING))
                .andExpect(jsonPath("$.data.targetId").value("PRODUCT-100001"));
    }

    @Test
    void reportEndpointAcceptsBackendOrderNumberTarget() throws Exception {
        EmbeddedDatabase database = database();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        AuditController controller = new AuditController(
                new AuditApplicationService(jdbcTemplate),
                devCurrentUserResolver(jdbcTemplate)
        );
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        createActiveUser(jdbcTemplate, 1L);
        seedReportOrder(jdbcTemplate, "OD-100001");
        ReportRequest request = new ReportRequest();
        request.setTargetType("ORDER");
        request.setTargetId("OD-100001");
        request.setReason("ORDER_RISK");
        request.setDescription("订单存在纠纷");

        mvc.perform(post("/api/audit/reports")
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditType").value(AuditApplicationService.AUDIT_TYPE_REPORT))
                .andExpect(jsonPath("$.data.targetType").value("ORDER"))
                .andExpect(jsonPath("$.data.targetId").value("OD-100001"));
    }

    @Test
    void reportEndpointAcceptsAfterSalesNumberTarget() throws Exception {
        EmbeddedDatabase database = database();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        AuditController controller = new AuditController(
                new AuditApplicationService(jdbcTemplate),
                devCurrentUserResolver(jdbcTemplate)
        );
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        createActiveUser(jdbcTemplate, 1L);
        seedReportOrder(jdbcTemplate, "OD-100001");
        seedReportAfterSales(jdbcTemplate, "AS-100001", "OD-100001");
        ReportRequest request = new ReportRequest();
        request.setTargetType("AFTER_SALES");
        request.setTargetId("AS-100001");
        request.setReason("AFTER_SALES_RISK");
        request.setDescription("售后处理存在争议");

        mvc.perform(post("/api/audit/reports")
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditType").value(AuditApplicationService.AUDIT_TYPE_REPORT))
                .andExpect(jsonPath("$.data.targetType").value("AFTER_SALES"))
                .andExpect(jsonPath("$.data.targetId").value("AS-100001"));
    }

    @Test
    void videoIdentityEndpointMustRejectClientSuppliedIdentityFields() throws Exception {
        EmbeddedDatabase database = database();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("INSERT INTO user_account (user_no, phone, password_hash, nickname, status) VALUES (?,?,?,?,?)", "U-VIDEO-API", "13800136666", "hash", "视频用户", "ACTIVE");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800136666");
        jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, video_identity_status, video_verified) VALUES (?,?,?,?)", userId, "UNVERIFIED", "UNVERIFIED", false);
        String videoUrl = new com.secondhand.platform.modules.media.application.MediaUploadTicketService(jdbcTemplate)
                .issue(userId, "VIDEO_IDENTITY", "video/mp4", 5_000_000L, "api-video.mp4")
                .storageUrl();
        AuditController controller = new AuditController(
                new AuditApplicationService(jdbcTemplate),
                devCurrentUserResolver(jdbcTemplate)
        );
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.secondhand.platform.shared.web.GlobalExceptionHandler())
                .build();
        String payload = """
                {"videoUrl":"%s","description":"认证","userId":999,"identityStatus":"VERIFIED","videoVerified":true,"admin":true}
                """.formatted(videoUrl);

        mvc.perform(post("/api/audit/video-identity")
                        .header("X-User-Id", String.valueOf(userId))
                        .header("X-Dev-Mode", "enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("identity fields must be server-derived"));

        Integer auditCount = jdbcTemplate.queryForObject("SELECT count(1) FROM audit_record WHERE audit_type = 'VIDEO_IDENTITY'", Integer.class);
        String identityStatus = jdbcTemplate.queryForObject("SELECT identity_status FROM user_profile WHERE user_id = ?", String.class, userId);
        String videoStatus = jdbcTemplate.queryForObject("SELECT video_identity_status FROM user_profile WHERE user_id = ?", String.class, userId);
        Boolean videoVerified = jdbcTemplate.queryForObject("SELECT video_verified FROM user_profile WHERE user_id = ?", Boolean.class, userId);
        org.junit.jupiter.api.Assertions.assertEquals(0, auditCount);
        org.junit.jupiter.api.Assertions.assertEquals("UNVERIFIED", identityStatus);
        org.junit.jupiter.api.Assertions.assertEquals("UNVERIFIED", videoStatus);
        org.junit.jupiter.api.Assertions.assertEquals(false, videoVerified);
    }

    @Test
    void realNameIdentityEndpointPersistsRealAuditAndRejectsClientSuppliedIdentityFields() throws Exception {
        EmbeddedDatabase database = database();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("INSERT INTO user_account (user_no, phone, password_hash, nickname, status) VALUES (?,?,?,?,?)", "U-REAL-API", "13800137777", "hash", "实名接口用户", "ACTIVE");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800137777");
        jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, video_identity_status, video_verified) VALUES (?,?,?,?)", userId, "UNVERIFIED", "UNVERIFIED", false);
        AuditController controller = new AuditController(
                new AuditApplicationService(jdbcTemplate),
                devCurrentUserResolver(jdbcTemplate)
        );
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.secondhand.platform.shared.web.GlobalExceptionHandler())
                .build();

        mvc.perform(post("/api/audit/real-name-identity")
                        .header("X-User-Id", String.valueOf(userId))
                        .header("X-Dev-Mode", "enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realName\":\"周小原\",\"idTail\":\"5678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditType").value(AuditApplicationService.AUDIT_TYPE_REAL_NAME_IDENTITY))
                .andExpect(jsonPath("$.data.status").value(AuditApplicationService.STATUS_PENDING));

        org.junit.jupiter.api.Assertions.assertEquals("PENDING", jdbcTemplate.queryForObject("SELECT identity_status FROM user_profile WHERE user_id = ?", String.class, userId));

        mvc.perform(post("/api/audit/real-name-identity")
                        .header("X-User-Id", String.valueOf(userId))
                        .header("X-Dev-Mode", "enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realName\":\"周小原\",\"idTail\":\"5678\",\"userId\":999,\"identityStatus\":\"VERIFIED\",\"admin\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("identity fields must be server-derived"));
    }

    private void createActiveUser(JdbcTemplate jdbcTemplate, Long userId) {
        jdbcTemplate.update("""
                INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                """, userId, "U-AUDIT-" + userId, "1393000" + userId, "hash", "审核用户" + userId);
    }

    private void seedReportProduct(JdbcTemplate jdbcTemplate, String productNo) {
        jdbcTemplate.update("""
                INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status)
                SELECT 21, 'U-AUDIT-SELLER-21', '13930000021', 'hash', '举报商品卖家', 'ACTIVE'
                WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE id = 21)
                """);
        jdbcTemplate.update("""
                INSERT INTO product_item (product_no,seller_id,title,category,price,product_status,audit_status,visible,trade_rule,created_at,updated_at)
                SELECT ?, 21, '举报目标商品', '女装', ?, 'ACTIVE', 'APPROVED', TRUE, 'offline-face-to-face-after-platform-order', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                WHERE NOT EXISTS (SELECT 1 FROM product_item WHERE product_no = ?)
                """, productNo, new BigDecimal("88.00"), productNo);
    }

    private void seedReportOrder(JdbcTemplate jdbcTemplate, String orderNo) {
        seedReportProduct(jdbcTemplate, "PRODUCT-100001");
        jdbcTemplate.update("""
                INSERT INTO trade_order (order_no,product_id,goods_id,product_no,product_title,trade_rule_snapshot,buyer_id,seller_id,amount,order_status,accepted_trade_rule,created_at,updated_at)
                SELECT ?, 100001, 100001, 'PRODUCT-100001', '举报目标订单商品', 'server-record', 1, 21, ?, 'PAID', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                WHERE NOT EXISTS (SELECT 1 FROM trade_order WHERE order_no = ?)
                """, orderNo, new BigDecimal("88.00"), orderNo);
    }

    private void seedReportAfterSales(JdbcTemplate jdbcTemplate, String afterSalesNo, String orderNo) {
        jdbcTemplate.update("""
                INSERT INTO after_sales_record (after_sales_no,order_no,applicant_id,after_sales_type,refund_amount,reason,description,evidence_urls,after_sales_status,created_at,updated_at)
                SELECT ?, ?, 1, 'REFUND_ONLY', ?, '售后纠纷', '售后举报目标描述', '', 'PENDING_REVIEW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                WHERE NOT EXISTS (SELECT 1 FROM after_sales_record WHERE after_sales_no = ?)
                """, afterSalesNo, orderNo, new BigDecimal("12.00"), afterSalesNo);
    }

    private CurrentUserResolver devCurrentUserResolver(JdbcTemplate jdbcTemplate) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        return new CurrentUserResolver(jdbcTemplate, environment);
    }

    private EmbeddedDatabase database() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
    }
}

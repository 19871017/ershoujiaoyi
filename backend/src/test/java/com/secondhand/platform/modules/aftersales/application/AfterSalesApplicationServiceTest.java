package com.secondhand.platform.modules.aftersales.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.secondhand.platform.modules.aftersales.AfterSalesResponse;
import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
import com.secondhand.platform.modules.notification.application.NotificationApplicationService;
import com.secondhand.platform.modules.order.CreateOrderResponse;
import com.secondhand.platform.modules.order.PayOrderResponse;
import com.secondhand.platform.modules.order.application.CreateOrderRequest;
import com.secondhand.platform.modules.order.application.OrderApplicationService;
import com.secondhand.platform.modules.product.CreateProductResponse;
import com.secondhand.platform.modules.product.application.CreateProductRequest;
import com.secondhand.platform.modules.product.application.ProductApplicationService;
import com.secondhand.platform.modules.wallet_ledger.application.CreditCommand;
import com.secondhand.platform.modules.wallet_ledger.application.WalletLedgerService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class AfterSalesApplicationServiceTest {
    private JdbcTemplate jdbcTemplate;
    private MediaUploadTicketService mediaUploadTicketService;
    private ProductApplicationService productService;
    private WalletLedgerService walletService;
    private OrderApplicationService orderService;
    private AfterSalesApplicationService afterSalesService;
    private NotificationApplicationService notificationService;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        mediaUploadTicketService = new MediaUploadTicketService(jdbcTemplate);
        productService = new ProductApplicationService(jdbcTemplate, mediaUploadTicketService);
        walletService = new WalletLedgerService(jdbcTemplate);
        orderService = new OrderApplicationService(productService, walletService, jdbcTemplate);
        notificationService = new NotificationApplicationService(jdbcTemplate);
        afterSalesService = new AfterSalesApplicationService(jdbcTemplate, mediaUploadTicketService, notificationService);
    }

    @Test
    void buyerCanCreatePaidOrderAfterSalesWithUploadedEvidence() {
        CreateOrderResponse order = paidOrder(7001L, "售后裙子", "89.00");
        CreateAfterSalesRequest request = afterSalesRequest(order.getOrderNo(), "89.00", List.of(evidence(7001L, "proof.jpg")));

        AfterSalesResponse response = afterSalesService.create(7001L, request);

        assertNotNull(response.getAfterSalesNo());
        assertEquals(true, response.getAfterSalesNo().matches("AS-USER-\\d{8}-\\d{12}"));
        assertEquals(order.getOrderNo(), response.getOrderNo());
        assertEquals(7001L, response.getApplicantId());
        assertEquals(1L, response.getSellerId());
        assertEquals("PENDING_REVIEW", response.getStatus());
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from after_sales_record where after_sales_no = ?", Integer.class, response.getAfterSalesNo()));

        AfterSalesResponse detail = afterSalesService.detail(response.getAfterSalesNo(), 7001L);
        assertEquals(response.getAfterSalesNo(), detail.getAfterSalesNo());
        assertEquals(1L, detail.getSellerId());
        AfterSalesResponse adminDetail = afterSalesService.getAdminDetail(response.getAfterSalesNo());
        assertEquals(response.getAfterSalesNo(), adminDetail.getAfterSalesNo());
        assertEquals(1L, adminDetail.getSellerId());
        assertEquals("REFUND_ONLY", detail.getAfterSalesType());
        assertEquals("成色不符", detail.getReason());
        assertEquals(1, detail.getEvidenceUrls().size());
        assertEquals(1, notificationService.listNotifications(7001L, "ORDER", 20).stream()
                .filter(item -> item.title().equals("售后申请已提交"))
                .filter(item -> item.targetUrl().equals("/pages/after-sales/detail/index?afterSalesNo=" + response.getAfterSalesNo() + "&orderNo=" + response.getOrderNo()))
                .count());
        assertThrows(IllegalArgumentException.class, () -> afterSalesService.detail(response.getAfterSalesNo(), 7002L));
    }

    @Test
    void adminReviewShouldNotifyApplicantWithAfterSalesDetailTarget() {
        CreateOrderResponse order = paidOrder(7401L, "售后通知外套", "119.00");
        CreateAfterSalesRequest request = afterSalesRequest(order.getOrderNo(), "80.00", List.of(evidence(7401L, "review.jpg")));
        AfterSalesResponse response = afterSalesService.create(7401L, request);

        AfterSalesResponse reviewed = afterSalesService.adminReview(response.getAfterSalesNo(), "APPROVED", 9901L, "同意处理");

        assertEquals("APPROVED", reviewed.getStatus());
        assertEquals(1L, reviewed.getSellerId());
        assertEquals(1, notificationService.listNotifications(7401L, "ORDER", 20).stream()
                .filter(item -> item.title().equals("售后审核已通过"))
                .filter(item -> item.targetUrl().equals("/pages/after-sales/detail/index?afterSalesNo=" + response.getAfterSalesNo() + "&orderNo=" + response.getOrderNo()))
                .count());
    }

    @Test
    void buyerCanCreatePlatformArbitrationFromAfterSalesCoordinationCopy() {
        CreateOrderResponse order = paidOrder(7301L, "售后协调外套", "129.00");
        CreateAfterSalesRequest request = afterSalesRequest(order.getOrderNo(), "100.00", List.of(evidence(7301L, "coordination.jpg")));
        request.setAfterSalesType("售后协调");

        AfterSalesResponse response = afterSalesService.create(7301L, request);

        assertEquals("PLATFORM_ARBITRATION", response.getAfterSalesType());
    }

    @Test
    void afterSalesShouldRejectUnpaidWrongBuyerDuplicateAndUnsafeEvidence() {
        CreateOrderResponse unpaid = pendingOrder(7101L, "未支付鞋子", "59.00");
        CreateAfterSalesRequest valid = afterSalesRequest(unpaid.getOrderNo(), "50.00", List.of(evidence(7101L, "proof.jpg")));
        assertThrows(IllegalArgumentException.class, () -> afterSalesService.create(7101L, valid));
        assertThrows(IllegalArgumentException.class, () -> afterSalesService.create(7102L, valid));

        CreateOrderResponse paid = paidOrder(7201L, "已付袜子", "39.00");
        CreateAfterSalesRequest unsafe = afterSalesRequest(paid.getOrderNo(), "20.00", List.of("https://img.example.com/fake.jpg"));
        assertThrows(IllegalArgumentException.class, () -> afterSalesService.create(7201L, unsafe));
        CreateAfterSalesRequest malformed = afterSalesRequest(paid.getOrderNo(), "20.00", List.of("/uploads/evidence/after-sales/7201/%2e%2e/proof.jpg"));
        assertThrows(IllegalArgumentException.class, () -> afterSalesService.create(7201L, malformed));
        CreateAfterSalesRequest tooMuch = afterSalesRequest(paid.getOrderNo(), "40.00", List.of(evidence(7201L, "too-much.jpg")));
        assertThrows(IllegalArgumentException.class, () -> afterSalesService.create(7201L, tooMuch));

        CreateAfterSalesRequest first = afterSalesRequest(paid.getOrderNo(), "20.00", List.of(evidence(7201L, "ok.jpg")));
        afterSalesService.create(7201L, first);
        CreateAfterSalesRequest second = afterSalesRequest(paid.getOrderNo(), "20.00", List.of(evidence(7201L, "ok2.jpg")));
        assertThrows(IllegalArgumentException.class, () -> afterSalesService.create(7201L, second));
    }

    private CreateOrderResponse pendingOrder(Long buyerId, String title, String price) {
        CreateProductResponse product = approvedProduct(title, price);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setGoodsId(product.getProductId());
        request.setAcceptedTradeRule(true);
        return orderService.createOrder(request, buyerId);
    }

    private CreateOrderResponse paidOrder(Long buyerId, String title, String price) {
        CreateOrderResponse order = pendingOrder(buyerId, title, price);
        recharge(buyerId, "500.00");
        PayOrderResponse paid = orderService.payOrder(order.getOrderNo(), buyerId);
        assertEquals("PAID", paid.getStatus());
        return order;
    }

    private CreateProductResponse approvedProduct(String title, String price) {
        upsertSellerProfile(1L);
        CreateProductRequest request = new CreateProductRequest();
        request.setTitle(title);
        request.setDescription("售后测试商品");
        request.setPrice(new BigDecimal(price));
        request.setImageUrls(List.of(uploadedMedia(1L, "PRODUCT_IMAGE", title + ".jpg")));
        CreateProductResponse response = productService.createProduct(1L, request);
        productService.approveForSale(response.getProductId());
        return response;
    }

    private void upsertSellerProfile(Long userId) {
        Integer accountRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account WHERE id = ?", Integer.class, userId);
        if (accountRows == null || accountRows == 0) {
            jdbcTemplate.update("INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status) VALUES (?, ?, ?, ?, ?, ?)", userId, "U-AS-" + userId, "1380015" + String.format("%04d", userId), "hash", "售后卖家" + userId, "ACTIVE");
        }
        Integer profileRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_profile WHERE user_id = ?", Integer.class, userId);
        if (profileRows == null || profileRows == 0) {
            jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, main_role, video_identity_status, video_verified) VALUES (?, ?, ?, ?, ?)", userId, "VERIFIED", "SELLER", "APPROVED", true);
            ensureSellerVideoCertification(userId);
            return;
        }
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "SELLER", "APPROVED", true, userId);
        ensureSellerVideoCertification(userId);
    }

    private void ensureSellerVideoCertification(Long userId) {
        String videoUrl = "/uploads/video-identity/" + userId + "/after-sales-test.mp4";
        jdbcTemplate.update("""
                INSERT INTO media_upload_ticket (ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at)
                SELECT ?, ?, 'VIDEO_IDENTITY', 'after-sales-test.mp4', 'video/mp4', 1024, ?, 'hash', 'UPLOADED', CURRENT_TIMESTAMP, DATEADD('HOUR', 1, CURRENT_TIMESTAMP)
                WHERE NOT EXISTS (SELECT 1 FROM media_upload_ticket WHERE storage_url = ?)
                """, "VIDEO-AS-" + userId, userId, videoUrl, videoUrl);
        jdbcTemplate.update("""
                INSERT INTO audit_record (audit_no, audit_type, user_id, target_type, target_id, reason, description, status, reviewed_at)
                SELECT ?, 'VIDEO_IDENTITY', ?, 'USER', ?, ?, '测试卖家视频认证通过', 'APPROVED', CURRENT_TIMESTAMP
                WHERE NOT EXISTS (SELECT 1 FROM audit_record WHERE audit_no = ?)
                """, "AUDIT-VIDEO-AS-" + userId, userId, String.valueOf(userId), videoUrl, "AUDIT-VIDEO-AS-" + userId);
    }

    private CreateAfterSalesRequest afterSalesRequest(String orderNo, String amount, List<String> evidenceUrls) {
        CreateAfterSalesRequest request = new CreateAfterSalesRequest();
        request.setOrderNo(orderNo);
        request.setAfterSalesType("仅退款");
        request.setRefundAmount(new BigDecimal(amount));
        request.setReason("成色不符");
        request.setDescription("收到后发现成色与描述不符");
        request.setEvidenceUrls(evidenceUrls);
        return request;
    }

    private String evidence(Long userId, String filename) {
        return uploadedMedia(userId, "AFTER_SALES_EVIDENCE", filename);
    }

    private String uploadedMedia(Long userId, String scene, String filename) {
        String storageUrl = mediaUploadTicketService.issue(userId, scene, "image/jpeg", 300_000L, filename).storageUrl();
        jdbcTemplate.update("UPDATE media_upload_ticket SET status = 'UPLOADED' WHERE owner_user_id = ? AND storage_url = ?", userId, storageUrl);
        return storageUrl;
    }

    private void recharge(Long userId, String amount) {
        CreditCommand command = new CreditCommand();
        command.setUserId(userId);
        command.setAmount(new BigDecimal(amount));
        command.setBalanceType("RECHARGE");
        command.setBizType("TEST_RECHARGE");
        command.setBizNo("TEST-AS-" + userId);
        command.setIdempotencyKey("TEST_AS_RECHARGE:" + userId + ':' + amount);
        walletService.credit(command);
    }
}

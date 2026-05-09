package com.secondhand.platform.modules.aftersales.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.secondhand.platform.modules.aftersales.AfterSalesResponse;
import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
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
        afterSalesService = new AfterSalesApplicationService(jdbcTemplate, mediaUploadTicketService);
    }

    @Test
    void buyerCanCreatePaidOrderAfterSalesWithIssuedEvidence() {
        CreateOrderResponse order = paidOrder(7001L, "售后裙子", "89.00");
        CreateAfterSalesRequest request = afterSalesRequest(order.getOrderNo(), "89.00", List.of(evidence(7001L, "proof.jpg")));

        AfterSalesResponse response = afterSalesService.create(7001L, request);

        assertNotNull(response.getAfterSalesNo());
        assertEquals(order.getOrderNo(), response.getOrderNo());
        assertEquals(7001L, response.getApplicantId());
        assertEquals("PENDING_REVIEW", response.getStatus());
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from after_sales_record where after_sales_no = ?", Integer.class, response.getAfterSalesNo()));

        AfterSalesResponse detail = afterSalesService.detail(response.getAfterSalesNo(), 7001L);
        assertEquals(response.getAfterSalesNo(), detail.getAfterSalesNo());
        assertEquals("REFUND_ONLY", detail.getAfterSalesType());
        assertEquals("成色不符", detail.getReason());
        assertEquals(1, detail.getEvidenceUrls().size());
        assertThrows(IllegalArgumentException.class, () -> afterSalesService.detail(response.getAfterSalesNo(), 7002L));
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
        CreateProductRequest request = new CreateProductRequest();
        request.setTitle(title);
        request.setDescription("售后测试商品");
        request.setPrice(new BigDecimal(price));
        request.setImageUrls(List.of(mediaUploadTicketService.issue(1L, "PRODUCT_IMAGE", "image/jpeg", 300_000L, title + ".jpg").storageUrl()));
        CreateProductResponse response = productService.createProduct(1L, request);
        productService.approveForSale(response.getProductId());
        return response;
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
        return mediaUploadTicketService.issue(userId, "AFTER_SALES_EVIDENCE", "image/jpeg", 300_000L, filename).storageUrl();
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

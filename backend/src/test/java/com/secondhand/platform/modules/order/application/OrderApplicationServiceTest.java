package com.secondhand.platform.modules.order.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.secondhand.platform.modules.order.CreateOrderResponse;
import com.secondhand.platform.modules.order.PayOrderResponse;
import com.secondhand.platform.modules.order.OrderDetailResponse;
import com.secondhand.platform.modules.order.OrderListItemResponse;
import com.secondhand.platform.modules.order.ShipOrderResponse;
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

class OrderApplicationServiceTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private ProductApplicationService productService;
    private WalletLedgerService walletService;
    private OrderApplicationService orderService;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        productService = new ProductApplicationService(jdbcTemplate, new com.secondhand.platform.modules.media.application.MediaUploadTicketService(jdbcTemplate));
        walletService = new WalletLedgerService(jdbcTemplate);
        orderService = new OrderApplicationService(productService, walletService, jdbcTemplate);
    }

    @Test
    void createOrderShouldPersistPendingOrderAndLockProductAcrossServiceRecreation() {
        CreateProductResponse product = approvedProduct("碎花连衣裙", "168.00");

        CreateOrderResponse order = orderService.createOrder(orderRequest(product.getProductId()), 2001L);

        assertEquals("PENDING_PAY", order.getStatus());
        assertNotNull(order.getOrderNo());
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from trade_order where order_no = ?", Integer.class, order.getOrderNo()));

        OrderApplicationService reloaded = new OrderApplicationService(productService, walletService, jdbcTemplate);
        assertThrows(IllegalArgumentException.class, () -> reloaded.createOrder(orderRequest(product.getProductId()), 2002L));
    }

    @Test
    void payOrderShouldPersistPaidStatusAndReplayAfterServiceRecreation() {
        CreateProductResponse product = approvedProduct("白色玛丽珍鞋", "99.00");
        CreateOrderResponse order = orderService.createOrder(orderRequest(product.getProductId()), 3001L);
        recharge(3001L, "200.00");

        PayOrderResponse paid = orderService.payOrder(order.getOrderNo(), 3001L);
        assertEquals("PAID", paid.getStatus());
        assertNotNull(paid.getLedgerNo());

        OrderApplicationService reloaded = new OrderApplicationService(productService, walletService, jdbcTemplate);
        PayOrderResponse replay = reloaded.payOrder(order.getOrderNo(), 3001L);

        assertEquals("PAID", replay.getStatus());
        assertTrue(replay.isIdempotentReplay());
        assertEquals(paid.getLedgerNo(), replay.getLedgerNo());
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from wallet_ledger_entry where biz_no = ? and biz_type = 'ORDER_PAYMENT'", Integer.class, order.getOrderNo()));
    }

    @Test
    void sellerShouldShipPaidOrderAndPersistLogistics() {
        CreateProductResponse product = approvedProduct("发货测试裙子", "89.00");
        CreateOrderResponse order = orderService.createOrder(orderRequest(product.getProductId()), 5301L);
        recharge(5301L, "200.00");
        orderService.payOrder(order.getOrderNo(), 5301L);
        ShipOrderRequest request = new ShipOrderRequest();
        request.setShippingType("EXPRESS");
        request.setShippingCompany("顺丰速运");
        request.setTrackingNo("SF53010001");
        request.setRemark("已消毒包装");

        ShipOrderResponse shipped = orderService.shipOrder(order.getOrderNo(), 1L, request);
        OrderDetailResponse detail = orderService.detailOrder(order.getOrderNo(), 5301L);

        assertEquals("SHIPPED", shipped.getStatus());
        assertEquals("EXPRESS", shipped.getShippingType());
        assertEquals("顺丰速运", detail.getShippingCompany());
        assertEquals("SF53010001", detail.getTrackingNo());
        assertNotNull(detail.getShippedAt());
        assertThrows(IllegalStateException.class, () -> orderService.shipOrder(order.getOrderNo(), 1L, request));
    }

    @Test
    void shipOrderShouldRejectWrongSellerAndUnpaidOrder() {
        CreateProductResponse product = approvedProduct("未付款不能发货", "39.00");
        CreateOrderResponse order = orderService.createOrder(orderRequest(product.getProductId()), 5401L);
        ShipOrderRequest request = new ShipOrderRequest();
        request.setShippingType("EXPRESS");
        request.setShippingCompany("圆通快递");
        request.setTrackingNo("YT54010001");

        assertThrows(IllegalStateException.class, () -> orderService.shipOrder(order.getOrderNo(), 1L, request));
        recharge(5401L, "200.00");
        orderService.payOrder(order.getOrderNo(), 5401L);
        assertThrows(IllegalArgumentException.class, () -> orderService.shipOrder(order.getOrderNo(), 5402L, request));
    }

    @Test
    void buyerShouldConfirmReceiptAndSettleToSellerWithdrawableBalance() {
        CreateProductResponse product = approvedProduct("确认收货裙子", "109.00");
        CreateOrderResponse order = orderService.createOrder(orderRequest(product.getProductId()), 5501L);
        recharge(5501L, "200.00");
        orderService.payOrder(order.getOrderNo(), 5501L);
        ShipOrderRequest ship = new ShipOrderRequest();
        ship.setShippingType("EXPRESS");
        ship.setShippingCompany("中通快递");
        ship.setTrackingNo("ZT55010001");
        orderService.shipOrder(order.getOrderNo(), 1L, ship);

        OrderDetailResponse completed = orderService.confirmReceipt(order.getOrderNo(), 5501L);
        OrderDetailResponse replay = orderService.confirmReceipt(order.getOrderNo(), 5501L);

        assertEquals("COMPLETED", completed.getStatus());
        assertNotNull(completed.getCompletedAt());
        assertEquals("COMPLETED", replay.getStatus());
        assertEquals(new BigDecimal("109.00"), jdbcTemplate.queryForObject("select withdrawable_balance from wallet_account where user_id = ?", BigDecimal.class, 1L));
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from wallet_ledger_entry where biz_no = ? and biz_type = 'ORDER_SETTLEMENT'", Integer.class, order.getOrderNo()));
    }

    @Test
    void confirmReceiptShouldRejectWrongBuyerUnshippedAndActiveAfterSales() {
        CreateProductResponse product = approvedProduct("确认拒绝裙子", "79.00");
        CreateOrderResponse order = orderService.createOrder(orderRequest(product.getProductId()), 5601L);
        recharge(5601L, "200.00");
        orderService.payOrder(order.getOrderNo(), 5601L);

        assertThrows(IllegalStateException.class, () -> orderService.confirmReceipt(order.getOrderNo(), 5601L));
        ShipOrderRequest ship = new ShipOrderRequest();
        ship.setShippingType("MEETUP");
        ship.setRemark("已约定校门口当面交付");
        orderService.shipOrder(order.getOrderNo(), 1L, ship);
        assertThrows(IllegalArgumentException.class, () -> orderService.confirmReceipt(order.getOrderNo(), 5602L));
        jdbcTemplate.update("insert into after_sales_record (after_sales_no, order_no, applicant_id, after_sales_type, refund_amount, reason, description, evidence_urls, after_sales_status, created_at, updated_at) values (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                "AS-CONFIRM-5601", order.getOrderNo(), 5601L, "REFUND_ONLY", new BigDecimal("20.00"), "成色不符", "确认收货前售后", "/uploads/evidence/after-sales/5601/demo.jpg", "PENDING_REVIEW");
        assertThrows(IllegalStateException.class, () -> orderService.confirmReceipt(order.getOrderNo(), 5601L));
    }

    @Test
    void detailOrderShouldReturnOnlyParticipantOrderAndAfterSalesNo() {
        CreateProductResponse product = approvedProduct("订单详情裙子", "69.00");
        CreateOrderResponse order = orderService.createOrder(orderRequest(product.getProductId()), 5201L);
        recharge(5201L, "200.00");
        orderService.payOrder(order.getOrderNo(), 5201L);
        jdbcTemplate.update("insert into after_sales_record (after_sales_no, order_no, applicant_id, after_sales_type, refund_amount, reason, description, evidence_urls, after_sales_status, created_at, updated_at) values (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                "AS-DETAIL-5201", order.getOrderNo(), 5201L, "REFUND_ONLY", new BigDecimal("20.00"), "成色不符", "订单详情售后", "/uploads/evidence/after-sales/5201/demo.jpg", "PENDING_REVIEW");

        OrderDetailResponse buyerDetail = orderService.detailOrder(order.getOrderNo(), 5201L);
        OrderDetailResponse sellerDetail = orderService.detailOrder(order.getOrderNo(), 1L);

        assertEquals(order.getOrderNo(), buyerDetail.getOrderNo());
        assertEquals("PAID", buyerDetail.getStatus());
        assertEquals("AS-DETAIL-5201", buyerDetail.getAfterSalesNo());
        assertEquals(product.getTitle(), buyerDetail.getProductTitle());
        assertEquals("AS-DETAIL-5201", sellerDetail.getAfterSalesNo());
        assertThrows(IllegalArgumentException.class, () -> orderService.detailOrder(order.getOrderNo(), 9999L));
    }

    @Test
    void listOrdersShouldReturnCurrentUserOrdersAndAfterSalesNo() {
        CreateProductResponse product = approvedProduct("订单列表裙子", "79.00");
        CreateOrderResponse order = orderService.createOrder(orderRequest(product.getProductId()), 5101L);
        recharge(5101L, "200.00");
        orderService.payOrder(order.getOrderNo(), 5101L);
        jdbcTemplate.update("insert into after_sales_record (after_sales_no, order_no, applicant_id, after_sales_type, refund_amount, reason, description, evidence_urls, after_sales_status, created_at, updated_at) values (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                "AS-LIST-5101", order.getOrderNo(), 5101L, "REFUND_ONLY", new BigDecimal("30.00"), "成色不符", "订单列表售后", "/uploads/evidence/after-sales/5101/demo.jpg", "PENDING_REVIEW");

        List<OrderListItemResponse> buyerOrders = orderService.listOrders(5101L, "buyer", "REFUNDING");
        List<OrderListItemResponse> sellerOrders = orderService.listOrders(1L, "seller", "ALL");

        assertEquals(1, buyerOrders.size());
        assertEquals(order.getOrderNo(), buyerOrders.get(0).getOrderNo());
        assertEquals("AS-LIST-5101", buyerOrders.get(0).getAfterSalesNo());
        assertEquals("buyer", buyerOrders.get(0).getRole());
        assertTrue(sellerOrders.stream().anyMatch(item -> order.getOrderNo().equals(item.getOrderNo())));
        assertEquals(0, orderService.listOrders(5999L, "buyer", "ALL").size());
    }

    @Test
    void payOrderShouldRejectWrongBuyerAfterReload() {
        CreateProductResponse product = approvedProduct("针织小外套", "59.00");
        CreateOrderResponse order = orderService.createOrder(orderRequest(product.getProductId()), 4001L);

        OrderApplicationService reloaded = new OrderApplicationService(productService, walletService, jdbcTemplate);

        assertThrows(IllegalArgumentException.class, () -> reloaded.payOrder(order.getOrderNo(), 4002L));
    }

    private CreateProductResponse approvedProduct(String title, String price) {
        CreateProductRequest request = new CreateProductRequest();
        request.setTitle(title);
        request.setDescription("订单测试商品");
        request.setPrice(new BigDecimal(price));
        String issued = new com.secondhand.platform.modules.media.application.MediaUploadTicketService(jdbcTemplate)
                .issue(1L, "PRODUCT_IMAGE", "image/jpeg", 300_000L, title + ".jpg")
                .storageUrl();
        request.setImageUrls(List.of(issued));
        CreateProductResponse response = productService.createProduct(request);
        productService.approveForSale(response.getProductId());
        return response;
    }

    private CreateOrderRequest orderRequest(Long productId) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setGoodsId(productId);
        request.setAcceptedTradeRule(true);
        return request;
    }

    private void recharge(Long userId, String amount) {
        CreditCommand command = new CreditCommand();
        command.setUserId(userId);
        command.setAmount(new BigDecimal(amount));
        command.setBalanceType("RECHARGE");
        command.setBizType("TEST_RECHARGE");
        command.setBizNo("TEST-" + userId);
        command.setIdempotencyKey("TEST_RECHARGE:" + userId + ':' + amount);
        walletService.credit(command);
    }
}
